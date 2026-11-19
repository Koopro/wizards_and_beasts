package at.koopro.wizardsandbeasts.brew;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;

/**
 * How a ruined brew goes wrong. It used to be the same grey sludge and puff of smoke every time; Potions
 * class is remembered for its disasters, and they were not all alike:
 * <ul>
 *   <li>{@link Kind#MELT}: Neville melts Seamus's cauldron into a twisted blob (<i>Philosopher's Stone</i>
 *       ch. 8). Only a pewter pot, and only sometimes; the pot is gone and leaves a few iron nuggets.</li>
 *   <li>{@link Kind#BOIL_OVER}: the brew foams over the rim and splashes everyone within
 *       {@link #SPLASH_RADIUS} blocks: retching, and a little scalding.</li>
 *   <li>{@link Kind#SMOKE}: it gives up in a cloud of smoke the colour of what it was meant to be.</li>
 * </ul>
 * Which one is picked from the pot and the dice; it never depends on who is standing nearby.
 */
@NullMarked
public final class BrewMishap {

    public enum Kind { MELT, BOIL_OVER, SMOKE }

    public static final double SPLASH_RADIUS = 3.0;
    public static final float MELT_CHANCE_PEWTER = 0.25f;
    public static final float BOIL_OVER_CHANCE = 0.5f;

    private BrewMishap() {}

    public static Kind pick(CauldronTier tier, RandomSource random) {
        if (tier == CauldronTier.PEWTER && random.nextFloat() < MELT_CHANCE_PEWTER) {
            return Kind.MELT;
        }
        return random.nextFloat() < BOIL_OVER_CHANCE ? Kind.BOIL_OVER : Kind.SMOKE;
    }

    /**
     * Plays the mishap out.
     *
     * @param colour the ruined brew's colour, ARGB; grey when it was unknown
     * @return whether the cauldron itself is gone
     */
    public static boolean happen(ServerLevel level, BlockPos pos, Kind kind, int colour) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.9;
        double z = pos.getZ() + 0.5;
        DustParticleOptions tinted = new DustParticleOptions(colour & 0xFFFFFF, 1.6f);
        switch (kind) {
            case MELT -> {
                level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.6f);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 30, 0.3, 0.3, 0.3, 0.03);
                level.sendParticles(ParticleTypes.LAVA, x, y, z, 8, 0.2, 0.1, 0.2, 0.0);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(Items.IRON_NUGGET, 3));
                splash(level, pos, 2.0f);
                return true;
            }
            case BOIL_OVER -> {
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 0.7f);
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f, 0.5f);
                level.sendParticles(tinted, x, y, z, 40, 0.8, 0.3, 0.8, 0.0);
                level.sendParticles(ParticleTypes.SPLASH, x, y, z, 40, 0.8, 0.2, 0.8, 0.2);
                splash(level, pos, 1.0f);
            }
            case SMOKE -> {
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.7f, 0.5f);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 24, 0.25, 0.2, 0.25, 0.02);
                level.sendParticles(tinted, x, y + 0.4, z, 40, 0.4, 0.6, 0.4, 0.0);
            }
        }
        return false;
    }

    private static void splash(ServerLevel level, BlockPos pos, float scald) {
        AABB box = new AABB(pos).inflate(SPLASH_RADIUS);
        java.util.Set<LivingEntity> hit = new java.util.LinkedHashSet<>(level.getEntitiesOfClass(LivingEntity.class, box));
        hit.addAll(level.getPlayers(p -> box.contains(p.position())));
        for (LivingEntity e : hit) {
            e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
            e.hurtServer(level, level.damageSources().hotFloor(), scald);
        }
    }
}
