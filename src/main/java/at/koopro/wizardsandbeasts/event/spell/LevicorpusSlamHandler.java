package at.koopro.wizardsandbeasts.event.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Levicorpus "slam on release". A target hung by Levicorpus is tagged; when its levitation runs out
 * (natural release) it is driven straight down so it crashes for fall damage. A Liberacorpus cleanse
 * strips the levitation early (a Remove, not an Expired) and clears the tag without a slam, so freeing
 * an ally is safe.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class LevicorpusSlamHandler {
    public static final String SLAM_TAG = "neo_levicorpus_slam";

    private LevicorpusSlamHandler() {}

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance inst = event.getEffectInstance();
        if (inst == null || !inst.is(MobEffects.LEVITATION)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity le) || !le.getTags().contains(SLAM_TAG)) {
            return;
        }
        le.removeTag(SLAM_TAG);
        Vec3 v = le.getDeltaMovement();
        le.setDeltaMovement(v.x, -1.6, v.z);
        le.hurtMarked = true;
        // Guarantee a meaningful crash even when the lift was short.
        le.fallDistance = Math.max(le.fallDistance, 3.0f);
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        MobEffectInstance inst = event.getEffectInstance();
        if (inst == null || !inst.is(MobEffects.LEVITATION)) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity le) {
            le.removeTag(SLAM_TAG); // freed early (Liberacorpus) — no slam
        }
    }
}
