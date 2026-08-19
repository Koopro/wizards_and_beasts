package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.module.command.ModuleCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb admin …} — server-owner configuration: which of the mod's systems exist at all.
 *
 * <p>One child today. It stays a group rather than collapsing {@code module} back to the top level
 * because the top level is a list of <em>categories</em>, and "turn features on and off for the
 * whole server" is a different kind of authority from every other branch — including {@code debug},
 * which inspects state rather than reconfiguring the install.
 */
@NullMarked
public final class AdminCommands {

    private AdminCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("admin")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(ModuleCommands.register());
    }
}
