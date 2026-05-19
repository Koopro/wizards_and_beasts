package at.koopro.wizardsandbeasts.resources;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetModelParityTest {
    private static final Pattern MODEL_REF = Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    void generatedItemDefinitions_referenceExistingModels() throws IOException {
        Path generatedItems = Path.of("src", "generated", "resources", "assets", "wizards_and_beasts", "items");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.list(generatedItems)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(path -> {
                try {
                    String json = Files.readString(path);
                    Matcher m = MODEL_REF.matcher(json);
                    while (m.find()) {
                        String modelId = m.group(1);
                        Path modelPath = modelPathFor(modelId);
                        if (!Files.exists(modelPath)) {
                            missing.add(path + " -> " + modelId + " -> " + modelPath);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertTrue(missing.isEmpty(),
                "Generated item definitions reference missing models:\n" + String.join("\n", missing));
    }

    private static Path modelPathFor(String modelId) {
        String[] parts = modelId.split(":", 2);
        String namespace = parts.length == 2 ? parts[0] : "minecraft";
        String localPath = parts.length == 2 ? parts[1] : parts[0];
        Path main = Path.of("src", "main", "resources", "assets", namespace, "models", localPath + ".json");
        if (Files.exists(main)) return main;
        return Path.of("src", "generated", "resources", "assets", namespace, "models", localPath + ".json");
    }
}
