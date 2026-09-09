package at.koopro.wizardsandbeasts.brew;

import at.koopro.wizardsandbeasts.brew.def.BrewDefinition;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffect;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffectEntry;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffectPhase;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The component seam: that it parses, that it dispatches, and that turning it on did not change what
 * an un-migrated brew does.
 *
 * <p>The components themselves need a level and a living entity to run, so what is checked here is
 * the layer that decides <em>which</em> component runs and <em>when</em> — the codec, the phase
 * filter, and the legacy-wrapping rule in {@code BrewDefinition}. That last one is the whole
 * compatibility story: if it is wrong, every brew written before today either doubles its effects or
 * loses them.
 */
class BrewEffectComponentTest {

    private static BrewEffect parse(String json) {
        return BrewEffect.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow();
    }

    private static BrewEffectEntry parseEntry(String json) {
        return BrewEffectEntry.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow();
    }

    // ── the codec ──────────────────────────────────────────────────────────────────────────

    @Test
    void everyComponentTypeParsesFromItsDiscriminator() {
        assertInstanceOf(BrewEffect.ApplyEffects.class, parse("""
                {"type":"apply_effects","effects":[{"id":"minecraft:speed","duration":200}]}"""));
        assertInstanceOf(BrewEffect.HealAndCure.class, parse("""
                {"type":"heal_and_cure","heal":4.0}"""));
        assertInstanceOf(BrewEffect.Nourish.class, parse("""
                {"type":"nourish","food":4}"""));
        assertInstanceOf(BrewEffect.Extinguish.class, parse("""
                {"type":"extinguish","fireResistanceTicks":100}"""));
        assertInstanceOf(BrewEffect.Flourish.class, parse("""
                {"type":"flourish","count":8}"""));
        assertInstanceOf(BrewEffect.FelixFelicis.class, parse("""
                {"type":"felix_felicis","strength":2}"""));
        assertInstanceOf(BrewEffect.PolyjuiceDisguise.class, parse("""
                {"type":"polyjuice_disguise"}"""));
        assertInstanceOf(BrewEffect.TruthSerum.class, parse("""
                {"type":"truth_serum","durationTicks":1200}"""));
    }

    /**
     * The list above must stay exhaustive.
     *
     * <p>It was not: three of the eight types had been added without a parse case, so a discriminator
     * that failed to dispatch would have gone unnoticed for whichever variant nobody had thought to
     * list. Counting is cruder than parsing each one but it cannot silently fall behind, and the
     * failure message says exactly what to add.
     */
    @Test
    void theDiscriminatorTestCoversEveryType() {
        assertEquals(8, BrewEffect.Type.values().length,
                "a BrewEffect.Type was added or removed — add it to "
                        + "everyComponentTypeParsesFromItsDiscriminator above, then update this count");
    }

    @Test
    void everyTypeHasAUniqueDiscriminatorAndACodec() {
        // A duplicated serialised name would make one variant unreachable from JSON, silently.
        java.util.Set<String> names = new java.util.HashSet<>();
        for (BrewEffect.Type type : BrewEffect.Type.values()) {
            assertTrue(names.add(type.getSerializedName()),
                    "duplicate discriminator: " + type.getSerializedName());
            assertNotNull(type.codec(), type + " has no codec");
        }
        assertEquals(BrewEffect.Type.values().length, names.size());
    }

