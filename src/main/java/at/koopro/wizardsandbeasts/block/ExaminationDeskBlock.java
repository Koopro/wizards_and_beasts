package at.koopro.wizardsandbeasts.block;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Placed inside the Hogwarts worldgen structure.
 * Block tag path: data/wizards_and_beasts/tags/block/hogwarts_structure_blocks.json
 *
 * Client-side screen opening is wired via {@link #onClientInteract} — set during
 * FMLClientSetupEvent by ExaminationDeskClientHandler. Null on the server; never invoked
 * server-side because the isClientSide guard runs first.
 */
public class ExaminationDeskBlock extends Block {

    /** Set by ExaminationDeskClientHandler on FMLClientSetupEvent. Null on server. */
    public static @Nullable Runnable onClientInteract = null;

    /** Top slab on four corner legs — matches the model, so the gaps under it are real. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 12, 0, 16, 15, 16),
            Block.box(1, 0, 1, 4, 12, 4),
            Block.box(12, 0, 1, 15, 12, 4),
            Block.box(1, 0, 12, 4, 12, 15),
            Block.box(12, 0, 12, 15, 12, 15));

    public ExaminationDeskBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!ModuleManager.isEnabled(Module.OWLS)) return InteractionResult.PASS;
        if (level.isClientSide() && onClientInteract != null) {
            onClientInteract.run();
        }
        return InteractionResult.SUCCESS;
    }
}
