package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.character.OpenCharacterSheetPayload;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CharacterCommands {

    private CharacterCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("character")
                .executes(ctx -> openForPlayer(ctx.getSource().getPlayerOrException()));
    }

    private static int openForPlayer(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.CHARACTER_SHEET)) {
            player.displayClientMessage(
                    Component.translatable("commands.wizards_and_beasts.character_sheet.disabled"),
                    true);
            return 0;
        }
        OpenCharacterSheetPayload.sendToPlayer(player);
        return 1;
    }
}
