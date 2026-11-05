package at.koopro.wizardsandbeasts.creature.bond;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every mod sound a bond profile names is a sound the mod registers. The Niffler's named {@code niffler_eat} and
 * {@code niffler_hiss}, while the events are {@code entity.niffler.eat} and {@code entity.niffler.hiss}: an unknown
 * id resolves to nothing, so feeding and refusing a niffler were silent (Beast Renaissance D9).
 */
class BondSoundIdsTest {

    private static final Path BONDS = Path.of("src/main/resources/data/wizards_and_beasts/creature_bonds");
    private static final Path SOUNDS = Path.of("src/main/resources/assets/wizards_and_beasts/sounds.json");

    @Test
    void everyModBondSoundIsRegistered() throws IOException {
        JsonObject sounds = JsonParser.parseString(Files.readString(SOUNDS)).getAsJsonObject();
        List<Path> files;
        try (Stream<Path> list = Files.list(BONDS)) {
            files = list.filter(p -> p.toString().endsWith(".json")).toList();
        }
        assertFalse(files.isEmpty());
        for (Path file : files) {
            JsonObject bond = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (String key : new String[] {"feedSound", "refuseSound"}) {
                if (!bond.has(key)) {
                    continue;
                }
                String id = bond.get(key).getAsString();
                if (id.startsWith("wizards_and_beasts:")) {
                    String path = id.substring("wizards_and_beasts:".length());
                    assertTrue(sounds.has(path), file.getFileName() + " " + key + " names " + id
                            + ", which sounds.json does not define");
                }
            }
        }
    }
}
