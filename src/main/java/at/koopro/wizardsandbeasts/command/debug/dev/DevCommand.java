package at.koopro.wizardsandbeasts.command.debug.dev;

import at.koopro.wizardsandbeasts.command.debug.DebugModule;
import at.koopro.wizardsandbeasts.command.debug.DebugOutput;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb debug dev} — get a feature in front of you and test it.
 *
 * <pre>
 * /wandb debug dev                      what kits exist
 * /wandb debug dev open  &lt;id|all&gt; [player]   open every gate on it
 * /wandb debug dev kit   &lt;id|all&gt; [player]   hand over the items it needs
 * /wandb debug dev reset &lt;id|all&gt; [player]   put it back to a fresh state
 * /wandb debug dev setup [player]            open all, then kit all
 * </pre>
 *
 * <p>Every node takes an optional player, through {@link DebugModule#onSelfOrTarget}, matching the
 * rest of the debug tree — setting up a second account to test something two-sided is half of what
 * these are for.
 *
 * <p>These <b>write</b>. Everything else under {@code /wandb debug} reads, and keeping the two apart
 * under one obvious literal is deliberate: {@code dev} is the word to look for before typing, and
 * everything that changes a player's progression is behind it.
 */
@NullMarked
public final class DevCommand {

    private DevCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dev")
                .executes(ctx -> list(ctx.getSource()));

        for (FeatureDevKits.Verb verb : FeatureDevKits.Verb.values()) {
            LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(verb.literal());
            node.then(DebugModule.onSelfOrTarget("all", (source, target) -> {
                FeatureDevKits.runAll(target, verb).send(source);
                return 1;
            }));
            for (FeatureDevKit kit : FeatureDevKits.all()) {
                node.then(DebugModule.onSelfOrTarget(kit.id(), (source, target) -> {
                    FeatureDevKits.run(kit, target, verb).send(source);
                    return 1;
                }));
            }
            root.then(node);
        }

        root.then(DebugModule.onSelfOrTarget("setup", (source, target) -> {
            FeatureDevKits.setup(target).send(source);
            return 1;
        }));
        return root;
    }

    private static int list(CommandSourceStack source) {
        DebugOutput out = new DebugOutput(source);
        out.header("Dev Kits");
        out.info("open = unlock the gates, kit = hand over the items, reset = back to fresh.");
        for (FeatureDevKit kit : FeatureDevKits.all()) {
            out.kv(kit.id(), kit.summary().isEmpty() ? kit.title() : kit.summary());
        }
        out.info("'all' runs every kit; 'setup' is open all then kit all.");
        out.info("Each takes an optional player: /wandb debug dev kit <id> <player>");
        return 1;
    }
}
