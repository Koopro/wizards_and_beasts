package at.koopro.wizardsandbeasts.client.atmosphere;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@code assets/<ns>/atmosphere/*.json} on the resource reload cycle: a place's air is appearance, so a
 * resource pack may change it without a datapack and without the server agreeing. Emitters naming a particle the
 * mod does not offer are dropped with a warning; the rest of the profile still loads.
 */
@NullMarked
public final class AtmosphereProfiles extends SimpleJsonResourceReloadListener<AtmosphereProfile> {

    public static final String DIRECTORY = "atmosphere";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AtmosphereProfiles() {
        super(AtmosphereProfile.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, AtmosphereProfile> map, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<Identifier, AtmosphereProfile> clean = new LinkedHashMap<>();
        map.forEach((id, profile) -> {
            var emitters = profile.emitters().stream().filter(e -> {
                boolean ok = AtmosphereParticles.known(e.particle());
                if (!ok) {
                    LOGGER.warn("Atmosphere {}: particle {} is not one of {}; emitter dropped", id, e.particle(),
                            AtmosphereParticles.names());
                }
                return ok;
            }).toList();
            clean.put(id, new AtmosphereProfile(profile.when(), profile.dimension(), profile.blocks(), profile.biome(),
                    profile.biomeWeight(), profile.biomeRequired(), profile.enter(), profile.leave(), profile.full(), emitters, profile.fog(),
                    profile.sounds(), profile.bed()));
        });
        PlaceSense.load(clean);
        LOGGER.info("Atmosphere: {} places.", clean.size());
    }
}
