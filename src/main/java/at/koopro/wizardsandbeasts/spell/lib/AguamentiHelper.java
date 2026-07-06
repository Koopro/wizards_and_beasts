package at.koopro.wizardsandbeasts.spell.lib;

import at.koopro.wizardsandbeasts.spell.core.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/** Aguamenti water-placement and cauldron-fill helpers. */
public final class AguamentiHelper {

    public static final int AGUAMENTI_MIN_HOLD_FOR_SOURCE = 30;

    private AguamentiHelper() {}

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
        SpellHelper.spawnBurst(level, spell, c, 8, 0.2);
    }
}
