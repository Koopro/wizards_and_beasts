package at.koopro.wizardsandbeasts.spell.effect;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure codec round-trip tests for {@link SpellEffectComponent}. No Minecraft bootstrap — codecs only
 * parse ids/enums/primitives, no registry resolution. Covers the type dispatch, the reworked
 * {@code dispel} scopes, and the recursive {@code aoe_apply} (lazy-initialized nested codec).
 */
class SpellEffectComponentCodecTest {

    private static final Gson GSON = new Gson();

    private static SpellEffectComponent parse(String json) {
        JsonElement el = GSON.fromJson(json, JsonElement.class);
        var result = SpellEffectComponent.CODEC.parse(JsonOps.INSTANCE, el);
        if (result.error().isPresent()) {
            throw new AssertionError("parse failed: " + result.error().get().message());
        }
        return result.result().orElseThrow();
    }

    private static void assertRoundTrip(SpellEffectComponent c) {
        JsonElement encoded = SpellEffectComponent.CODEC.encodeStart(JsonOps.INSTANCE, c).result().orElseThrow();
        SpellEffectComponent back = SpellEffectComponent.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(c, back, "encode -> decode must be lossless");
    }

    @Test
    void applyEffect_dispatchAndDefaults() {
        SpellEffectComponent c = parse("""
                { "type": "apply_effect", "effect": "minecraft:glowing", "duration": 100 }
                """);
        SpellEffectComponent.ApplyEffect ae = assertInstanceOf(SpellEffectComponent.ApplyEffect.class, c);
        assertEquals(100, ae.duration());
        assertEquals(0, ae.amplifier());
        assertEquals(false, ae.target());
        assertEquals(false, ae.darkArts());
        assertRoundTrip(c);
    }

    @Test
    void dispel_scopeVariants() {
        SpellEffectComponent.Dispel all = assertInstanceOf(SpellEffectComponent.Dispel.class,
                parse("{ \"type\": \"dispel\" }"));
        assertEquals(SpellEffectComponent.DispelScope.ALL, all.scope());
        assertTrue(all.respectImmunity(), "respect_immunity defaults true");

        SpellEffectComponent.Dispel mod = assertInstanceOf(SpellEffectComponent.Dispel.class,
                parse("{ \"type\": \"dispel\", \"scope\": \"mod_namespaced\" }"));
        assertEquals(SpellEffectComponent.DispelScope.MOD_NAMESPACED, mod.scope());

        SpellEffectComponent.Dispel specific = assertInstanceOf(SpellEffectComponent.Dispel.class,
                parse("""
                        { "type": "dispel", "scope": "specific",
                          "effects": ["wizards_and_beasts:lumos_field"], "respect_immunity": false }
                        """));
        assertEquals(SpellEffectComponent.DispelScope.SPECIFIC, specific.scope());
        assertEquals(1, specific.effects().size());
        assertEquals(false, specific.respectImmunity());
        assertRoundTrip(specific);
    }

    @Test
    void clearFire_unit() {
        assertInstanceOf(SpellEffectComponent.ClearFire.class, parse("{ \"type\": \"clear_fire\" }"));
    }

    @Test
    void aoeApply_recursiveNesting() {
        SpellEffectComponent c = parse("""
                {
                  "type": "aoe_apply",
                  "radius": 3.5,
                  "effects": [
                    { "type": "apply_effect", "effect": "minecraft:slow_falling", "duration": 90 },
                    { "type": "impulse", "strength": 1.2, "direction": "up" }
                  ]
                }
                """);
        SpellEffectComponent.AoeApply aoe = assertInstanceOf(SpellEffectComponent.AoeApply.class, c);
        assertEquals(3.5f, aoe.radius());
        assertEquals(2, aoe.components().size());
        assertInstanceOf(SpellEffectComponent.ApplyEffect.class, aoe.components().get(0));
        assertInstanceOf(SpellEffectComponent.Impulse.class, aoe.components().get(1));
        assertRoundTrip(c);
    }

    @Test
    void componentList_parsesAsField() {
        var result = SpellEffectComponent.CODEC.listOf().parse(JsonOps.INSTANCE,
                GSON.fromJson("""
                        [ { "type": "ignite", "seconds": 4 },
                          { "type": "explosion", "radius": 2.0 } ]
                        """, JsonElement.class));
        List<SpellEffectComponent> list = result.result().orElseThrow();
        assertEquals(2, list.size());
    }

    // ── F2: cadence-tagged entries ──────────────────────────────────────

    private static SpellEffectEntry parseEntry(String json) {
        JsonElement el = GSON.fromJson(json, JsonElement.class);
        var result = SpellEffectEntry.CODEC.parse(JsonOps.INSTANCE, el);
        if (result.error().isPresent()) {
            throw new AssertionError("entry parse failed: " + result.error().get().message());
        }
        return result.result().orElseThrow();
    }

    @Test
    void entry_withoutCadence_defaultsToTick_backCompat() {
        // Pre-cadence component JSON must parse unchanged as an entry.
        SpellEffectEntry entry = parseEntry("""
                { "type": "apply_effect", "effect": "minecraft:glowing", "duration": 100 }
                """);
        assertEquals(EffectCadence.TICK, entry.cadence());
        assertInstanceOf(SpellEffectComponent.ApplyEffect.class, entry.component());
    }

    @Test
    void entry_cadenceVariants_parseAndRoundTrip() {
        SpellEffectEntry start = parseEntry("""
                { "type": "heal", "amount": 2.0, "cadence": "start" }
                """);
        assertEquals(EffectCadence.START, start.cadence());

        SpellEffectEntry end = parseEntry("""
                { "type": "clear_fire", "cadence": "end" }
                """);
        assertEquals(EffectCadence.END, end.cadence());

        JsonElement encoded = SpellEffectEntry.CODEC.encodeStart(JsonOps.INSTANCE, end).result().orElseThrow();
        SpellEffectEntry back = SpellEffectEntry.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(end, back, "entry encode -> decode must be lossless (incl. cadence)");
    }

    @Test
    void entry_cadenceIsTopLevelOnly_nestedAoeChildrenStayPlainComponents() {
        SpellEffectEntry entry = parseEntry("""
                {
                  "type": "aoe_apply",
                  "radius": 3.0,
                  "cadence": "end",
                  "effects": [
                    { "type": "ignite", "seconds": 2 }
                  ]
                }
                """);
        assertEquals(EffectCadence.END, entry.cadence());
        SpellEffectComponent.AoeApply aoe = assertInstanceOf(SpellEffectComponent.AoeApply.class, entry.component());
        // Children are SpellEffectComponent, not entries — they fire when the parent fires.
        assertInstanceOf(SpellEffectComponent.Ignite.class, aoe.components().get(0));
    }

    @Test
    void entryList_mixedCadences_parse() {
        var result = SpellEffectEntry.CODEC.listOf().parse(JsonOps.INSTANCE,
                GSON.fromJson("""
                        [ { "type": "heal", "amount": 1.0, "cadence": "start" },
                          { "type": "ignite", "seconds": 4 },
                          { "type": "clear_fire", "cadence": "end" } ]
                        """, JsonElement.class));
        List<SpellEffectEntry> list = result.result().orElseThrow();
        assertEquals(3, list.size());
        assertEquals(EffectCadence.START, list.get(0).cadence());
        assertEquals(EffectCadence.TICK, list.get(1).cadence());
        assertEquals(EffectCadence.END, list.get(2).cadence());
    }
}
