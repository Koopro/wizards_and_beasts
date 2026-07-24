package at.koopro.wizardsandbeasts.spell.lib;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;
import java.util.Map;

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
    /**
     * Fires the caster's look ray and tries to set the struck surface alight.
     *
     * @param impactColor particle colour for the scorch shown when a surface is struck but cannot hold fire
     * @return {@code true} only if fire was actually placed
     */
    public static boolean tryIgniteBlockAlongLook(ServerLevel level, ServerPlayer caster, float maxRange,
                                                  int impactColor) {
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(caster.getLookAngle().scale(maxRange));
        BlockHitResult blockHit = level.clip(new ClipContext(
                start, end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                caster));
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        if (tryIgniteAdjacentToBlockHit(level, blockHit)) {
            return true;
        }
        // A surface was struck but fire cannot survive there — a bare wall face, a wet or unsupported spot.
        // Scorch it anyway: without this the spell looked like it simply had not fired, which is most of
        // why fire spells read as "working only sometimes".
        playSpellImpact(level, blockHit.getLocation(), impactColor);
        return false;
    }

    /**
     * Places fire in the air block adjacent to the hit face (flint-and-steel style).
     *
     * @return {@code true} if fire was placed; {@code false} when the spot cannot hold it
     */
    public static boolean tryIgniteAdjacentToBlockHit(ServerLevel level, BlockHitResult blockHit) {
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = blockHit.getBlockPos().relative(blockHit.getDirection());
        if (!BaseFireBlock.canBePlacedAt(level, pos, blockHit.getDirection().getOpposite())) {
            return false;
        }
        BlockState fire = BaseFireBlock.getState(level, pos);
        if (fire == null) {
            return false;
        }
        level.setBlockAndUpdate(pos, fire);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35f, 0.8f);
        return true;
    }

    /**
     * Scatters fire across valid ground within {@code radius} of {@code center} — the lingering
     * flame-patch (Incendio) / fire-shrapnel (Confringo) area-denial. Only places fire where vanilla
     * permits it (air with a supporting face), so it self-limits on open, wet or unsupported terrain.
     *
     * @return the number of fire blocks actually placed
     */
    public static int scatterGroundFire(ServerLevel level, Vec3 center, int attempts, double radius) {
        int placed = 0;
        for (int i = 0; i < attempts; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double dist = level.random.nextDouble() * radius;
            double x = center.x + Math.cos(angle) * dist;
            double z = center.z + Math.sin(angle) * dist;
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos p = BlockPos.containing(x, center.y + dy, z);
                if (!level.getBlockState(p).isAir()) {
                    continue;
                }
                if (!BaseFireBlock.canBePlacedAt(level, p, net.minecraft.core.Direction.UP)) {
                    continue;
                }
                BlockState fire = BaseFireBlock.getState(level, p);
                if (fire != null) {
                    level.setBlockAndUpdate(p, fire);
                    placed++;
                }
                break;
            }
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, center.x, center.y + 0.3, center.z,
                Math.max(4, attempts), radius * 0.5, 0.1, radius * 0.5, 0.0);
        return placed;
    }

    /** Common impact burst + soft impact sound for world interactions. */
    public static void playSpellImpact(ServerLevel level, Vec3 pos, int color) {
        spawnBurst(level, pos, color, 10, 0.18);
        level.playSound(null, BlockPos.containing(pos), ModSounds.SPELL_IMPACT_GENERIC.get(), SoundSource.PLAYERS,
                0.22f, 1.05f + level.random.nextFloat() * 0.12f);
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

        // Freeze a small terrain patch: water sheets over to slippery ice, lava sets into obsidian/stone.
        if (freezeTerrainPatch(level, hitPos, 2)) {
            level.playSound(null, hitPos, SoundEvents.POWDER_SNOW_PLACE, SoundSource.PLAYERS, 0.5f, 0.9f);
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

    /**
     * Freezes fluid blocks within {@code radius} of {@code center}: water becomes slippery ice, lava
     * sources set into obsidian (flowing lava into cobblestone). Only pure fluid blocks are touched so
     * waterlogged/decorated blocks are left intact.
     *
     * @return true if at least one block was frozen
     */
    private static boolean freezeTerrainPatch(ServerLevel level, BlockPos center, int radius) {
        boolean any = false;
        for (BlockPos p : BlockPos.betweenClosed(
                center.offset(-radius, -1, -radius), center.offset(radius, 1, radius))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.WATER)) {
                level.setBlockAndUpdate(p.immutable(), Blocks.ICE.defaultBlockState());
                any = true;
            } else if (s.is(Blocks.LAVA)) {
                BlockState frozen = s.getFluidState().isSource()
                        ? Blocks.OBSIDIAN.defaultBlockState()
                        : Blocks.COBBLESTONE.defaultBlockState();
                level.setBlockAndUpdate(p.immutable(), frozen);
                any = true;
            }
        }
        return any;
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
            spawnBurst(level, spell, center, 10, 0.18);
            level.playSound(null, BlockPos.containing(center), ModSounds.SPELL_IMPACT_STUPEFY.get(), SoundSource.PLAYERS,
                    0.38f, 1.03f + level.random.nextFloat() * 0.1f);
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
        // Fire shrapnel: the blast scatters embers that leave a ring of lingering fire for area denial.
        scatterGroundFire(level, Vec3.atCenterOf(hit.getBlockPos()), 6, 2.2);
    }

    public static void applyProtegoCastPulse(ServerLevel level, ServerPlayer caster, Spell spell) {
        level.playSound(null, caster.blockPosition(), ModSounds.PROTEGO_RAISE.get(), SoundSource.PLAYERS,
                0.88f, 1.0f + level.random.nextFloat() * 0.12f);
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
        spawnBurst(level, spell, center, 12, 0.2);
    }

    public static void applyArrestoAreaStabilize(ServerLevel level, ServerPlayer caster, Spell spell) {
        AABB area = caster.getBoundingBox().inflate(4.0, 3.0, 4.0);
        // Time-stop bubble: everything but the caster is caught. Living things are slow-fallen, near-frozen
        // in place (velocity killed + heavy slowness), and projectiles have their flight halted.
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && e != caster)) {
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOW_FALLING, 90, 0, false, true, true));
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOWNESS, 40, 6, false, true, true));
            living.fallDistance = 0f;
            living.setDeltaMovement(living.getDeltaMovement().multiply(0.0, 0.2, 0.0));
            living.hurtMarked = true;
        }
        for (net.minecraft.world.entity.projectile.Projectile proj :
                level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, area,
                        p -> p.isAlive() && p.getOwner() != caster)) {
            proj.setDeltaMovement(Vec3.ZERO);
            proj.hurtMarked = true;
        }
        playSpellImpact(level, caster.getBoundingBox().getCenter(), spell.getColor());
    }

    // ── Particles ───────────────────────────────────────────────────────

    public static void spawnBeam(ServerLevel level, Spell spell, Vec3 from, Vec3 to) {
        SpellParticles.spawnBeam(level, spell, from, to);
    }

    /**
     * Spawns a colored particle beam between two points.
     */
    public static void spawnBeam(ServerLevel level, Vec3 from, Vec3 to, int argbColor) {
        SpellParticles.spawnBeam(level, from, to, argbColor);
    }

    public static void spawnTrail(ServerLevel level, Spell spell, Vec3 position, Vec3 motion, int segments) {
        SpellParticles.spawnTrail(level, spell, position, motion, segments);
    }

    /**
     * Spawns a short colored particle trail behind a moving entity/projectile.
     */
    public static void spawnTrail(ServerLevel level, Vec3 position, Vec3 motion,
                                   int argbColor, int segments) {
        SpellParticles.spawnTrail(level, position, motion, argbColor, segments);
    }

    public static void spawnBurst(ServerLevel level, Spell spell, Vec3 pos, int count, double spread) {
        SpellParticles.spawnBurst(level, spell, pos, count, spread);
    }

    public static void spawnBurst(ServerLevel level, SpellFamily family, int argb, Vec3 pos, int count,
                                  double spread) {
        SpellParticles.spawnBurst(level, family, argb, pos, count, spread);
    }

    /**
     * Spawns a burst of colored particles at a position.
     */
    public static void spawnBurst(ServerLevel level, Vec3 pos, int argbColor,
                                   int count, double spread) {
        SpellParticles.spawnBurst(level, pos, argbColor, count, spread);
    }
}
