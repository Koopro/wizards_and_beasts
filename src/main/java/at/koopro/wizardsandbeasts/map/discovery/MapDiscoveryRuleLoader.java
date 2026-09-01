package at.koopro.wizardsandbeasts.map.discovery;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Loads {@link MapDiscoveryRule}s from {@code data/<ns>/map_discovery/*.json} on every datapack
 * reload, mirroring {@code PocketTemplateLoader}.
 *
 * <p>Rules are keyed by file id and never referenced by id from anywhere else, so unlike the pocket
 * templates there is no second identity to keep in sync — a pack overrides a shipped rule by
 * writing a file at the same path, and disables one by writing an empty override.
 */
public final class MapDiscoveryRuleLoader extends SimpleJsonResourceReloadListener<MapDiscoveryRule> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String DIRECTORY = "map_discovery";

    public MapDiscoveryRuleLoader() {
        super(MapDiscoveryRule.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, MapDiscoveryRule> map,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        MapDiscoveryRules.replaceAll(map);
        LOGGER.info("MapDiscoveryRuleLoader: {} structure rules, {} landmark rules.",
                MapDiscoveryRules.structures().size(), MapDiscoveryRules.blocks().size());
    }
}
