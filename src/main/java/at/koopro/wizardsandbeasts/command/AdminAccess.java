package at.koopro.wizardsandbeasts.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Who may administer this mod: its {@code /wandb} command tree and the module admin surface.
 *
 * <p>Operator permission alone is not enough when an allow-list is configured. These commands reshape the
 * mod itself — which features exist, what a player has learned, what a wand is — so a server can restrict
 * them to specific people rather than to everyone holding {@code /gamemode}. When {@code adminUuids} lists
 * any UUID, <b>only</b> those players qualify; an operator who is not on the list is refused exactly like
 * anyone else.
 *
 * <p>Two deliberate escape hatches, both about not being able to lock yourself out permanently:
 * <ul>
 *   <li>a non-player source (server console, command block, RCON) always qualifies — the console is where
 *       you fix a mistake in the list;</li>
 *   <li>an empty list falls back to the operator check, so servers that never configure this keep exactly
 *       the behaviour they had.</li>
 * </ul>
 *
 * <p>Enforced on every command tree <i>and</i> on the module network handler. A screen that does not open
 * is a courtesy; the server-side check is the boundary.
 *
 * <p>Deliberately <b>not</b> applied to in-fiction authority: {@code MinistryPermissions} still grants an
 * Auror the right to make an arrest by rank, because that is roleplay, not administration.
 */
@NullMarked
public final class AdminAccess {

    private AdminAccess() {}

    /** Predicate for {@code .requires(…)} on a command tree. */
    public static Predicate<CommandSourceStack> requirement() {
        return AdminAccess::allows;
    }

    /** Whether {@code source} may administer the mod. */
    public static boolean allows(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return true; // console / command block / RCON — the recovery path
        }
        return allows(player.getUUID(),
                WizardsAndBeastsCommandPermissions.GAMEMASTER.test(source));
    }

    /** Whether {@code player} may administer the mod, given whether they hold operator permission. */
    public static boolean allows(UUID playerId, boolean hasOperatorPermission) {
        return decide(playerId, at.koopro.wizardsandbeasts.Config.adminUuids(), hasOperatorPermission);
    }

    /**
     * The rule itself, taking its inputs explicitly so it can be tested without a server.
     *
     * @param configured raw UUID strings from config; unparseable entries are ignored rather than
     *                   silently widening or narrowing access
     */
    public static boolean decide(UUID playerId, List<? extends String> configured, boolean hasOperatorPermission) {
        boolean sawValidEntry = false;
        for (String raw : configured) {
            UUID parsed = parse(raw);
            if (parsed == null) {
                continue;
            }
            sawValidEntry = true;
            if (parsed.equals(playerId)) {
                return true;
            }
        }
        // A list with entries is a closed allow-list. A list with none (or only junk) means "not configured",
        // which must not brick an existing server, so operator permission still applies.
        return !sawValidEntry && hasOperatorPermission;
    }

    /** True when an allow-list is actually in force, for diagnostics and command feedback. */
    public static boolean isRestricted() {
        for (String raw : at.koopro.wizardsandbeasts.Config.adminUuids()) {
            if (parse(raw) != null) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static UUID parse(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(trimmed.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
