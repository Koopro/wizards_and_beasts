package at.koopro.wizardsandbeasts.wand.bench;

import at.koopro.wizardsandbeasts.item.wand.WandModuleHooks;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class WandmakersBenchBlock extends BaseEntityBlock implements EntityBlock {
    public static final MapCodec<WandmakersBenchBlock> CODEC = simpleCodec(WandmakersBenchBlock::new);

    public WandmakersBenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!WandModuleHooks.isWandsEnabled()) {
            return InteractionResult.FAIL;
        }
        if (!(level.getBlockEntity(pos) instanceof WandmakersBenchBlockEntity bench)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            bench.recalcTierScore();
            List<Identifier> enhIds = new ArrayList<>();
            for (BlockPos ep : bench.getDetectedEnhancers()) {
                Identifier bid = BuiltInRegistries.BLOCK.getKey(level.getBlockState(ep).getBlock());
                if (bid != null) {
                    enhIds.add(bid);
                }
            }
            List<Identifier> enhSnapshot = List.copyOf(enhIds);
            MenuProvider provider = new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("gui.wizards_and_beasts.wandmakers_bench");
                }

                @Override
                public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player p) {
                    return new WandmakersBenchMenu(syncId, inv, bench, enhSnapshot);
                }
            };
            final List<Identifier> finalEnh = enhSnapshot;
            serverPlayer.openMenu(provider, buf -> {
                buf.writeBlockPos(pos);
                buf.writeVarInt(finalEnh.size());
                for (Identifier id : finalEnh) {
                    buf.writeUtf(id.toString(), 320);
                }
            });
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, Orientation orientation, boolean isMoving) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof WandmakersBenchBlockEntity bench) {
            bench.recalcTierScore();
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WandmakersBenchBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide()) {
            return null;
        }
        return (tickerLevel, tickerPos, tickerState, blockEntity) -> {
            if (!(blockEntity instanceof WandmakersBenchBlockEntity bench)) {
                return;
            }
            if (tickerLevel.getGameTime() % 10 != 0) {
                return;
            }
            if (bench.getDetectedEnhancers().isEmpty()) {
                return;
            }
            RandomSource random = tickerLevel.getRandom();
            for (BlockPos enhancerPos : bench.getDetectedEnhancers()) {
                double startX = enhancerPos.getX() + 0.5;
                double startY = enhancerPos.getY() + 0.75;
                double startZ = enhancerPos.getZ() + 0.5;
                double targetX = tickerPos.getX() + 0.5;
                double targetY = tickerPos.getY() + 0.8;
                double targetZ = tickerPos.getZ() + 0.5;
                double velocityX = (targetX - startX) * 0.05 + (random.nextDouble() - 0.5) * 0.01;
                double velocityY = (targetY - startY) * 0.05 + 0.01;
                double velocityZ = (targetZ - startZ) * 0.05 + (random.nextDouble() - 0.5) * 0.01;
                tickerLevel.addParticle(ParticleTypes.ENCHANT, startX, startY, startZ, velocityX, velocityY, velocityZ);
            }
        };
    }
}
