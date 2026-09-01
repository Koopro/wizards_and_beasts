package at.koopro.wizardsandbeasts.shadow;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps a wizard in Shadow Form half-there.
 *
 * <p>Re-asserted every tick rather than set once, because vanilla recomputes the invisible flag from
 * potion effects during {@code aiStep} and would clear it straight back off — the same reason
 * {@code CloakEffectsHandler} re-applies the Cloak's every tick. It also means nothing has to clean
 * up when the effect expires: stop asserting and vanilla takes it away on its own.
 *
 * <p><b>Semi-invisible, and that is the whole design.</b> No camouflage is applied here. Eyes lose
 * you; every hostile thing in the world still knows precisely where you are. Borrowing a Hidebehind's
 * shadow is not the same as being one, and the wizard who wants the stronger version spends a
 * Demiguise hair for it.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ShadowFormHandler {

    /** Ticks between wisps. Sparse: a trail of particles would undo the concealment it decorates. */
    private static final int PARTICLE_INTERVAL = 10;

    private ShadowFormHandler() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (entity.level().isClientSide() || !entity.hasEffect(ModEffects.SHADOW_FORM)) {
            return;
        }
        entity.setInvisible(true);

        if (entity.tickCount % PARTICLE_INTERVAL == 0 && entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    2, 0.2, 0.3, 0.2, 0.0);
        }
    }
}
