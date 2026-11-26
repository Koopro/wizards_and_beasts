package at.koopro.wizardsandbeasts.block.floo;

import at.koopro.wizardsandbeasts.floo.FlooCues;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The iron basket grate that stands in a hearth's opening: the heart of a player-built Floo hearth (Floo remake,
 * 2026-11-26). Logs go on it ({@link #FUEL}), an ordinary fire burns on them ({@link HearthFire#WARM}), and Floo Powder
 * turns that fire emerald ({@link HearthFire#FLOO}). The fire itself is drawn by {@code FlooGrateRenderer} so the green
 * roar can fill the whole opening and rise, not just this one block.
 *
 * <p>Low collision (4 px) so a traveller steps onto the grate and stands in the fire, as Harry and Ron stand upright in
 * the Burrow hearth (CANON.md).
 */
@NullMarked
public class FlooGrateBlock extends BaseEntityBlock {

    public static final MapCodec<FlooGrateBlock> CODEC = simpleCodec(FlooGrateBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FUEL = BooleanProperty.create("fuel");
    public static final EnumProperty<HearthFire> FIRE = EnumProperty.create("fire", HearthFire.class);

    private static final VoxelShape OUTLINE_NS = Block.box(2, 0, 3, 14, 10, 13);
    private static final VoxelShape OUTLINE_EW = Block.box(3, 0, 2, 13, 10, 14);
    private static final VoxelShape STEP = Block.box(0, 0, 0, 16, 4, 16);

    public FlooGrateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FUEL, false)
                .setValue(FIRE, HearthFire.NONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FUEL, FIRE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // The grate's front faces the room, i.e. the player placing it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? OUTLINE_NS : OUTLINE_EW;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return STEP;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlooGrateBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.FLOO_GRATE.get(), FlooGrateBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Placing the grate last into a finished build is the usual way to finish a hearth: answer at once.
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof FlooGrateBlockEntity grate) {
            grate.recheck(server, state);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        HearthFire fire = state.getValue(FIRE);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        switch (fire) {
            case WARM -> {
                if (random.nextInt(10) == 0) {
                    level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                            0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
                }
                for (int i = 0; i < 2; i++) {
                    level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.5,
                            y + 0.4 + random.nextDouble() * 0.4, z + (random.nextDouble() - 0.5) * 0.4, 0, 0.05, 0);
                }
                if (random.nextInt(6) == 0) {
                    level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
                }
            }
            case FLOO -> {
                // Green motes rising across the whole opening (three wide), the roar's sparks.
                Direction across = state.getValue(FACING).getClockWise();
                for (int i = 0; i < 3; i++) {
                    double t = (random.nextDouble() - 0.5) * 2.6;
                    level.addParticle(new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), FlooCues.EMERALD),
                            x + across.getStepX() * t, y + random.nextDouble() * 2.4, z + across.getStepZ() * t,
                            0, 0.06 + random.nextDouble() * 0.05, 0);
                }
                if (random.nextInt(8) == 0) {
                    level.playLocalSound(x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                            1.0F, 0.6F + random.nextFloat() * 0.2F, false);
                }
            }
            case NONE -> {
            }
        }
    }
}
