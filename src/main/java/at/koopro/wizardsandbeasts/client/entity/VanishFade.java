package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * How visible a creature that can turn invisible is this frame, 1 shown to 0 gone, eased over {@link #TICKS}.
 *
 * <p>The server only says "invisible" or not (vanilla's shared flag, set by the invisibility effect the Demiguise's
 * {@code watched_invisibility} applies). Popping out between two frames reads as a despawn, not as magic, so the
 * client fades it and marks the moment with a brief silver shimmer at the body: the one effect here, and it says
 * "it is still there, you just can't see it". Cosmetic and client-only; nothing is synced for it.
 */
public final class VanishFade {

    private VanishFade() {}

    /** Ticks for a full fade out or in. */
    public static final float TICKS = 8f;
    /** Below this alpha the creature counts as gone and GeckoLib's own invisibility rule takes over. */
    public static final int MIN_ALPHA = 6;
    /** Hair-silver shimmer, the colour of the cloaks woven from it. */
    static final int SHIMMER_ARGB = 0xFFD9E2EE;
    private static final int SHIMMER_COUNT = 7;
    private static final int SHIMMER_COOLDOWN_TICKS = 20;

    /** {@code {visibility, lastAge, lastTarget, lastShimmerTick}} per entity; render thread only. */
    private static final Map<Entity, float[]> STATE = new WeakHashMap<>();

    public static float visibility(Entity entity, float partialTick) {
        Player viewer = Minecraft.getInstance().player;
        float target = entity.isInvisible() && (viewer == null || entity.isInvisibleTo(viewer)) ? 0f : 1f;
        float age = entity.tickCount + partialTick;
        float[] s = STATE.get(entity);
        if (s == null) {
            STATE.put(entity, new float[] {target, age, target, -1000f});
            return target;
        }
        float dt = Mth.clamp(age - s[1], 0f, TICKS);
        s[0] = step(s[0], target, dt);
        s[1] = age;
        if (target != s[2]) {
            s[2] = target;
            if (age - s[3] >= SHIMMER_COOLDOWN_TICKS && entity.level().isClientSide()
                    && entity.level().getEntity(entity.getId()) == entity) {
                s[3] = age;
                shimmer(entity);
            }
        }
        return s[0];
    }

    /** Pure easing step, public for tests: moves {@code current} toward {@code target} by {@code dt / TICKS}. */
    public static float step(float current, float target, float dt) {
        float delta = dt / TICKS;
        return current < target ? Math.min(target, current + delta) : Math.max(target, current - delta);
    }

    private static void shimmer(Entity entity) {
        AABB box = entity.getBoundingBox();
        var random = entity.level().getRandom();
        for (int i = 0; i < SHIMMER_COUNT; i++) {
            double x = Mth.lerp(random.nextDouble(), box.minX, box.maxX);
            double y = Mth.lerp(random.nextDouble(), box.minY + box.getYsize() * 0.2, box.maxY);
            double z = Mth.lerp(random.nextDouble(), box.minZ, box.maxZ);
            entity.level().addParticle(new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), SHIMMER_ARGB),
                    x, y, z, 0.0, 0.01 + random.nextDouble() * 0.02, 0.0);
        }
    }
}
