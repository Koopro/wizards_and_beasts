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

import javax.annotation.Nullable;

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

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            if (level.getBlockEntity(pos) instanceof ExpansionFocusBlockEntity be) {
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
