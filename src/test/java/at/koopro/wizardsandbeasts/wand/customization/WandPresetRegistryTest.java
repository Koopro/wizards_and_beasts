package at.koopro.wizardsandbeasts.wand.customization;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Presets are combinations of module ids written by hand, which makes a typo the obvious failure
 * mode — and a silent one. {@link WandPresetRegistry} drops an invalid preset rather than shipping a
 * wand with an unfillable slot, so the thing worth asserting is that nothing was dropped.
 */
class WandPresetRegistryTest {

    /** Presets reference modules, so the module registry has to exist first. */
    @BeforeAll
    static void bootstrap() {
        WandModuleRegistry.bootstrap();
        WandPresetRegistry.bootstrap();
    }

    @Test
    void everyPresetSurvivedRegistration() {
        List<WandPreset> presets = WandPresetRegistry.all();

        assertFalse(presets.isEmpty(), "no presets registered at all");
        assertEquals(12, presets.size(),
                "a preset was dropped during bootstrap — it names a module that does not exist, "
                        + "or puts one in the wrong slot; check the server log for which");
    }

    @Test
    void everyPresetFillsTheRequiredSlots() {
        for (WandPreset preset : WandPresetRegistry.all()) {
            for (WandSlot slot : WandSlot.values()) {
                if (slot.isRequired()) {
                    assertTrue(preset.configuration().getModule(slot).isPresent(),
                            preset.id() + " leaves required slot '" + slot.slotId() + "' empty");
                }
            }
        }
    }

    @Test
    void everyPresetModuleIsRegisteredForTheSlotItSitsIn() {
        for (WandPreset preset : WandPresetRegistry.all()) {
            for (WandSlot slot : WandSlot.values()) {
                preset.configuration().getModule(slot).ifPresent(moduleId -> {
                    WandModule module = WandModuleRegistry.get(moduleId).orElse(null);
                    assertTrue(module != null,
                            preset.id() + " references unregistered module " + moduleId);
                    assertEquals(slot, module.slot(),
                            preset.id() + " puts " + moduleId + " in slot " + slot.slotId()
                                    + " but it belongs to " + module.slot().slotId());
                });
            }
        }
    }

    @Test
    void presetsAreDistinct() {
        List<WandPreset> presets = WandPresetRegistry.all();
        long distinctIds = presets.stream().map(WandPreset::id).distinct().count();
        long distinctConfigs = presets.stream().map(WandPreset::configuration).distinct().count();

        assertEquals(presets.size(), distinctIds, "two presets share an id");
        assertEquals(presets.size(), distinctConfigs,
                "two presets are the same wand — one of them has nothing to offer the player");
    }
}
