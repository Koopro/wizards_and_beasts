package at.koopro.wizardsandbeasts.brew.amortentia;

import at.koopro.wizardsandbeasts.house.House;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** C1: Amortentia smells different to each person (HBP ch.9), and the same to the same person every time. */
class AmortentiaScentTest {

    @Test
    void eachWizardSmellsTheirOwnThreeThings() {
        UUID harry = UUID.nameUUIDFromBytes("harry".getBytes());
        UUID hermione = UUID.nameUUIDFromBytes("hermione".getBytes());
        List<Amortentia.Scent> once = Amortentia.scents(harry, House.GRYFFINDOR);
        assertEquals(once, Amortentia.scents(harry, House.GRYFFINDOR), "the same wizard smelled something else");
        assertEquals(3, once.size());
        assertEquals(3, new HashSet<>(once).size(), "a scent repeated: " + once);
        assertTrue(once.get(0).key().endsWith("scent.house.gryffindor"), "the house comes first: " + once);
        assertNotEquals(once, Amortentia.scents(hermione, House.GRYFFINDOR), "two wizards smelled the same");
        assertEquals(3, Amortentia.scents(harry, null).size(), "an unsorted wizard smells three pool lines");
    }

    @Test
    void everyScentIsWritten() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json"))).getAsJsonObject();
        for (int i = 0; i < Amortentia.POOL; i++) {
            assertTrue(lang.has("amortentia.wizards_and_beasts.scent." + i), "pool scent " + i);
        }
        for (House house : House.values()) {
            String key = Amortentia.scents(UUID.randomUUID(), house).get(0).key();
            assertTrue(lang.has(key), key);
        }
    }
}
