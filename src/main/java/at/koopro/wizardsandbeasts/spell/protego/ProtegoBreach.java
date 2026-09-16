package at.koopro.wizardsandbeasts.spell.protego;

import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.network.spell.SpellDataDeltaS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.lib.SpellHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What it costs to have a shield broken rather than let it fade.
 *
 * <p>Breaking a ward is the only ending with consequences, and they are what makes the top tiers a
 * decision instead of a strictly better button: the wider the shape, the longer the caster stands
 * there unable to raise another, and Horribilis bites the hand that raised it.
 *
 * <ul>
 *   <li>a shockwave that throws whoever was pressing the ward off it — the breach buys a moment</li>
 *   <li>a recast lockout, stamped on the Protego cooldown so the spell HUD shows it</li>
 *   <li>a stagger on the caster, and at Horribilis real backlash damage and weakness</li>
 * </ul>
 */
public final class ProtegoBreach {

    /** Stagger after any breach; the dome tiers stagger harder. */
    private static final int STAGGER_TICKS = 40;
    private static final int BACKLASH_WEAKNESS_TICKS = 100;

    private ProtegoBreach() {}

    public static void apply(ServerLevel level, ProtegoShieldEntity shield, @Nullable ServerPlayer caster) {
        shockwave(level, shield, caster);
        if (caster == null) {
            return;
        }
        applyRecastLockout(caster, shield.tier());

        int staggerAmplifier = shield.tier().index() >= ProtegoTier.MAXIMA.index() ? 1 : 0;
        caster.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, staggerAmplifier,
                false, true, true));

        if (shield.tier().backlashOnBreach()) {
            // The charm that swallows curses gives them back when it fails. Plain magic damage with
            // no attacker: it comes from the collapsing ward, not from whoever broke it, and nothing
            // should credit them with a kill for it.
            caster.hurtServer(level, level.damageSources().magic(), ProtegoRules.HORRIBILIS_BACKLASH_DAMAGE);
            caster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BACKLASH_WEAKNESS_TICKS, 0,
                    false, true, true));
            caster.displayClientMessage(
                    Component.translatable("spell.wizards_and_beasts.protego.backlash").withStyle(ChatFormatting.RED),
                    true);
        } else {
            caster.displayClientMessage(
                    Component.translatable("spell.wizards_and_beasts.protego.broken").withStyle(ChatFormatting.RED),
                    true);
        }
    }

    /** Everything that was leaning on the ward when it went is thrown clear of it. */
    private static void shockwave(ServerLevel level, ProtegoShieldEntity shield, @Nullable ServerPlayer caster) {
        Vec3 centre = shield.centre();
        double reach = shield.tier().radius() + 1.0;
        float strength = 0.6f + shield.tier().index() * 0.2f;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(centre, reach * 2, reach * 2, reach * 2), LivingEntity::isAlive)) {
            if (caster != null && ProtegoWardManager.isAlly(caster, living)) {
                continue;
            }
            Vec3 away = living.getBoundingBox().getCenter().subtract(centre);
            if (away.lengthSqr() < 1.0e-4 || away.lengthSqr() > reach * reach) {
                continue;
            }
            SpellHelper.applyKnockback(living, away, strength);
        }
    }

    /**
     * Stamps the recast lockout onto the Protego cooldown.
     *
     * <p>The cooldown is the right home for it: the spell HUD, the wheel and the cast gate already
     * read it, so a broken ward reads as "Protego is recovering" everywhere without a second timer
     * to keep in sync. Never shortens an existing cooldown.
     */
    private static void applyRecastLockout(ServerPlayer caster, ProtegoTier tier) {
        PlayerSpellData data = caster.getData(ModAttachments.SPELL_DATA.get());
        String spellId = Spells.PROTEGO.getId();
        long expiry = caster.level().getGameTime() + tier.breachLockoutTicks();
        if (data.getCooldownExpiry(spellId) >= expiry) {
            return;
        }
        data.setCooldown(spellId, expiry);
        SpellDataDeltaS2CPayload.sendTo(caster, spellId, expiry,
                data.getCastCount(spellId), data.getSuccessfulHits(spellId), data.getGlobalCooldownEndTick());
    }
}
