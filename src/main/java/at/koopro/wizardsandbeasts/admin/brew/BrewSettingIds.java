package at.koopro.wizardsandbeasts.admin.brew;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The ids brew and recipe values are addressed by, all in this mod's namespace:
 * <pre>
 * brew/&lt;ns&gt;/&lt;brew&gt;/enabled            brew/&lt;ns&gt;/&lt;brew&gt;/effects
 * brew_recipe/&lt;ns&gt;/&lt;recipe&gt;/heat_time   brew_recipe/&lt;ns&gt;/&lt;recipe&gt;/failure_chance
 * brew_recipe/&lt;ns&gt;/&lt;recipe&gt;/cauldron_tier
 * brew_recipe/&lt;ns&gt;/&lt;recipe&gt;/ingredient.&lt;item ns&gt;.&lt;item path&gt;
 * </pre>
 * The property is always the last path segment (up to its first dot), so {@code AdminLangKeys} finds one shared
 * text per property, as for spells, heritages and creatures.
 */
@NullMarked
public final class BrewSettingIds {

    public static final String BREW = "brew/";
    public static final String RECIPE = "brew_recipe/";
    public static final String ENABLED = "enabled";
    public static final String EFFECTS = "effects";
    public static final String HEAT_TIME = "heat_time";
    public static final String FAILURE_CHANCE = "failure_chance";
    public static final String CAULDRON_TIER = "cauldron_tier";
    public static final String INGREDIENT = "ingredient";

    private BrewSettingIds() {}

    public enum Kind { BREW, RECIPE }

    /**
     * @param target   the brew or recipe id, {@code ns:path}
     * @param property the property, e.g. {@code heat_time} or {@code ingredient}
     * @param item     for an ingredient count, the item id; else null
     */
    public record Parsed(Kind kind, String target, String property, @Nullable String item) {}

    public static Identifier brew(String brewId, String property) {
        return of(BREW, brewId, property);
    }

    public static Identifier recipe(String recipeId, String property) {
        return of(RECIPE, recipeId, property);
    }

    public static Identifier ingredient(String recipeId, String itemId) {
        Identifier item = Identifier.parse(itemId);
        return of(RECIPE, recipeId, INGREDIENT + "." + item.getNamespace() + "." + item.getPath());
    }

    private static Identifier of(String prefix, String targetId, String property) {
        Identifier target = Identifier.parse(targetId);
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                prefix + target.getNamespace() + "/" + target.getPath() + "/" + property);
    }

    public static @Nullable Parsed parse(Identifier id) {
        if (!WizardsAndBeastsMod.MODID.equals(id.getNamespace())) {
            return null;
        }
        String path = id.getPath();
        Kind kind;
        String rest;
        if (path.startsWith(RECIPE)) {
            kind = Kind.RECIPE;
            rest = path.substring(RECIPE.length());
        } else if (path.startsWith(BREW)) {
            kind = Kind.BREW;
            rest = path.substring(BREW.length());
        } else {
            return null;
        }
        int firstSlash = rest.indexOf('/');
        int lastSlash = rest.lastIndexOf('/');
        if (firstSlash <= 0 || lastSlash <= firstSlash) {
            return null;
        }
        String target = rest.substring(0, firstSlash) + ":" + rest.substring(firstSlash + 1, lastSlash);
        String last = rest.substring(lastSlash + 1);
        if (last.startsWith(INGREDIENT + ".")) {
            String[] parts = last.substring(INGREDIENT.length() + 1).split("\\.", 2);
            if (parts.length != 2 || kind != Kind.RECIPE) {
                return null;
            }
            return new Parsed(kind, target, INGREDIENT, parts[0] + ":" + parts[1]);
        }
        return new Parsed(kind, target, last, null);
    }

    public static boolean isBrewSetting(Identifier id) {
        return parse(id) != null;
    }
}
