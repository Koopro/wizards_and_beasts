package at.koopro.wizardsandbeasts.creature;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The client learns every creature through {@code CreatureDefinitionsSyncS2CPayload}, which sends each definition
 * through {@link CreatureDefinition#CODEC} as NBT. Before that payload existed the client side of a dedicated server
 * had no definitions at all, and no generic creature played a single attack, hit, death or idle clip (Beast
 * Renaissance D1). These tests pin the two things the payload relies on:
 * <ul>
 *   <li>every shipped definition survives encode → decode unchanged, so what the client animates from is what the
 *       server decided from;</li>
 *   <li>the {@code id} inside each file is the file's own id, because the client keys its table by {@code id()}
 *       while the server keys it by file name.</li>
 * </ul>
 */
class CreatureDefinitionSyncRoundTripTest {

    private static final Gson GSON = new Gson();
    private static final Path CREATURES_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "creatures");

    @Test
    void everyShippedDefinitionSurvivesTheWire() throws IOException {
        for (Path json : shippedJsons()) {
            CreatureDefinition sent = decodeJson(json);
            Tag wire = CreatureDefinition.CODEC.encodeStart(NbtOps.INSTANCE, sent)
                    .getOrThrow(error -> new AssertionError(json.getFileName() + " does not encode: " + error));
            CreatureDefinition received = CreatureDefinition.CODEC.parse(NbtOps.INSTANCE, wire)
                    .getOrThrow(error -> new AssertionError(json.getFileName() + " does not decode back: " + error));
            assertEquals(sent, received, json.getFileName() + " changed on the way to the client");
            assertEquals(sent.clips(), received.clips(), json.getFileName() + " lost clips on the way to the client");
        }
    }

    @Test
    void everyDefinitionIdMatchesItsFile() throws IOException {
        for (Path json : shippedJsons()) {
            String file = json.getFileName().toString().replace(".json", "");
            CreatureDefinition def = decodeJson(json);
            assertEquals("wizards_and_beasts:" + file, def.id().toString(),
                    json.getFileName() + " declares a different id, so the client would file it under the wrong key");
        }
    }

    private static List<Path> shippedJsons() throws IOException {
        try (Stream<Path> files = Files.list(CREATURES_DIR)) {
            List<Path> jsons = files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
            assertFalse(jsons.isEmpty(), "expected creature JSONs on disk");
            return jsons;
        }
    }

    private static CreatureDefinition decodeJson(Path json) throws IOException {
        JsonElement element = GSON.fromJson(Files.readString(json), JsonElement.class);
        return CreatureDefinition.CODEC.parse(JsonOps.INSTANCE, element)
                .getOrThrow(error -> new AssertionError(json.getFileName() + " does not parse: " + error));
    }
}
