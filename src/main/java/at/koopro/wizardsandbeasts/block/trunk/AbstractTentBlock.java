package at.koopro.wizardsandbeasts.block.trunk;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Shared shell for the two tent preview blocks — see the Tent Models (Exterior Geometry) prompt. Everything
 * here is identical between {@code tent_canvas} and {@code tent_grand}; each subclass supplies only its own
 * {@link TentBlockEntity} (registered under its own {@link BlockEntityType}) and {@code MapCodec}, matching
 * how {@code FlooFireplaceBlock}/{@code PocketConfiguratorBlock} are each one small concrete class rather
 * than a shared factory-injected one.
 *
 * <p>Deliberately minimal: no gameplay, no pocket-space linkage. Collision/outline are the vanilla default
 * full single-block {@code VoxelShape} — never overridden — so "footprint is one block, the model is not"
 * costs zero extra code, the same way {@code TrunkBlock} does it. {@link #FACING} mirrors
 * {@code TrunkBlock.FACING}: the entrance flap faces the placer, matching {@code GeoBlockRenderer}'s
 * automatic {@code HORIZONTAL_FACING} rotation (no per-tent override needed — see
 * {@code AUDIT_PUNCHLIST.md}).
 *
 * <p>{@link #getRenderShape} returns {@link RenderShape#INVISIBLE}, not {@code MODEL}: 100% of the visible
 * geometry comes from the GeckoLib {@code BlockEntityRenderer}, so there is deliberately no
 * blockstate/block-model JSON — which is also why neither tent has a {@code BlockItem} (out of scope per
 * the prompt; placement here is {@code /setblock} only).
 */
public abstract class AbstractTentBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected AbstractTentBlock(BlockBehaviour.@NonNull Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        return null;
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level,
                                                        @NonNull BlockPos pos, @NonNull Player player,
                                                        @NonNull BlockHitResult hit) {
        // No interaction behaviour in this prompt (§4) — only the doctrine every gated block in the mod
        // follows: registration is never gated, so a disabled module still leaves a visible, inert tent.
        if (!level.isClientSide() && !ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) {
            player.displayClientMessage(
                    Component.literal("The tent is folded up tight.").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }
}
