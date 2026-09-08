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
 *
 * <p>Scope: this file guards decoding only. What each wood and core is <em>tuned to</em> is pinned in
 * {@link WandWoodCastModifierTest} and {@link WandCoreCastModifierTest}; the per-core table and the
 * test asserting {@code troll_whisker} stayed unauthored both lived here until the last three cores
 * were authored and {@code WandStatsResolver}'s enum fallback was deleted.
 */
class ShippedWandDefinitionJsonTest {

    private static final Path WOODS = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "wizards_and_beasts", "wand_woods");
    private static final Path CORES = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "wizards_and_beasts", "wand_cores");
    /**
     * The hand-authored half only. {@code processResources} merges this with the generated file, but
     * every key these definitions name is authored by hand, so a miss here is a real miss.
     */
    private static final Path EN_US = Path.of("src", "main", "resources",
            "assets", "wizards_and_beasts", "lang", "en_us.json");

    private static List<Path> jsonFiles(Path dir) throws IOException {
        assertTrue(Files.isDirectory(dir), "definition directory is missing: " + dir);
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> found = files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
            assertFalse(found.isEmpty(), "no definitions found under " + dir);
            return found;
        }
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

    /**
     * Every {@code display_name} names a translation key that exists.
     *
     * <p>{@code WandLoreNames} is what turns these into the wood and core lines on a wand's tooltip and
     * on Ollivander's trial cards. It has two fallbacks — a title-cased id, then a literal — and neither
     * catches this one: a {@code display_name} that decodes fine but names a key no language file has
     * resolves to a {@link net.minecraft.network.chat.Component} the tooltip renders as the raw key,
     * {@code wand_core.wizards_and_beasts.rougarou_hair}, straight across the card.
     *
     * <p>{@code LangParityTest} does not reach these: it scans Java for literal
     * {@code Component.translatable(…)} calls, and these keys live in JSON.
     */
    @Test
    void everyDisplayNameKeyIsTranslated() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(EN_US)).getAsJsonObject();
        List<String> missing = new ArrayList<>();
        for (Path dir : List.of(WOODS, CORES)) {
            for (Path file : jsonFiles(dir)) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                JsonObject displayName = json.getAsJsonObject("display_name");
                if (displayName == null || !displayName.has("translate")) {
                    continue;
                }
                String key = displayName.get("translate").getAsString();
                if (!lang.has(key)) {
                    missing.add(file.getFileName() + " -> " + key);
                }
            }
        }
        assertEquals(List.of(), missing,
                "these definitions name a translation key en_us does not have, so the wand tooltip "
                        + "and Ollivander's trial cards would print the raw key: " + missing);
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
