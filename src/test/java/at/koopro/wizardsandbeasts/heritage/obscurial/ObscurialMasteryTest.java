package at.koopro.wizardsandbeasts.heritage.obscurial;

import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hybrid Obscurus mastery (Heritage 2.0): one path per origin, the controlled one safer and the unleashed one
 * stronger, and no point on either where a number turns into a free lunch.
 */
class ObscurialMasteryTest {

    private static final ObscurialMastery.Path CONTROLLED = ObscurialMastery.Path.CONTROLLED;
    private static final ObscurialMastery.Path UNLEASHED = ObscurialMastery.Path.UNLEASHED;

    @Test
    void thePathIsTheOriginAndNothingElse() {
        assertEquals(CONTROLLED, ObscurialMastery.path(ConditionOrigin.SUPPRESSED));
        assertEquals(UNLEASHED, ObscurialMastery.path(ConditionOrigin.UNLEASHED));
        assertNull(ObscurialMastery.path(ConditionOrigin.BITTEN));
        assertNull(ObscurialMastery.path(ConditionOrigin.SIRED));
        assertNull(ObscurialMastery.path(null));
    }

    @Test
    void anUnpractisedHostIsTheOldObscurialOnTheControlledPath() {
        assertEquals(1f, ObscurialMastery.darkDrainMultiplier(CONTROLLED, 0f));
        assertEquals(1f, ObscurialMastery.collapseMultiplier(CONTROLLED, 0f));
        assertEquals(1f, ObscurialMastery.abilityCostMultiplier(CONTROLLED, 0f));
        assertEquals(1f, ObscurialMastery.stressGainMultiplier(CONTROLLED));
        assertEquals(0.40f, ObscurialMastery.forcedBreakThreshold(CONTROLLED, 0f, 0.40f));
        assertEquals(0f, ObscurialMastery.collapseDamage(CONTROLLED));
    }

    @Test
    void controlledMasteryBuysSafetyAndNothingSharper() {
        assertEquals(0.65f, ObscurialMastery.darkDrainMultiplier(CONTROLLED, 100f), 1e-6f);
        assertEquals(0.5f, ObscurialMastery.collapseMultiplier(CONTROLLED, 100f), 1e-6f);
        assertEquals(0.25f, ObscurialMastery.forcedBreakThreshold(CONTROLLED, 100f, 0.40f), 1e-6f);
        assertEquals(1f, ObscurialMastery.abilityCostMultiplier(CONTROLLED, 100f), "no discount on the safe path");
        assertEquals(0f, ObscurialMastery.graspDamage(CONTROLLED, 100f), "no teeth on the safe path");
    }

    @Test
    void unleashedMasteryBuysOutputAndAlwaysPaysForIt() {
        assertEquals(0.7f, ObscurialMastery.abilityCostMultiplier(UNLEASHED, 100f), 1e-6f);
        assertEquals(6f, ObscurialMastery.graspDamage(UNLEASHED, 100f), 1e-6f);
        // The price holds at every mastery: faster strain, a longer collapse that wounds, no help holding it down.
        for (float mastery : new float[]{0f, 50f, 100f}) {
            assertEquals(1.25f, ObscurialMastery.stressGainMultiplier(UNLEASHED));
            assertEquals(1.25f, ObscurialMastery.collapseMultiplier(UNLEASHED, mastery));
            assertTrue(ObscurialMastery.collapseDamage(UNLEASHED) > 0f);
            assertEquals(1f, ObscurialMastery.darkDrainMultiplier(UNLEASHED, mastery));
            assertEquals(0.40f, ObscurialMastery.forcedBreakThreshold(UNLEASHED, mastery, 0.40f));
        }
    }

    @Test
    void graspHasNoTeethUntilTheObscurusIsHeld() {
        assertEquals(0f, ObscurialMastery.graspDamage(UNLEASHED, ObscurialMastery.GRASP_MASTERY - 0.01f));
        assertTrue(ObscurialMastery.graspDamage(UNLEASHED, ObscurialMastery.GRASP_MASTERY) > 0f);
        assertEquals(ObscurialMastery.GRASP_MASTERY, ObscurialMastery.required(ObscurialAbility.GRASP));
        assertEquals(0f, ObscurialMastery.required(ObscurialAbility.SURGE));
    }

    @Test
    void masteryStaysInItsRangeWhateverIsAsked() {
        assertEquals(0f, ObscurialMastery.clamp(-40f));
        assertEquals(100f, ObscurialMastery.clamp(1e9f));
        // Out-of-range input cannot push a multiplier past the ends of its range.
        assertEquals(0.65f, ObscurialMastery.darkDrainMultiplier(CONTROLLED, 500f), 1e-6f);
        assertEquals(1f, ObscurialMastery.darkDrainMultiplier(CONTROLLED, -500f), 1e-6f);
        assertTrue(ObscurialMastery.FLAG.startsWith("obscurial_"), "a cure clears obscurial_* flags; mastery must go too");
    }

    @Test
    void everyWordOfItIsWritten() throws IOException {
        Path enUs = Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang", "en_us.json");
        JsonObject lang;
        try (Reader reader = Files.newBufferedReader(enUs)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (ObscurialMastery.Path path : ObscurialMastery.Path.values()) {
            assertTrue(lang.has(path.translationKey()), "missing " + path.translationKey());
            assertTrue(lang.has(path.translationKey() + ".desc"), "missing " + path.translationKey() + ".desc");
        }
        for (String suffix : List.of("exhausted", "too_little_control", "change_failed", "vent_recovering", "vented",
                "vented_mastered", "needs_mastery", "volatile_cooldowns", "volatile", "critical", "daylight",
                "forced_back", "breaks_loose")) {
            assertTrue(lang.has("obscurial.wizards_and_beasts." + suffix), "missing obscurial." + suffix);
        }
        assertTrue(lang.has("heritage.wizards_and_beasts.profile.obscurus.mastery"));
        for (String ability : List.of("obscurus_surge", "obscurus_grasp")) {
            String desc = lang.get("ability.wizards_and_beasts." + ability + ".desc").getAsString();
            assertTrue(!desc.equals("Obscurus form attack."), ability + " still has the placeholder description");
        }
    }
}
