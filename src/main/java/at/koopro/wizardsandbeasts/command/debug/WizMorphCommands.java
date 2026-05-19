package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.network.form.DebugOverlayToggleS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class WizMorphCommands {

    private WizMorphCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("morph")
                .then(Commands.literal("debug")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("on")
                                        .executes(ctx -> toggleDebug(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> toggleDebug(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                false)))));
    }

    private static int toggleDebug(CommandSourceStack source, ServerPlayer target, boolean enabled) {
        PlayerHeritageData data = target.getData(ModAttachments.HERITAGE_DATA.get());
        data.setDebugOverlay(enabled);
        DebugOverlayToggleS2CPayload.sendToPlayer(target, enabled);

        source.sendSuccess(() -> Component.literal("Debug overlay for " + target.getName().getString() + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(enabled ? "ON" : "OFF")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        return 1;
    }
}
