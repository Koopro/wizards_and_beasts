package at.koopro.wizardsandbeasts.brew.tuning;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffect;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffectEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The effective brew and recipe registries: what the datapacks define, with the administrator's overrides on top.
 *
 * <p><b>An overlay, not a second path.</b> The reload listeners still load and register the authored brews and
 * recipes; they then hand them here ({@link #acceptAuthoredBrews}, {@link #acceptAuthoredRecipes}), and this class
 * re-registers each one into {@link Brews} / {@link BrewingRecipes} with its override applied. Every consumer — the
 * cauldron's recipe match and heat time, the failure roll, drinking, silver refining, the debug inspector, and the
 * client sync that already sends the live registries — reads those two registries, so all of them see the tuned
 * values and none of them had to change. With no overrides the registries hold exactly what the datapacks define.
 *
 * <p>Server state (the integrated client's own reads go through {@code ClientBrewData}, fed by the sync).
 */
@NullMarked
public final class BrewTuning {

    private static Map<String, Brew> authoredBrews = Map.of();
    private static Map<String, BrewingRecipe> authoredRecipes = Map.of();
    private static Map<String, BrewOverride> brewOverrides = Map.of();
    private static Map<String, RecipeOverride> recipeOverrides = Map.of();

    private BrewTuning() {}

    // ── writers ──

    /** The brew listener finished loading; take its result as the authored set and re-apply overrides. */
    public static synchronized void acceptAuthoredBrews(Collection<Brew> loaded) {
        Map<String, Brew> map = new LinkedHashMap<>();
        loaded.forEach(brew -> map.put(brew.id(), brew));
        authoredBrews = map;
        rebuildBrews();
    }

    public static synchronized void acceptAuthoredRecipes(Collection<BrewingRecipe> loaded) {
        Map<String, BrewingRecipe> map = new LinkedHashMap<>();
        loaded.forEach(recipe -> map.put(recipe.id(), recipe));
        authoredRecipes = map;
        rebuildRecipes();
    }

    public static synchronized void publishOverrides(Map<String, BrewOverride> brews, Map<String, RecipeOverride> recipes) {
        brewOverrides = Map.copyOf(brews);
        recipeOverrides = Map.copyOf(recipes);
        rebuildBrews();
        rebuildRecipes();
    }

    private static void rebuildBrews() {
        Brews.clear();
        for (Brew authored : authoredBrews.values()) {
            Brews.register(effective(authored, brewOverride(authored.id())));
        }
    }

    private static void rebuildRecipes() {
        BrewingRecipes.clear();
        for (BrewingRecipe authored : authoredRecipes.values()) {
            BrewingRecipes.register(effective(authored, recipeOverride(authored.id())));
        }
    }

    // ── readers ──

    public static @Nullable Brew authoredBrew(String id) {
        return authoredBrews.get(id);
    }

    public static @Nullable BrewingRecipe authoredRecipe(String id) {
        return authoredRecipes.get(id);
    }

    public static Collection<Brew> authoredBrews() {
        return List.copyOf(authoredBrews.values());
    }

    public static Collection<BrewingRecipe> authoredRecipes() {
        return List.copyOf(authoredRecipes.values());
    }

    public static BrewOverride brewOverride(String id) {
        return brewOverrides.getOrDefault(id, BrewOverride.NONE);
    }

    public static RecipeOverride recipeOverride(String id) {
        return recipeOverrides.getOrDefault(id, RecipeOverride.NONE);
    }

    /** Whether this brew may be brewed and drunk. On unless an administrator switched it off. */
    public static boolean enabled(@Nullable String brewId) {
        return brewId == null || brewOverride(brewId).enabled().orElse(Boolean.TRUE);
    }

    // ── the overlay itself (pure) ──

    /**
     * The one {@code apply_effects} component this brew's drink-time mob effects live in, or null when there is none
     * or more than one — the effect list is then not a single thing an editor can safely replace.
     */
    public static BrewEffect.@Nullable ApplyEffects editableEffects(Brew brew) {
        BrewEffect.ApplyEffects found = null;
        for (BrewEffectEntry entry : brew.components()) {
            if (entry.component() instanceof BrewEffect.ApplyEffects apply) {
                if (found != null) {
                    return null;
                }
                found = apply;
            }
        }
        return found;
    }

    /**
     * {@code authored} with the effect-list override applied: the {@code apply_effects} component's list is replaced
     * by the enabled lines, and the brew's legacy effect list is rebuilt to match, so the definition the client sync
     * derives from it carries the same effects. Other components are untouched.
     */
    public static Brew effective(Brew authored, BrewOverride override) {
        if (override.effects().isEmpty() || editableEffects(authored) == null) {
            return authored;
        }
        List<BrewEffectText.Line> lines = BrewEffectText.parse(override.effects().get(),
                id -> BuiltInRegistries.MOB_EFFECT.containsKey(id));
        if (lines == null) {
            return authored;
        }
        List<BrewEffect.ApplyEffects.EffectSpec> specs = lines.stream()
                .filter(BrewEffectText.Line::enabled).map(BrewEffectText.Line::spec).toList();
        List<BrewEffectEntry> components = new ArrayList<>();
        for (BrewEffectEntry entry : authored.components()) {
            components.add(entry.component() instanceof BrewEffect.ApplyEffects
                    ? new BrewEffectEntry(new BrewEffect.ApplyEffects(specs), entry.phase()) : entry);
        }
        List<Brew.EffectSpec> legacy = new ArrayList<>();
        for (BrewEffect.ApplyEffects.EffectSpec spec : specs) {
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(spec.id());
            if (effect != null) {
                Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
                legacy.add(new Brew.EffectSpec(holder, spec.duration(), spec.amplifier(), spec.ambient()));
            }
        }
        return new Brew(authored.id(), authored.displayName(), authored.color(), legacy, authored.flavorText(),
                authored.silverVariant(), components);
    }

    /** {@code authored} with heat time, failure chance, cauldron tier and ingredient counts overridden. */
    public static BrewingRecipe effective(BrewingRecipe authored, RecipeOverride override) {
        if (override.isEmpty()) {
            return authored;
        }
        List<BrewingRecipe.Ingredient> ingredients = new ArrayList<>();
        for (BrewingRecipe.Ingredient ingredient : authored.ingredients()) {
            Integer count = override.ingredientCounts().get(itemId(ingredient.item()));
            ingredients.add(count == null ? ingredient : new BrewingRecipe.Ingredient(ingredient.item(), count));
        }
        return new BrewingRecipe(authored.id(), ingredients,
                override.cauldronTier().orElse(authored.cauldronTier()),
                override.heatTimeTicks().orElse(authored.heatTimeTicks()),
                authored.outputBrewId(),
                override.failureChance().orElse(authored.failureChance()),
                authored.catalyst());
    }

    public static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /**
     * Pairs where an earlier recipe would always match first when a later one's pot is filled: every ingredient of
     * the earlier one is present in the later one's quantities, and the earlier one needs no better cauldron.
     * {@code BrewingRecipes.findMatch} takes the first match, so the later recipe could never be brewed.
     */
    public static List<String> shadowedPairs(List<BrewingRecipe> ordered) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            for (int j = i + 1; j < ordered.size(); j++) {
                if (covers(ordered.get(i), ordered.get(j))) {
                    out.add(ordered.get(i).id() + ">" + ordered.get(j).id());
                }
            }
        }
        return out;
    }

    private static boolean covers(BrewingRecipe first, BrewingRecipe later) {
        if (!later.cauldronTier().isAtLeast(first.cauldronTier())) {
            return false;
        }
        for (BrewingRecipe.Ingredient need : first.ingredients()) {
            int have = 0;
            for (BrewingRecipe.Ingredient offered : later.ingredients()) {
                if (offered.item() == need.item()) {
                    have += offered.count();
                }
            }
            if (have < need.count()) {
                return false;
            }
        }
        return true;
    }
}
