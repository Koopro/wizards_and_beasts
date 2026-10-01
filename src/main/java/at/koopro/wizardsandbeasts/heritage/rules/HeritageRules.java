package at.koopro.wizardsandbeasts.heritage.rules;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The live heritage rules every gate consults. Pure — no server, no config — so the gates stay unit-testable.
 *
 * <p><b>Two layers</b>, exactly as {@code SpellTuning}: the <em>local</em> layer is written by the side that owns
 * the state (the server, from world data); the <em>remote</em> layer is written on a client connected to someone
 * else's server, from {@code HeritageRulesSyncS2CPayload}, and wins while it is set, so the selection screen shows
 * the server's choices rather than the shipped defaults. A client on its own integrated server never sets it.
 *
 * <p>Snapshots are immutable maps published through volatile fields.
 */
@NullMarked
public final class HeritageRules {

    private static volatile Map<String, HeritageRule> local = Map.of();
    private static volatile @Nullable Map<String, HeritageRule> remote;

    private HeritageRules() {}

    public static Map<String, HeritageRule> current() {
        Map<String, HeritageRule> synced = remote;
        return synced != null ? synced : local;
    }

    public static Map<String, HeritageRule> local() {
        return local;
    }

    public static HeritageRule rule(Heritage heritage) {
        return current().getOrDefault(heritage.getId(), HeritageRule.NONE);
    }

    // ── writers ──

    public static synchronized void publishLocal(Map<String, HeritageRule> rules) {
        local = Map.copyOf(rules);
    }

    public static synchronized void acceptRemote(Map<String, HeritageRule> rules) {
        remote = Map.copyOf(rules);
    }

    public static synchronized void clearRemote() {
        remote = null;
    }

    // ── readers ──

    /**
     * Whether a new character may choose {@code heritage}: the administrator's word if they gave one, otherwise
     * whether the heritage ships finished ({@link Heritage#isAlphaAvailable()}).
     */
    public static boolean selectable(Heritage heritage) {
        return rule(heritage).selectable().orElse(heritage.isAlphaAvailable());
    }

    /**
     * Whether this heritage's plain two-form change may <em>begin</em>. Only ever consulted for entering the
     * second shape: leaving it is always allowed, so closing the rule can never trap a player in a form.
     */
    public static boolean transformationAllowed(Heritage heritage) {
        return rule(heritage).transformation().orElse(Boolean.TRUE);
    }

    /**
     * Why the gate refuses {@code heritage}, as a lang key, or null when it is selectable. A heritage that simply
     * is not finished says so; one an administrator closed says that instead.
     */
    public static @Nullable String refusalKey(Heritage heritage) {
        if (selectable(heritage)) {
            return null;
        }
        return rule(heritage).selectable().isPresent()
                ? "message.wizards_and_beasts.type_selection.closed"
                : "message.wizards_and_beasts.type_selection.coming_soon";
    }
}
