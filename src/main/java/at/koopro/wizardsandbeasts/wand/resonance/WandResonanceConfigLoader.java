package at.koopro.wizardsandbeasts.wand.resonance;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.google.gson.Gson;
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
import java.util.Optional;

/**
 * Loads {@code data/<modid>/wand_resonance_config.json} once per server start.
 */
public final class WandResonanceConfigLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static WandResonanceConfig cached = WandResonanceConfig.DEFAULT;

    private WandResonanceConfigLoader() {
    }

    public static WandResonanceConfig getConfig(RegistryAccess registryAccess) {
        return cached;
    }

    public static void load(MinecraftServer server) {
        cached = read(server.getResourceManager()).orElse(WandResonanceConfig.DEFAULT);
        LOGGER.info("Loaded wand resonance config (match threshold {}).", cached.matchThreshold());
    }

    private static Optional<WandResonanceConfig> read(ResourceManager manager) {
        Identifier id = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wand_resonance_config.json");
        Optional<Resource> resource = manager.getResource(id);
        if (resource.isEmpty()) {
            LOGGER.warn("Missing wand resonance config at {}, using defaults.", id);
            return Optional.empty();
        }
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject o = GSON.fromJson(reader, JsonObject.class);
            if (o == null) {
                return Optional.empty();
            }
            WandResonanceConfig d = WandResonanceConfig.DEFAULT;
            float match = o.has("match_threshold") ? o.get("match_threshold").getAsFloat() : d.matchThreshold();
            float refuse = o.has("refuse_threshold") ? o.get("refuse_threshold").getAsFloat() : d.refuseThreshold();
            int radius = o.has("scan_radius") ? o.get("scan_radius").getAsInt() : d.scanRadius();
            float ww = o.has("wood_weight") ? o.get("wood_weight").getAsFloat() : d.woodWeight();
            float cw = o.has("core_weight") ? o.get("core_weight").getAsFloat() : d.coreWeight();
            float lw = o.has("length_weight") ? o.get("length_weight").getAsFloat() : d.lengthWeight();
            float fw = o.has("flexibility_weight") ? o.get("flexibility_weight").getAsFloat() : d.flexibilityWeight();
            float cdb = o.has("corruption_dark_bonus_threshold")
                    ? o.get("corruption_dark_bonus_threshold").getAsFloat()
                    : d.corruptionDarkBonusThreshold();
            return Optional.of(new WandResonanceConfig(match, refuse, radius, ww, cw, lw, fw, cdb));
        } catch (Exception e) {
            LOGGER.error("Failed to read wand resonance config", e);
            return Optional.empty();
        }
    }
}
