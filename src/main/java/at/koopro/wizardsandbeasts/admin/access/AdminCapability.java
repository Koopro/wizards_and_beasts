package at.koopro.wizardsandbeasts.admin.access;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One area of administrative authority.
 *
 * <p>Phase 1 grants all of them or none of them — {@link AdminPolicy} is the one place that decides, and
 * today it asks {@link at.koopro.wizardsandbeasts.command.AdminAccess} a yes/no question. The split exists
 * now so that every setting and every panel already <em>names</em> the authority it needs; when a role
 * system arrives it replaces {@link AdminPolicy#capabilitiesOf} and nothing downstream changes.
 *
 * <p>{@link #node()} is the permission-node spelling a future PermissionAPI integration would register.
 */
@NullMarked
public enum AdminCapability {
    /** Server-wide rules and tuning: the default for most settings. */
    CONFIG("admin.config"),
    /** What exists in the game: spells, creatures, brews, heritages. */
    CONTENT("admin.content"),
    /** Look and feel: beams, overlays, previews. */
    VISUAL("admin.visual"),
    /** Diagnostic switches and logging. */
    DEBUG("admin.debug"),
    /** Acting on other players' characters. */
    PLAYERS("admin.players"),
    /** Acting on the world: structures, wards, the Floo network. Also: seeing where a player is. */
    WORLD("admin.world"),
    /**
     * Creating or destroying a player's money. Kept apart from {@link #PLAYERS} on purpose: inspecting a vault is a
     * players matter, changing one is the most abusable thing an administrator can do.
     */
    MONEY("admin.money");

    private final String node;

    AdminCapability(String node) {
        this.node = node;
    }

    public String node() {
        return node;
    }

    /** Wire/command lookup by {@link #name()}; null for anything this build does not know. */
    public static @Nullable AdminCapability byName(String name) {
        for (AdminCapability capability : values()) {
            if (capability.name().equals(name)) {
                return capability;
            }
        }
        return null;
    }
}
