package at.koopro.wizardsandbeasts.pose.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import at.koopro.wizardsandbeasts.pose.PoseOverride;
import at.koopro.wizardsandbeasts.pose.PoseOverrideService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb pose flight <off|auto|hover|glide|propelled> [player]}
 *
 * <p>The wave 1 test harness. It writes the same {@code PoseOverride} attachment the broom system
 * will later write, so this is not scaffolding to be thrown away — it is the first caller of a
 * permanent field.
 *
 * <p>The optional target exists so one player can pose another and watch the result in third person
 * without alt-tabbing between two clients, which is the only way to verify the sync path by eye.
 */
@NullMarked
public final class PoseCommands {

    private PoseCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        LiteralArgumentBuilder<CommandSourceStack> flight = Commands.literal("flight");

        flight.then(Commands.literal("off")
                .executes(ctx -> apply(ctx.getSource(), ctx.getSource().getPlayerOrException(), null))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> apply(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player"), null))));

        flight.then(Commands.literal("auto")
                .executes(ctx -> auto(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> auto(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player")))));

        for (FlightPoseState state : FlightPoseState.values()) {
            flight.then(Commands.literal(state.getSerializedName())
                    .executes(ctx -> apply(ctx.getSource(),
                            ctx.getSource().getPlayerOrException(), state))
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> apply(ctx.getSource(),
                                    EntityArgument.getPlayer(ctx, "player"), state))));
        }

        return Commands.literal("pose")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(flight);
    }

    /**
     * Forces a state, or clears the override when {@code state} is null.
     *
     * <p>A grounded player is accepted and stored, per schema §8 — no error and no warning. The
     * feedback says so explicitly rather than staying silent, because "nothing happened" and "it
     * worked but you are standing on the ground" look identical otherwise.
     */
    private static int apply(CommandSourceStack source, ServerPlayer target,
                             FlightPoseState state) {
        if (reportDisabled(source)) {
            return 0;
        }
        if (state == null) {
            PoseOverrideService.clear(target);
            source.sendSuccess(() -> Component.translatable(
                    "commands.wizards_and_beasts.pose.flight.cleared", target.getName()), true);
            return 1;
        }

        PoseOverrideService.force(target, state);
        boolean flying = target.getAbilities().flying;
        source.sendSuccess(() -> Component.translatable(
                flying ? "commands.wizards_and_beasts.pose.flight.set"
                       : "commands.wizards_and_beasts.pose.flight.set_grounded",
                target.getName(), state.getSerializedName()), true);
        return 1;
    }

    /** Hands the player back to the derived state machine by clearing the manual flag. */
    private static int auto(CommandSourceStack source, ServerPlayer target) {
        if (reportDisabled(source)) {
            return 0;
        }
        // Cleared rather than set to a state: auto means "let the deriver decide", and seeding it
        // with a guess would show one wrong frame before the first derivation replaced it.
        PoseOverrideService.set(target, PoseOverride.NONE);
        source.sendSuccess(() -> Component.translatable(
                "commands.wizards_and_beasts.pose.flight.auto", target.getName()), true);
        return 1;
    }

    /**
     * @return true when the module is off, having already told the source
     *
     * <p>Reports rather than failing: a disabled module is a configuration state, not a malformed
     * command, and a red "Unknown command" would send an operator looking for a typo.
     */
    private static boolean reportDisabled(CommandSourceStack source) {
        if (ModuleManager.isEnabled(Module.PLAYER_ANIMATION)) {
            return false;
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.wizards_and_beasts.pose.module_disabled"), false);
        return true;
    }
}
