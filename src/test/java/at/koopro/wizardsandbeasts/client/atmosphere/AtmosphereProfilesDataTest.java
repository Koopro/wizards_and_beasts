package at.koopro.wizardsandbeasts.client.atmosphere;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped atmosphere profile parses, and everything it names exists: its particles are the mod's own, its sounds
 * have a sounds.json entry whose files are on disk, and its block and biome tags are shipped. A profile that names a
 * missing sound or tag fails silently in game (no sound, a place never recognised), so it is caught here.
 */
class AtmosphereProfilesDataTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/wizards_and_beasts");
    private static final Path RESOURCES = Path.of("src/main/resources");

    private static JsonObject read(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<Path> profiles() throws IOException {
        try (Stream<Path> files = Files.list(ASSETS.resolve("atmosphere"))) {
            return files.filter(f -> f.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static AtmosphereProfile parse(Path file) throws IOException {
        return AtmosphereProfile.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))).getOrThrow();
    }

    @Test
    void everyProfileParsesAndNamesOnlyWhatExists() throws IOException {
        List<Path> files = profiles();
        assertFalse(files.isEmpty());
        JsonObject sounds = read(ASSETS.resolve("sounds.json"));
        List<String> failures = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            var result = AtmosphereProfile.CODEC.parse(JsonOps.INSTANCE, json);
            if (result.error().isPresent()) {
                failures.add(name + ": " + result.error().get().message());
                continue;
            }
            AtmosphereProfile p = result.getOrThrow();
            for (var e : p.emitters()) {
                if (!AtmosphereParticles.known(e.particle())) {
                    failures.add(name + ": unknown particle " + e.particle());
                }
            }
            List<Identifier> named = new ArrayList<>();
            p.sounds().forEach(c -> named.add(c.sound()));
            p.bed().ifPresent(b -> named.add(b.sound()));
            for (Identifier id : named) {
                if (!id.getNamespace().equals("wizards_and_beasts")) {
                    continue;
                }
                if (!sounds.has(id.getPath())) {
                    failures.add(name + ": sound " + id + " has no sounds.json entry");
                    continue;
                }
                for (JsonElement s : sounds.getAsJsonObject(id.getPath()).getAsJsonArray("sounds")) {
                    String sample = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                    String prefix = "wizards_and_beasts:";
                    if (sample.startsWith(prefix)
                            && !Files.exists(ASSETS.resolve("sounds/" + sample.substring(prefix.length()) + ".ogg"))) {
                        failures.add(name + ": " + id + " -> missing " + sample + ".ogg");
                    }
                }
            }
            p.blocks().forEach(r -> tag(failures, name, "block", r.tag().location()));
            p.biome().ifPresent(b -> tag(failures, name, "worldgen/biome", b.location()));
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static void tag(List<String> failures, String profile, String registry, Identifier id) {
        if (id.getNamespace().equals("minecraft")) {
            return;
        }
        Path p = RESOURCES.resolve("data/" + id.getNamespace() + "/tags/" + registry + "/" + id.getPath() + ".json");
        if (!Files.exists(p)) {
            failures.add(profile + ": tag #" + id + " (" + registry + ") is not shipped");
        }
    }

    @Test
    void oneShotsHaveSubtitlesAndDoNotNag() throws IOException {
        JsonObject lang = read(ASSETS.resolve("lang/en_us.json"));
        JsonObject sounds = read(ASSETS.resolve("sounds.json"));
        List<String> failures = new ArrayList<>();
        for (Path file : profiles()) {
            AtmosphereProfile p = parse(file);
            for (var cue : p.sounds()) {
                JsonObject entry = sounds.getAsJsonObject(cue.sound().getPath());
                if (entry == null || !entry.has("subtitle") || !lang.has(entry.get("subtitle").getAsString())) {
                    failures.add(file.getFileName() + ": one-shot " + cue.sound() + " needs a translated subtitle");
                }
                if (cue.interval().min() < 4f) {
                    failures.add(file.getFileName() + ": " + cue.sound() + " repeats faster than every 4 s");
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }
}
