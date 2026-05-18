package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.apparition.command.ApparitionCommands;
import at.koopro.wizardsandbeasts.trunk.command.TrunkCommands;
import at.koopro.wizardsandbeasts.trunk.command.PocketDebugCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class WorldCommands {

    private WorldCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("world")
                .then(ApparitionCommands.registerWard())
                .then(ApparitionCommands.registerTest())
                .then(TrunkCommands.register())
                .then(PocketDebugCommands.register());
    }
}
