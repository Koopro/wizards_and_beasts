package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.command.AdminAccess;
import at.koopro.wizardsandbeasts.network.floo.OpenFlooGuiS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Who may see which hearth, and the one place that decides it.
 *
 * <h2>Why this exists</h2>
 * <p>Three call sites built the destination list independently — the lit hearth's right-click, the
 * step-into-the-flames offer, and now the typed address — each doing the same
 * visibility filter, the same "not this hearth" exclusion and the same map to
 * {@link FlooDestinationDto}. Three copies of a <em>visibility</em> rule is the kind of duplication
 * that ends with one door showing an address the other two hide.
 *
 * <h2>The rule</h2>
 * <p>A player may reach a hearth if any of four things is true: it is public, they have been there
 * before, they own it, or they administer the server. Everything else is invisible, and invisible
 * means <em>indistinguishable from not existing</em> — see {@link #resolveSpoken}.
 *
 * <p>Deliberately the same set for the list and for a typed address. A player who types the exact
 * name of a private hearth they have never visited is told nothing answers to it, because any other
 * reply — "that line is sealed", "you are not trusted there" — would confirm the address exists and
 * turn the text field into an oracle for enumerating other people's homes. The refusal has to be the
 * same sentence as the one for an address that was never registered at all.
 *
 * <p>Admin access is included because an operator who cannot see a hearth cannot moderate it, and
 * the alternative is reading raw saved data to answer a report. It runs through
 * {@link AdminAccess}, so a server with a configured {@code adminUuids} allow-list restricts this
 * the same way it restricts every other administrative power.
 */
@NullMarked
public final class FlooAccess {

    private FlooAccess() {
    }

    /** Every hearth {@code player} is allowed to know about, in list order. */
    public static List<FlooRegistryEntry> visibleTo(ServerPlayer player, FlooNetworkManager manager) {
        return manager.getAllEntries().stream()
                .filter(e -> mayReach(player, e))
                .sorted(Comparator.comparing(e -> FlooAddress.key(e.networkAddress())))
                .toList();
    }

    /**
     * Whether {@code player} may reach {@code entry} at all.
     *
     * <p>Separate from {@link #visibleTo} because a typed address asks the question about one entry
     * the player may never have been shown, and answering it by scanning the visible list would make
     * the cost of a keystroke proportional to the size of the network.
     */
    public static boolean mayReach(ServerPlayer player, FlooRegistryEntry entry) {
        // Short-circuits before isAdmin on purpose: the admin check reads the config allow-list and
        // the permission set, and the overwhelmingly common answer is "yes, it is public".
        return mayReach(entry.isPublic(),
                entry.isOwnedBy(player.getUUID()),
                hasVisited(player, entry.networkAddress()),
                isAdmin(player));
    }

    /**
     * The visibility rule itself, as arithmetic.
     *
     * <p>Split out from the {@link ServerPlayer} form so the truth table can be tested: the four
     * inputs each need a level, an attachment, a saved-data lookup or a permission set to obtain, and
     * a rule that can only be exercised with all four present is a rule nobody checks.
     *
     * <p>Plain disjunction, and deliberately flat rather than nested. Every clause is an independent
     * reason to be allowed through, and writing it as one expression makes it obvious that no clause
     * can accidentally gate another.
     */
    public static boolean mayReach(boolean isPublic, boolean isOwner, boolean hasVisited, boolean isAdmin) {
        return isPublic || isOwner || hasVisited || isAdmin;
    }

    /** Whether {@code player} has ever arrived at {@code address}. */
    public static boolean hasVisited(ServerPlayer player, String address) {
        return player.getData(ModAttachments.FLOO_VISITED_DESTINATIONS.get())
                .contains(FlooAddress.key(address));
    }

    /**
     * Whether {@code player} may act on any hearth, owned or not.
     *
     * <p>{@code COMMANDS_GAMEMASTER} is the operator half - the same permission the mod's command
     * tree gates on, rather than a bare level number - and {@link AdminAccess} is what turns it into
     * this mod's answer, so a configured allow-list narrows it rather than being bypassed here.
     */
    public static boolean isAdmin(ServerPlayer player) {
        return AdminAccess.allows(player.getUUID(),
                player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    /**
     * Whether {@code player} may rename, unregister, or change the visibility of {@code entry}.
     *
     * <p>An <b>unowned</b> entry is editable by anyone. That is not an oversight: every hearth
     * registered before ownership existed has no owner, and treating those as locked would strand
     * them permanently — unregisterable by the person who built them, on a world where nobody has
     * ever been an owner. Ownerless means "as it always was", and it is a state new registrations
     * cannot enter.
     */
    public static boolean mayAdminister(ServerPlayer player, FlooRegistryEntry entry) {
        return entry.owner().isEmpty()
                || entry.isOwnedBy(player.getUUID())
                || isAdmin(player);
    }

    /**
     * Resolve a <em>spoken</em> address to a hearth, mumbling included.
     *
     * <h2>Why this is not a map lookup</h2>
     * <p>An address that is typed is an address that can be got wrong, and getting it wrong is the
     * point: "Diagonally" is the most famous journey in the fiction and it is a mispronunciation. An
     * exact-match lookup would make every typo a flat refusal, which is both less interesting and
     * less forgiving than the source material.
     *
     * <p>So: exact key first, and failing that the closest visible address within
     * {@link at.koopro.wizardsandbeasts.Config#flooSpeakTypoTolerance} edits. A near miss connects —
     * to <em>something</em>. Whether it connects to what the player meant is
     * {@link FlooTravelHandler}'s business; this only says what the fire heard.
     *
     * <p>The fuzzy half is switchable ({@code flooFuzzyMatch}). Off, only an exact address is heard
     * and everything else is refused with no journey — which is a coherent server to run, just a
     * less forgiving one.
     *
     * <h2>An exact address you may not reach is not an address</h2>
     * <p>The permission check sits on the exact-match branch, and a failure there falls through to
     * the fuzzy search rather than returning early. That ordering is load-bearing: returning early
     * would make "private hearth you have not visited" resolve differently from "no such hearth",
     * and the difference would be observable — type a name, get refused instantly rather than after
     * a near-match, and you have learned the address exists.
     *
     * @return the entry the network understood, or null when nothing was close enough
     */
    @Nullable
    public static FlooRegistryEntry resolveSpoken(ServerPlayer player, FlooNetworkManager manager,
                                                   String typed) {
        String wanted = FlooAddress.key(typed);
        if (wanted.isEmpty()) {
            return null;
        }
        FlooRegistryEntry exact = manager.getEntry(wanted);
        if (exact != null && mayReach(player, exact)) {
            return exact;
        }

        if (!at.koopro.wizardsandbeasts.Config.flooFuzzyMatch) {
            return null;
        }
        int tolerance = Math.max(0, at.koopro.wizardsandbeasts.Config.flooSpeakTypoTolerance);
        if (tolerance == 0) {
            return null;
        }

        FlooRegistryEntry best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (FlooRegistryEntry candidate : visibleTo(player, manager)) {
            int distance = editDistance(wanted, FlooAddress.key(candidate.networkAddress()), tolerance);
            // Strictly better, so a tie between two equally-mangled addresses keeps the first in list
            // order rather than depending on iteration luck. The list is sorted by key, so the choice
            // is at least stable across servers and across restarts.
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return bestDistance <= tolerance ? best : null;
    }

    /** Open the travel or call picker at {@code hearth} for {@code player}. */
    public static void openPicker(ServerPlayer player, FlooNetworkManager manager,
                                  String hearthAddress, boolean headInFire) {
        List<FlooDestinationDto> destinations = visibleTo(player, manager).stream()
                .filter(e -> !FlooAddress.sameAddress(e.networkAddress(), hearthAddress))
                .map(e -> new FlooDestinationDto(e.networkAddress(), e.dimension().toString(), e.isEnabled()))
                .toList();
        OpenFlooGuiS2CPayload.send(player, destinations, hearthAddress, headInFire);
    }

    /**
     * Levenshtein distance, abandoned as soon as it cannot come in under {@code cap}.
     *
     * <p>The cap is not an optimisation, it is what keeps this affordable on a large network: without
     * it every keystroke-length mistake would run a full matrix against every registered address. With
     * it, the row minimum bails out of anything already too far gone, and the length pre-check throws
     * out most candidates before a matrix is allocated at all.
     *
     * <p>Two rows rather than a full matrix, because the traceback is never wanted — only the number.
     */
    static int editDistance(String a, String b, int cap) {
        if (a.equals(b)) {
            return 0;
        }
        if (Math.abs(a.length() - b.length()) > cap) {
            return cap + 1;
        }
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
                rowMin = Math.min(rowMin, current[j]);
            }
            if (rowMin > cap) {
                return cap + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
