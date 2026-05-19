package at.koopro.wizardsandbeasts.wand.ollivander;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Loads {@code data/<modid>/ollivander_pool/*.json} at server start (at minimum {@code standard_pool.json}).
 */
public final class OllivanderPoolLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static List<OllivanderPoolEntry> cached = List.of();

    private OllivanderPoolLoader() {
    }

    public static List<OllivanderPoolEntry> getPool(RegistryAccess registryAccess) {
        return cached;
    }

    public static void load(MinecraftServer server) {
        List<OllivanderPoolEntry> merged = new ArrayList<>();
        ResourceManager manager = server.getResourceManager();
        loadFile(manager, Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ollivander_pool/standard_pool.json"), merged);
        if (merged.isEmpty()) {
            LOGGER.warn("Ollivander pool empty after load — check datapack path data/{}/ollivander_pool/", WizardsAndBeastsMod.MODID);
        }
        cached = Collections.unmodifiableList(merged);
        LOGGER.info("Loaded {} Ollivander pool entries.", cached.size());
    }

    private static void loadFile(ResourceManager manager, Identifier id, List<OllivanderPoolEntry> merged) {
        Optional<Resource> resource = manager.getResource(id);
        if (resource.isEmpty()) {
            LOGGER.warn("Missing Ollivander pool file {}", id);
            return;
        }
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("entries")) {
                return;
            }
            JsonArray arr = root.getAsJsonArray("entries");
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                float weight = o.get("weight").getAsFloat();
                Identifier wood = Identifier.parse(o.get("wood_key").getAsString());
                Identifier core = Identifier.parse(o.get("core_key").getAsString());
                String flex = o.get("flexibility").getAsString();
                int minLv = o.has("minimum_player_level") ? o.get("minimum_player_level").getAsInt() : 0;
                merged.add(new OllivanderPoolEntry(weight, wood, core, flex, minLv));
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to load Ollivander pool {}", id, ex);
        }
    }
}
