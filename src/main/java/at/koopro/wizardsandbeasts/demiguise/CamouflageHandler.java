package at.koopro.wizardsandbeasts.demiguise;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Making mobs look past somebody.
 *
 * <p>Three hooks, and each one closes a hole the other two leave open:
 *
 * <ol>
 *   <li><b>Target selection</b> is refused outright, so nothing new picks a camouflaged player.
 *       This is the same mechanism the Invisibility Cloak uses; camouflage is the version you can
 *       carry in a pocket.</li>
 *   <li><b>Existing targets are dropped</b> the moment camouflage lands. Cancelling future
 *       selections alone would leave the zombie already walking toward you still walking toward
 *       you, and the effect would feel broken exactly when it was bought to save you.</li>
 *   <li><b>Attacking breaks it.</b> A Demiguise's concealment is passive. Without this the effect
 *       would be a free five-second ambush window, which is a different and much stronger item than
 *       the one the hair is meant to be.</li>
 * </ol>
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class CamouflageHandler {

    /** How far out existing targets are dropped when camouflage lands. Matches the cloak's sweep. */
    private static final double FORGET_RADIUS = 64.0;

    private CamouflageHandler() {}

    /** Nothing chooses a camouflaged target. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (isCamouflaged(event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
        }
    }

    /**
     * Swinging at something ends the concealment, for the attacker.
     *
     * <p>Both effects are removed together — being visible again but still untargetable would be the
     * worst of both readings, and the hair grants them as a pair.
     */
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        breakCamouflage(event.getEntity());
    }

    /**
     * Anything else that lands a blow also loses it, which covers a camouflaged mob and any path
     * that deals damage without going through {@link AttackEntityEvent} — a thrown potion, a spell,
     * a summoned Patronus.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            breakCamouflage(attacker);
        }
    }

    /** Drops every target currently locked onto {@code hidden}. Called when camouflage begins. */
    public static void forgetTargets(LivingEntity hidden) {
        AABB range = hidden.getBoundingBox().inflate(FORGET_RADIUS);
        hidden.level().getEntitiesOfClass(Mob.class, range, mob -> mob.getTarget() == hidden)
                .forEach(mob -> mob.setTarget(null));
    }

    public static boolean isCamouflaged(@org.jspecify.annotations.Nullable LivingEntity entity) {
        return entity != null && entity.hasEffect(ModEffects.CAMOUFLAGE);
    }

    private static void breakCamouflage(LivingEntity attacker) {
        if (!attacker.hasEffect(ModEffects.CAMOUFLAGE)) {
            return;
        }
        attacker.removeEffect(ModEffects.CAMOUFLAGE);
        attacker.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        if (attacker instanceof Player player && player.level().isClientSide()) {
            return;
        }
        Demiguise.vanishEffects(attacker);
    }
}
