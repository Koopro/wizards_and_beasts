package at.koopro.wizardsandbeasts.standing.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.standing.StandingBand;
import at.koopro.wizardsandbeasts.standing.StandingService;
import at.koopro.wizardsandbeasts.standing.deed.DeedRegistry;
import at.koopro.wizardsandbeasts.standing.deed.DeedTrigger;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * {@code /wandb player standing …}
 *
 * <p>Reading your own standing needs nothing — it is the same information the character sheet shows,
 * and a player without the sheet module should still be able to ask. Writing it is administration:
 * driving someone's alignment directly is authoring, so it sits behind the mod-admin gate rather than
 * plain operator, matching how {@code /wandb ministry notoriety set} is gated.
 */
@NullMarked
public final class StandingCommands {

    private StandingCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("standing")
                .executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))

                .then(Commands.literal("set")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("axis", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            // Only the stored axis can be set. The derived two have their
                                            // own commands already, and offering them here would suggest
                                            // a write that is deliberately refused.
                                            for (StandingAxis axis : StandingAxis.values()) {
                                                if (axis.isStored()) {
                                                    b.suggest(axis.getSerializedName());
                                                }
                                            }
                                            return b.buildFuture();
                                        })
                                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                                .executes(ctx -> set(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "axis"),
                                                        FloatArgumentType.getFloat(ctx, "value")))))))

                .then(Commands.literal("reset")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))

                .then(Commands.literal("deeds")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .executes(ctx -> listDeeds(ctx.getSource())));
    }

    private static int show(CommandSourceStack source, ServerPlayer target) {
        source.sendSuccess(() -> Component.literal("Standing — ").withStyle(ChatFormatting.GOLD)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE)), false);

        for (StandingAxis axis : StandingAxis.values()) {
            float value = StandingService.valueOf(target, axis);
            StandingBand band = StandingService.bandOf(target, axis);
            source.sendSuccess(() -> Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(axis.displayName().copy().withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(": "))
                    .append(axis.bandName(band).copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.format(Locale.ROOT, "  (%+.1f / %.0f)",
                                    value, StandingService.bound()))
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(axis.isStored() ? "" : "  derived")
                            .withStyle(ChatFormatting.DARK_AQUA)), false);
        }
        return 1;
    }

    private static int set(CommandSourceStack source, ServerPlayer target, String rawAxis, float value) {
        StandingAxis axis = StandingAxis.byName(rawAxis.toLowerCase(Locale.ROOT));
        if (axis == null) {
            source.sendFailure(Component.literal("Unknown axis: " + rawAxis));
            return 0;
        }
        if (!StandingService.set(target, axis, value)) {
            // Named rather than generic: the two derived axes each have a real front door, and the
            // person typing this needs to be sent to it rather than told "no".
            source.sendFailure(Component.literal(axis.getSerializedName()
                    + " is derived and cannot be set directly — use "
                    + (axis == StandingAxis.MINISTRY
                            ? "/wandb ministry notoriety"
                            : "the dark corruption commands")
                    + " instead."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Set ").withStyle(ChatFormatting.GREEN)
                .append(axis.displayName().copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" for "))
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.format(Locale.ROOT, " to %+.1f",
                        StandingService.valueOf(target, axis)))), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer target) {
        StandingService.reset(target);
        source.sendSuccess(() -> Component.literal("Reset stored standing for ")
                .withStyle(ChatFormatting.GREEN)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" — derived axes are unchanged.")
                        .withStyle(ChatFormatting.DARK_GRAY)), true);
        return 1;
    }

    /** What the datapack actually loaded. The cheapest way to tell "no deeds" from "deeds not firing". */
    private static int listDeeds(CommandSourceStack source) {
        if (DeedRegistry.count() == 0) {
            source.sendSuccess(() -> Component.literal("No magical deeds are loaded.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(DeedRegistry.count() + " magical deeds loaded:")
                .withStyle(ChatFormatting.GOLD), false);
        for (DeedTrigger trigger : DeedTrigger.values()) {
            int forTrigger = DeedRegistry.forTrigger(trigger).size();
            if (forTrigger > 0) {
                source.sendSuccess(() -> Component.literal("  " + trigger.getSerializedName() + ": "
                        + forTrigger).withStyle(ChatFormatting.GRAY), false);
            }
        }
        return DeedRegistry.count();
    }
}
