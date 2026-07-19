package at.koopro.wizardsandbeasts.ministry.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.data.MinistryRank;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.function.Predicate;

/**
 * Authority for Ministry commands: <b>in-world rank or operator</b>.
 *
 * <p>Rank is the point — an Auror should be able to make an arrest without being given server operator, and
 * the console must still be able to do everything. So each check passes if the caller holds at least the
 * required {@link MinistryRank}, or is a game-master (which also covers command blocks and the console,
 * neither of which has a rank).
 */
@NullMarked
public final class MinistryPermissions {

    private MinistryPermissions() {}

    /** A predicate for {@code .requires(…)} demanding {@code rank} or operator. */
    public static Predicate<CommandSourceStack> atLeast(MinistryRank rank) {
        return source -> holds(source, rank);
    }

    public static boolean holds(CommandSourceStack source, MinistryRank required) {
        // Deliberately still plain operator permission, not the admin allow-list: in-world rank is roleplay
        // authority, and a server's own staff should be able to act as the Ministry without being a mod admin.
        if (WizardsAndBeastsCommandPermissions.GAMEMASTER.test(source)) {
            return true;
        }
        ServerPlayer player = source.getPlayer();
        return player != null && MinistryRecords.get(player).rank().atLeast(required);
    }

    /**
     * Debug/administrative surface — never reachable by rank. Gated on mod administration rather than plain
     * operator permission: driving someone's notoriety directly is authoring, not policing.
     */
    public static Predicate<CommandSourceStack> operatorOnly() {
        return WizardsAndBeastsCommandPermissions.ADMIN;
    }
}
