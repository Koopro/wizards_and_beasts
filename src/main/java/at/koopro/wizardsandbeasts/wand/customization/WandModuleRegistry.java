package at.koopro.wizardsandbeasts.wand.customization;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/**
 * Hybrid two-tier registry for WandModules.
 *
 * Tier 1 — Code-defined built-ins: registered via bootstrap(), called at mod init.
 * Tier 2 — Datapack additions: populated by WandModuleLoader on each resource reload.
 *          Datapack modules with matching ids override built-ins (with a warning).
 *
 * At lookup time both tiers are indistinguishable. Datapack entries take precedence.
 */
public final class WandModuleRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<Identifier, WandModule> BUILT_INS = new LinkedHashMap<>();
    private static final Map<Identifier, WandModule> DATAPACK = new LinkedHashMap<>();

    private WandModuleRegistry() {}

    // ── Tier 1: built-in registration ────────────────────────────────────────

    /** Called from WizardsAndBeastsMod constructor (FMLCommonSetupEvent) to seed built-ins. */
    public static void bootstrap() {
        // HANDLE variants
        register(WandSlot.HANDLE, "classic");
        register(WandSlot.HANDLE, "gnarled");
        register(WandSlot.HANDLE, "carved");
        register(WandSlot.HANDLE, "twisted");
        register(WandSlot.HANDLE, "braided");
        register(WandSlot.HANDLE, "smooth");

        // SHAFT variants
        register(WandSlot.SHAFT, "straight");
        register(WandSlot.SHAFT, "spiral");
        register(WandSlot.SHAFT, "knotted");
        register(WandSlot.SHAFT, "tapered");
        register(WandSlot.SHAFT, "segmented");

        // TIP variants
        register(WandSlot.TIP, "pointed");
        register(WandSlot.TIP, "orb");
        register(WandSlot.TIP, "split");
        register(WandSlot.TIP, "crystal");
        register(WandSlot.TIP, "blunt");

        // CORE variants
        register(WandSlot.CORE, "phoenix_feather");
        register(WandSlot.CORE, "dragon_heartstring");
        register(WandSlot.CORE, "unicorn_hair");
        register(WandSlot.CORE, "thestral_tail");
        register(WandSlot.CORE, "veela_hair");

        // ORNAMENT variants
        register(WandSlot.ORNAMENT, "runes");
        register(WandSlot.ORNAMENT, "gem_inlay");
        register(WandSlot.ORNAMENT, "metal_band");
        register(WandSlot.ORNAMENT, "engraving");
    }

    private static void register(WandSlot slot, String variant) {
        Identifier id = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, variant);
        BUILT_INS.put(id, WandModule.builtin(id, slot, variant));
    }

    // ── Tier 2: datapack operations ──────────────────────────────────────────

    /** Called by WandModuleLoader at the start of each reload cycle. */
    static void clearDatapack() {
        DATAPACK.clear();
    }

    /** Called by WandModuleLoader for each successfully parsed datapack module. */
    static void addDatapackModule(@NonNull Identifier id, @NonNull WandModule module) {
        if (BUILT_INS.containsKey(id)) {
            LOGGER.warn("[W&B] Datapack wand module '{}' overrides a built-in module.", id);
        }
        DATAPACK.put(id, module);
    }

    // ── Lookup API ────────────────────────────────────────────────────────────

    /** Returns the WandModule for the given id, preferring datapack over built-in. */
    public static Optional<WandModule> get(@NonNull Identifier id) {
        WandModule dp = DATAPACK.get(id);
        if (dp != null) return Optional.of(dp);
        return Optional.ofNullable(BUILT_INS.get(id));
    }

    /**
     * Returns all modules registered for the given slot.
     * Includes built-ins, with any datapack overrides applied.
     * Datapack-only modules for this slot are appended at the end.
     */
    public static List<WandModule> getAllForSlot(@NonNull WandSlot slot) {
        Map<Identifier, WandModule> combined = new LinkedHashMap<>();
        for (WandModule m : BUILT_INS.values()) {
            if (m.slot() == slot) combined.put(m.id(), m);
        }
        for (WandModule m : DATAPACK.values()) {
            if (m.slot() == slot) combined.put(m.id(), m); // overrides same-id built-in
        }
        return List.copyOf(combined.values());
    }

    /**
     * Validates that the bone referenced by a module exists in the given set of bone names.
     * Logs a warning if not found. Called at datapack load time.
     */
    public static void warnIfBoneMissing(@NonNull WandModule module, @NonNull Set<String> boneNames) {
        if (!boneNames.contains(module.boneName())) {
            LOGGER.warn("[W&B] Wand module '{}' references bone '{}' which was not found in the wand model.",
                    module.id(), module.boneName());
        }
    }
}
