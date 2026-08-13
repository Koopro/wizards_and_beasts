package at.koopro.wizardsandbeasts.heritage.appearance;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped {@code heritage_appearance} JSON parses, round-trips, and says something true.
 *
 * <p>The codec tests cover the schema; this covers the content. A datapack entry that decodes fine
 * but names a form the mod does not register, or claims a variant twice, fails at render time as a
 * missing model or a silently ignored file — neither of which points at the JSON that caused it.
 */
class ShippedHeritageAppearanceJsonTest {

    private static final Path DIR = Path.of(
            "src", "main", "resources", "data", "wizards_and_beasts", "heritage_appearance");

    /**
     * Form ids the mod registers. Deliberately duplicated from {@code FormRegistry} rather than read
     * from it: that class is common code but its static initialiser builds {@code Identifier}s, and
     * the point here is to catch a typo in a JSON, which a hardcoded list does just as well while
     * staying a pure parsing test.
     */
    private static final Set<String> KNOWN_FORM_IDS = Set.of(
            "human_default",
            "werewolf_human", "werewolf_wolf",
            "obscurial_human", "obscurial_dark",
            "goblin_default",
            "house_elf_default",
            "veela_human", "veela_harpy",
            "giant_default", "half_giant_default",
            "centaur_default",
            "vampire_human", "vampire_bat",
            "merfolk_land", "merfolk_water",
            "animagus_cat", "animagus_dog", "animagus_stag",
            "animagus_hawk", "animagus_hare", "animagus_beetle");

    private static List<Path> shippedJson() throws IOException {
        try (Stream<Path> files = Files.list(DIR)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static List<HeritageAppearance> loadAll() throws IOException {
        List<HeritageAppearance> entries = new ArrayList<>();
        for (Path json : shippedJson()) {
            String name = json.getFileName().toString();
            JsonElement source = JsonParser.parseString(Files.readString(json));
            entries.add(HeritageAppearance.CODEC.parse(JsonOps.INSTANCE, source)
                    .getOrThrow(msg -> new AssertionError(name + " failed to decode: " + msg)));
        }
        return entries;
    }

    @Test
    void everyShippedJson_roundTrips() throws IOException {
        List<Path> jsons = shippedJson();
        assertFalse(jsons.isEmpty(), "expected shipped heritage appearance entries on disk");

        for (Path json : jsons) {
            String name = json.getFileName().toString();
            JsonElement source = JsonParser.parseString(Files.readString(json));

            HeritageAppearance decoded = HeritageAppearance.CODEC.parse(JsonOps.INSTANCE, source)
                    .getOrThrow(msg -> new AssertionError(name + " failed to decode: " + msg));
            JsonElement reencoded = HeritageAppearance.CODEC.encodeStart(JsonOps.INSTANCE, decoded)
                    .getOrThrow(msg -> new AssertionError(name + " failed to encode: " + msg));
            HeritageAppearance again = HeritageAppearance.CODEC.parse(JsonOps.INSTANCE, reencoded)
                    .getOrThrow(msg -> new AssertionError(name + " failed to re-decode: " + msg));

            assertEquals(decoded, again, name + " did not survive a codec round trip");
        }
    }

    @Test
    void everyDeclaredFormId_isARegisteredForm() throws IOException {
        for (HeritageAppearance entry : loadAll()) {
            FormAppearance form = entry.form();
            if (form == null) {
                continue;
            }
            assertTrue(KNOWN_FORM_IDS.contains(form.formId()),
                    entry.id() + " declares form '" + form.formId() + "', which no form registry entry provides");
        }
    }

    @Test
    void noTwoEntries_claimTheSameHeritageOrVariant() throws IOException {
        Set<String> heritages = new HashSet<>();
        Set<String> variants = new HashSet<>();

        for (HeritageAppearance entry : loadAll()) {
            if (entry.variant().isPresent()) {
                assertTrue(variants.add(entry.variant().get()),
                        "two entries claim variant '" + entry.variant().get() + "'; one silently wins");
            } else {
                assertTrue(heritages.add(entry.heritage()),
                        "two entries claim heritage '" + entry.heritage() + "'; one silently wins");
            }
        }
    }

    /**
     * Provenance has to be real. The codec already rejects a blank citation and the both/neither
     * cases, so what is left to check is that nothing shipped as fan-extrapolation without that being
     * a deliberate call — currently nothing has.
     */
    @Test
    void everyShippedEntry_carriesACitationRatherThanAnExtrapolationFlag() throws IOException {
        for (HeritageAppearance entry : loadAll()) {
            assertFalse(entry.provenance().fanExtrapolation(),
                    entry.id() + " ships as fan-extrapolation; if that is intended, record it in "
                            + "MIGRATION_DELTAS.md and relax this test deliberately");
            assertTrue(entry.provenance().citation().isPresent(), entry.id() + " has no citation");
        }
    }

    /** A shipped entry with no mechanisms is legal but pointless — it is a file that does nothing. */
    @Test
    void noShippedEntry_isEmpty() throws IOException {
        for (HeritageAppearance entry : loadAll()) {
            assertFalse(entry.isEmpty(),
                    entry.id() + " declares no mechanisms, so the file has no effect; delete it or fill it in");
        }
    }
}
