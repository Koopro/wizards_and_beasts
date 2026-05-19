package at.koopro.wizardsandbeasts.block.floo;

import at.koopro.wizardsandbeasts.floo.FlooDestinationDto;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.floo.OpenFlooGuiS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Functional Floo Network travel block.
 * LORE: A fireplace connected to the Floo Network. Players throw Floo Powder into
 * the cold fireplace to light it, then step in and announce their destination.
 * Emits emerald-green dust particles and light level 10 when lit.
 * Managed by {@link at.koopro.wizardsandbeasts.floo.FlooNetworkManager} — must be
 * registered via {@code /wandb floo register} before it accepts travel.
 */
public class FlooFireplaceBlock extends BaseEntityBlock {

    public static final MapCodec<FlooFireplaceBlock> CODEC = simpleCodec(FlooFireplaceBlock::new);

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public FlooFireplaceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(LIT, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LIT, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new FlooFireplaceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.FLOO_FIREPLACE.get(),
                FlooFireplaceBlockEntity::serverTick);
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level,
                                                         @NonNull BlockPos pos, @NonNull Player player,
                                                         @NonNull BlockHitResult hit) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            return InteractionResult.PASS;
        }
        if (state.getValue(LIT)) {
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                if (level.getBlockEntity(pos) instanceof FlooFireplaceBlockEntity be) {
                    if (!be.isRegistered()) {
                        sp.displayClientMessage(
                                Component.literal("This fireplace is not registered on the Floo Network."), true);
                        return InteractionResult.SUCCESS;
                    }
                    if (!be.isEnabled()) {
                        sp.displayClientMessage(
                                Component.literal("That connection has been sealed."), true);
                        return InteractionResult.SUCCESS;
                    }
                    FlooNetworkManager manager = FlooNetworkManager.get((ServerLevel) sp.level());
                    List<FlooDestinationDto> destinations = manager.getDestinations(sp).stream()
                            .filter(e -> !e.networkAddress().equalsIgnoreCase(be.getNetworkAddress()))
                            .map(e -> new FlooDestinationDto(e.networkAddress(),
                                    e.dimension().toString(), e.isEnabled()))
                            .toList();
                    OpenFlooGuiS2CPayload.send(sp, destinations);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            player.displayClientMessage(
                    Component.literal("The fireplace is cold. You need Floo Powder."), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level,
                             @NonNull BlockPos pos, @NonNull RandomSource random) {
        if (!state.getValue(LIT)) return;
        // VISUAL: dust particle approximation — replace with custom floo flame particle when available
        DustParticleOptions greenDust = new DustParticleOptions(0xFF21B342, 1.2f);
        int count = 4 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double cx = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            double cy = pos.getY() + 0.2 + random.nextDouble() * 0.4;
            double cz = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            level.addParticle(greenDust, cx, cy, cz, 0, 0.04, 0);
        }
    }
}
