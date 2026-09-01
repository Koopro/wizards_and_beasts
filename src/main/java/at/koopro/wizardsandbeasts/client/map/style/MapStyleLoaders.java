package at.koopro.wizardsandbeasts.client.map.style;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.Map;

/**
 * The two client-side reload listeners behind the map's look.
 *
 * <p>Both live on the <em>resource</em> reload cycle rather than the datapack one. Everything they
 * load is appearance, so a texture pack that wants the map drawn in sepia, or wants a mod's biomes
 * charted properly, should be able to say so without shipping a datapack and without the server
 * agreeing.
 */
public final class MapStyleLoaders {

    private static final Logger LOGGER = LogUtils.getLogger();

    private MapStyleLoaders() {
    }

    public static final class Biomes extends SimpleJsonResourceReloadListener<MapBiomeStyle> {
        public static final String DIRECTORY = "map_biome_style";

        public Biomes() {
            super(MapBiomeStyle.CODEC, FileToIdConverter.json(DIRECTORY));
        }

        @Override
        protected void apply(Map<Identifier, MapBiomeStyle> map, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            MapStyles.replaceBiomeStyles(map);
            LOGGER.info("MapBiomeStyleLoader: {} files covering {} biomes.",
                    map.size(), MapStyles.biomeStyleCount());
        }
    }

    public static final class Markers extends SimpleJsonResourceReloadListener<MapMarkerStyle> {
        public static final String DIRECTORY = "map_marker_style";

        public Markers() {
            super(MapMarkerStyle.CODEC, FileToIdConverter.json(DIRECTORY));
        }

        @Override
        protected void apply(Map<Identifier, MapMarkerStyle> map, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            MapStyles.replaceMarkerStyles(map);
            LOGGER.info("MapMarkerStyleLoader: {} marker styles.", MapStyles.markerStyleCount());
        }
    }
}
