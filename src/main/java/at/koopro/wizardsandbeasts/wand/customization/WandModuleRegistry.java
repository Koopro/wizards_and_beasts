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

    /** Per-slot view cache for {@link #getAllForSlot}, invalidated on any registry write. */
    private static volatile @Nullable Map<WandSlot, List<WandModule>> slotViewCache;

    private WandModuleRegistry() {}

    // ── Tier 1: built-in registration ────────────────────────────────────────

    /**
     * Called from WizardsAndBeastsMod constructor (FMLCommonSetupEvent) to seed built-ins.
     *
     * <p>Clears first, so calling it twice re-seeds rather than colliding with itself. The
     * duplicate-id guard in {@link #register} exists to catch two <em>different</em> modules
     * claiming one id, and it cannot tell that apart from a second bootstrap unless the table
     * starts empty.
     */
    public static void bootstrap() {
        BUILT_INS.clear();
        slotViewCache = null;

        // HANDLE variants
        register(WandSlot.HANDLE, "classic");
        register(WandSlot.HANDLE, "gnarled");
        register(WandSlot.HANDLE, "carved");
        register(WandSlot.HANDLE, "twisted");
        register(WandSlot.HANDLE, "braided");
        register(WandSlot.HANDLE, "smooth");
        register(WandSlot.HANDLE, "ribbed");
        register(WandSlot.HANDLE, "bulbous");
        register(WandSlot.HANDLE, "fluted");
        register(WandSlot.HANDLE, "wrapped");
        // Shaped after the wands as they appear on screen.
        register(WandSlot.HANDLE, "vine");
        register(WandSlot.HANDLE, "bone");
        register(WandSlot.HANDLE, "talon");
        register(WandSlot.HANDLE, "flared");

        // SHAFT variants
        register(WandSlot.SHAFT, "straight");
        register(WandSlot.SHAFT, "spiral");
        register(WandSlot.SHAFT, "knotted");
        register(WandSlot.SHAFT, "tapered");
        register(WandSlot.SHAFT, "segmented");
        register(WandSlot.SHAFT, "twining");
        register(WandSlot.SHAFT, "bowed");
        register(WandSlot.SHAFT, "reeded");
        register(WandSlot.SHAFT, "nodular");
        register(WandSlot.SHAFT, "barked");

        // TIP variants
        register(WandSlot.TIP, "pointed");
        register(WandSlot.TIP, "orb");
        register(WandSlot.TIP, "split");
        register(WandSlot.TIP, "crystal");
        register(WandSlot.TIP, "blunt");
        register(WandSlot.TIP, "needle");
        register(WandSlot.TIP, "chisel");
        register(WandSlot.TIP, "budded");
        register(WandSlot.TIP, "claw");
        register(WandSlot.TIP, "serpent");
        register(WandSlot.TIP, "spiralled");

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
        register(WandSlot.ORNAMENT, "wire_wrap");
        register(WandSlot.ORNAMENT, "initial_plate");
        register(WandSlot.ORNAMENT, "spiral_inlay");
        register(WandSlot.ORNAMENT, "vine_wrap");
        register(WandSlot.ORNAMENT, "serpent_coil");
    }

    private static void register(WandSlot slot, String variant) {
        Identifier id = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, variant);
        // A module id carries no slot, so two slots using the same variant name collide on one
        // Identifier and the second silently replaces the first — the module simply stops
        // existing, with nothing in the log and an empty slot at render time. Caught once
        // already, by a "vine" handle and a "vine" shaft.
        WandModule clash = BUILT_INS.get(id);
        if (clash != null) {
            throw new IllegalStateException("Duplicate wand module id '" + id + "': slot '"
                    + clash.slot().slotId() + "' already registered it, slot '" + slot.slotId()
                    + "' is trying to reuse it. Module ids must be unique across all slots.");
        }
        BUILT_INS.put(id, WandModule.builtin(id, slot, variant));
        slotViewCache = null;
    }

    // ── Tier 2: datapack operations ──────────────────────────────────────────

    /** Called by WandModuleLoader at the start of each reload cycle. */
    static void clearDatapack() {
        DATAPACK.clear();
        slotViewCache = null;
    }

    /** Called by WandModuleLoader for each successfully parsed datapack module. */
    static void addDatapackModule(@NonNull Identifier id, @NonNull WandModule module) {
        if (BUILT_INS.containsKey(id)) {
            LOGGER.warn("[W&B] Datapack wand module '{}' overrides a built-in module.", id);
        }
        DATAPACK.put(id, module);
        slotViewCache = null;
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
     *
     * <p>Result is cached per slot and rebuilt only when the registry changes (bootstrap/reload),
     * since this is called from the per-frame wand render path — never rebuild on every call.
     */
    public static List<WandModule> getAllForSlot(@NonNull WandSlot slot) {
        Map<WandSlot, List<WandModule>> cache = slotViewCache;
        if (cache == null) {
            cache = buildSlotViewCache();
            slotViewCache = cache;
        }
        return cache.getOrDefault(slot, List.of());
    }

    private static Map<WandSlot, List<WandModule>> buildSlotViewCache() {
        Map<WandSlot, Map<Identifier, WandModule>> bySlot = new EnumMap<>(WandSlot.class);
        for (WandModule m : BUILT_INS.values()) {
            bySlot.computeIfAbsent(m.slot(), s -> new LinkedHashMap<>()).put(m.id(), m);
        }
        for (WandModule m : DATAPACK.values()) {
            bySlot.computeIfAbsent(m.slot(), s -> new LinkedHashMap<>()).put(m.id(), m); // overrides same-id built-in
        }
        Map<WandSlot, List<WandModule>> result = new EnumMap<>(WandSlot.class);
        for (Map.Entry<WandSlot, Map<Identifier, WandModule>> entry : bySlot.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue().values()));
        }
        return result;
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
