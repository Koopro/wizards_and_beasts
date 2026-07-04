package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.DragonTraits;
import at.koopro.wizardsandbeasts.entity.creature.DragonEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared dragon fire-breath: a server-authoritative conal sweep, projectile-free. On a cooldown it
 * exhales toward the current attack target — entities inside the cone (with line of sight) take fire +
 * small direct damage; a fan of sampled rays resolves the first block hit per ray and applies the
 * breed's {@link DragonTraits.BlockEffect} ({@code IGNITE} = vanilla fire, {@code ASH} = clean removal
 * of timber/bone-tagged blocks). Visuals (tinted particles + the {@code breath} animation) are decoupled
 * from the hit logic and driven by the breed's {@code fire_color}/{@code flame_shape}.
 *
 * <p>Tuning (canon-agnostic, agent latitude): half-angle and reach derive from {@link DragonTraits.FlameShape};
 * cooldown {@value #COOLDOWN_TICKS}t, {@value #DAMAGE} damage + {@value #BURN_SECONDS}s fire, {@value #RAYS} rays.
 */
public final class DragonBreathGoal extends Goal {

    /** Timber/bone blocks an {@code ASH} flame reduces to nothing. Data tag — no block registration here. */
    public static final TagKey<Block> ASH_COMBUSTIBLE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dragon_ash_combustible"));

    private static final int COOLDOWN_TICKS = 80;
    private static final int RAYS = 18;
    private static final float DAMAGE = 5.0f;
    private static final int BURN_SECONDS = 5;

    private final DragonEntity mob;
    private int cooldown;

    public DragonBreathGoal(DragonEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.CREATURES) || mob.dragonTraits() == null) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        double range = mob.dragonTraits().fireRange();
        return target != null && target.isAlive() && mob.distanceToSqr(target) <= range * range;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        cooldown = COOLDOWN_TICKS / 2;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (--cooldown > 0 || !mob.hasLineOfSight(target)) {
            return;
        }
        cooldown = COOLDOWN_TICKS;
        breathe();
    }

    private void breathe() {
        DragonTraits traits = mob.dragonTraits();
        if (traits == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        // Animation is GeckoLib-synced from the server trigger; no bespoke packet.
        mob.triggerAnim(DragonEntity.ACTION_CONTROLLER, "breath");

        DragonTraits.FlameShape shape = traits.flameShape();
        double halfAngle = Math.toRadians(halfAngleDegrees(shape));
        double range = traits.fireRange() * reachFactor(shape);
        Vec3 origin = mob.getEyePosition();
        Vec3 look = mob.getViewVector(1.0f).normalize();

        burnEntities(level, origin, look, range, Math.cos(halfAngle));
        Vec3 right = orthonormalRight(look);
        Vec3 up = right.cross(look).normalize();
        // Shared set dedupes block writes so the overlapping cone rays form one fire carpet, not N writes.
        Set<BlockPos> touched = new HashSet<>();
        double splash = splashRadius(shape);
        for (int i = 0; i < RAYS; i++) {
            Vec3 dir = sampleCone(look, right, up, halfAngle);
            castRay(level, traits, origin, dir, range, splash, touched);
        }
    }

    /** Cone-sweep entity damage: ignite + small direct damage to every living thing in the arc with LOS. */
    private void burnEntities(ServerLevel level, Vec3 origin, Vec3 look, double range, double coneDot) {
        AABB area = mob.getBoundingBox().inflate(range);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != mob && e.isAlive());
        for (LivingEntity victim : victims) {
            Vec3 toVictim = victim.getBoundingBox().getCenter().subtract(origin);
            if (toVictim.lengthSqr() > range * range || look.dot(toVictim.normalize()) < coneDot) {
                continue;
            }
            if (!mob.hasLineOfSight(victim)) {
                continue;
            }
            victim.igniteForSeconds(BURN_SECONDS);
            victim.hurt(mob.damageSources().onFire(), DAMAGE);
        }
    }

    /**
     * One ray: spawn tinted flame along it, then scorch the surface where it strikes. Ice-and-Fire-style —
     * the breath carpets fire onto any solid ground in a splash around the impact (not just flammable
     * blocks), so terrain visibly burns instead of merely singeing the victim.
     */
    private void castRay(ServerLevel level, DragonTraits traits, Vec3 origin, Vec3 dir, double range,
                         double splash, Set<BlockPos> touched) {
        Vec3 end = origin.add(dir.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(
                origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        Vec3 stop = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : end;
        spawnFlame(level, traits, origin, stop);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos impact = BlockPos.containing(hit.getLocation());
        scorchArea(level, traits, impact, splash, touched);
    }

    /** Apply the block effect across a small footprint at the impact: IGNITE = fire carpet, ASH = clean removal. */
    private void scorchArea(ServerLevel level, DragonTraits traits, BlockPos impact, double splash,
                            Set<BlockPos> touched) {
        int r = (int) Math.ceil(splash);
        boolean ash = traits.blockEffect() == DragonTraits.BlockEffect.ASH;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > splash * splash) {
                    continue;
                }
                int x = impact.getX() + dx;
                int z = impact.getZ() + dz;
                if (ash) {
                    ashColumn(level, x, impact.getY(), z, touched);
                } else {
                    igniteColumn(level, x, impact.getY(), z, touched);
                }
            }
        }
    }

    /** Find the surface near {@code y} in this column and lay vanilla fire on the air block above it. */
    private void igniteColumn(ServerLevel level, int x, int y, int z, Set<BlockPos> touched) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos ground = new BlockPos(x, y + dy, z);
            BlockPos above = ground.above();
            if (!touched.add(above)) {
                return;
            }
            BlockState groundState = level.getBlockState(ground);
            if (groundState.isFaceSturdy(level, ground, Direction.UP)
                    && level.getBlockState(above).isAir()) {
                level.setBlockAndUpdate(above, BaseFireBlock.getState(level, above));
                return;
            }
        }
    }

    /** Remove the timber/bone-tagged blocks in this column, no drops, with an ash puff. */
    private void ashColumn(ServerLevel level, int x, int y, int z, Set<BlockPos> touched) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            if (!touched.add(pos)) {
                continue;
            }
            if (level.getBlockState(pos).is(ASH_COMBUSTIBLE)) {
                level.removeBlock(pos, false);
                level.sendParticles(ParticleTypes.ASH,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    private void spawnFlame(ServerLevel level, DragonTraits traits, Vec3 origin, Vec3 stop) {
        DustParticleOptions tint = new DustParticleOptions(0xFF000000 | traits.fireColor(), 1.6f);
        Vec3 delta = stop.subtract(origin);
        double length = delta.length();
        // One sample per ~half block so the jet reads as a continuous stream, not dots; widen along its length.
        int steps = Math.max(4, (int) (length * 2));
        Vec3 step = delta.scale(1.0 / steps);
        for (int s = 1; s <= steps; s++) {
            Vec3 p = origin.add(step.scale(s));
            double spread = 0.05 + 0.06 * s / steps; // cone widens downrange
            level.sendParticles(tint, p.x, p.y, p.z, 2, spread, spread, spread, 0.0);
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, spread, spread, spread, 0.01);
            if (s % 3 == 0) {
                level.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, spread, spread, spread, 0.0);
            }
        }
    }

    // ── cone geometry ──────────────────────────────────────────────────────────

    private static double halfAngleDegrees(DragonTraits.FlameShape shape) {
        return switch (shape) {
            case JET -> 15.0;    // tight lance
            case BURST -> 40.0;  // wide puff
            case STREAM -> 25.0; // default
        };
    }

    private static double reachFactor(DragonTraits.FlameShape shape) {
        return shape == DragonTraits.FlameShape.BURST ? 0.6 : 1.0; // BURST = shorter
    }

    /** Ground-scorch footprint radius (blocks) per impact — wider for the BURST puff, tight for the JET lance. */
    private static double splashRadius(DragonTraits.FlameShape shape) {
        return switch (shape) {
            case JET -> 1.0;
            case BURST -> 2.5;
            case STREAM -> 1.5;
        };
    }

    private static Vec3 orthonormalRight(Vec3 look) {
        Vec3 ref = Math.abs(look.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return look.cross(ref).normalize();
    }

    private Vec3 sampleCone(Vec3 look, Vec3 right, Vec3 up, double halfAngle) {
        double theta = mob.getRandom().nextDouble() * halfAngle;
        double phi = mob.getRandom().nextDouble() * 2.0 * Math.PI;
        return look.scale(Math.cos(theta))
                .add(right.scale(Math.sin(theta) * Math.cos(phi)))
                .add(up.scale(Math.sin(theta) * Math.sin(phi)))
                .normalize();
    }
}
