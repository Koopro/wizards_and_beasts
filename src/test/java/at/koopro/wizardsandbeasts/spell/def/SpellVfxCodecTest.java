package at.koopro.wizardsandbeasts.spell.def;

import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code vfx} block: its codec, its clamps, and the promise that two spells look different.
 *
 * <p>Pure codec work over {@code JsonOps} — {@link SpellVfx} touches no registry, which is the whole
 * reason it names particles by an enum rather than by id.
 */
class SpellVfxCodecTest {

    private static final Gson GSON = new Gson();
    private static final Path SPELLS =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "spells");

    private static SpellVfx parse(String json) {
        JsonElement element = GSON.fromJson(json, JsonElement.class);
        return SpellVfx.CODEC.parse(JsonOps.INSTANCE, element)
                .getOrThrow(message -> new AssertionError("parse failed: " + message));
    }

    // -- codec -----------------------------------------------------------------------------------

    @Test
    void aFullBlockRoundTrips() {
        SpellVfx original = new SpellVfx(SpellVfx.Style.FIRE_EMBER, SpellVfx.Style.ICE_SHARD, 3, 20);
        JsonElement encoded = SpellVfx.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));
        SpellVfx reparsed = SpellVfx.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("parse failed: " + message));
        assertEquals(original, reparsed);
    }

    @Test
    void everyFieldIsOptional() {
        SpellVfx empty = parse("{}");
        assertEquals(SpellVfx.Style.ARCANE_MOTE, empty.trail());
        assertEquals(SpellVfx.Style.ARCANE_MOTE, empty.impact());
        assertEquals(1, empty.trailDensity());
        assertEquals(12, empty.impactCount());
    }

    /**
     * A typo has to fail at load, naming the file, rather than at render time.
     *
     * <p>This is the reason styles are an enum and not a free-form particle id: an unknown id would
     * parse fine and then either render nothing or throw inside the particle engine.
     */
    @Test
    void anUnknownStyleIsRejectedByTheCodec() {
        JsonElement element = GSON.fromJson("{\"trail\":\"glitter_bomb\"}", JsonElement.class);
        assertTrue(SpellVfx.CODEC.parse(JsonOps.INSTANCE, element).isError(),
                "an unknown style must not parse");
    }

    // -- clamps ----------------------------------------------------------------------------------

    @Test
    void countsAreClampedOnConstructionNotTrusted() {
        SpellVfx absurd = parse("{\"trailDensity\":9999,\"impactCount\":100000}");
        assertEquals(SpellVfx.MAX_TRAIL_DENSITY, absurd.trailDensity());
        assertEquals(SpellVfx.MAX_IMPACT_COUNT, absurd.impactCount());
    }

    @Test
    void negativeCountsBecomeZeroRatherThanLoopingForever() {
        SpellVfx negative = parse("{\"trailDensity\":-5,\"impactCount\":-1}");
        assertEquals(0, negative.trailDensity());
        assertEquals(0, negative.impactCount());
    }

    @Test
    void theCapsAreSmallEnoughToMatter() {
        // A cap of 10,000 is not a cap. These numbers are the actual performance contract.
        assertTrue(SpellVfx.MAX_IMPACT_COUNT <= 64, "impact cap must stay in effect-not-stutter range");
        assertTrue(SpellVfx.MAX_TRAIL_DENSITY <= 8, "trail cap must stay below smoke-screen density");
    }

    // -- defaults --------------------------------------------------------------------------------

    @Test
    void aSpellWithNoBlockKeepsItsFamilyLook() {
        // The compatibility promise: adding the field changed nothing for anything already shipped.
        for (SpellFamily family : SpellFamily.values()) {
            SpellVfx fallback = SpellVfx.defaultFor(family);
            assertEquals(family, fallback.trail().particleFamily(),
                    family + " must fall back to its own particle");
            assertEquals(family, fallback.impact().particleFamily());
        }
    }

    @Test
    void everyFamilyHasAStyleAndEveryStyleAFamily() {
        Set<SpellFamily> covered = new HashSet<>();
        for (SpellVfx.Style style : SpellVfx.Style.values()) {
            assertNotNull(style.particleFamily(), style + " must map to a particle family");
            covered.add(style.particleFamily());
        }
        assertEquals(SpellFamily.values().length, covered.size(),
                "every family needs a style, or defaultFor silently falls to arcane");
    }

    @Test
    void resolvePrefersTheAuthoredBlock() {
        SpellVfx authored = new SpellVfx(SpellVfx.Style.DARK_WISP, SpellVfx.Style.DARK_WISP, 2, 20);
        assertEquals(authored, SpellVfx.resolve(java.util.Optional.of(authored), SpellFamily.FIRE));
        assertEquals(SpellVfx.defaultFor(SpellFamily.FIRE),
                SpellVfx.resolve(java.util.Optional.empty(), SpellFamily.FIRE));
    }

    // -- the shipped data ------------------------------------------------------------------------

    @Test
    void everyShippedSpellParsesWithItsVfxBlock() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(SPELLS)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject json = GSON.fromJson(Files.readString(path), JsonObject.class);
                var parsed = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json);
                if (parsed.isError()) {
                    problems.add(path.getFileName() + ": "
                            + parsed.error().map(Object::toString).orElse("unknown"));
                    continue;
                }
                SpellDefinition def = parsed.getOrThrow();
                // COMING_SOON spells are exempt: the cast gate refuses them before dispatch, so they
                // never draw a particle and there is no look to tell apart. Demanding a vfx block
                // from them would mean authoring an appearance for a spell that cannot appear.
                if (def.implementationState() == SpellImplementationState.COMING_SOON) {
                    continue;
                }
                if (def.vfx().isEmpty()) {
                    problems.add(path.getFileName() + " has no vfx block, so it falls back to the "
                            + "family look and is indistinguishable from its neighbours");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * The acceptance criterion, as a test: two spells side by side must be tellable apart.
     *
     * <p>Colour alone was not enough — the mod already had 26 distinct colours and every spell still
     * drew the same particle, so the only difference was hue on an identical sprite. This asserts the
     * *shapes* vary: the shipped roster uses most of the available styles rather than defaulting
     * everything to arcane.
     */
    @Test
    void theShippedRosterUsesMoreThanOneLook() throws IOException {
        Set<String> trails = new HashSet<>();
        Set<String> impacts = new HashSet<>();
        try (Stream<Path> files = Files.list(SPELLS)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject json = GSON.fromJson(Files.readString(path), JsonObject.class);
                // Same exemption as above: a spell the cast gate always refuses contributes no look.
                JsonElement state = json.get("implementationState");
                if (state != null && "coming_soon".equals(state.getAsString())) {
                    continue;
                }
                JsonObject vfx = json.getAsJsonObject("vfx");
                assertNotNull(vfx, path.getFileName() + " has no vfx block");
                trails.add(vfx.get("trail").getAsString());
                impacts.add(vfx.get("impact").getAsString());
            }
        }
        assertTrue(trails.size() >= 5, "expected the roster to use at least five trail looks, got " + trails);
        assertTrue(impacts.size() >= 4, "expected at least four impact looks, got " + impacts);
    }
}
