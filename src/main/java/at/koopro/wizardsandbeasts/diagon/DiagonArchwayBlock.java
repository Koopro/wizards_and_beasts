package at.koopro.wizardsandbeasts.diagon;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A brick of the archway into Diagon Alley, at rest: a plain brick, drawn and lit exactly like the wall round it, so the
 * arch cannot be told from the wall. A wand on the marked brick ({@link #KEY}, three up and two across from the
 * dustbin) opens every archway brick joined to it.
 *
 * <p>Opening hands each brick to {@link DiagonArchwayMovingBlock}, which plays its part (the bricks nearest the tap
 * wriggle first, then each ring folds back into the wall and shrinks away) and stands open until nobody is in the arch;
 * closing plays it backwards and hands the bricks back here. A wand on any other brick, or the key without a wand, does
 * nothing. Unbreakable, no drops.
 */
@NullMarked
public final class DiagonArchwayBlock extends Block {

    public static final MapCodec<DiagonArchwayBlock> CODEC = simpleCodec(DiagonArchwayBlock::new);
    public static final BooleanProperty KEY = BooleanProperty.create("key");

    /** Bricks in one archway at most: the joined group is walked from the key brick. */
    private static final int MAX_GROUP = 64;

    public DiagonArchwayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KEY, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KEY);
    }

    /** A wand on the marked brick opens the archway; the click is the wall's, so the wand does not also cast. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof WandItem) || !state.getValue(KEY)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server) {
            setArch(server, pos, true);
        }
        return InteractionResult.SUCCESS;
    }

    static boolean isArchway(BlockState state) {
        return state.getBlock() instanceof DiagonArchwayBlock || state.getBlock() instanceof DiagonArchwayMovingBlock;
    }

    static boolean isKey(BlockState state) {
        return state.getBlock() instanceof DiagonArchwayBlock ? state.getValue(KEY)
                : state.getBlock() instanceof DiagonArchwayMovingBlock && state.getValue(DiagonArchwayMovingBlock.KEY);
    }

    /** Every archway brick joined face to face with the key brick, resting or moving, the key first. */
    static List<BlockPos> group(Level level, BlockPos key) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(key);
        seen.add(key);
        while (!queue.isEmpty() && found.size() < MAX_GROUP) {
            BlockPos at = queue.poll();
            found.add(at);
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (seen.add(next) && isArchway(level.getBlockState(next))) {
                    queue.add(next);
                }
            }
        }
        return found;
    }

    /**
     * Opens or closes the whole archway round the key brick: every brick becomes a moving brick, told its ring (its
     * distance from the key, so the hole starts where the wand touched) and the side of the arch it folds into. Bricks in
     * the top course and the middle column fold up into the lintel; the rest fold sideways into the nearer jamb.
     */
    static void setArch(ServerLevel level, BlockPos key, boolean open) {
        long now = level.getGameTime();
        BlockState moving = ModBlocks.DIAGON_ARCHWAY_MOVING.get().defaultBlockState()
                .setValue(DiagonArchwayMovingBlock.OPEN, open);
        List<BlockPos> bricks = group(level, key);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : bricks) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        // The wall runs along whichever horizontal axis the arch spreads over; its normal is the other.
        boolean alongZ = maxZ - minZ >= maxX - minX;
        float normalX = alongZ ? 1f : 0f;
        float normalZ = alongZ ? 0f : 1f;
        for (BlockPos pos : bricks) {
            boolean isKey = isKey(level.getBlockState(pos));
            level.setBlock(pos, moving.setValue(DiagonArchwayMovingBlock.KEY, isKey), Block.UPDATE_ALL);
            int along = alongZ ? pos.getZ() : pos.getX();
            int lo = alongZ ? minZ : minX;
            int hi = alongZ ? maxZ : maxX;
            int toLo = along - lo;
            int toHi = hi - along;
            float fx = 0f, fy = 0f, fz = 0f, slide;
            if (pos.getY() == maxY || toLo == toHi) {
                fy = 1f;                                   // up into the lintel
                slide = maxY - pos.getY() + 1;
            } else {
                float sign = toHi < toLo ? 1f : -1f;       // into the nearer jamb
                if (alongZ) {
                    fz = sign;
                } else {
                    fx = sign;
                }
                slide = Math.min(toLo, toHi) + 1;
            }
            if (level.getBlockEntity(pos) instanceof DiagonArchwayBlockEntity brick) {
                float dx = pos.getX() - key.getX();
                float dy = pos.getY() - key.getY();
                float dz = pos.getZ() - key.getZ();
                int ring = Math.round((float) Math.sqrt(dx * dx + dy * dy + dz * dz));
                brick.start(now, ring, fx, fy, fz, slide, normalX, normalZ);
            }
            if (!open) {
                level.scheduleTick(pos, ModBlocks.DIAGON_ARCHWAY_MOVING.get(), DiagonArchwayMovingBlock.SETTLE_TICKS);
            } else {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                                ModBlocks.DIAGON_ARCHWAY.get().defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5,
                        pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
            }
        }
        level.playSound(null, key, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.9f, open ? 0.55f : 0.45f);
        level.playSound(null, key, SoundEvents.DEEPSLATE_BRICKS_PLACE, SoundSource.BLOCKS, 0.8f, 0.6f);
        if (open) {
            level.playSound(null, key, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 0.8f);
        }
    }
}
