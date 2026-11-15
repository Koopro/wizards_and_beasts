package at.koopro.wizardsandbeasts.client.vfx;

import at.koopro.wizardsandbeasts.client.beam.WandTipTracker;
import at.koopro.wizardsandbeasts.particle.ShapeLayout;
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
    // ── motes that move with intent (VfxMoteParticle keeps the spawner's velocity) ──────────────

    private static SpellTintParticleOptions mote(int argb) {
        return new SpellTintParticleOptions(ModParticles.VFX_MOTE.get(), argb);
    }

    /** One mote at {@code p} moving at {@code v}, if the budget allows. */
    public static void moteAt(ClientLevel level, Vec3 p, Vec3 v, int argb) {
        if (VfxBudget.count(1, 0, p) > 0) {
            level.addParticle(mote(argb), p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    /** The cast's plain line of tinted particles from wand to target, as the server loop drew it. */
    public static void line(Vec3 from, Vec3 to, SpellFamily family, int argb, double perBlock) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        double dist = from.distanceTo(to);
        int points = VfxBudget.count(Math.max(8, (int) (dist * perBlock)) + 1, 6, from.lerp(to, 0.5));
        var opts = ModParticles.tinted(family, argb);
        ShapeLayout.line(from, to, points, (x, y, z) -> level.addParticle(opts,
                x + level.random.nextGaussian() * 0.035, y + level.random.nextGaussian() * 0.035,
                z + level.random.nextGaussian() * 0.035, 0.0, 0.0, 0.0));
    }

    /** Motes streaming from the far end of a line back to its near end for {@code life} ticks: a summoning. */
    public static void pull(Vec3 near, Vec3 far, int argb, int life) {
        Vec3 back = near.subtract(far);
        if (back.length() < 0.1) {
            return;
        }
        Vec3 dir = back.normalize();
        VfxEmitters.start((level, rnd, age) -> {
            for (int k = 0; k < 3; k++) {
                double t = 0.35 + rnd.nextDouble() * 0.65;
                Vec3 p = near.add(back.scale(-t)).add(rnd.nextGaussian() * 0.12, rnd.nextGaussian() * 0.12,
                        rnd.nextGaussian() * 0.12);
                moteAt(level, p, dir.scale(0.32 + rnd.nextDouble() * 0.12), argb);
            }
        }, life, 1);
    }

    /** Motes thrown along {@code dir} from {@code origin}: a gust, a banishing, a blast's sparks. */
    public static void gust(Vec3 origin, Vec3 dir, int argb, int count, double speed, double spread) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 d = dir.lengthSqr() < 1.0e-6 ? UP : dir.normalize();
        int n = VfxBudget.count(count, 2, origin);
        for (int i = 0; i < n; i++) {
            Vec3 v = d.scale(speed * (0.7 + level.random.nextDouble() * 0.6)).add(level.random.nextGaussian() * spread,
                    level.random.nextGaussian() * spread, level.random.nextGaussian() * spread);
            level.addParticle(mote(argb), origin.x, origin.y, origin.z, v.x, v.y, v.z);
        }
    }

    /** Motes drawn in from a sphere of {@code radius} to {@code centre} for {@code life} ticks: mending, gathering. */
    public static void converge(Vec3 centre, int argb, double radius, int life, int perTick) {
        VfxEmitters.start((level, rnd, age) -> {
            for (int k = 0; k < perTick; k++) {
                Vec3 off = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian());
                if (off.lengthSqr() < 1.0e-6) {
                    continue;
                }
                off = off.normalize().scale(radius);
                moteAt(level, centre.add(off), off.scale(-0.11), argb);
            }
        }, life, 1);
    }

    /** Motes circling {@code centre} flat, {@code n} at a time, for {@code life} ticks: a mind spun round. */
    public static void orbit(Vec3 centre, int argb, double radius, int life, int n) {
        VfxEmitters.start((level, rnd, age) -> {
            for (int k = 0; k < n; k++) {
                double a = age * 0.45 + k * Math.PI * 2 / n;
                moteAt(level, centre.add(Math.cos(a) * radius, Math.sin(age * 0.3 + k) * 0.06, Math.sin(a) * radius),
                        Vec3.ZERO, argb);
            }
        }, life, 1);
    }

    /** Motes sinking slowly round {@code centre} for {@code life} ticks: movement as if through treacle. */
    public static void sink(Vec3 centre, int argb, double radius, int life) {
        VfxEmitters.start((level, rnd, age) -> {
            double a = rnd.nextDouble() * Math.PI * 2;
            Vec3 p = centre.add(Math.cos(a) * radius, 0.4 + rnd.nextDouble() * 0.6, Math.sin(a) * radius);
            moteAt(level, p, new Vec3(0, -0.018, 0), argb);
        }, life, 1);
    }

    /** Motes rising round {@code centre} for {@code life} ticks. */
    public static void rise(Vec3 centre, int argb, double radius, int life, double speed) {
        VfxEmitters.start((level, rnd, age) -> {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = radius * Math.sqrt(rnd.nextDouble());
            moteAt(level, centre.add(Math.cos(a) * r, 0.05, Math.sin(a) * r), new Vec3(0, speed, 0), argb);
        }, life, 1);
    }

    /** {@code count} puffs of tinted smoke round {@code pos}. */
    public static void mist(Vec3 pos, int argb, int count, double spread, double rise) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        int n = VfxBudget.count(count, 1, pos);
        for (int i = 0; i < n; i++) {
            level.addParticle(new SpellTintParticleOptions(ModParticles.SMOKE_PUFF.get(), argb),
                    pos.x + level.random.nextGaussian() * spread, pos.y + level.random.nextGaussian() * spread * 0.5,
                    pos.z + level.random.nextGaussian() * spread, 0.0, rise, 0.0);
        }
    }

    /** Thin rings going out from {@code centre} one after another. */
    public static void pulses(Vec3 centre, Vec3 normal, int argb, float from, float to, int life, int times,
                              int gap) {
        for (int i = 0; i < times; i++) {
            thinRing(centre, normal, argb, from, to, life, i * gap);
        }
    }

    /** Rings closing in on {@code centre}: a binding, a shrinking, a seal. */
    public static void closing(Vec3 centre, Vec3 normal, int argb, float from, float to, int life, int times,
                               int gap) {
        for (int i = 0; i < times; i++) {
            VfxQuads.spawn(VfxQuads.RING_THIN, VfxQuads.Look.GLOW, centre, normal, from, to, argb, life, 0,
                    Math.max(1, life / 3), 0f, VfxQuads.Growth.LINEAR, i * gap);
        }
    }

    /** A slow ring that takes its time to spread: slowing, holding. */
    public static void slowRing(Vec3 centre, Vec3 normal, int argb, float from, float to, int life, int delay) {
        VfxQuads.spawn(VfxQuads.RING_THIN, VfxQuads.Look.GLOW, centre, normal, from, to, argb, life, 3,
                Math.max(1, life / 2), 0f, VfxQuads.Growth.LINEAR, delay);
    }

    /** A light held at a caster's wand tip for {@code life} ticks (Lumos). */
    public static void tipGlow(int casterId, int argb, int life) {
        VfxEmitters.start((level, rnd, age) -> {
            if (!(level.getEntity(casterId) instanceof LivingEntity caster)) {
                return;
            }
            Vec3 tip = WandTipTracker.resolve(caster, 1.0f);
            for (int k = 0; k < 3; k++) {
                moteAt(level, tip.add(rnd.nextGaussian() * 0.04, rnd.nextGaussian() * 0.04, rnd.nextGaussian() * 0.04),
                        Vec3.ZERO, argb);
            }
            if (VfxBudget.count(1, 0, tip) > 0) {
                level.addParticle(ModParticles.tinted(SpellFamily.LIGHT, argb), tip.x, tip.y, tip.z, 0, 0, 0);
            }
        }, life, 1);
    }

    /** Runs {@code then} with the caster and its wand tip {@code delay} ticks from now, when the arm has come round. */
    public static void atTip(int casterId, int delay, java.util.function.BiConsumer<LivingEntity, Vec3> then) {
        VfxEmitters.start((level, rnd, age) -> {
            if (level.getEntity(casterId) instanceof LivingEntity caster) {
                then.accept(caster, WandTipTracker.resolve(caster, 1.0f));
            }
        }, 1, 1, delay);
    }
}
