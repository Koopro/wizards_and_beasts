package at.koopro.wizardsandbeasts.bestiary.harvest;

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
 * Loads {@code data/<namespace>/bestiary/harvest/*.json} into {@link HarvestTable}, alongside the
 * entries themselves at {@code bestiary/entries/}.
 */
@NullMarked
public final class HarvestRuleLoader extends SimpleJsonResourceReloadListener<HarvestRule> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String DIRECTORY = "bestiary/harvest";

    public HarvestRuleLoader() {
        super(HarvestRule.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, HarvestRule> jsonEntries,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        HarvestTable.replaceAll(new HashMap<>(jsonEntries));
        LOGGER.info("[W&B] Loaded {} bestiary harvest rules.", HarvestTable.count());
    }
}