    @Test
    void aComponentRoundTripsThroughItsCodec() {
        BrewEffect original = new BrewEffect.HealAndCure(
                6.0f, List.of(Identifier.parse("minecraft:poison")), true);
        var encoded = BrewEffect.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(original, BrewEffect.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void optionalFieldsTakeTheirDefaults() {
        BrewEffect.HealAndCure bare = (BrewEffect.HealAndCure) parse("""
                {"type":"heal_and_cure"}""");
        assertEquals(0f, bare.heal());
        assertTrue(bare.cure().isEmpty());
        assertFalse(bare.cureAllHarmful());
    }

    // ── phases ─────────────────────────────────────────────────────────────────────────────

    @Test
    void aComponentWithNoPhaseIsAnOnDrinkComponent() {
        // The compatibility rule. Every component authored before phases existed must keep meaning
        // "this happens when somebody drinks it".
        assertEquals(BrewEffectPhase.ON_DRINK, parseEntry("""
                {"type":"flourish","count":4}""").phase());
    }

    @Test
    void anExplicitPhaseIsHonoured() {
        assertEquals(BrewEffectPhase.ON_BREW_COMPLETE, parseEntry("""
                {"type":"flourish","count":4,"phase":"on_brew_complete"}""").phase());
    }

    @Test
    void thePhaseRidesBesideTheComponentsOwnFields() {
        // Flat JSON, not a nested {"component": {...}, "phase": ...} wrapper - which is what lets a
        // pre-phase component file parse untouched.
        BrewEffectEntry entry = parseEntry("""
                {"type":"heal_and_cure","heal":2.0,"phase":"on_brew_complete"}""");
        assertEquals(2.0f, ((BrewEffect.HealAndCure) entry.component()).heal());
        assertEquals(BrewEffectPhase.ON_BREW_COMPLETE, entry.phase());
    }

    @Test
    void hasPhaseFindsOnlyTheMatchingEntries() {
        List<BrewEffectEntry> entries = List.of(
                BrewEffectEntry.onDrink(new BrewEffect.Flourish(4, Optional.empty())),
                new BrewEffectEntry(new BrewEffect.Flourish(4, Optional.empty()),
                        BrewEffectPhase.ON_BREW_COMPLETE));

        assertTrue(BrewEffectEntry.hasPhase(entries, BrewEffectPhase.ON_DRINK));
        assertTrue(BrewEffectEntry.hasPhase(entries, BrewEffectPhase.ON_BREW_COMPLETE));
        assertFalse(BrewEffectEntry.hasPhase(List.of(), BrewEffectPhase.ON_DRINK));
        assertFalse(BrewEffectEntry.hasPhase(null, BrewEffectPhase.ON_DRINK));
    }

    @Test
    void hasPhaseIsFalseWhenOnlyTheOtherPhaseIsPresent() {
        List<BrewEffectEntry> drinkOnly =
                List.of(BrewEffectEntry.onDrink(new BrewEffect.Flourish(4, Optional.empty())));
        assertFalse(BrewEffectEntry.hasPhase(drinkOnly, BrewEffectPhase.ON_BREW_COMPLETE),
                "a brew with no completion components must not build a context for one");
    }

    // ── the legacy bridge ──────────────────────────────────────────────────────────────────

    @Test
    void aLegacyEffectsListBecomesAnApplyEffectsComponent() {
        // The reason no brew had to be rewritten. An old file keeps its `effects` array and gains a
        // component derived from it at load, so the drink path has exactly one route to walk.
        BrewDefinition def = new BrewDefinition("brew.test.name", 0xFF00FF00,
                List.of(new BrewDefinition.EffectEntry(Identifier.parse("minecraft:speed"), 200, 1, false)),
                Optional.empty(), Optional.empty());

        Brew brew = def.toBrew("test:legacy");

        assertNotNull(brew);
        assertEquals(1, brew.components().size());
        assertInstanceOf(BrewEffect.ApplyEffects.class, brew.components().getFirst().component());
        assertEquals(BrewEffectPhase.ON_DRINK, brew.components().getFirst().phase());
    }

    @Test
    void aComponentOnlyBrewIsAccepted() {
        // It used to be rejected: toBrew returned null for an empty effects list. That rule would now
        // throw away every brew whose behaviour is entirely in its components.
        BrewDefinition def = new BrewDefinition("brew.test.name", 0xFF00FF00,
                List.of(), Optional.empty(), Optional.empty(),
                List.of(BrewEffectEntry.onDrink(new BrewEffect.HealAndCure(4f, List.of(), true))));

        Brew brew = def.toBrew("test:components_only");

        assertNotNull(brew, "a brew with components and no effects list must survive load");
        assertEquals(1, brew.components().size());
        assertTrue(brew.effects().isEmpty());
    }

    @Test
    void aBrewWithNeitherEffectsNorComponentsIsStillRejected() {
        BrewDefinition def = new BrewDefinition("brew.test.name", 0xFF00FF00,
                List.of(), Optional.empty(), Optional.empty(), List.of());
        assertNull(def.toBrew("test:empty"), "a brew that would do nothing must not load");
    }

    @Test
    void componentsAndALegacyListCoexist() {
        BrewDefinition def = new BrewDefinition("brew.test.name", 0xFF00FF00,
                List.of(new BrewDefinition.EffectEntry(Identifier.parse("minecraft:speed"), 200, 0, false)),
                Optional.empty(), Optional.empty(),
                List.of(BrewEffectEntry.onDrink(new BrewEffect.Flourish(8, Optional.empty()))));

        Brew brew = def.toBrew("test:both");

        assertNotNull(brew);
        assertEquals(2, brew.components().size(), "the authored component plus the derived one");
    }

    @Test
    void roundTrippingABrewDoesNotDuplicateItsEffects() {
        // fromBrew feeds the client sync payload. toBrew appends an apply_effects derived from the
        // effects list; echoing that back ALONGSIDE the effects list would double every effect on
        // the client the first time a brew was synced.
        BrewDefinition original = new BrewDefinition("brew.test.name", 0xFF00FF00,
                List.of(new BrewDefinition.EffectEntry(Identifier.parse("minecraft:speed"), 200, 0, false)),
                Optional.empty(), Optional.empty(),
                List.of(BrewEffectEntry.onDrink(new BrewEffect.Flourish(8, Optional.empty()))));

        Brew brew = original.toBrew("test:sync");
        assertNotNull(brew);
        BrewDefinition echoed = BrewDefinition.fromBrew(brew);

        assertEquals(1, echoed.effects().size(), "the effects list survives once");
        assertEquals(1, echoed.components().size(), "and only the AUTHORED component comes back");
        assertInstanceOf(BrewEffect.Flourish.class, echoed.components().getFirst().component());

        // And the round trip is stable: re-baking gives the same component count, not a growing one.
        Brew rebaked = echoed.toBrew("test:sync");
        assertNotNull(rebaked);
        assertEquals(brew.components().size(), rebaked.components().size(),
                "a brew must not gain a component every time it is synced");
    }

    @Test
    void threeConsecutiveSyncsDoNotGrowTheComponentList() {
        // The failure this guards is cumulative, so one round trip is not enough to see it: a bug
        // that appended rather than replaced would look fine once and be obviously wrong by the
        // third join. Three players joining a server is an ordinary afternoon.
        BrewDefinition def = new BrewDefinition("brew.test.name", 0xFF00FF00,
                List.of(new BrewDefinition.EffectEntry(Identifier.parse("minecraft:speed"), 200, 0, false)),
                Optional.empty(), Optional.empty(),
                List.of(BrewEffectEntry.onDrink(new BrewEffect.HealAndCure(4f, List.of(), true))));

        Brew brew = def.toBrew("test:sync3");
        assertNotNull(brew);
        int effectCount = brew.effects().size();
        int componentCount = brew.components().size();

        for (int round = 1; round <= 3; round++) {
            BrewDefinition wire = BrewDefinition.fromBrew(brew);
            brew = wire.toBrew("test:sync3");
            assertNotNull(brew, "round " + round + " lost the brew entirely");
            assertEquals(effectCount, brew.effects().size(), "effects grew on sync round " + round);
            assertEquals(componentCount, brew.components().size(),
                    "components grew on sync round " + round);
        }

        // And the surviving component is still the authored one, not a stack of derived duplicates.
        long applyEffects = brew.components().stream()
                .filter(e -> e.component() instanceof BrewEffect.ApplyEffects)
                .count();
        assertEquals(1, applyEffects, "exactly one derived apply_effects must survive three syncs");
    }
}
