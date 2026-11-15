package at.koopro.wizardsandbeasts.item.rarity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every id in the rarity table names a real item: a typo would silently leave an artefact white. */
class ItemRaritiesTest {

    @Test
    void everyEntryIsARealItem() throws IOException {
        Path lang = Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang", "en_us.json");
        JsonObject names = JsonParser.parseString(Files.readString(lang, StandardCharsets.UTF_8)).getAsJsonObject();
        for (String id : ItemRarities.table().keySet()) {
            assertTrue(names.has("item.wizards_and_beasts." + id), "no item named " + id);
        }
    }
}
