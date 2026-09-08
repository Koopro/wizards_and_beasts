package at.koopro.wizardsandbeasts.command.debug.feature;

import at.koopro.wizardsandbeasts.command.debug.DebugModule;
import at.koopro.wizardsandbeasts.command.debug.DebugOutput;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb debug feature <id> [player]} — one subsystem's view of a player.
 *
 * <p>Built from {@link FeatureDebugSections} rather than written out node by node, so a subsystem
 * that registers a section gets its command for free. The alternative had been a debug node per
 * feature written by whoever last needed one, which is how the mod ended up with debug output for
 * seven subsystems out of thirty.
 *
 * <p>Every node takes an optional player, through {@link DebugModule#onSelfOrTarget}, because "what
 * does the server think is going on with <em>them</em>" is the question that actually gets asked.
 */
@NullMarked
public final class FeatureDebugCommand {

    private FeatureDebugCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("feature")
                .executes(ctx -> list(ctx.getSource()));

        root.then(DebugModule.onSelfOrTarget("all", (source, target) -> {
            FeatureDebugSections.reportAll(target, FeatureDebugSection.Detail.FULL).send(source);
            return 1;
        }));
        // A deliberate second spelling of the panel's own view: the same brief dump the box over a
        // player's head shows, printed where it can be copied out of the log.
        root.then(DebugModule.onSelfOrTarget("brief", (source, target) -> {
            FeatureDebugSections.reportAll(target, FeatureDebugSection.Detail.BRIEF).send(source);
            return 1;
        }));

        for (FeatureDebugSection section : FeatureDebugSections.all()) {
            root.then(DebugModule.onSelfOrTarget(section.id(), (source, target) ->
                    report(source, target, section)));
        }
        return root;
    }

    private static int report(CommandSourceStack source, ServerPlayer target,
                              FeatureDebugSection section) {
        FeatureDebugSections.report(section, target, FeatureDebugSection.Detail.FULL).send(source);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        DebugOutput out = new DebugOutput(source);
        out.header("Feature Sections");
        out.kv("all", "Every section at once, in full.");
        out.kv("brief", "Every section, headline rows only — what the in-world panel shows.");
        for (FeatureDebugSection section : FeatureDebugSections.all()) {
            out.kv(section.id(), section.summary().isEmpty() ? section.title() : section.summary());
        }
        out.info("Each takes an optional player: /wandb debug feature <id> <player>");
        return 1;
    }
}
