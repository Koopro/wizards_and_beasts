package at.koopro.wizardsandbeasts.event.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionChargeManager;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchDamageTypes;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTags;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Two things splinching needs from the damage pipeline.
 *
 * <p><b>The floor.</b> Splinch damage is clamped before it applies so it can never take a wizard below half a
 * heart. Deliberately a pre-application clamp rather than a resistance modifier or a cancelled event: a
 * resistance would scale wrongly against absorption and armour, and cancelling would throw away the hurt
 * animation, the sound and the knockback that sell the wound. The bleed keeps ticking at one heart, and any
 * <i>other</i> source can still finish you — being pinned there and helpless is the tension; splinching
 * itself is not the killer.
 *
 * <p><b>The interruption.</b> A hit landing mid-attempt is what inflates the miss, and for an anchored jump
 * it ends the attempt outright.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SplinchDamageHandler {

    /** Health a splinch always leaves behind. Half a heart. */
    public static final float SURVIVAL_FLOOR = 1.0f;

    private SplinchDamageHandler() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();

        if (event.getSource().is(SplinchDamageTypes.SPLINCH)) {
            float survivable = Math.max(0.0f, entity.getHealth() - SURVIVAL_FLOOR);
            if (event.getAmount() > survivable) {
                event.setAmount(survivable);
            }
            return;
        }

        // Any other hit destabilises an attempt in flight.
        if (entity instanceof ServerPlayer player) {
            ApparitionChargeManager.onDamaged(player);
        }
    }

    /**
     * Finishing anything tagged {@code cures_splinch} puts the wizard back together — at either amplifier,
     * because a remedy that only mended the mild version would be worthless exactly when it mattered.
     *
     * <p>Read off the tag rather than the item, so Dittany, a future Essence of Dittany, a healer's draught
     * or a datapack's own remedy all work without this handler learning any of their names. The effect still
     * decays on its own; this is the fast path, not the only one.
     */
    @SubscribeEvent
    public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
        if (!event.getItem().is(SplinchTags.CURES_SPLINCH)) {
            return;
        }
        event.getEntity().removeEffect(ModEffects.SPLINCHED);
    }
}
