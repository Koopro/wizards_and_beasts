package at.koopro.wizardsandbeasts.client.entity.life;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rig side of {@link CreatureLifePass}'s contract: lids come in pairs, sit in front of a head, and no clip keys
 * them (the pass owns them, and a clip keying their scale would fight the blink every frame).
 */
class CreatureLifeRigTest {

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "geckolib");
    private static final Path RIGS = ASSETS.resolve(Path.of("models", "entity"));
    private static final Path ANIMS = ASSETS.resolve(Path.of("animations", "entity"));

    @Test
    void lidsComeInPairsUnderAHeadAndNoClipKeysThem() throws IOException {
        List<String> problems = new ArrayList<>();
        int withLids = 0;
        try (var files = Files.list(RIGS)) {
            for (Path rig : files.filter(p -> p.toString().endsWith(".geo.json")).sorted().toList()) {
                String id = rig.getFileName().toString().replace(".geo.json", "");
                Set<String> bones = new HashSet<>();
                Set<String> lidParents = new HashSet<>();
                JsonArray geometry = JsonParser.parseString(Files.readString(rig)).getAsJsonObject()
                        .getAsJsonArray("minecraft:geometry");
                for (JsonElement geo : geometry) {
                    JsonArray list = geo.getAsJsonObject().getAsJsonArray("bones");
                    if (list == null) continue;
                    for (JsonElement b : list) {
                        JsonObject bone = b.getAsJsonObject();
                        String name = bone.get("name").getAsString();
                        bones.add(name);
                        if (name.startsWith("eyelid_") && bone.has("parent")) {
                            lidParents.add(bone.get("parent").getAsString());
                        }
                    }
                }
                boolean left = bones.contains("eyelid_left");
                boolean right = bones.contains("eyelid_right");
                if (!left && !right) continue;
                withLids++;
                if (left != right) problems.add(id + ": has one eyelid bone, not a pair");
                if (!bones.contains("head")) problems.add(id + ": has lids but no head bone");
                for (String parent : lidParents) {
                    if (!parent.equals("head")) problems.add(id + ": a lid hangs off " + parent + ", not head");
                }
                Path anim = ANIMS.resolve(id + ".animation.json");
                if (Files.exists(anim)) {
                    JsonObject clips = JsonParser.parseString(Files.readString(anim)).getAsJsonObject()
                            .getAsJsonObject("animations");
                    for (String clip : clips.keySet()) {
                        JsonObject keyed = clips.getAsJsonObject(clip).getAsJsonObject("bones");
                        if (keyed == null) continue;
                        for (String bone : keyed.keySet()) {
                            if (bone.startsWith("eyelid_")) problems.add(id + ": " + clip + " keys " + bone);
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(withLids >= 1, "the Demiguise pilot ships lids");
    }
}
