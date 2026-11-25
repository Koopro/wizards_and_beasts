package at.koopro.wizardsandbeasts.settlement.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three small canon places (post-canon D3: Stoatshead Hill, the Gaunt shack, the Lovegoods' house) are wired from
 * structure set to template: set names the structure, the structure's biome tag and start pool exist, the pool's one
 * element is the template on disk. A broken link generates nothing and says nothing. The Gaunt chest carries the loot
 * table its treasure site wards. Real-terrain generation is checked by the settlement probe's {@code places} mode.
 */
class CanonPlacesDataTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final List<String> PLACES = List.of("portkey_hill", "gaunt_shack", "rook_house");

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Path tag(String ref) {
        return DATA.resolve("tags/worldgen/biome/" + ref.substring(ref.indexOf(':') + 1) + ".json");
    }

    @Test
    void everyPlaceIsWiredFromItsSetToItsTemplate() throws IOException {
        for (String place : PLACES) {
            JsonObject set = read(DATA.resolve("worldgen/structure_set/" + place + ".json"));
            assertEquals("wizards_and_beasts:" + place,
                    set.getAsJsonArray("structures").get(0).getAsJsonObject().get("structure").getAsString(), place);
            JsonObject placement = set.getAsJsonObject("placement");
            if (placement.has("target_biomes")) {
                assertTrue(Files.exists(tag(placement.get("target_biomes").getAsString().substring(1))),
                        place + ": placement biome tag missing");
            }
            JsonObject structure = read(DATA.resolve("worldgen/structure/" + place + ".json"));
            assertTrue(Files.exists(tag(structure.get("biomes").getAsString().substring(1))),
                    place + ": structure biome tag missing");
            String pool = structure.get("start_pool").getAsString();
            JsonObject poolJson = read(DATA.resolve("worldgen/template_pool/" + pool.substring(pool.indexOf(':') + 1)
                    + ".json"));
            String location = poolJson.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("element")
                    .get("location").getAsString();
            assertEquals("wizards_and_beasts:places/" + place + "/a", location, place);
            assertTrue(Files.exists(DATA.resolve("structure/places/" + place + "/a.nbt")), place + ": template missing");
        }
    }

    @Test
    void theGauntChestIsTheWardedTreasureSite() throws IOException {
        String table = "wizards_and_beasts:chests/gaunt_shack";
        assertTrue(Files.exists(DATA.resolve("loot_table/chests/gaunt_shack.json")));
        JsonObject site = read(DATA.resolve("treasure_sites/gaunt_shack.json"));
        assertEquals(table, site.getAsJsonArray("loot_tables").get(0).getAsString());
        assertEquals(1.0, site.get("chance").getAsDouble(), "HBP ch.23: the hiding place is always cursed");
        assertTrue(Files.exists(DATA.resolve("tags/worldgen/structure/treasure_site/gaunt_shack.json")));
        // The template's chest names the table (NBT strings are stored as plain UTF-8 inside the gzip).
        try (InputStream in = new GZIPInputStream(Files.newInputStream(DATA.resolve("structure/places/gaunt_shack/a.nbt")))) {
            String raw = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(raw.contains(table), "the shack's chest does not carry " + table);
        }
    }
}
