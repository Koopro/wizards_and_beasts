package at.koopro.wizardsandbeasts.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

import java.util.function.Predicate;

public final class WizardsAndBeastsCommandPermissions {

    /** NeoForge game-master permission node (operators / cheats-enabled contexts). */
    public static final Predicate<CommandSourceStack> GAMEMASTER =
            source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);

    /**
     * Who may use this mod's administrative commands: the configured {@code adminUuids} allow-list when one
     * is set, ordinary operator permission when it is not. This is what {@code /wandb} subtrees gate on —
     * {@link #GAMEMASTER} remains the raw vanilla-style check that {@link AdminAccess} falls back to.
     */
    public static final Predicate<CommandSourceStack> ADMIN = AdminAccess::allows;

    private WizardsAndBeastsCommandPermissions() {}
}
