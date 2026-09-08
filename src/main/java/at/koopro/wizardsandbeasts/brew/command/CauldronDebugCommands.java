package at.koopro.wizardsandbeasts.brew.command;

import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.block.brew.WizardingCauldronBlock;
import at.koopro.wizardsandbeasts.brew.CauldronVisual;
import at.koopro.wizardsandbeasts.brew.debug.CauldronDebugInspector;
import at.koopro.wizardsandbeasts.command.debug.inspect.DebugInspectors;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Driving a cauldron from a command, and printing what is in it.
 *
 * <p>The dump itself moved to {@link CauldronDebugInspector}, which is what the floating panel draws
 * and what {@code /wandb debug inspect} prints. It used to live here as a hundred lines of chat
 * building, and having it in one place is the whole reason the panel and the command cannot drift.
 * This file keeps the two things a report cannot do: reach a pot by coordinates, and poke it.
 */
@NullMarked
public final class CauldronDebugCommands {

    private CauldronDebugCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("cauldron")
                .then(Commands.literal("inspect")
                        .executes(ctx -> inspect(ctx.getSource(), null)))
                .then(Commands.literal("at")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> inspect(ctx.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos")))
                                .then(Commands.literal("fill")
                                        .executes(ctx -> drive(ctx.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"), "fill")))
                                .then(Commands.literal("start")
                                        .executes(ctx -> drive(ctx.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"), "start")))
                                ))
                .executes(ctx -> inspect(ctx.getSource(), null));
    }

    /**
     * Perform one step of the ritual from a command instead of a right-click.
     *
     * <p>Exists because the whole flow was unreachable to any automated check: every gesture is a
     * right-click, and a right-click needs a human at a client. A bug that only appears in the
     * interaction path could therefore be neither reproduced nor confirmed fixed except by asking
     * somebody to go and try it — which is exactly how two of them shipped.
     *
     * <p>Drives the <em>same</em> block-entity methods the block does, so a result here is evidence
     * about the real path rather than about a parallel one.
     */
    private static int drive(CommandSourceStack source, BlockPos pos, String action)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!(source.getLevel().getBlockEntity(pos) instanceof CauldronBlockEntity be)) {
            source.sendFailure(Component.literal("No cauldron block entity at " + pos.toShortString())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        BlockState before = source.getLevel().getBlockState(pos);
        String beforeVisual = visualOf(before);

        switch (action) {
            case "fill" -> be.setFilled(true);
            case "start" -> {
                CauldronBlockEntity.StartResult result = be.startBrewing(
                        ((WizardingCauldronBlock) before.getBlock()).tier(), player.getUUID());
                source.sendSuccess(() -> Component.literal("startBrewing -> " + result), false);
            }
            default -> {
                source.sendFailure(Component.literal("unknown action " + action));
                return 0;
            }
        }

        BlockState after = source.getLevel().getBlockState(pos);
        String afterVisual = visualOf(after);
        source.sendSuccess(() -> Component.literal(
                "visual " + beforeVisual + " -> " + afterVisual
                        + "  |  filled=" + be.isFilled()
                        + "  phase=" + be.phase()).withStyle(
                beforeVisual.equals(afterVisual) && !"empty".equals(afterVisual)
                        ? ChatFormatting.YELLOW : ChatFormatting.GREEN), false);
        return 1;
    }

    private static String visualOf(BlockState state) {
        return state.hasProperty(CauldronVisual.PROPERTY)
                ? state.getValue(CauldronVisual.PROPERTY).getSerializedName()
                : "NO PROPERTY";
    }

    /**
     * Prints the pot's dump into chat.
     *
     * <p>With no position it inspects whatever you are looking at, which is now also drawn beside the
     * cauldron while debug mode is on — the panel is the better way to read it, and this is the way
     * to keep a copy in the log.
     */
    private static int inspect(CommandSourceStack source, @Nullable BlockPos explicitPos)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (explicitPos == null) {
            return DebugInspectors.lookedAt(player)
                    .map(result -> {
                        result.report().send(source);
                        return 1;
                    })
                    .orElseGet(() -> {
                        source.sendFailure(Component.literal("Look at a cauldron.")
                                .withStyle(ChatFormatting.RED));
                        return 0;
                    });
        }
        ServerLevel level = source.getLevel();
        BlockState state = level.getBlockState(explicitPos);
        CauldronDebugInspector inspector = new CauldronDebugInspector();
        if (!inspector.matches(level, explicitPos, state)) {
            source.sendFailure(Component.literal(
                    "That is not a cauldron (" + state.getBlock().getName().getString() + ").")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        inspector.inspect(level, explicitPos, state, player).send(source);
        return 1;
    }
}
