package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.network.DebugOverlayToggleS2CPacket;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Morph debug tools under {@code /WizardsAndBeastsMod debug morph ...}.
 */
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
        DebugOverlayToggleS2CPacket.sendToPlayer(target, enabled);

        String state = enabled ? "\u00A7aON" : "\u00A7cOFF";
        source.sendSuccess(() -> Component.literal(
                "\u00A77Debug overlay for " + target.getName().getString() + ": " + state), false);
        return 1;
    }
}
