package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.apparition.command.ApparitionPointCommands;
import at.koopro.wizardsandbeasts.bloodpact.command.PactCommands;
import at.koopro.wizardsandbeasts.spell.command.ProficiencyCommands;
import at.koopro.wizardsandbeasts.spell.command.SpellCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb magic …} — the things a player can <em>do</em>, as opposed to the wand they hold.
 *
 * <p>Spells, proficiency and the Patronus used to hang off {@code /wandb wand}, which tied the
 * casting system to one item. Apparition arrived as a top-level {@code apparate}; the blood pact
 * as a top-level {@code pact}. They are all the same category of thing — magic a character
 * performs — and they read like it now.
 *
 * <p>Ungated at the group, like {@link PlayerCommands}: {@code spell learn}, {@code apparate mark}
 * and {@code animagus transform} are ordinary play, while {@code spell learn_all} and
 * {@code pact break} are not.
 */
@NullMarked
public final class MagicCommands {

    private MagicCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("magic")
                .then(SpellCommands.register())
                .then(ProficiencyCommands.register())
                .then(SpellCommands.registerPatronus())
                .then(AnimagusCommands.register())
                .then(ApparitionPointCommands.register())
                .then(PactCommands.register());
    }
}
