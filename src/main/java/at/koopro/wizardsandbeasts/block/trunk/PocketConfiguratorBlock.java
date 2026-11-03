package at.koopro.wizardsandbeasts.block.trunk;

import at.koopro.wizardsandbeasts.block.ExpansionFocusBlockEntity;
import at.koopro.wizardsandbeasts.trunk.TrunkRegistryData;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jspecify.annotations.Nullable;

public class PocketConfiguratorBlock extends BaseEntityBlock {

    public static final MapCodec<PocketConfiguratorBlock> CODEC = simpleCodec(PocketConfiguratorBlock::new);

    public PocketConfiguratorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExpansionFocusBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    /** The plinth and the rings above it, not the whole cube. */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            net.minecraft.world.phys.shapes.Shapes.or(net.minecraft.world.level.block.Block.box(2, 0, 2, 14, 8, 14),
                    net.minecraft.world.level.block.Block.box(4, 8, 4, 12, 16, 12));

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                                                  BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    /** Drawn by the armillary rig; the JSON cube stays for the item icon and break particles. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            if (level.getBlockEntity(pos) instanceof ExpansionFocusBlockEntity be) {
                be.play(ExpansionFocusBlockEntity.CLIP_ATTUNE);
                sp.openMenu(be, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()
                && level instanceof ServerLevel sl
                && sl.dimension().equals(ModDimensions.EXTENSION_REALM)
                && sl.getBlockEntity(pos) instanceof ExpansionFocusBlockEntity be) {
            TrunkRegistryData.get(sl).getPocketAtPos(pos).ifPresent(r -> be.setPocketId(r.pocketId()));
        }
    }
}
