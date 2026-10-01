package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.admin.command.AdminConfigCommands;
import at.koopro.wizardsandbeasts.module.command.ModuleCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb admin …} — server-owner configuration: which of the mod's systems exist at all, and how
 * they are tuned.
 *
 * <p>It stays a group rather than collapsing its children to the top level because the top level is a
 * list of <em>categories</em>, and "reconfigure the whole server" is a different kind of authority from
 * every other branch — including {@code debug}, which inspects state rather than reconfiguring the install.
 *
 * <ul>
 *   <li>{@code module} — module states ({@code ModuleStateService});</li>
 *   <li>{@code panel} — opens the Control Center;</li>
 *   <li>{@code spell} — the Magic section: per-spell values, resets and test casts;</li>
 *   <li>{@code config} — the Control Center's settings from chat or the console
 *       ({@code AdminSettingService}, the same mutation API the panel's packets use).</li>
 * </ul>
 *
 * <p>{@code panel} is a child rather than making {@code admin} itself executable: {@code CommandTreeShapeTest}
 * holds every top-level node to being a category, not a verb.
 */
@NullMarked
public final class AdminCommands {

    private AdminCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("admin")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(ModuleCommands.register())
                .then(AdminConfigCommands.panel())
                .then(AdminConfigCommands.config())
                .then(at.koopro.wizardsandbeasts.admin.command.AdminSpellCommands.register())
                .then(at.koopro.wizardsandbeasts.admin.command.AdminHeritageCommands.register())
                .then(at.koopro.wizardsandbeasts.admin.command.AdminCreatureCommands.register())
                .then(at.koopro.wizardsandbeasts.admin.command.AdminBrewCommands.register())
                .then(at.koopro.wizardsandbeasts.admin.command.AdminWandCommands.register())
                .then(at.koopro.wizardsandbeasts.admin.command.AdminBroomCommands.register())
                .then(at.koopro.wizardsandbeasts.admin.command.AdminProfileCommands.register())
                .then(at.koopro.wizardsandbeasts.admin.command.AdminPlayerCommands.register());
    }
}
