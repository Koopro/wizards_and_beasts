package at.koopro.wizardsandbeasts.command.debug.inspect;

import at.koopro.wizardsandbeasts.command.debug.DebugOutput;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb debug inspect} — the chat half of the in-world panel.
 *
 * <p>The panel is the better tool while you are standing in front of something, and it is what the
 * dump was moved out of chat <em>for</em>. This stays because two things the panel cannot do still
 * matter: it prints the whole report rather than the first twenty-odd rows, and it can be pointed at
 * a position across the world, which is how you inspect a cauldron somebody has reported from their
 * coordinates without going there.
 */
@NullMarked
public final class DebugInspectCommand {

    private DebugInspectCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("inspect")
                .executes(ctx -> inspectLookedAt(ctx.getSource()))
                .then(Commands.literal("list")
                        .executes(ctx -> listInspectors(ctx.getSource())))
                .then(Commands.literal("at")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> inspectAt(ctx.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos")))));
    }

    private static int inspectLookedAt(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return DebugInspectors.lookedAt(player)
                .map(result -> {
                    result.report().send(source);
                    return 1;
                })
                .orElseGet(() -> {
                    new DebugOutput(source).warn("Nothing under your crosshair within "
                            + (int) DebugInspectors.REACH + " blocks.");
                    return 0;
                });
    }

    /**
     * A named position rather than a look.
     *
     * <p>Blocks only, deliberately: an entity has no stable address to name, and
     * {@code getLoadedBlockPos} refusing an unloaded chunk is the right answer rather than a report
     * about a block entity that is not currently in memory.
     */
    private static int inspectAt(CommandSourceStack source, BlockPos pos) {
        DebugInspectors.bootstrap();
        ServerLevel level = source.getLevel();
        BlockState state = level.getBlockState(pos);
        ServerPlayer viewer = source.getPlayer();
        if (viewer == null) {
            new DebugOutput(source).warn("Run this as a player: inspectors report against a viewer.");
            return 0;
        }
        for (DebugInspector.OfBlock inspector : DebugInspectors.blockInspectors()) {
            if (inspector.matches(level, pos, state)) {
                DebugReport report = inspector.inspect(level, pos, state, viewer);
                report.send(source);
                return 1;
            }
        }
        new DebugOutput(source).warn("No inspector claimed " + pos.toShortString() + ".");
        return 0;
    }

    private static int listInspectors(CommandSourceStack source) {
        DebugInspectors.bootstrap();
        DebugOutput out = new DebugOutput(source);
        out.header("Inspectors");
        for (DebugInspector inspector : DebugInspectors.all()) {
            out.kv(inspector.id(), inspector.summary().isEmpty() ? "—" : inspector.summary());
        }
        out.info("First match wins; 'block' and 'entity' are the catch-alls and are tried last.");
        return 1;
    }
}
