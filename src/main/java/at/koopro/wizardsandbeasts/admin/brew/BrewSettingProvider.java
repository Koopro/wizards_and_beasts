package at.koopro.wizardsandbeasts.admin.brew;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingProvider;
import at.koopro.wizardsandbeasts.admin.config.SettingBinding;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.admin.config.SettingType;
import at.koopro.wizardsandbeasts.admin.config.SettingTypes;
import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffect;
import at.koopro.wizardsandbeasts.brew.tuning.BrewEffectText;
import at.koopro.wizardsandbeasts.brew.tuning.BrewOverride;
import at.koopro.wizardsandbeasts.brew.tuning.BrewTuning;
import at.koopro.wizardsandbeasts.brew.tuning.BrewTuningService;
import at.koopro.wizardsandbeasts.brew.tuning.RecipeOverride;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Resolves brew and recipe setting ids ({@link BrewSettingIds}) into live {@link AdminSetting}s over the world's
 * {@link BrewOverride}s and {@link RecipeOverride}s.
 *
 * <p>Built on every lookup from the authored registries in {@link BrewTuning}, so a {@code /reload} that adds or
 * removes a brew is reflected at once and a setting never outlives what it edits. A brew or recipe added by any
 * datapack is editable with no code: its settings exist because it exists. Each setting's default is the authored
 * value, and it goes through {@code AdminSettingService} like every other setting.
 */
@NullMarked
public final class BrewSettingProvider implements AdminSettingProvider {

    /** Every brew's and recipe's values, for profiles and snapshots. Ids that do not resolve are skipped later. */
    @Override
    public java.util.Collection<Identifier> enumerate(net.minecraft.server.@org.jspecify.annotations.Nullable MinecraftServer server) {
        java.util.List<Identifier> out = new java.util.ArrayList<>();
        for (Brew brew : at.koopro.wizardsandbeasts.brew.Brews.all()) {
            out.add(BrewSettingIds.brew(brew.id(), BrewSettingIds.ENABLED));
            out.add(BrewSettingIds.brew(brew.id(), BrewSettingIds.EFFECTS));
        }
        for (BrewingRecipe recipe : BrewingRecipes.all()) {
            out.add(BrewSettingIds.recipe(recipe.id(), BrewSettingIds.HEAT_TIME));
            out.add(BrewSettingIds.recipe(recipe.id(), BrewSettingIds.FAILURE_CHANCE));
            out.add(BrewSettingIds.recipe(recipe.id(), BrewSettingIds.CAULDRON_TIER));
            for (BrewingRecipe.Ingredient ingredient : recipe.ingredients()) {
                out.add(BrewSettingIds.ingredient(recipe.id(),
                        BrewTuning.itemId(ingredient.item())));
            }
        }
        return out;
    }

    public static final int MIN_HEAT_TICKS = 20;
    public static final int MAX_HEAT_TICKS = 72_000;
    public static final int MAX_INGREDIENT_COUNT = 64;
    /** Effect ids are checked against the live mob-effect registry, the same one the drink applies from. */
    static final java.util.function.Predicate<Identifier> KNOWN_EFFECT = BuiltInRegistries.MOB_EFFECT::containsKey;

    @Override
    public @Nullable AdminSetting<?> resolve(Identifier id) {
        BrewSettingIds.Parsed parsed = BrewSettingIds.parse(id);
        if (parsed == null) {
            return null;
        }
        return parsed.kind() == BrewSettingIds.Kind.BREW ? brewSetting(id, parsed) : recipeSetting(id, parsed);
    }

    /** Whether this brew's drink-time effects can be edited: they are exactly one {@code apply_effects} component. */
    public static boolean effectsEditable(Brew authored) {
        return BrewTuning.editableEffects(authored) != null;
    }

    // ── brews ──

