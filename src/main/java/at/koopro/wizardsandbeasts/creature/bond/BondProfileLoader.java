package at.koopro.wizardsandbeasts.creature.bond;

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
 * Reload listener for {@code data/wizards_and_beasts/creature_bonds/*.json}, mirroring
 * {@code CreatureDefinitionLoader}.
 *
 * <p>The file name is the creature id: {@code creature_bonds/bowtruckle.json} is the Bowtruckle's
 * profile. Nothing cross-checks it against the entity registry here — a datapack may legitimately
 * describe a creature this mod does not ship — so a profile for an unknown id simply never matches
 * a living entity.
 */
@NullMarked
public final class BondProfileLoader extends SimpleJsonResourceReloadListener<BondProfile> {

    public static final String DIRECTORY = "creature_bonds";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BondProfileLoader() {
        super(BondProfile.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, BondProfile> jsonEntries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<Identifier, BondProfile> loaded = new HashMap<>(jsonEntries);
        BondProfileRegistry.replaceAll(loaded);
        LOGGER.info("Loaded {} creature bond profile(s).", loaded.size());
    }
}
