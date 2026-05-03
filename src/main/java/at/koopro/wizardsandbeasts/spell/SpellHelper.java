package at.koopro.wizardsandbeasts.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * Static helpers for spell implementations.
 * Use these when overriding {@link Spell#execute} for custom behavior.
 */
public final class SpellHelper {

    private SpellHelper() {}
    private static final Map<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block> REPARO_BLOCK_REPAIR_MAP =
            Map.of(
                    Blocks.CHIPPED_ANVIL, Blocks.ANVIL,
                    Blocks.DAMAGED_ANVIL, Blocks.CHIPPED_ANVIL,
                    Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS,
                    Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS,
                    Blocks.CRACKED_DEEPSLATE_TILES, Blocks.DEEPSLATE_TILES
            );

    // ── Entity queries ──────────────────────────────────────────────────

    /**
     * Finds all living entities (and optionally items) in a cone in front of the caster.
     */
    public static List<Entity> findEntitiesInCone(ServerLevel level, ServerPlayer caster,
                                                   float range, boolean includeItems) {
        Vec3 look = caster.getLookAngle();
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(look.scale(range));
        AABB area = new AABB(start, end).inflate(1.5);
        final double minDot = 0.75; // ~41 deg half-angle cone

        return level.getEntities(caster, area, e -> {
            if (e == caster) return false;
            if (!(e instanceof LivingEntity) && !(includeItems && e instanceof ItemEntity)) {
                return false;
            }

            Vec3 targetCenter = e.getBoundingBox().getCenter();
            Vec3 toTarget = targetCenter.subtract(start);
            double distance = toTarget.length();
            if (distance <= 1.0e-4 || distance > range) {
                return false;
            }

            Vec3 dirToTarget = toTarget.scale(1.0 / distance);
            if (look.dot(dirToTarget) < minDot) {
                return false;
            }

            // Ignore entities behind walls so cone spells feel deterministic.
            BlockHitResult blockHit = level.clip(new ClipContext(
                    start,
                    targetCenter,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    caster));
            return blockHit.getType() == HitResult.Type.MISS;
        });
    }

    /** Extra Accio targets that are physical but not living/items. */
    public static List<Entity> findAccioExtraTargets(ServerLevel level, ServerPlayer caster, float range) {
        Vec3 look = caster.getLookAngle();
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(look.scale(range));
        AABB area = new AABB(start, end).inflate(1.6);
        final double minDot = 0.72;
        List<Entity> out = new ArrayList<>();
        for (Entity e : level.getEntities(caster, area, SpellHelper::isAccioExtraEntity)) {
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 toTarget = center.subtract(start);
            double distance = toTarget.length();
            if (distance <= 1.0e-4 || distance > range) continue;
            Vec3 dirToTarget = toTarget.scale(1.0 / distance);
            if (look.dot(dirToTarget) < minDot) continue;
            BlockHitResult blockHit = level.clip(new ClipContext(
                    start, center,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    caster));
            if (blockHit.getType() == HitResult.Type.MISS) {
                out.add(e);
            }
        }
        return out;
    }

    private static boolean isAccioExtraEntity(Entity entity) {
        return entity instanceof Projectile || entity instanceof VehicleEntity;
    }

    /**
     * Finds the closest living entity the caster is looking at within range.
     * Uses a dot-product cone check (0.85 threshold ≈ 30° half-angle).
     */
    @Nullable
    public static LivingEntity findTargetedEntity(ServerLevel level, ServerPlayer caster,
                                                   float range) {
        return findTargetedEntity(level, caster, range, e -> true);
    }

    /**
     * First living entity along the caster's view ray, up to {@code maxRange} blocks.
     * Uses {@link ProjectileUtil#getHitResultOnViewVector} so range is not capped by
     * vanilla {@link net.minecraft.world.entity.Entity#pick} entity interaction distance (~3 blocks).
     */
    @Nullable
    public static LivingEntity findLivingAlongCrosshair(ServerPlayer caster, float maxRange) {
        if (maxRange <= 0) return null;
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(
                caster,
                e -> e instanceof LivingEntity && e != caster && e.isPickable(),
                maxRange);
        if (hit.getType() != HitResult.Type.ENTITY) return null;
        Entity e = ((EntityHitResult) hit).getEntity();
        return e instanceof LivingEntity living ? living : null;
    }

    /**
     * Finds the closest living entity the caster is looking at, with an additional filter.
     */
    @Nullable
    public static LivingEntity findTargetedEntity(ServerLevel level, ServerPlayer caster,
                                                   float range, Predicate<LivingEntity> filter) {
        Vec3 start = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        AABB searchArea = caster.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);

        LivingEntity target = null;
        double closestDist = range * range;

        for (Entity e : level.getEntities(caster, searchArea, e -> e instanceof LivingEntity && e != caster)) {
            LivingEntity living = (LivingEntity) e;
            if (!filter.test(living)) continue;

            Vec3 toEntity = e.position().add(0, e.getBbHeight() / 2, 0).subtract(start).normalize();
            if (look.dot(toEntity) > 0.85) {
                Vec3 targetCenter = e.getBoundingBox().getCenter();
                BlockHitResult blockHit = level.clip(new ClipContext(
                        start,
                        targetCenter,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        caster));
                if (blockHit.getType() != HitResult.Type.MISS) {
                    continue;
                }
                double dist = start.distanceToSqr(e.position());
                if (dist < closestDist) {
                    closestDist = dist;
                    target = living;
                }
            }
        }
        return target;
    }

    // ── Combat effects ──────────────────────────────────────────────────

    /**
     * Applies knockback to an entity in the given direction.
     */
    public static void applyKnockback(Entity entity, Vec3 direction, float strength) {
        float capped = Math.max(0.0f, Math.min(4.5f, strength));
        Vec3 kb = direction.normalize().scale(capped);
        entity.push(kb.x, 0.3, kb.z);
        entity.hurtMarked = true;
    }

    /**
     * Creates a spell explosion at the given position.
     */
    public static void createExplosion(Level level, Entity source, Vec3 pos,
                                        float power, boolean breaksBlocks) {
        Level.ExplosionInteraction interaction = breaksBlocks
                ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.MOB;
        level.explode(source, pos.x, pos.y, pos.z, power, interaction);
    }

    /**
     * Ignites an entity for the given duration in seconds.
     */
    public static void ignite(Entity entity, int seconds) {
        entity.igniteForSeconds(seconds);
    }

    /**
     * Lights the first block along the look ray (where flint & steel could place fire).
     */
    public static void tryIgniteBlockAlongLook(ServerLevel level, ServerPlayer caster, float maxRange) {
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(caster.getLookAngle().scale(maxRange));
        BlockHitResult blockHit = level.clip(new ClipContext(
                start, end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                caster));
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        tryIgniteAdjacentToBlockHit(level, blockHit);
    }

    /** Places fire in the air block adjacent to the hit face (flint-and-steel style). */
    public static void tryIgniteAdjacentToBlockHit(ServerLevel level, BlockHitResult blockHit) {
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = blockHit.getBlockPos().relative(blockHit.getDirection());
        if (!BaseFireBlock.canBePlacedAt(level, pos, blockHit.getDirection().getOpposite())) {
            return;
        }
        BlockState fire = BaseFireBlock.getState(level, pos);
        if (fire == null) {
            return;
        }
        level.setBlockAndUpdate(pos, fire);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35f, 0.8f);
    }

    /** Common impact burst + soft impact sound for world interactions. */
    public static void playSpellImpact(ServerLevel level, Vec3 pos, int color) {
        spawnBurst(level, pos, color, 10, 0.18);
        level.playSound(null, BlockPos.containing(pos), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.22f, 1.4f);
    }

    public static BlockHitResult raycastFromCaster(ServerLevel level, ServerPlayer caster, Vec3 start, Vec3 end,
                                                   ClipContext.Block blockMode, ClipContext.Fluid fluidMode) {
        return level.clip(new ClipContext(start, end, blockMode, fluidMode, caster));
    }

    /** Whitelist for Diffindo-style non-griefy block cuts. */
    public static boolean isCuttableByDiffindo(BlockState state) {
        return state.is(Blocks.COBWEB)
                || state.is(Blocks.VINE)
                || state.is(Blocks.WEEPING_VINES)
                || state.is(Blocks.WEEPING_VINES_PLANT)
                || state.is(Blocks.TWISTING_VINES)
                || state.is(Blocks.TWISTING_VINES_PLANT)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SUGAR_CANE)
                || state.getBlock() instanceof CropBlock;
    }

    public static boolean tryDiffindoCutBlock(ServerLevel level, BlockPos pos, Spell spell) {
        BlockState state = level.getBlockState(pos);
        if (!isCuttableByDiffindo(state)) {
            return false;
        }
        level.destroyBlock(pos, true);
        playSpellImpact(level, pos.getCenter(), spell.getColor());
        return true;
    }

    public static boolean tryGlaciusBlockInteraction(ServerLevel level, BlockHitResult hit, Spell spell) {
        BlockPos hitPos = hit.getBlockPos();
        BlockState hitState = level.getBlockState(hitPos);
        if (hitState.is(Blocks.FIRE) || hitState.is(Blocks.SOUL_FIRE)) {
            level.removeBlock(hitPos, false);
            level.playSound(null, hitPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.45f, 1.1f);
            playSpellImpact(level, hit.getLocation(), spell.getColor());
            return true;
        }

        BlockPos placePos = hitPos.relative(hit.getDirection());
        BlockState placeState = level.getBlockState(placePos);
        if ((placeState.isAir() || placeState.canBeReplaced())
                && Blocks.SNOW.defaultBlockState().canSurvive(level, placePos)) {
            level.setBlockAndUpdate(placePos, Blocks.SNOW.defaultBlockState());
            playSpellImpact(level, placePos.getCenter(), spell.getColor());
            return true;
        }
        return false;
    }

    public static int pushNearbyLightweightEntities(ServerLevel level, @Nullable Entity caster,
                                                    Vec3 center, Vec3 direction, float strength, double radius) {
        if (strength <= 0f || radius <= 0d) {
            return 0;
        }
        Vec3 dir = direction.lengthSqr() > 1.0e-6 ? direction.normalize() : new Vec3(0, 0, 1);
        AABB area = new AABB(center, center).inflate(radius);
        int pushed = 0;
        for (Entity e : level.getEntities(caster, area, SpellHelper::isLightweightPushTarget)) {
            applyKnockback(e, dir, strength);
            e.fallDistance = 0f;
            pushed++;
        }
        return pushed;
    }

    private static boolean isLightweightPushTarget(Entity entity) {
        return entity instanceof ItemEntity || entity instanceof Projectile;
    }

    public static boolean tryReparoRepairBlock(ServerLevel level, BlockPos pos, Spell spell) {
        BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.Block repaired = REPARO_BLOCK_REPAIR_MAP.get(state.getBlock());
        if (repaired == null) {
            return false;
        }
        level.setBlockAndUpdate(pos, repaired.withPropertiesOf(state));
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.3f, 1.2f);
        playSpellImpact(level, pos.getCenter(), spell.getColor());
        return true;
    }

    public static int applyStupefyImpactPulse(ServerLevel level, @Nullable Entity source, Vec3 center, Spell spell) {
        AABB area = new AABB(center, center).inflate(2.6);
        int affected = 0;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && e != source)) {
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOWNESS, 40, 1, false, true, true));
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WEAKNESS, 30, 0, false, true, true));
            affected++;
        }
        if (affected > 0) {
            playSpellImpact(level, center, spell.getColor());
        }
        return affected;
    }

    public static void applyConfringoBlockImpact(ServerLevel level, BlockHitResult hit, Spell spell, float explosionPower,
                                                 boolean breaksBlocks) {
        tryIgniteAdjacentToBlockHit(level, hit);
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.ICE)) {
            BlockPos p = hit.getBlockPos();
            level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
        }
        createExplosion(level, null, Vec3.atCenterOf(hit.getBlockPos()), explosionPower, breaksBlocks);
    }

    public static void applyProtegoCastPulse(ServerLevel level, ServerPlayer caster, Spell spell) {
        Vec3 center = caster.getBoundingBox().getCenter();
        AABB area = caster.getBoundingBox().inflate(3.0);
        for (Entity e : level.getEntities(caster, area, entity -> entity instanceof Projectile || entity instanceof LivingEntity)) {
            Vec3 away = e.getBoundingBox().getCenter().subtract(center);
            if (away.lengthSqr() < 1.0e-4) continue;
            applyKnockback(e, away, 1.2f);
        }
        for (BlockPos p : BlockPos.betweenClosed(
                BlockPos.containing(center).offset(-2, -1, -2),
                BlockPos.containing(center).offset(2, 2, 2))) {
            BlockState st = level.getBlockState(p);
            if (st.is(Blocks.FIRE) || st.is(Blocks.SOUL_FIRE)) {
                level.removeBlock(p, false);
            }
        }
        playSpellImpact(level, center, spell.getColor());
    }

    public static void applyArrestoAreaStabilize(ServerLevel level, ServerPlayer caster, Spell spell) {
        AABB area = caster.getBoundingBox().inflate(3.5, 2.5, 3.5);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && e != caster)) {
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOW_FALLING, 90, 0, false, true, true));
            living.fallDistance = 0f;
            Vec3 damped = living.getDeltaMovement().multiply(0.65, 0.45, 0.65);
            living.setDeltaMovement(damped);
            living.hurtMarked = true;
        }
        playSpellImpact(level, caster.getBoundingBox().getCenter(), spell.getColor());
    }

    public static final int AGUAMENTI_MIN_HOLD_FOR_SOURCE = 30;

    /**
     * @return true if the hit was farmland or a vanilla cauldron and was updated (no source-water hold)
     */
    public static boolean aguamentiSoakSoilOrFillCauldron(ServerLevel level, BlockPos hit, Spell spell) {
        BlockState st = level.getBlockState(hit);
        if (st.is(Blocks.FARMLAND) && st.hasProperty(BlockStateProperties.MOISTURE)) {
            level.setBlockAndUpdate(hit, st.setValue(BlockStateProperties.MOISTURE, 7));
            splash(level, hit, spell);
            return true;
        }
        if (st.is(Blocks.CAULDRON)) {
            level.setBlockAndUpdate(hit, Blocks.WATER_CAULDRON.defaultBlockState());
            splash(level, hit, spell);
            return true;
        }
        if (st.is(Blocks.WATER_CAULDRON) && st.hasProperty(LayeredCauldronBlock.LEVEL)) {
            int lv = st.getValue(LayeredCauldronBlock.LEVEL);
            if (lv < 3) {
                level.setBlockAndUpdate(hit, st.setValue(LayeredCauldronBlock.LEVEL, lv + 1));
            }
            splash(level, hit, spell);
            return true;
        }
        return false;
    }

    /**
     * Air block the beam would fill with a water source on long hold: air in front of a solid, or
     * first open air along the look ray.
     */
    @Nullable
    public static BlockPos aguamentiResolveSourceWaterAim(ServerLevel level, Vec3 start, Vec3 look,
                                                            float maxRange, BlockHitResult blockHit) {
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos().relative(blockHit.getDirection());
        }
        for (int step = 1; step <= (int) Math.ceil(maxRange); step++) {
            BlockPos c = BlockPos.containing(start.add(look.scale(step)));
            BlockState bs = level.getBlockState(c);
            if (bs.isAir() || bs.canBeReplaced()) {
                if (level.getFluidState(c).isEmpty()) {
                    return c;
                }
                return null;
            }
        }
        return null;
    }

    public static void aguamentiTryPlaceSourceAfterHold(ServerLevel level, ServerPlayer caster, Spell spell,
                                                        BlockPos waterTarget, int waterHoldTicks) {
        if (waterTarget == null) {
            return;
        }
        if (waterHoldTicks < AGUAMENTI_MIN_HOLD_FOR_SOURCE) {
            return;
        }
        BlockState at = level.getBlockState(waterTarget);
        if (!at.isAir() && !at.canBeReplaced()) {
            return;
        }
        if (!level.getFluidState(waterTarget).isEmpty()) {
            return;
        }
        level.setBlockAndUpdate(waterTarget, Blocks.WATER.defaultBlockState());
        level.playSound(null, waterTarget, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.25f, 1.0f);
        splash(level, waterTarget, spell);
    }

    private static void splash(ServerLevel level, BlockPos pos, Spell spell) {
        Vec3 c = pos.getCenter();
        level.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 10, 0.25, 0.1, 0.25, 0.0);
        spawnBurst(level, c, spell.getColor(), 8, 0.2);
    }

    // ── Particles ───────────────────────────────────────────────────────

    /**
     * Spawns a colored particle beam between two points.
     */
    public static void spawnBeam(ServerLevel level, Vec3 from, Vec3 to, int argbColor) {
        SpellParticles.spawnBeam(level, from, to, argbColor);
    }

    /**
     * Spawns a short colored particle trail behind a moving entity/projectile.
     */
    public static void spawnTrail(ServerLevel level, Vec3 position, Vec3 motion,
                                   int argbColor, int segments) {
        SpellParticles.spawnTrail(level, position, motion, argbColor, segments);
    }

    /**
     * Spawns a burst of colored particles at a position.
     */
    public static void spawnBurst(ServerLevel level, Vec3 pos, int argbColor,
                                   int count, double spread) {
        SpellParticles.spawnBurst(level, pos, argbColor, count, spread);
    }
}