    private static @Nullable AdminSetting<?> brewSetting(Identifier id, BrewSettingIds.Parsed parsed) {
        Brew authored = BrewTuning.authoredBrew(parsed.target());
        if (authored == null) {
            return null;
        }
        String brewId = authored.id();
        return switch (parsed.property()) {
            case BrewSettingIds.ENABLED -> AdminSetting.builder(id, SettingTypes.bool(),
                            new BrewBinding<>(brewId, Boolean.TRUE, BrewOverride::enabled, BrewOverride::withEnabled))
                    .category(AdminCategory.BREWING)
                    .build();
            case BrewSettingIds.EFFECTS -> {
                BrewEffect.ApplyEffects effects = BrewTuning.editableEffects(authored);
                if (effects == null) {
                    yield null;
                }
                String shipped = BrewEffectText.formatSpecs(effects.effects());
                boolean otherComponents = authored.components().size() > 1;
                yield AdminSetting.builder(id, EffectListType.INSTANCE,
                                new BrewBinding<>(brewId, shipped, BrewOverride::effects, BrewOverride::withEffects))
                        .category(AdminCategory.BREWING)
                        // A brew whose only behaviour is its effects must keep at least one, or drinking it does nothing.
                        .validator((candidate, registry) -> otherComponents || enabledCount(candidate) > 0
                                ? null : "admin.wizards_and_beasts.conflict.brew_does_nothing")
                        // Adding an effect the brew never had, or strengthening one, changes what players drink.
                        .dangerRule((previous, candidate, authoredText, setting) ->
                                strengthens(authoredText, candidate) ? setting.warningKey() : null)
                        .build();
            }
            default -> null;
        };
    }

    private static int enabledCount(String text) {
        List<BrewEffectText.Line> lines = BrewEffectText.parse(text, KNOWN_EFFECT);
        return lines == null ? 0 : (int) lines.stream().filter(BrewEffectText.Line::enabled).count();
    }

    /** True when {@code candidate} enables an effect {@code authored} lacks, or raises an amplifier or duration past it. */
    static boolean strengthens(String authored, String candidate) {
        List<BrewEffectText.Line> before = BrewEffectText.parse(authored, KNOWN_EFFECT);
        List<BrewEffectText.Line> after = BrewEffectText.parse(candidate, KNOWN_EFFECT);
        if (before == null || after == null) {
            return false;
        }
        Map<Identifier, BrewEffectText.Line> shipped = new HashMap<>();
        before.forEach(line -> shipped.put(line.effect(), line));
        for (BrewEffectText.Line line : after) {
            if (!line.enabled()) {
                continue;
            }
            BrewEffectText.Line was = shipped.get(line.effect());
            if (was == null || line.amplifier() > was.amplifier() || line.duration() > was.duration() * 2) {
                return true;
            }
        }
        return false;
    }

    // ── recipes ──

    private static @Nullable AdminSetting<?> recipeSetting(Identifier id, BrewSettingIds.Parsed parsed) {
        BrewingRecipe authored = BrewTuning.authoredRecipe(parsed.target());
        if (authored == null) {
            return null;
        }
        String recipeId = authored.id();
        return switch (parsed.property()) {
            case BrewSettingIds.HEAT_TIME -> AdminSetting.builder(id, SettingTypes.integer(MIN_HEAT_TICKS, MAX_HEAT_TICKS),
                            new RecipeBinding<>(recipeId, authored.heatTimeTicks(), RecipeOverride::heatTimeTicks,
                                    RecipeOverride::withHeatTime))
                    .category(AdminCategory.BREWING)
                    .build();
            case BrewSettingIds.FAILURE_CHANCE -> AdminSetting.builder(id, SettingTypes.decimal(0.0, 1.0, 0.01),
                            new RecipeBinding<Double>(recipeId, shortest(authored.failureChance()),
                                    o -> o.failureChance().map(BrewSettingProvider::shortest),
                                    (o, v) -> o.withFailureChance(v.map(Double::floatValue))))
                    .category(AdminCategory.BREWING)
                    .build();
            case BrewSettingIds.CAULDRON_TIER -> AdminSetting.builder(id, SettingTypes.enumeration(CauldronTier.class),
                            new RecipeBinding<>(recipeId, authored.cauldronTier(), RecipeOverride::cauldronTier,
                                    RecipeOverride::withTier))
                    .category(AdminCategory.BREWING)
                    .validator((candidate, registry) -> shadowingProblem(recipeId, o -> o.withTier(Optional.of(candidate))))
                    .build();
            case BrewSettingIds.INGREDIENT -> {
                String item = parsed.item();
                BrewingRecipe.Ingredient ingredient = item == null ? null : ingredient(authored, item);
                if (ingredient == null) {
                    yield null;
                }
                yield AdminSetting.builder(id, SettingTypes.integer(1, MAX_INGREDIENT_COUNT),
                                new RecipeBinding<Integer>(recipeId, ingredient.count(),
                                        o -> Optional.ofNullable(o.ingredientCounts().get(item)),
                                        (o, v) -> o.withIngredientCount(item, v)))
                        .category(AdminCategory.BREWING)
                        .validator((candidate, registry) -> shadowingProblem(recipeId,
                                o -> o.withIngredientCount(item, Optional.of(candidate))))
                        .build();
            }
            default -> null;
        };
    }

