package at.koopro.wizardsandbeasts.brew.tuning;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffect;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffectEntry;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The effect-list text, and the overlay that turns authored brews and recipes into the effective registries. */
class BrewTuningTest {

    private static final Identifier SPEED = Identifier.withDefaultNamespace("speed");
    private static final Identifier LUCK = Identifier.withDefaultNamespace("luck");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void reset() {
        BrewTuning.publishOverrides(Map.of(), Map.of());
        BrewTuning.acceptAuthoredBrews(List.of());
        BrewTuning.acceptAuthoredRecipes(List.of());
    }

    private static List<BrewEffectText.Line> parse(String text) {
        return BrewEffectText.parse(text, BuiltInRegistries.MOB_EFFECT::containsKey);
    }

    // ── the text form ──

    @Test
    void aValidListParsesAndFormatsCanonically() {
        List<BrewEffectText.Line> lines = parse("minecraft:speed 1200 1;  -minecraft:luck 600 0 AMBIENT");
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).enabled());
        assertFalse(lines.get(1).enabled());
        assertTrue(lines.get(1).ambient());
        assertEquals("minecraft:speed 1200 1; -minecraft:luck 600 0 ambient", BrewEffectText.format(lines));
        assertEquals(lines, parse(BrewEffectText.format(lines)));
        assertEquals(List.of(), parse("   "));
    }

    @Test
    void invalidEffectListsAreRejectedWhole() {
        assertNull(parse("wizards_and_beasts:no_such_effect 600 0"), "unknown effect");
        assertNull(parse("minecraft:speed 0 0"), "zero duration");
        assertNull(parse("minecraft:speed " + (BrewEffectText.MAX_DURATION + 1) + " 0"), "duration too long");
        assertNull(parse("minecraft:speed 600 " + (BrewEffectText.MAX_AMPLIFIER + 1)), "amplifier too high");
        assertNull(parse("minecraft:speed 600 -1"), "negative amplifier");
        assertNull(parse("minecraft:speed 600 0; minecraft:speed 300 1"), "duplicate effect");
        assertNull(parse("minecraft:speed six hundred"), "malformed");
        assertNull(parse("minecraft:speed 600 0 loud"), "unknown flag");
        StringBuilder many = new StringBuilder();
        int i = 0;
        for (Identifier id : BuiltInRegistries.MOB_EFFECT.keySet()) {
            if (i++ > BrewEffectText.MAX_EFFECTS) {
                break;
            }
            many.append(id).append(" 20 0;");
        }
        assertNull(parse(many.toString()), "too many effects");
    }

    // ── the overlay ──

    private static Brew brew(String id, List<BrewEffect.ApplyEffects.EffectSpec> effects) {
        return new Brew(id, "brew.test", 0xFF0000, List.of(), null, null,
                List.of(BrewEffectEntry.onDrink(new BrewEffect.ApplyEffects(effects)),
                        BrewEffectEntry.onDrink(new BrewEffect.Flourish(4, Optional.of(false)))));
    }

    @Test
    void anEffectOverrideReplacesTheOneEffectListAndKeepsOtherComponents() {
        Brew authored = brew("test:tonic", List.of(new BrewEffect.ApplyEffects.EffectSpec(SPEED, 600, 0, false)));
        BrewTuning.acceptAuthoredBrews(List.of(authored));
        BrewTuning.publishOverrides(Map.of("test:tonic", BrewOverride.NONE.withEffects(
                Optional.of("minecraft:speed 1200 1; -minecraft:luck 600 0"))), Map.of());

        Brew live = Brews.byId("test:tonic");
        BrewEffect.ApplyEffects effects = BrewTuning.editableEffects(live);
        assertEquals(List.of(new BrewEffect.ApplyEffects.EffectSpec(SPEED, 1200, 1, false)), effects.effects(),
                "the disabled luck row must not apply");
        assertEquals(2, live.components().size());
        assertTrue(live.components().get(1).component() instanceof BrewEffect.Flourish);
        assertEquals(1, live.effects().size(), "the legacy list follows, so the client sync carries the same effects");

        BrewTuning.publishOverrides(Map.of(), Map.of());
        assertSame(authored, Brews.byId("test:tonic"), "without overrides the registry holds the authored brew");
    }

    @Test
    void aDisabledBrewIsReportedDisabled() {
        BrewTuning.publishOverrides(Map.of("test:tonic", BrewOverride.NONE.withEnabled(Optional.of(false))), Map.of());
        assertFalse(BrewTuning.enabled("test:tonic"));
        assertTrue(BrewTuning.enabled("test:other"));
    }

    @Test
    void aBrewWithTwoEffectListsIsNotEditable() {
        Brew two = new Brew("test:two", "brew.test", 0, List.of(), null, null, List.of(
                BrewEffectEntry.onDrink(new BrewEffect.ApplyEffects(List.of())),
                BrewEffectEntry.onDrink(new BrewEffect.ApplyEffects(List.of()))));
        assertNull(BrewTuning.editableEffects(two));
        assertSame(two, BrewTuning.effective(two, BrewOverride.NONE.withEffects(Optional.of("minecraft:luck 20 0"))));
    }

    @Test
    void recipeOverridesChangeNumbersNotIdentity() {
        BrewingRecipe authored = new BrewingRecipe("test:recipe", List.of(
                new BrewingRecipe.Ingredient(Items.GOLD_INGOT, 2), new BrewingRecipe.Ingredient(Items.GLOWSTONE_DUST, 1)),
                CauldronTier.PEWTER, 400, "test:tonic", 0.1f, Optional.empty());
        BrewTuning.acceptAuthoredRecipes(List.of(authored));
        BrewTuning.publishOverrides(Map.of(), Map.of("test:recipe", RecipeOverride.NONE
                .withHeatTime(Optional.of(800)).withFailureChance(Optional.of(0.5f)).withTier(Optional.of(CauldronTier.COPPER))
                .withIngredientCount("minecraft:gold_ingot", Optional.of(5))));
        BrewingRecipe live = BrewingRecipes.byId("test:recipe");
        assertEquals(800, live.heatTimeTicks());
        assertEquals(0.5f, live.failureChance());
        assertEquals(CauldronTier.COPPER, live.cauldronTier());
        assertEquals(5, live.ingredients().get(0).count());
        assertEquals(Items.GOLD_INGOT, live.ingredients().get(0).item());
        assertEquals("test:tonic", live.outputBrewId());
    }

    @Test
    void shadowingIsDetected() {
        BrewingRecipe small = new BrewingRecipe("test:a", List.of(new BrewingRecipe.Ingredient(Items.GOLD_INGOT, 1)),
                CauldronTier.PEWTER, 20, "test:x", 0f, Optional.empty());
        BrewingRecipe big = new BrewingRecipe("test:b", List.of(new BrewingRecipe.Ingredient(Items.GOLD_INGOT, 2),
                new BrewingRecipe.Ingredient(Items.REDSTONE, 1)), CauldronTier.PEWTER, 20, "test:y", 0f, Optional.empty());
        assertEquals(List.of("test:a>test:b"), BrewTuning.shadowedPairs(List.of(small, big)));
        assertEquals(List.of(), BrewTuning.shadowedPairs(List.of(big, small)));
        BrewingRecipe needsCopper = new BrewingRecipe("test:a", small.ingredients(), CauldronTier.COPPER, 20, "test:x", 0f, Optional.empty());
        assertEquals(List.of(), BrewTuning.shadowedPairs(List.of(needsCopper, big)), "a pewter pot cannot brew the copper recipe");
    }

    @Test
    void overridesSurviveTheWorldFile() {
        BrewOverride brew = BrewOverride.NONE.withEnabled(Optional.of(false)).withEffects(Optional.of("minecraft:luck 600 0"));
        JsonElement json = BrewOverride.CODEC.encodeStart(JsonOps.INSTANCE, brew).getOrThrow();
        assertEquals(brew, BrewOverride.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        RecipeOverride recipe = RecipeOverride.NONE.withTier(Optional.of(CauldronTier.BRASS))
                .withIngredientCount("minecraft:redstone", Optional.of(3));
        JsonElement recipeJson = RecipeOverride.CODEC.encodeStart(JsonOps.INSTANCE, recipe).getOrThrow();
        assertEquals(recipe, RecipeOverride.CODEC.parse(JsonOps.INSTANCE, recipeJson).getOrThrow());
        assertTrue(recipe.withTier(Optional.empty()).withIngredientCount("minecraft:redstone", Optional.empty()).isEmpty());
    }
}
