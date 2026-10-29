package at.koopro.wizardsandbeasts.heritage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one condition slot and who may fill it. Each refusal here used to be a silent overwrite or an impossible state:
 * a bite replacing an Obscurus, {@code condition set} replacing whatever was there, a werewolf reassigned to Goblin.
 */
class ConditionRulesTest {

    @Test
    void aWizardWithNothingCanTakeEitherCondition() {
        for (ConditionOrigin origin : ConditionOrigin.values()) {
            assertNull(ConditionRules.toAcquire(Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD, null, origin),
                    origin.getId() + " should take hold in a half-blood wizard");
        }
    }

    @Test
    void aBiteFindsNoRoomBesideAnObscurus() {
        assertEquals(ConditionRules.Refusal.OTHER_CONDITION, ConditionRules.toAcquire(Heritage.WIZARDKIND,
                HeritageVariant.PURE_BLOOD, ConditionOrigin.SUPPRESSED, ConditionOrigin.BITTEN));
        assertEquals(ConditionRules.Refusal.OTHER_CONDITION, ConditionRules.toAcquire(Heritage.WIZARDKIND,
                HeritageVariant.PURE_BLOOD, ConditionOrigin.BITTEN, ConditionOrigin.UNLEASHED));
    }

    @Test
    void theSameConditionIsNeverWrittenOverItself() {
        assertEquals(ConditionRules.Refusal.ALREADY_CARRIES, ConditionRules.toAcquire(Heritage.WIZARDKIND,
                HeritageVariant.HALF_BLOOD, ConditionOrigin.BITTEN, ConditionOrigin.SAVAGE_BITTEN));
        assertEquals(ConditionRules.Refusal.ALREADY_CARRIES, ConditionRules.toAcquire(Heritage.WIZARDKIND,
                HeritageVariant.HALF_BLOOD, ConditionOrigin.SUPPRESSED, ConditionOrigin.UNLEASHED));
    }

    @Test
    void whoCannotCarryIsAskedBeforeWhatTheyCarry() {
        assertEquals(ConditionRules.Refusal.CANNOT_CARRY, ConditionRules.toAcquire(Heritage.GOBLIN,
                HeritageVariant.GOBLIN_COMMON, null, ConditionOrigin.BITTEN));
        assertEquals(ConditionRules.Refusal.CANNOT_CARRY, ConditionRules.toAcquire(Heritage.WIZARDKIND,
                HeritageVariant.SQUIB, null, ConditionOrigin.SUPPRESSED), "an Obscurus needs magic to force down");
        assertNull(ConditionRules.toAcquire(Heritage.WIZARDKIND, HeritageVariant.SQUIB, null, ConditionOrigin.BITTEN),
                "a bite is a curse of the body; a Squib catches it");
        assertEquals(ConditionRules.Refusal.NO_HERITAGE, ConditionRules.toAcquire(null, null, null,
                ConditionOrigin.BITTEN));
    }

    @Test
    void aHeritageChangeMayNotStrandACondition() {
        assertEquals(ConditionRules.Refusal.WOULD_ORPHAN, ConditionRules.toReassign(ConditionOrigin.BITTEN,
                Heritage.GOBLIN, HeritageVariant.GOBLIN_COMMON));
        assertEquals(ConditionRules.Refusal.WOULD_ORPHAN, ConditionRules.toReassign(ConditionOrigin.SUPPRESSED,
                Heritage.WIZARDKIND, HeritageVariant.SQUIB));
        assertNull(ConditionRules.toReassign(ConditionOrigin.BITTEN, Heritage.WIZARDKIND, HeritageVariant.SQUIB));
        assertNull(ConditionRules.toReassign(null, Heritage.GIANT, HeritageVariant.GIANT_FULL));
    }

    @Test
    void everyRefusalHasItsSentence() throws IOException {
        Path enUs = Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang", "en_us.json");
        JsonObject lang;
        try (Reader reader = Files.newBufferedReader(enUs)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (ConditionRules.Refusal refusal : ConditionRules.Refusal.values()) {
            assertTrue(lang.has(refusal.translationKey()), "missing " + refusal.translationKey());
        }
        assertTrue(lang.has("heritage.wizards_and_beasts.werewolf.bite_no_room"));
        assertTrue(lang.has("admin.wizards_and_beasts.heritage_action.condition_incompatible"));
    }
}
