package at.koopro.wizardsandbeasts.command.debug.inspect;

import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

/**
 * What a feature knows about one thing in the world.
 *
 * <p>Block entities and mobs are the two places in this mod where state hides. A cauldron holds a
 * phase, a timer, a recipe and six item slots and shows you a texture; a beast holds a trait sheet,
 * an ability set and a tame state and shows you a model. When either misbehaves the question is
 * always "what does the server actually think this is", and until now the only way to ask was a
 * command that answered into chat — which scrolls, which is per-player, and which cannot be read
 * while you are looking at the thing it describes.
 *
 * <p>An inspector answers that question once, as data. {@code /wandb debug inspect} prints it and
 * the floating panel draws it, and neither knows anything about cauldrons.
 *
 * <p>Implementations must be side-effect free. This runs up to four times a second per player with a
 * panel open, and a dump that changes what it reports is not a dump.
 */
@NullMarked
public interface DebugInspector {

    /** Stable id, shown in the inspector list and used by {@code /wandb debug inspect list}. */
    String id();

    /** One line for the overview. */
    default String summary() {
        return "";
    }

    /** A block in the world. */
    interface OfBlock extends DebugInspector {
        boolean matches(ServerLevel level, BlockPos pos, BlockState state);

        DebugReport inspect(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer viewer);
    }

    /** An entity in the world. */
    interface OfEntity extends DebugInspector {
        boolean matches(Entity entity);

        DebugReport inspect(Entity entity, ServerPlayer viewer);
    }
}
