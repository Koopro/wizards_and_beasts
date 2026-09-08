package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.command.debug.DebugModule;
import at.koopro.wizardsandbeasts.corruption.DarkCorruptionService;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.petrify.PetrifyServerLogic;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.function.BiConsumer;

/**
 * {@code /wandb player condition} — the eight per-player conditions that had no way in.
 *
 * <h2>Why these needed a command at all</h2>
 *
 * <p>Dark corruption, the Dark Mark, mental stability, resolve, happiness, love protection and
 * Cruciatus exposure are all attachments that gameplay writes and <em>nothing</em> could set. They
 * are also all thresholds: corruption gates the Dark Arts, stability has a Longbottom floor, resolve
 * decides whether Imperio holds. Testing any of those meant reaching the threshold the intended way
 * — being tortured for several real minutes, in one case — which is not a test anybody runs twice.
 *
 * <p>Corruption is the one with a service in front of it, and it goes through that service rather
 * than around it, so the display sync fires. The rest are written directly because there is nothing
 * else to call.
 *
 * <p>{@code clear} uses {@code removeData} rather than writing a zero. The attachment's own default
 * is the correct "unset" value — 100 for stability, 50 for resolve, and whatever happiness ships
 * with — and hard-coding those here would make this file the second place they are declared, which
 * is the place that goes stale.
 */
@NullMarked
public final class PlayerConditionCommands {

    private PlayerConditionCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        floatField(set, "corruption", 0f, DarkCorruptionService.MAX,
                (player, value) -> {
                    player.setData(ModAttachments.DARK_CORRUPTION.get(), value);
                    DarkCorruptionService.syncDisplay(player);
                });
        floatField(set, "stability", 0f, 100f,
                (player, value) -> player.setData(ModAttachments.MENTAL_STABILITY.get(), value));
        floatField(set, "resolve", 0f, 100f,
                (player, value) -> player.setData(ModAttachments.RESOLVE.get(), value));
        floatField(set, "happiness", 0f, 100f,
                (player, value) -> player.setData(ModAttachments.HAPPINESS.get(), value));
        boolField(set, "dark_mark",
                (player, value) -> player.setData(ModAttachments.DARK_MARK.get(), value));
        boolField(set, "love_protection",
                (player, value) -> player.setData(ModAttachments.LOVE_PROTECTION.get(), value));
        intField(set, "crucio_exposure",
                (player, value) -> player.setData(ModAttachments.CRUCIO_EXPOSURE_TICKS.get(), value));
        // Petrification is a state with a beginning and an end rather than a number, and both ends
        // have real logic hanging off them -- the render layer, the cure. Driven, not written.
        boolField(set, "petrified", (player, value) -> {
            if (value) {
                PetrifyServerLogic.beginPetrify(player.level(), player);
            } else {
                PetrifyServerLogic.cure(player.level(), player);
            }
        });

        return Commands.literal("condition")
                .executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))))
                .then(set)
                .then(DebugModule.onSelfOrTarget("clear", PlayerConditionCommands::clear));
    }

    private static void floatField(LiteralArgumentBuilder<CommandSourceStack> set, String name,
                                   float min, float max, BiConsumer<ServerPlayer, Float> writer) {
        set.then(Commands.literal(name)
                .then(Commands.argument("value", FloatArgumentType.floatArg(min, max))
                        .executes(ctx -> apply(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                name, FloatArgumentType.getFloat(ctx, "value"), writer))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> apply(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "target"), name,
                                        FloatArgumentType.getFloat(ctx, "value"), writer)))));
    }

    private static void boolField(LiteralArgumentBuilder<CommandSourceStack> set, String name,
                                  BiConsumer<ServerPlayer, Boolean> writer) {
        set.then(Commands.literal(name)
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> apply(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                name, BoolArgumentType.getBool(ctx, "value"), writer))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> apply(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "target"), name,
                                        BoolArgumentType.getBool(ctx, "value"), writer)))));
    }

    private static void intField(LiteralArgumentBuilder<CommandSourceStack> set, String name,
                                 BiConsumer<ServerPlayer, Integer> writer) {
        set.then(Commands.literal(name)
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                        .executes(ctx -> apply(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                name, IntegerArgumentType.getInteger(ctx, "value"), writer))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> apply(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "target"), name,
                                        IntegerArgumentType.getInteger(ctx, "value"), writer)))));
    }

    private static <T> int apply(CommandSourceStack source, ServerPlayer target, String field,
                                 T value, BiConsumer<ServerPlayer, T> writer) {
        writer.accept(target, value);
        source.sendSuccess(() -> Component.literal(field + " = " + value + " for "
                + target.getName().getString()).withStyle(ChatPalette.color(ChatPalette.OK)), true);
        return 1;
    }

    private static int clear(CommandSourceStack source, ServerPlayer target) {
        target.removeData(ModAttachments.DARK_CORRUPTION.get());
        target.removeData(ModAttachments.MENTAL_STABILITY.get());
        target.removeData(ModAttachments.RESOLVE.get());
        target.removeData(ModAttachments.HAPPINESS.get());
        target.removeData(ModAttachments.DARK_MARK.get());
        target.removeData(ModAttachments.LOVE_PROTECTION.get());
        target.removeData(ModAttachments.CRUCIO_EXPOSURE_TICKS.get());
        PetrifyServerLogic.cure(target.level(), target);
        DarkCorruptionService.syncDisplay(target);
        source.sendSuccess(() -> Component.literal("Conditions reset to defaults for "
                        + target.getName().getString())
                .withStyle(ChatPalette.color(ChatPalette.OK)), true);
        return 1;
    }

    private static int show(CommandSourceStack source, ServerPlayer target)
            throws CommandSyntaxException {
        DebugReport report = DebugReport.of("Conditions · " + target.getName().getString());
        report.bar("corruption", DarkCorruptionService.get(target) / DarkCorruptionService.MAX);
        report.row("corruption", String.format("%.1f / %.0f",
                DarkCorruptionService.get(target), DarkCorruptionService.MAX));
        report.bar("stability", target.getData(ModAttachments.MENTAL_STABILITY.get()) / 100f);
        report.bar("resolve", target.getData(ModAttachments.RESOLVE.get()) / 100f);
        report.bar("happiness", target.getData(ModAttachments.HAPPINESS.get()) / 100f);
        report.flag("dark mark", target.getData(ModAttachments.DARK_MARK.get()));
        report.flag("love protection", target.getData(ModAttachments.LOVE_PROTECTION.get()));
        report.row("crucio exposure", target.getData(ModAttachments.CRUCIO_EXPOSURE_TICKS.get()) + "t");
        report.send(source);
        return 1;
    }
}
