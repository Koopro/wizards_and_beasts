package at.koopro.wizardsandbeasts.wand.customization;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Datapack half of the hybrid WandModuleRegistry.
 * Reads from data/&lt;namespace&gt;/wand_modules/&lt;slot&gt;/&lt;module_id&gt;.json.
 *
 * The slot directory component must match the "slot" field inside the JSON body.
 * Module ids are derived as &lt;namespace&gt;:&lt;module_id&gt; (the slot directory component is stripped).
 *
 * Datapack modules that share an id with a built-in override the built-in (with a warning).
 * Loaded via AddServerReloadListenersEvent in WizardsAndBeastsMod.
 */
public final class WandModuleLoader extends SimpleJsonResourceReloadListener<WandModuleSpec> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String DIRECTORY = "wand_modules";

    public WandModuleLoader() {
        super(WandModuleSpec.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, WandModuleSpec> jsonEntries,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        WandModuleRegistry.clearDatapack();
        int loaded = 0;
        int skipped = 0;

        for (Map.Entry<Identifier, WandModuleSpec> entry : jsonEntries.entrySet()) {
            Identifier fileId = entry.getKey(); // e.g. examplemod:tip/custom_starburst
            WandModuleSpec spec = entry.getValue();

            // Path must be <slot>/<module_name>
            String path = fileId.getPath();
            int sep = path.indexOf('/');
            if (sep == -1) {
                LOGGER.warn("[W&B] Wand module at '{}' has invalid path: expected <slot>/<name>", fileId);
                skipped++;
                continue;
            }

            String slotName = path.substring(0, sep);
            String moduleName = path.substring(sep + 1);

            WandSlot pathSlot = WandSlot.byId(slotName).orElse(null);
            if (pathSlot == null) {
                LOGGER.warn("[W&B] Wand module '{}' references unknown slot '{}'", fileId, slotName);
                skipped++;
                continue;
            }

            if (spec.slot() != pathSlot) {
                LOGGER.error("[W&B] Slot mismatch for wand module at '{}': path slot='{}', json slot='{}'",
                        fileId, pathSlot.slotId(), spec.slot().slotId());
                skipped++;
                continue;
            }

            Identifier moduleId = Identifier.fromNamespaceAndPath(fileId.getNamespace(), moduleName);
            WandModule module = new WandModule(
                    moduleId,
                    spec.slot(),
                    spec.boneVariant(),
                    spec.textureOverride(),
                    spec.tintOverride()
            );

            WandModuleRegistry.addDatapackModule(moduleId, module);
            loaded++;
        }

        LOGGER.info("[W&B] Loaded {} datapack wand module(s) ({} skipped).", loaded, skipped);
    }
}
