package at.koopro.wizardsandbeasts.visual.beam;

import at.koopro.wizardsandbeasts.visual.hud.HudTime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Preset naming and built-ins, the preview's fade loop and the HUD timer readout — the pure parts of Visuals. */
class BeamPresetAndFadeTest {

    @Test
    void builtinsAreOnePerBeamSpellAndComplete() {
        List<BeamPreset> builtins = BeamPreset.builtins(spell -> 0x44CCFF);
        assertEquals(BeamVisualDefaults.SPELLS.size(), builtins.size());
        for (BeamPreset preset : builtins) {
            assertTrue(preset.builtin());
            assertTrue(preset.id().startsWith(BeamPreset.BUILTIN_PREFIX));
            assertEquals(BeamVisualProperty.values().length, preset.values().size(), preset.id());
        }
    }

    @Test
    void customIdsCanNeverCollideWithBuiltins() {
        for (String name : List.of("builtin:crucio", "Builtin Crucio", "  My Look  ", "a--b", "!!!")) {
            assertFalse(BeamPreset.idFor(name).contains(":"), name);
        }
        assertEquals("my_look", BeamPreset.idFor("  My Look  "));
        assertEquals("preset", BeamPreset.idFor("!!!"));
    }

    @Test
    void presetNamesAreBounded() {
        assertTrue(BeamPreset.validName("Green Jet"));
        assertTrue(BeamPreset.validName("Bella's-curse_2"));
        assertFalse(BeamPreset.validName(""));
        assertFalse(BeamPreset.validName("   "));
        assertFalse(BeamPreset.validName("x".repeat(33)));
        assertFalse(BeamPreset.validName("<script>"));
        assertFalse(BeamPreset.validName("a:b"));
        assertFalse(BeamPreset.validName(null));
    }

    @Test
    void fadeLoopBrightensHoldsDimsAndRests() {
        assertEquals(1f, BeamFadeCycle.alpha(123.4f, 0, 0), "no fades: always full");
        int in = 10;
        int out = 20;
        assertEquals(0f, BeamFadeCycle.alpha(0, in, out), 1e-6);
        assertEquals(0.5f, BeamFadeCycle.alpha(5, in, out), 1e-6);
        assertEquals(1f, BeamFadeCycle.alpha(in + 1, in, out), 1e-6);
        assertEquals(0.5f, BeamFadeCycle.alpha(in + BeamFadeCycle.HOLD_TICKS + 10, in, out), 1e-6);
        assertEquals(0f, BeamFadeCycle.alpha(in + BeamFadeCycle.HOLD_TICKS + out + 1, in, out), 1e-6);
        int length = BeamFadeCycle.length(in, out);
        assertEquals(BeamFadeCycle.alpha(5, in, out), BeamFadeCycle.alpha(length + 5, in, out), 1e-6, "loops");
    }

    @Test
    void hudTimerReadoutAndFill() {
        assertEquals("0s", HudTime.readout(0));
        assertEquals("1s", HudTime.readout(1), "never 0s while running");
        assertEquals("45s", HudTime.readout(45 * 20));
        assertEquals("3:07", HudTime.readout((3 * 60 + 7) * 20));
        assertEquals("20:00", HudTime.readout(24_000));
        assertEquals("1:00:00", HudTime.readout(3600 * 20));
        assertEquals(0.5f, HudTime.fraction(50, 100), 1e-6);
        assertEquals(0f, HudTime.fraction(10, 0), 1e-6);
        assertEquals(1f, HudTime.fraction(200, 100), 1e-6);
    }
}
