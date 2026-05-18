package at.koopro.wizardsandbeasts.block;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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

    public ExaminationDeskBlock(BlockBehaviour.Properties properties) {
        super(properties);
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
