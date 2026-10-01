package at.koopro.wizardsandbeasts.admin.brew;

import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffect;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffectEntry;
import at.koopro.wizardsandbeasts.brew.tuning.BrewTuning;
import at.koopro.wizardsandbeasts.brew.tuning.BrewTuningService;
import at.koopro.wizardsandbeasts.network.admin.AdminBrewPayloads.BrewSummary;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the effective brew and recipe registries for the Brewing section. Stores nothing: every value comes from
 * {@link Brews} / {@link BrewingRecipes} (what the cauldron and the drink use) and the authored copies in
 * {@link BrewTuning}. Edits are settings ({@link BrewSettingProvider}) and never pass through here.
 */
@NullMarked
public final class BrewAdminService {

    private static final String FACT = "admin.wizards_and_beasts.brew_fact.";

    private BrewAdminService() {}

    public static boolean authorised(AdminContext actor) {
        return actor.canRead() && actor.canModify(AdminCapability.CONTENT);
    }

    /** Every brew the server has, in registration order. */
    public static List<BrewSummary> list() {
        List<BrewSummary> out = new ArrayList<>();
        for (Brew brew : Brews.all()) {
            out.add(summary(brew));
        }
        return List.copyOf(out);
    }

    /** The recipes that produce {@code brewId}, in match order. */
    public static List<BrewingRecipe> recipesFor(String brewId) {
        List<BrewingRecipe> out = new ArrayList<>();
        for (BrewingRecipe recipe : BrewingRecipes.all()) {
            if (recipe.outputBrewId().equals(brewId)) {
                out.add(recipe);
            }
        }
        return out;
    }

    static BrewSummary summary(Brew brew) {
        List<BrewingRecipe> recipes = recipesFor(brew.id());
        BrewingRecipe recipe = recipes.isEmpty() ? null : recipes.get(0);
        Set<String> effects = new LinkedHashSet<>();
        List<String> shown = new ArrayList<>();
        for (BrewEffect.ApplyEffects.EffectSpec spec : drinkEffects(brew)) {
            effects.add(spec.id().toString());
            shown.add(spec.id().getPath() + " " + seconds(spec.duration()));
        }
        Brew authored = BrewTuning.authoredBrew(brew.id());
        return new BrewSummary(brew.id(), brew.displayName(), brew.color(), BrewTuning.enabled(brew.id()),
                recipe == null ? "" : recipe.id(),
                recipe == null ? "" : recipe.cauldronTier().name(),
                recipe == null ? 0 : recipe.heatTimeTicks(),
                recipe == null ? 0f : recipe.failureChance(),
                difficulty(recipe),
                List.copyOf(effects), String.join(", ", shown),
                authored != null && BrewSettingProvider.effectsEditable(authored),
                brew.isSilverBased(),
                !BrewTuning.brewOverride(brew.id()).isEmpty()
                        || recipes.stream().anyMatch(r -> !BrewTuning.recipeOverride(r.id()).isEmpty()));
    }

    /** Every mob effect this brew applies when drunk, across its apply_effects components. */
    public static List<BrewEffect.ApplyEffects.EffectSpec> drinkEffects(Brew brew) {
        List<BrewEffect.ApplyEffects.EffectSpec> out = new ArrayList<>();
        for (BrewEffectEntry entry : brew.components()) {
            if (entry.component() instanceof BrewEffect.ApplyEffects apply) {
                out.addAll(apply.effects());
            }
        }
        return out;
    }

    /**
     * A label derived from what actually makes a recipe hard — the cauldron it needs, its failure chance, and whether
     * it has a timed catalyst. The data has no difficulty field; this is a reading of the fields it has.
     */
    public static String difficulty(@Nullable BrewingRecipe recipe) {
        if (recipe == null) {
            return "UNBREWABLE";
        }
        int score = recipe.cauldronTier().ordinal()
                + (recipe.failureChance() >= 0.3f ? 2 : recipe.failureChance() >= 0.1f ? 1 : 0)
                + (recipe.catalyst().isPresent() ? 1 : 0);
        return score == 0 ? "BASIC" : score <= 2 ? "STANDARD" : score == 3 ? "ADVANCED" : "MASTER";
    }

