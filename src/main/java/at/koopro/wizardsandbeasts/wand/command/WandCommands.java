package at.koopro.wizardsandbeasts.wand.command;

import at.koopro.wizardsandbeasts.spell.command.ProficiencyCommands;
import at.koopro.wizardsandbeasts.spell.command.SpellCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class WandCommands {

    private WandCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("wand")
                .then(SpellCommands.registerSpellCommand())
                .then(ProficiencyCommands.register())
                .then(SpellCommands.registerGiveCommand())
                .then(WandConfigCommands.register());
    }
}
