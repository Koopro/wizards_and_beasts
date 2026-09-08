package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.heritage.command.AppearanceCommands;
import at.koopro.wizardsandbeasts.heritage.command.HeritageCommands;
import at.koopro.wizardsandbeasts.heritage.command.ProfessionCommands;
import at.koopro.wizardsandbeasts.owl.command.OWLCommands;
import at.koopro.wizardsandbeasts.skill.command.SkillCommands;
import at.koopro.wizardsandbeasts.standing.command.StandingCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb player …} — everything that describes one character sheet's worth of a player:
 * what they are, what they have trained, what they look like.
 *
 * <p>Deliberately <b>not</b> gated as a whole. Half of these are reads a player runs on themselves
 * ({@code heritage info}, {@code skill list}, {@code profession points}) and half are admin writes
 * on someone else. The gate therefore stays on the individual verbs, which is where the two are
 * actually distinguishable — see {@link AdminCommands} for the opposite case.
 */
@NullMarked
public final class PlayerCommands {

    private PlayerCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("player")
                .then(CharacterCommands.register())
                .then(HeritageCommands.register())
                .then(ProfessionCommands.register())
                .then(OWLCommands.register())
                .then(at.koopro.wizardsandbeasts.owl.post.OwlPostCommands.register())
                .then(AppearanceCommands.register())
                .then(StatsCommands.register())
                .then(SkillCommands.register())
                .then(SkillCommands.registerVocation())
                .then(StandingCommands.register())
                .then(AbilityFrameworkCommands.register())
                .then(PlayerVaultCommands.register())
                .then(PlayerConditionCommands.register());
    }
}
