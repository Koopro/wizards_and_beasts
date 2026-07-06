package at.koopro.wizardsandbeasts.trunk.template;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@link PocketTemplate}s from {@code data/<ns>/pocket_templates/*.json} on every
 * datapack reload. Registry entries are keyed by the JSON {@code templateId} field
 * (which {@code TrunkRecord.templateId} references), not the file id.
 */
public final class PocketTemplateLoader extends SimpleJsonResourceReloadListener<PocketTemplate> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String DIRECTORY = "pocket_templates";

    public PocketTemplateLoader() {
        super(PocketTemplate.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, PocketTemplate> map,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, PocketTemplate> byTemplateId = new HashMap<>();
        for (Map.Entry<Identifier, PocketTemplate> entry : map.entrySet()) {
            PocketTemplate template = entry.getValue();
            PocketTemplate clash = byTemplateId.put(template.templateId(), template);
            if (clash != null) {
                LOGGER.warn("Duplicate pocket templateId '{}' (file {} wins).", template.templateId(), entry.getKey());
            }
            if (!entry.getKey().getPath().endsWith(template.templateId())) {
                LOGGER.warn("Pocket template file '{}' declares templateId '{}' — file name and id should match.",
                        entry.getKey(), template.templateId());
            }
        }
        PocketTemplateRegistry.replaceAll(byTemplateId);
        LOGGER.info("PocketTemplateLoader: loaded {} pocket templates.", byTemplateId.size());
    }
}
