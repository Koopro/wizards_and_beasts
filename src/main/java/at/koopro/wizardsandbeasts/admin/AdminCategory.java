package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The Control Center's sections, in navigation order. Shared by server and client: the server files each
 * setting under one, the client draws one sidebar entry per constant.
 *
 * <p>A section with no settings registered is shown as "coming in the next administration module" by the
 * client, so a section goes live the moment something is filed under it — no screen code changes.
 */
@NullMarked
public enum AdminCategory {
    DASHBOARD(AdminCapability.CONFIG),
    PLAYERS(AdminCapability.PLAYERS),
    GAME_RULES(AdminCapability.CONFIG),
    MAGIC(AdminCapability.CONTENT),
    DARK_ARTS(AdminCapability.CONTENT),
    HERITAGES(AdminCapability.CONTENT),
    CREATURES(AdminCapability.CONTENT),
    BREWING(AdminCapability.CONTENT),
    WANDS(AdminCapability.CONTENT),
    TRAVEL(AdminCapability.CONTENT),
    MINISTRY(AdminCapability.CONFIG),
    ECONOMY(AdminCapability.CONFIG),
    WORLD(AdminCapability.WORLD),
    MODULES(AdminCapability.CONFIG),
    PROFILES(AdminCapability.CONFIG),
    VISUALS(AdminCapability.VISUAL),
    PERFORMANCE(AdminCapability.CONFIG),
    DEBUG(AdminCapability.DEBUG),
    ADVANCED(AdminCapability.CONFIG);

    private static final String KEY_PREFIX = "admin.wizards_and_beasts.section.";

    /** The authority a setting filed here needs unless it names its own. */
    private final AdminCapability defaultCapability;

    AdminCategory(AdminCapability defaultCapability) {
        this.defaultCapability = defaultCapability;
    }

    public AdminCapability defaultCapability() {
        return defaultCapability;
    }

    /** Stable lower-case id: wire format and command argument. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return KEY_PREFIX + id();
    }

    /** One line under the section title saying what the section is for. */
    public String summaryKey() {
        return KEY_PREFIX + id() + ".summary";
    }

    public static @Nullable AdminCategory byId(String id) {
        for (AdminCategory category : values()) {
            if (category.id().equals(id)) {
                return category;
            }
        }
        return null;
    }
}