    private static BrewingRecipe.@Nullable Ingredient ingredient(BrewingRecipe recipe, String itemId) {
        for (BrewingRecipe.Ingredient ingredient : recipe.ingredients()) {
            if (BrewTuning.itemId(ingredient.item()).equals(itemId)) {
                return ingredient;
            }
        }
        return null;
    }

    /**
     * Recipes remain valid: a change that would let this recipe always match before another one — or another before
     * it — makes the later recipe impossible to brew ({@code findMatch} takes the first match), and is refused. Pairs
     * that already shadowed each other before the change are not this change's fault and do not block it.
     */
    static @Nullable String shadowingProblem(String recipeId, UnaryOperator<RecipeOverride> change) {
        List<BrewingRecipe> before = new ArrayList<>(BrewingRecipes.all());
        List<BrewingRecipe> after = new ArrayList<>();
        for (BrewingRecipe recipe : before) {
            if (recipe.id().equals(recipeId)) {
                BrewingRecipe authored = BrewTuning.authoredRecipe(recipeId);
                after.add(authored == null ? recipe
                        : BrewTuning.effective(authored, change.apply(BrewTuning.recipeOverride(recipeId))));
            } else {
                after.add(recipe);
            }
        }
        Set<String> existing = new HashSet<>(BrewTuning.shadowedPairs(before));
        for (String pair : BrewTuning.shadowedPairs(after)) {
            if (!existing.contains(pair)) {
                return "admin.wizards_and_beasts.conflict.recipe_shadowed";
            }
        }
        return null;
    }

    private static double shortest(float value) {
        return Double.parseDouble(Float.toString(value));
    }

    // ── bindings ──

    private record BrewBinding<T>(String brewId, T authored, Function<BrewOverride, Optional<T>> read,
                                  BiFunction<BrewOverride, Optional<T>, BrewOverride> write) implements SettingBinding<T> {
        @Override
        public T get() {
            return read.apply(BrewTuning.brewOverride(brewId)).orElse(authored);
        }

        @Override
        public void set(T value) {
            Optional<T> stored = value.equals(authored) ? Optional.empty() : Optional.of(value);
            server(s -> BrewTuningService.updateBrew(s, brewId, o -> write.apply(o, stored)));
        }

        @Override
        public T defaultValue() {
            return authored;
        }

        @Override
        public boolean available() {
            return ServerLifecycleHooks.getCurrentServer() != null && BrewTuning.authoredBrew(brewId) != null;
        }
    }

    private record RecipeBinding<T>(String recipeId, T authored, Function<RecipeOverride, Optional<T>> read,
                                    BiFunction<RecipeOverride, Optional<T>, RecipeOverride> write) implements SettingBinding<T> {
        @Override
        public T get() {
            return read.apply(BrewTuning.recipeOverride(recipeId)).orElse(authored);
        }

        @Override
        public void set(T value) {
            Optional<T> stored = value.equals(authored) ? Optional.empty() : Optional.of(value);
            server(s -> BrewTuningService.updateRecipe(s, recipeId, o -> write.apply(o, stored)));
        }

        @Override
        public T defaultValue() {
            return authored;
        }

        @Override
        public boolean available() {
            return ServerLifecycleHooks.getCurrentServer() != null && BrewTuning.authoredRecipe(recipeId) != null;
        }
    }

    private static void server(Consumer<MinecraftServer> action) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("no server to store a brew override on");
        }
        action.accept(server);
    }


    /** The effect list as one text value, canonicalised on parse; unknown effects or bad numbers make it invalid. */
    enum EffectListType implements SettingType<String> {
        INSTANCE;

        @Override
        public SettingKind kind() {
            return SettingKind.STRING;
        }

        @Override
        public @Nullable String parse(String raw) {
            List<BrewEffectText.Line> lines = BrewEffectText.parse(raw, KNOWN_EFFECT);
            return lines == null ? null : BrewEffectText.format(lines);
        }

        @Override
        public boolean inBounds(String value) {
            return value.length() <= maxLength();
        }

        @Override
        public String format(String value) {
            return value;
        }

        @Override
        public int maxLength() {
            return 1024;
        }
    }
}
