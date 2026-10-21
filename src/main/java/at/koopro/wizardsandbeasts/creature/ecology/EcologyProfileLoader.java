package at.koopro.wizardsandbeasts.creature.ecology;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Reload listener for {@code data/wizards_and_beasts/creature_ecology/*.json}. The file name is the creature id,
 * as for {@code creature_bonds}: the ecology of bespoke Java creatures (Niffler, Mooncalf, Bowtruckle…) and
 * datapack ones lives in one place.
 */
@NullMarked
public final class EcologyProfileLoader extends SimpleJsonResourceReloadListener<EcologyProfile> {

    public static final String DIRECTORY = "creature_ecology";
    private static final Logger LOGGER = LogUtils.getLogger();

    public EcologyProfileLoader() {
        super(EcologyProfile.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, EcologyProfile> jsonEntries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        EcologyProfileRegistry.replaceAll(new HashMap<>(jsonEntries));
        LOGGER.info("Loaded {} creature ecology profile(s).", jsonEntries.size());
    }
}
