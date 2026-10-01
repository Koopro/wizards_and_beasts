package at.koopro.wizardsandbeasts.admin;

import org.jspecify.annotations.NullMarked;

/**
 * The translation keys a setting's id implies. One derivation shared by the server (commands) and the client
 * (the panel), so the two can never look a setting up under different keys. The wire carries ids only.
 */
@NullMarked
public final class AdminLangKeys {

    private static final String SETTING = "admin.wizards_and_beasts.setting.";
    private static final String ROOT = "admin.wizards_and_beasts.";

    private AdminLangKeys() {}

    public static String settingName(String path) {
        return base(path);
    }

    public static String settingDescription(String path) {
        return base(path) + ".desc";
    }

    /** The confirmation text of a dangerous setting. */
    public static String settingWarning(String path) {
        return base(path) + ".warning";
    }

    /**
     * An entity-scoped id {@code <family>/<entity...>/<property>} → the family's shared property key
     * ({@code spell/<ns>/<spell>/cooldown_ticks} → {@code admin.wizards_and_beasts.spell_property.cooldown_ticks},
     * {@code heritage/veela/selectable} → {@code ...heritage_property.selectable}); anything else → the setting's
     * own key. The entity's name is shown beside it by the panel, so "Cooldown" needs no per-spell text.
     */
    private static String base(String path) {
        int first = path.indexOf('/');
        if (first > 0) {
            // The last segment names the property; anything after its first dot qualifies it
            // (brew_recipe/…/ingredient.minecraft.gold_ingot is one "ingredient" among several).
            String last = path.substring(path.lastIndexOf('/') + 1);
            int dot = last.indexOf('.');
            return ROOT + path.substring(0, first) + "_property." + (dot > 0 ? last.substring(0, dot) : last);
        }
        return SETTING + path;
    }

    /** Whether {@code path} names one property of one entity (a spell, a heritage) rather than a global setting. */
    public static boolean entityScoped(String path) {
        return path.indexOf('/') > 0;
    }
}
