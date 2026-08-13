package at.koopro.wizardsandbeasts.pose.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import at.koopro.wizardsandbeasts.pose.PoseOverride;
import at.koopro.wizardsandbeasts.pose.PoseOverrideService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
                .then(flight)
                .then(castTree());
    }

    /**
     * {@code /wandb pose cast <off|play|windup|release|recovery> [ticks|player]}
     *
     * <p>The cast equivalent of the flight harness, and it drives the <em>production</em> path: it
     * sends the same {@code SpellCastAnimationS2CPayload} a real cast sends, so what is inspected is
     * the shipping wiring rather than a parallel debug route that can drift from it.
     *
     * <p>This exists because no spell declares {@code castTiming} yet, so nothing broadcasts one
     * during play. Without it the whole cast path is unobservable.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> castTree() {
        LiteralArgumentBuilder<CommandSourceStack> cast = Commands.literal("cast");

        cast.then(Commands.literal("off")
                .executes(ctx -> clearCast(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> clearCast(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player")))));

        cast.then(Commands.literal("play")
                .executes(ctx -> playCast(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                        DEFAULT_TEST_TICKS))
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 200))
                        .executes(ctx -> playCast(ctx.getSource(),
                                ctx.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(ctx, "ticks")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> playCast(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        IntegerArgumentType.getInteger(ctx, "ticks"))))));

        // Held phases park the pose so it can be looked at, which is the whole point of a harness:
        // a thirty-tick cast is over before anyone can judge a shoulder angle.
        for (int phase = 0; phase < PHASE_NAMES.length; phase++) {
            int held = phase;
            cast.then(Commands.literal(PHASE_NAMES[phase])
                    .executes(ctx -> holdCast(ctx.getSource(),
                            ctx.getSource().getPlayerOrException(), held))
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> holdCast(ctx.getSource(),
                                    EntityArgument.getPlayer(ctx, "player"), held))));
        }

        return cast;
    }

    /** Index order matches {@code SpellCastAnimationS2CPayload.holdPhase}. */
    private static final String[] PHASE_NAMES = {"windup", "release", "recovery"};

    /**
     * Defaults for a debug cast, duplicated here rather than imported from the client.
     *
     * <p>This class runs on the server, including a dedicated one. Reaching into
     * {@code client.pose} for a constant would drag {@code ClientCastAnimationState} — and through it
     * {@code Minecraft} — onto a classpath that has no client, and the crash would be at command
     * registration rather than anywhere near the cause.
     */
    private static final int DEFAULT_TEST_TICKS = 30;
    private static final float DEBUG_WINDUP_END = 0.35f;
    private static final float DEBUG_RELEASE_END = 0.6f;

    /** A cast id that is obviously not a spell, so a stray held pose is traceable to the command. */
    private static final String DEBUG_CAST_ID = "wizards_and_beasts:debug_cast";

    private static int playCast(CommandSourceStack source, ServerPlayer target, int ticks) {
        if (reportDisabled(source)) {
            return 0;
        }
        SpellCastAnimationS2CPayload.send(target, new SpellCastAnimationS2CPayload(
                target.getId(), DEBUG_CAST_ID, ticks, DEBUG_WINDUP_END, DEBUG_RELEASE_END,
                SpellCastAnimationS2CPayload.NO_HOLD));
        source.sendSuccess(() -> Component.translatable(
                "commands.wizards_and_beasts.pose.cast.play", target.getName(), ticks), true);
        return 1;
    }

    private static int holdCast(CommandSourceStack source, ServerPlayer target, int phase) {
        if (reportDisabled(source)) {
            return 0;
        }
        // A long duration is irrelevant for a held cast — it never advances and never expires — but a
        // positive one is required, since a zero-tick payload is the wire's "clear".
        SpellCastAnimationS2CPayload.send(target, new SpellCastAnimationS2CPayload(
                target.getId(), DEBUG_CAST_ID, 1, DEBUG_WINDUP_END, DEBUG_RELEASE_END, phase));
        source.sendSuccess(() -> Component.translatable(
                "commands.wizards_and_beasts.pose.cast.hold", target.getName(),
                PHASE_NAMES[phase]), true);
        return 1;
    }

    private static int clearCast(CommandSourceStack source, ServerPlayer target) {
        if (reportDisabled(source)) {
            return 0;
        }
        // Zero ticks is the clear signal, so "off" needs no second payload type.
        SpellCastAnimationS2CPayload.send(target, new SpellCastAnimationS2CPayload(
                target.getId(), DEBUG_CAST_ID, 0, 0f, 0f,
                SpellCastAnimationS2CPayload.NO_HOLD));
        source.sendSuccess(() -> Component.translatable(
                "commands.wizards_and_beasts.pose.cast.cleared", target.getName()), true);
        return 1;
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
