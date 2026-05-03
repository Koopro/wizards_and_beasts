package at.koopro.wizardsandbeasts.client.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Exports and imports debug transform data to/from a JSON config file.
 * File location: {@code config/<modId>/creature_transforms.json}
 */
public final class DebugConfigExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugConfigExporter.class);
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(WizardsAndBeastsMod.MODID + "/creature_transforms.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DebugConfigExporter() {}

    public static void save(Map<String, Map<String, DebugTransformData>> allData) {
        JsonObject root = new JsonObject();
        for (var creatureEntry : allData.entrySet()) {
            JsonObject creature = new JsonObject();
            JsonObject parts = new JsonObject();
            for (var partEntry : creatureEntry.getValue().entrySet()) {
                DebugTransformData d = partEntry.getValue();
                if (!d.isDefault()) {
                    parts.add(partEntry.getKey(), d.toJson());
                }
            }
            if (parts.size() > 0) {
                creature.addProperty("model_mode",
                        ModelDebugEditor.get().getModelMode().name());
                creature.add("parts", parts);
                root.add(creatureEntry.getKey(), creature);
            }
        }
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(root));
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("\u00a7a[Debug] Exported transforms to creature_transforms.json"), false);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save debug transforms", e);
        }
    }

    public static Map<String, Map<String, DebugTransformData>> load() {
        Map<String, Map<String, DebugTransformData>> result = new HashMap<>();
        if (!Files.exists(CONFIG_PATH)) return result;
        try {
            String json = Files.readString(CONFIG_PATH);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (var creatureEntry : root.entrySet()) {
                Map<String, DebugTransformData> parts = new HashMap<>();
                JsonObject creatureObj = creatureEntry.getValue().getAsJsonObject();
                JsonObject partsObj = creatureObj.getAsJsonObject("parts");
                if (partsObj != null) {
                    for (var partEntry : partsObj.entrySet()) {
                        parts.put(partEntry.getKey(),
                                DebugTransformData.fromJson(partEntry.getValue().getAsJsonObject()));
                    }
                }
                result.put(creatureEntry.getKey(), parts);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load debug transforms", e);
        }
        return result;
    }
}
