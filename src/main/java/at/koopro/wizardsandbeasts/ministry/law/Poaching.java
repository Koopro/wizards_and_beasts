package at.koopro.wizardsandbeasts.ministry.law;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Killing a protected species ({@code tags/entity_type/protected_species.json}: unicorn, golden snidget, phoenix,
 * thestral, occamy) is poaching, and an offence when a Ministry official sees it -- the rule
 * {@link CodeOfWandUse} set, that the Ministry knows only what it sees. Out of sight the poacher is told what they
 * did and nothing is filed. Only with the Ministry module on ({@link TraceService#isActive()}), like every offence.
 *
 * <p>User decision (2026-10-23): harming protected creatures has consequences beyond the unicorn's own curse.
 * An event rather than a mixin: {@link LivingDeathEvent} names the killer.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class Poaching {

    public static final TagKey<EntityType<?>> PROTECTED = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "protected_species"));

    private Poaching() {}

    public static boolean isProtected(LivingEntity creature) {
        return creature.getType().is(PROTECTED);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer killer)
                || !isProtected(victim)) {
            return;
        }
        check(killer, victim);
    }

    /**
     * Files the offence for a protected creature an official saw killed.
     *
     * @return whether it was filed
     */
    public static boolean check(ServerPlayer killer, LivingEntity victim) {
        if (!TraceService.isActive()) {
            return false;
        }
        if (!CodeOfWandUse.seenByOfficial(killer)) {
            PlayerFeedback.actionBar(killer, Component.translatable("ministry.wizards_and_beasts.poaching.unseen",
                    victim.getDisplayName()));
            return false;
        }
        TraceService.report(killer, MagicalOffence.POACHING);
        PlayerFeedback.toast(killer, NoticeKind.FAIL,
                Component.translatable("ministry.wizards_and_beasts.poaching.title"),
                Component.translatable("ministry.wizards_and_beasts.poaching.body", victim.getDisplayName()));
        return true;
    }
}
