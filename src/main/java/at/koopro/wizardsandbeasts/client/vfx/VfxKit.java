package at.koopro.wizardsandbeasts.client.vfx;

import at.koopro.wizardsandbeasts.client.beam.WandTipTracker;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The reusable pieces a spell's look is built from. Each is one stage of the brief's sequence — a shockwave, a mark,
 * a ward's runes, residue that lingers, a spiral that climbs — and knows nothing about which spell asked for it.
 * {@link SpellVfxDirector} composes them per spell; a new spell's look is a few calls here, not new rendering code.
 *
 * <p>Everything spends from {@link VfxBudget} and is capped by {@link VfxQuads} / {@link VfxEmitters}.
 */
@NullMarked
public final class VfxKit {

    public static final Vec3 UP = new Vec3(0, 1, 0);

    private VfxKit() {}

    // ── surfaces ───────────────────────────────────────────────────────────────────────────────

    /**
     * The top of the first solid surface at or below {@code pos}, within {@code maxDown} blocks; null over a drop.
     * A creature hit in the air throws its mark on the ground under it, not on nothing.
     */
    public static @Nullable Vec3 groundBelow(Vec3 pos, int maxDown) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        BlockPos start = BlockPos.containing(pos);
        for (int k = 0; k <= maxDown + 1; k++) {
            BlockPos at = start.below(k);
            BlockState state = level.getBlockState(at);
            VoxelShape shape = state.getCollisionShape(level, at);
            if (shape.isEmpty() || !state.isFaceSturdy(level, at, Direction.UP)) {
                continue;
            }
            double top = at.getY() + shape.max(Direction.Axis.Y);
            if (top <= pos.y + 0.05) {
                return new Vec3(pos.x, top, pos.z);
            }
        }
        return null;
    }

    // ── flat shapes ────────────────────────────────────────────────────────────────────────────

    /** A ring of light thrown out from {@code pos} in the plane facing {@code normal}. */
    public static void shockwave(Vec3 pos, Vec3 normal, int argb, float from, float to, int life, int delay) {
        VfxQuads.spawn(VfxQuads.SHOCKWAVE, VfxQuads.Look.GLOW, pos, normal, from, to, argb, life, 0,
                Math.max(1, life * 2 / 3), 0f, VfxQuads.Growth.BURST, delay);
    }

    /** A thin second ring, for the echo after a shockwave or a soft pulse. */
    public static void thinRing(Vec3 pos, Vec3 normal, int argb, float from, float to, int life, int delay) {
        VfxQuads.spawn(VfxQuads.RING_THIN, VfxQuads.Look.GLOW, pos, normal, from, to, argb, life, 0,
                Math.max(1, life / 2), 0f, VfxQuads.Growth.BURST, delay);
    }

    /** A mark left on a surface: scorch, frost, creep. Lit by the world, so it reads as matter, not light. */
    public static VfxQuads.@Nullable Quad mark(net.minecraft.resources.Identifier texture, Vec3 pos, Vec3 normal,
                                               int argb, float size, int life, int fadeOut) {
        return VfxQuads.spawn(texture, VfxQuads.Look.LIT, pos, normal, size * 0.7f, size, argb, life, 0, fadeOut,
                0f, VfxQuads.Growth.LAND, 0);
    }

    /** A turning circle of runes lying on the ground: a ward's footprint, a ritual. */
    public static VfxQuads.@Nullable Quad runeCircle(Vec3 ground, int argb, float size, int life, int fadeIn,
                                                     int fadeOut, float spinPerTick) {
        return VfxQuads.spawn(VfxQuads.RUNE_CIRCLE, VfxQuads.Look.GLOW, ground, UP, size * 0.6f, size, argb, life,
                fadeIn, fadeOut, spinPerTick, VfxQuads.Growth.LAND, 0);
    }

    /** A soft stepped disc on the ground. */
    public static void glowDisc(Vec3 ground, int argb, float from, float to, int life) {
        VfxQuads.spawn(VfxQuads.GLOW_DISC, VfxQuads.Look.GLOW, ground, UP, from, to, argb, life, 2,
                Math.max(1, life / 2), 0f, VfxQuads.Growth.LAND, 0);
    }

    // ── particles that last ────────────────────────────────────────────────────────────────────

    /** Embers and smoke rising off a burnt spot for {@code life} ticks. */
    public static void residue(Vec3 ground, int emberArgb, int smokeArgb, float radius, int life) {
        VfxEmitters.start((level, rnd, age) -> {
            Vec3 p = ground.add((rnd.nextDouble() - 0.5) * radius * 2, 0.05, (rnd.nextDouble() - 0.5) * radius * 2);
            if (VfxBudget.count(1, 0, p) > 0) {
                level.addParticle(ModParticles.tinted(SpellFamily.FIRE, emberArgb), p.x, p.y, p.z,
                        0.0, 0.02 + rnd.nextDouble() * 0.02, 0.0);
            }
            if (age % 9 == 0 && VfxBudget.count(1, 0, p) > 0) {
                level.addParticle(new SpellTintParticleOptions(ModParticles.SMOKE_PUFF.get(), smokeArgb),
                        p.x, p.y + 0.1, p.z, 0.0, 0.015, 0.0);
            }
        }, life, 3);
    }

    /** Motes climbing a body in a triple helix, for {@code life} ticks, following the entity as it moves. */
    public static void spiral(int entityId, SpellFamily family, int argb, int life, double radius) {
        VfxEmitters.start((level, rnd, age) -> {
            Entity e = level.getEntity(entityId);
            if (e == null) {
                return;
            }
            double h = e.getBbHeight() * (age / (double) life) * 1.05;
            double angle = age * 0.55;
            var opts = ModParticles.tinted(family, argb);
            int n = VfxBudget.count(3, 1, e.position());
            for (int k = 0; k < n; k++) {
                double a = angle + k * Math.PI * 2.0 / 3.0;
                level.addParticle(opts, e.getX() + Math.cos(a) * radius, e.getY() + h, e.getZ() + Math.sin(a) * radius,
                        0.0, 0.015, 0.0);
            }
        }, life, 1);
    }

    /** Smoke curling off a caster's wand tip for {@code life} ticks: a dark curse being held. */
    public static void wandSmoke(int casterId, int argb, int life, int every) {
        VfxEmitters.start((level, rnd, age) -> {
            if (!(level.getEntity(casterId) instanceof LivingEntity caster)) {
                return;
            }
            Vec3 tip = WandTipTracker.resolve(caster, 1.0f);
            if (VfxBudget.count(1, 0, tip) > 0) {
                level.addParticle(new SpellTintParticleOptions(ModParticles.SMOKE_PUFF.get(), argb),
                        tip.x, tip.y, tip.z, (rnd.nextDouble() - 0.5) * 0.01, 0.02, (rnd.nextDouble() - 0.5) * 0.01);
            }
        }, life, every);
    }

    /** Dark smoke and wisps rising round a spot for {@code life} ticks. */
    public static void darkRise(Vec3 ground, int smokeArgb, int wispArgb, float radius, int life) {
        VfxEmitters.start((level, rnd, age) -> {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = radius * (0.4 + rnd.nextDouble() * 0.6);
            Vec3 p = ground.add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
            if (VfxBudget.count(1, 0, p) > 0) {
                level.addParticle(new SpellTintParticleOptions(ModParticles.SMOKE_PUFF.get(), smokeArgb),
                        p.x, p.y, p.z, 0.0, 0.025, 0.0);
            }
            if (age % 4 == 0 && VfxBudget.count(1, 0, p) > 0) {
                level.addParticle(ModParticles.tinted(SpellFamily.DARK, wispArgb), p.x, p.y + 0.3, p.z,
                        0.0, 0.03, 0.0);
            }
        }, life, 2);
    }

    /** A one-off scatter of tinted particles, budgeted. */
    public static void scatter(Vec3 pos, SpellFamily family, int argb, int count, double spread, double up) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        int n = VfxBudget.count(count, 1, pos);
        var opts = ModParticles.tinted(family, argb);
        for (int i = 0; i < n; i++) {
            level.addParticle(opts,
                    pos.x + (level.random.nextDouble() - 0.5) * spread * 2,
                    pos.y + (level.random.nextDouble() - 0.5) * spread * 2,
                    pos.z + (level.random.nextDouble() - 0.5) * spread * 2,
                    0.0, up, 0.0);
        }
    }
}
