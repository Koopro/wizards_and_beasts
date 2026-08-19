package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.bestiary.command.BestiaryCommands;
import at.koopro.wizardsandbeasts.creature.command.CreatureCommands;
import at.koopro.wizardsandbeasts.entity.niffler.command.NifflerCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb beast …} — beasts as knowledge ({@code bestiary}) and beasts as entities
 * ({@code creature}, {@code niffler}).
 *
 * <p>{@code niffler} used to be a child of {@code bestiary}, which put a spawner inside a
 * discovery-tier tree it has nothing to do with. It is a sibling now.
 */
@NullMarked
public final class BeastCommands {

    private BeastCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("beast")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(BestiaryCommands.register())
                .then(CreatureCommands.register())
                .then(NifflerCommands.register());
    }
}
