package at.koopro.wizardsandbeasts.wand.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped wand wood and core definition parses through its real codec.
 *
 * <p>Worth having because the failure mode is silent. A datapack definition that fails to decode is
 * logged and dropped, and a dropped definition is indistinguishable at the cast site from one that
 * contributes nothing — {@code WandStatsResolver} falls back to neutral by design so an incomplete
 * datapack cannot break casting. So a typo in a category name does not crash, does not warn where
 * anyone looks, and just quietly removes a wood's entire contribution.
 *
 * <p>{@code category_damage_bonus} is the field that invites it: its keys are
 * {@code SpellCategory.getSerializedName()}, and the only four that exist are {@code combat},
 * {@code utility}, {@code defense} and {@code dark_arts}. Anything else — a magical school, a
 * plausible-sounding "healing" — fails the whole record.
 */
class ShippedWandDefinitionJsonTest {

    private static final Path WOODS = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "wizards_and_beasts", "wand_woods");
    private static final Path CORES = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "wizards_and_beasts", "wand_cores");

    private static List<Path> jsonFiles(Path dir) throws IOException {
        assertTrue(Files.isDirectory(dir), "definition directory is missing: " + dir);
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> found = files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
            assertFalse(found.isEmpty(), "no definitions found under " + dir);
            return found;
        }
    }

    /**
     * core id -> its authored contribution. Three cores are absent on purpose and must stay absent
     * until their values are ruled on: {@code troll_whisker} has a definition but no authored block,
     * and {@code rougarou_hair} / {@code white_river_monster_spine} have no definition file at all.
     * All three are carried by {@code WandStatsResolver}'s temporary enum fallback.
     *
     * <p>Category bonuses specified as Transfiguration (thunderbird) and Charms (veela) are omitted:
     * {@code SpellCategory} has no counterpart for either.
     */
    private static final Map<String, WandCastModifiers> EXPECTED_CORES = Map.of(
            "dragon_heartstring", new WandCastModifiers(1.25f, 1.05f, 1.00f, 0.03f, Map.of()),
            "phoenix_feather", new WandCastModifiers(1.05f, 0.95f, 1.25f, 0.00f, Map.of()),
            "unicorn_hair", new WandCastModifiers(0.88f, 0.88f, 1.00f, -0.06f, Map.of()),
            "thunderbird_tail_feather", new WandCastModifiers(1.15f, 0.95f, 1.10f, 0.04f, Map.of()),
            "wampus_cat_hair", new WandCastModifiers(1.20f, 1.00f, 1.00f, 0.05f, Map.of()),
            "veela_hair", new WandCastModifiers(1.15f, 1.05f, 1.00f, 0.06f, Map.of()),
            "thestral_tail_hair", new WandCastModifiers(1.22f, 1.00f, 1.10f, 0.05f, Map.of()));

    /** The counterpart of {@code WandWoodCastModifierTest} for cores. */
    @Test
    void everyAuthoredCoreContributesItsAuthoredValues() throws IOException {
        for (Path file : jsonFiles(CORES)) {
            String id = file.getFileName().toString().replace(".json", "");
            WandCastModifiers expected = EXPECTED_CORES.get(id);
            if (expected == null) {
                continue;
            }
            WandCoreDefinition def = WandCoreDefinition.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                    .getOrThrow(AssertionError::new);
            assertEquals(expected, def.castModifiers(),
                    id + " no longer contributes its authored values — if that was a balance change, "
                            + "update this table deliberately.");
        }
    }

    /** {@code troll_whisker} must stay unauthored until its values are ruled on. */
    @Test
    void trollWhiskerRemainsUnauthored() throws IOException {
        Path file = CORES.resolve("troll_whisker.json");
        assertTrue(Files.exists(file), "troll_whisker.json is gone; update this test if deliberate");
        WandCoreDefinition def = WandCoreDefinition.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                .getOrThrow(AssertionError::new);
        assertTrue(def.castModifiers().isNeutral(),
                "troll_whisker now carries cast_modifiers. That is fine — but WandStatsResolver's "
                        + "enum fallback exists for the cores that do not, so check whether the "
                        + "fallback can now go.");
    }

    @Test
    void everyCoreDefinitionDecodes() throws IOException {
        for (Path file : jsonFiles(CORES)) {
            JsonElement json = JsonParser.parseString(Files.readString(file));
            WandCoreDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(error -> new AssertionError(file.getFileName() + " failed to decode: " + error));
        }
    }

    /**
     * A definition carrying a {@code cast_modifiers} block must actually contribute something.
     * An all-identity block decodes cleanly and then reads as {@code isNeutral()}, which the resolver
     * skips for wood and treats as "not authored" for core — so authoring one is indistinguishable
     * from authoring nothing, and is almost certainly a mistake rather than an intent.
     */
    @Test
    void anAuthoredCastModifiersBlockIsNeverNeutral() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : jsonFiles(WOODS)) {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!json.has("cast_modifiers")) {
                continue;
            }
            WandWoodDefinition def = WandWoodDefinition.CODEC
                    .parse(JsonOps.INSTANCE, json).getOrThrow(AssertionError::new);
            if (def.castModifiers().isNeutral()) {
                offenders.add(file.getFileName().toString());
            }
        }
        for (Path file : jsonFiles(CORES)) {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!json.has("cast_modifiers")) {
                continue;
            }
            WandCoreDefinition def = WandCoreDefinition.CODEC
                    .parse(JsonOps.INSTANCE, json).getOrThrow(AssertionError::new);
            if (def.castModifiers().isNeutral()) {
                offenders.add(file.getFileName().toString());
            }
        }
        assertEquals(List.of(), offenders,
                "these carry a cast_modifiers block that resolves to neutral, so it does nothing");
    }

    /** Negative category bonuses are legal and load-bearing — rowan's Dark Arts penalty is one. */
    @Test
    void aNegativeCategoryBonusSurvivesDecoding() throws IOException {
        Path rowan = WOODS.resolve("rowan.json");
        assertTrue(Files.exists(rowan), "rowan.json is gone; update this test if that was deliberate");
        WandWoodDefinition def = WandWoodDefinition.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(rowan)))
                .getOrThrow(AssertionError::new);

        Float darkArts = def.castModifiers().categoryDamageBonus()
                .get(at.koopro.wizardsandbeasts.spell.core.SpellCategory.DARK_ARTS);
        assertTrue(darkArts != null && darkArts < 0.0f,
                "rowan's negative Dark Arts bonus did not survive the codec — if negatives are being "
                        + "dropped or clamped, every penalty in the tuning table is silently absent");
    }
}
