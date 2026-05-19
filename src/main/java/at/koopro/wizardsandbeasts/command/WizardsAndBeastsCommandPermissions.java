package at.koopro.wizardsandbeasts.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

import java.util.function.Predicate;

public final class WizardsAndBeastsCommandPermissions {

    /** NeoForge game-master permission node (operators / cheats-enabled contexts). */
    public static final Predicate<CommandSourceStack> GAMEMASTER =
            source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);

    private WizardsAndBeastsCommandPermissions() {}
}