    private static String seconds(int ticks) {
        return ticks % 20 == 0 ? (ticks / 20) + "s" : String.format(Locale.ROOT, "%.1fs", ticks / 20.0);
    }

    // ── detail ──

    public record Detail(BrewSummary summary, List<AdminSpellFact> recipe, List<String> components,
                         List<AdminSettingDescriptor> settings) {}

    public static @Nullable Detail detail(String brewId, AdminContext viewer) {
        Brew brew = Brews.byId(brewId);
        if (brew == null) {
            return null;
        }
        List<AdminSpellFact> facts = new ArrayList<>();
        List<AdminSettingDescriptor> settings = new ArrayList<>();
        add(settings, BrewSettingIds.brew(brew.id(), BrewSettingIds.ENABLED), viewer);
        add(settings, BrewSettingIds.brew(brew.id(), BrewSettingIds.EFFECTS), viewer);
        for (BrewingRecipe recipe : recipesFor(brew.id())) {
            facts.add(new AdminSpellFact(FACT + "recipe", recipe.id(), false));
            List<String> ingredients = new ArrayList<>();
            for (BrewingRecipe.Ingredient ingredient : recipe.ingredients()) {
                ingredients.add(ingredient.count() + "× " + BrewTuning.itemId(ingredient.item()));
            }
            facts.add(new AdminSpellFact(FACT + "ingredients", String.join(", ", ingredients), false));
            facts.add(new AdminSpellFact(FACT + "effective_heat",
                    seconds(BrewTuningService.heatTimeFor(recipe)) + " (" + recipe.heatTimeTicks() + " ticks before the speed rule)", false));
            facts.add(new AdminSpellFact(FACT + "effective_failure", String.format(Locale.ROOT, "%.0f%% (%.0f%% before the failure rule and skill)",
                    100 * BrewTuningService.baseFailureFor(recipe), 100 * recipe.failureChance()), false));
            recipe.catalyst().ifPresent(c -> facts.add(new AdminSpellFact(FACT + "catalyst",
                    BrewTuning.itemId(c.item()) + String.format(Locale.ROOT, " between %.0f%% and %.0f%% of the brew, +%.0f%% if missed",
                            100 * c.windowStart(), 100 * c.windowEnd(), 100 * c.missPenalty()), false)));
            add(settings, BrewSettingIds.recipe(recipe.id(), BrewSettingIds.HEAT_TIME), viewer);
            add(settings, BrewSettingIds.recipe(recipe.id(), BrewSettingIds.FAILURE_CHANCE), viewer);
            add(settings, BrewSettingIds.recipe(recipe.id(), BrewSettingIds.CAULDRON_TIER), viewer);
            for (BrewingRecipe.Ingredient ingredient : recipe.ingredients()) {
                add(settings, BrewSettingIds.ingredient(recipe.id(), BrewTuning.itemId(ingredient.item())), viewer);
            }
        }
        if (recipesFor(brew.id()).isEmpty()) {
            facts.add(new AdminSpellFact(FACT + "recipe", FACT + "no_recipe", true));
        }
        if (brew.isSilverBased()) {
            facts.add(new AdminSpellFact(FACT + "silver", brew.silverVariant(), false));
        }
        List<String> components = new ArrayList<>();
        for (BrewEffectEntry entry : brew.components()) {
            components.add(entry.component().type().getSerializedName() + " · " + entry.phase().getSerializedName());
        }
        return new Detail(summary(brew), List.copyOf(facts), List.copyOf(components), List.copyOf(settings));
    }

    private static void add(List<AdminSettingDescriptor> out, Identifier id, AdminContext viewer) {
        AdminSetting<?> setting = AdminSettings.registry().get(id);
        if (setting != null) {
            out.add(AdminSettingDescriptor.of(setting, viewer));
        }
    }
}
