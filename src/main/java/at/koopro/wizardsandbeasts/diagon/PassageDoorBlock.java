package at.koopro.wizardsandbeasts.diagon;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NullMarked;

/**
 * The Leaky Cauldron's back door, and the yard's door back into the pub: it never swings open, it takes you through.
 *
 * <p>The pub stands in the overworld and its yard, the wall and Diagon Alley stand in a pocket dimension, so nothing
 * behind the pub can be reached or seen from outside it. Using the door from the bar steps you into the yard; using it
 * from the yard steps you back to the bar. The client shows a short fade, not a loading screen
 * ({@code client.diagon.DiagonPassageScreen}). Unbreakable, no drops: it belongs to the building.
 */
@NullMarked
public final class PassageDoorBlock extends DoorBlock {

    public static final MapCodec<PassageDoorBlock> CODEC = simpleCodec(PassageDoorBlock::new);

    public PassageDoorBlock(Properties properties) {
        super(BlockSetType.DARK_OAK, properties);
    }

    @Override
    public MapCodec<PassageDoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer traveller) {
            BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            server.playSound(null, lower, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.8f, 0.85f);
            if (server.dimension() == DiagonAlley.LEVEL) {
                DiagonAlley.leave(traveller);
            } else {
                DiagonAlley.enter(traveller, lower, state.getValue(FACING));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
