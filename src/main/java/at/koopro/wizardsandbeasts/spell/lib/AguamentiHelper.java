package at.koopro.wizardsandbeasts.spell.lib;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.brew.CauldronPhase;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/** Aguamenti water-placement and cauldron-fill helpers. */
public final class AguamentiHelper {

    public static final int AGUAMENTI_MIN_HOLD_FOR_SOURCE = 30;

    private AguamentiHelper() {}

    /**
     * @return true if the hit was farmland or a cauldron — vanilla or brewing — and was handled (no source-water hold)
     */
    public static boolean aguamentiSoakSoilOrFillCauldron(ServerLevel level, ServerPlayer caster, BlockPos hit,
                                                          Spell spell) {
        BlockState st = level.getBlockState(hit);
        if (st.is(Blocks.FARMLAND) && st.hasProperty(BlockStateProperties.MOISTURE)) {
            level.setBlockAndUpdate(hit, st.setValue(BlockStateProperties.MOISTURE, 7));
            splash(level, hit, spell);
            return true;
        }
        // A brewing pot keeps its water on the block entity, not in its blockstate, so the vanilla checks below
        // never matched it and the jet went on to stack a source block against the pot. Filled the way a water
        // bucket fills it — an idle, empty pot only; anything else is splashed and left alone.
        if (level.getBlockEntity(hit) instanceof CauldronBlockEntity pot) {
            if (pot.phase() == CauldronPhase.IDLE && !pot.isFilled()) {
                pot.setFilled(true);
                level.playSound(null, hit, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
                PlayerFeedback.actionBar(caster, Component.translatable("brew.wizards_and_beasts.cauldron.filled"));
            }
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
     * The open cell on the near side of the block the jet hits, where a long hold places a source — or
     * {@code null} when there is nowhere to put one.
     *
     * <p>Nothing when the jet hits nothing. This used to fall back to the first open cell along the aim, and in
     * open air that is the cell one block from the caster's eyes: a hold at the sky poured water on their head.
     *
     * <p>Nothing in or above the caster's own footprint either, because water there lands on them — aiming up at
     * a low ceiling did the same thing from the other side.
     */
    @Nullable
    public static BlockPos aguamentiResolveSourceWaterAim(ServerLevel level, BlockHitResult blockHit, AABB casterBody) {
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos target = blockHit.getBlockPos().relative(blockHit.getDirection());
        BlockState at = level.getBlockState(target);
        if ((!at.isAir() && !at.canBeReplaced()) || !level.getFluidState(target).isEmpty()) {
            return null;
        }
        return drainsOnto(target, casterBody) ? null : target;
    }

    /** Whether water in {@code cell} would be inside {@code body} or fall onto it: its column, at or above its feet. */
    private static boolean drainsOnto(BlockPos cell, AABB body) {
        return cell.getX() < body.maxX && cell.getX() + 1 > body.minX
                && cell.getZ() < body.maxZ && cell.getZ() + 1 > body.minZ
                && cell.getY() + 1 > body.minY;
    }

    /** Places a source at a target from {@link #aguamentiResolveSourceWaterAim} once it has been held long enough. */
    public static void aguamentiTryPlaceSourceAfterHold(ServerLevel level, Spell spell,
                                                        @Nullable BlockPos waterTarget, int waterHoldTicks) {
        if (waterTarget == null || waterHoldTicks < AGUAMENTI_MIN_HOLD_FOR_SOURCE) {
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
