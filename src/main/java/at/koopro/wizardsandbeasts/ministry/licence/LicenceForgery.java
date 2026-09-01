package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * What happens when somebody hands a Ministry official a licence they wrote themselves.
 *
 * <p>A forgery is not checked at the gate; it is checked <em>at the counter</em>. Every dealing with
 * an official is another chance for the ink to be looked at too closely
 * ({@link LicenceRules#FORGERY_DETECTION_CHANCE}), which is what makes a forged licence a decision
 * with a shape: it works, it keeps working, and the longer you lean on it the more certain it is that
 * one day it does not.
 *
 * <p>Detection is terminal. The scroll is struck off on the spot, the offence goes on the file as
 * {@link MagicalOffence#FORGED_DOCUMENTS} — the only paperwork crime in the mod that is arrestable —
 * and every official within {@link LicenceRules#ALERT_RADIUS} is turned on the holder. Nothing
 * un-revokes a licence; the remedy is a new one, honestly obtained.
 */
@NullMarked
public final class LicenceForgery {

    private LicenceForgery() {}

    /**
     * Rolls one inspection against the document {@code player} is presenting for {@code type}.
     *
     * <p>Safe to call on every Ministry interaction: a genuine licence, a missing one and an
     * already-revoked one all return {@code false} without touching the random source, so the roll is
     * only ever spent on a forgery that is currently working.
     *
     * @return {@code true} if the forgery was detected on this interaction
     */
    public static boolean inspect(ServerPlayer player, LicenseType type) {
        ItemStack document = MinistryLicences.findDocument(player, type);
        if (document == null) {
            return false;
        }
        return inspect(player, document);
    }

    /** Inspects one specific document. */
    public static boolean inspect(ServerPlayer player, ItemStack document) {
        LicenseData data = MinistryLicences.read(document);
        if (data == null || !data.forged() || data.revoked()) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (level.getRandom().nextFloat() >= LicenceRules.FORGERY_DETECTION_CHANCE) {
            return false;
        }
        strike(player, level, document, data);
        return true;
    }

    /** Marks the scroll, files the crime and raises the alarm. */
    private static void strike(ServerPlayer player, ServerLevel level, ItemStack document, LicenseData data) {
        document.set(ModDataComponents.LICENSE_DATA.get(), data.revokedCopy());

        PlayerFeedback.refuse(player,
                Component.translatable("ministry.wizards_and_beasts.licence.forgery.detected"),
                Component.translatable("ministry.wizards_and_beasts.licence.forgery.detected.detail"));
        player.displayClientMessage(
                Component.translatable("ministry.wizards_and_beasts.licence.revoked_banner")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
        level.sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getEyeY(), player.getZ(), 12, 0.3, 0.3, 0.3, 0.01);

        // Filed after the scroll is struck, so a listener that reads the document during the report
        // sees the revocation rather than the state it was in a moment ago.
        TraceService.report(player, MagicalOffence.FORGED_DOCUMENTS);
        alertOfficials(player, level);
    }

    /**
     * Turns every nearby official on the holder.
     *
     * <p>Only {@link Mob}s can actually be given a target; anything else in the tag is notified in the
     * only way a non-AI entity can be, which is not at all. That is deliberate rather than an
     * oversight — the alert is defined by the tag so a future Auror mob inherits it, and a tag member
     * with no AI simply has nothing to do about it.
     */
    private static void alertOfficials(ServerPlayer player, ServerLevel level) {
        AABB range = player.getBoundingBox().inflate(LicenceRules.ALERT_RADIUS);
        List<LivingEntity> officials = level.getEntitiesOfClass(LivingEntity.class, range,
                entity -> entity != player && entity.isAlive()
                        && entity.getType().is(MinistryLicenceTags.MINISTRY_OFFICIALS));
        for (LivingEntity official : officials) {
            if (official instanceof Mob mob) {
                mob.setTarget(player);
                mob.setLastHurtByMob(player);
            }
        }
        if (!officials.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("ministry.wizards_and_beasts.licence.forgery.alerted", officials.size())
                            .withStyle(ChatFormatting.RED), false);
        }
    }

    /**
     * Stamps a scroll as a forgery. The one place the flag is set, so "how does a player get a forged
     * licence" has a single answer to point at when that content lands.
     */
    public static ItemStack forge(ItemStack scroll) {
        LicenseData data = MinistryLicences.read(scroll);
        if (data != null) {
            scroll.set(ModDataComponents.LICENSE_DATA.get(), data.forgedCopy());
        }
        return scroll;
    }
}
