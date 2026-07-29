package at.koopro.wizardsandbeasts.wand.customization;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Named whole-wand presets, shaped after the wands as they appear on screen.
 *
 * <p>Each entry is only a combination of modules that already exist — a preset adds no geometry and
 * no new slot behaviour, it just spares the player from assembling a recognisable wand three slots
 * at a time.
 *
 * <p>Registration is validated at bootstrap rather than trusted. A preset naming a module that is
 * not registered would otherwise fail silently at render time, leaving a wand with an empty slot and
 * no clue why; here it is logged loudly and dropped, so a typo shows up on the server console the
 * first time the mod loads rather than in a bug report.
 */
public final class WandPresetRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Identifier, WandPreset> PRESETS = new LinkedHashMap<>();

    private WandPresetRegistry() {}

    /** Called from mod init, after {@link WandModuleRegistry#bootstrap()} — presets reference modules. */
    public static void bootstrap() {
        PRESETS.clear();

        // Plain holly, an unshowy wand: the base shape with a shaped grip.
        register("harry", "Harry's Wand", "smooth", "straight", "pointed", null);
        // Vine wood, carved with a climbing vine.
        register("hermione", "Hermione's Wand", "vine", "straight", "pointed", "vine_wrap");
        // Thick and plain, with a heavy end.
        register("ron", "Ron's Wand", "bulbous", "straight", "blunt", null);
        // The Elder Wand: pale, with its run of nodes.
        register("elder", "The Elder Wand", "classic", "nodular", "pointed", null);
        // Bone-white and thin, with the row of knuckles.
        register("voldemort", "Yew Wand", "bone", "straight", "claw", null);
        // Gnarled and clawed.
        register("bellatrix", "Talon Wand", "talon", "barked", "claw", null);
        // A cane: flared grip under a serpent's head.
        register("lucius", "Serpent Cane", "flared", "straight", "serpent", "serpent_coil");
        // Slim and severe.
        register("snape", "Ebony Wand", "smooth", "straight", "needle", null);
        // Ornate, banded and engraved.
        register("sirius", "Ornate Wand", "flared", "reeded", "spiralled", "runes");
        // Chunky and hand-carved.
        register("luna", "Carved Wand", "bulbous", "barked", "budded", null);
        // Neat, dark and unadorned.
        register("draco", "Hawthorn Wand", "carved", "straight", "pointed", null);
        // Precise, with a metal collar.
        register("mcgonagall", "Fir Wand", "ribbed", "segmented", "pointed", "metal_band");
    }

    private static void register(String presetId, String displayName,
                                 String handle, String shaft, String tip, String ornament) {
        Map<WandSlot, Identifier> modules = new LinkedHashMap<>();
        if (!put(modules, presetId, WandSlot.HANDLE, handle)
                || !put(modules, presetId, WandSlot.SHAFT, shaft)
                || !put(modules, presetId, WandSlot.TIP, tip)) {
            return;
        }
        if (ornament != null && !put(modules, presetId, WandSlot.ORNAMENT, ornament)) {
            return;
        }
        Identifier id = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, presetId);
        PRESETS.put(id, new WandPreset(id, displayName, new WandConfiguration(modules)));
    }

    /** Resolves a module id and checks it is registered for the slot it is being assigned to. */
    private static boolean put(Map<WandSlot, Identifier> modules, String presetId,
                               WandSlot slot, String variant) {
        Identifier moduleId = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, variant);
        WandModule module = WandModuleRegistry.get(moduleId).orElse(null);
        if (module == null) {
            LOGGER.error("[W&B] Wand preset '{}' references unknown module '{}' — preset dropped.",
                    presetId, moduleId);
            return false;
        }
        if (module.slot() != slot) {
            LOGGER.error("[W&B] Wand preset '{}' puts module '{}' in slot '{}', but it belongs to "
                            + "'{}' — preset dropped.",
                    presetId, moduleId, slot.slotId(), module.slot().slotId());
            return false;
        }
        modules.put(slot, moduleId);
        return true;
    }

    public static Optional<WandPreset> get(@NonNull Identifier id) {
        return Optional.ofNullable(PRESETS.get(id));
    }

    /** All presets, in registration order. */
    public static List<WandPreset> all() {
        return List.copyOf(new ArrayList<>(PRESETS.values()));
    }
}
