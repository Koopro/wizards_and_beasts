package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.event.heritage.VampireHeritageHandler;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicy;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicyResolver;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireSiring;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vampirism as a condition on top of a witch, wizard or Veela (Heritage 2.0): who may carry it, and that every system
 * that used to ask "is this the Vampire heritage?" now finds the thirst, the sun and the bat on a sired character too.
 */
class VampirismConditionTest {

    private static PlayerHeritageData character(Heritage heritage, HeritageVariant lineage,
                                                ConditionOrigin condition) {
        PlayerHeritageData data = new PlayerHeritageData();
        data.setSelectedHeritage(heritage);
        data.setSelectedHeritageVariant(lineage);
        data.setCondition(condition);
        return data;
    }

    @Test
    void onlyWizardkindAndVeelaMayBeTurned() {
        for (HeritageVariant lineage : HeritageVariant.values()) {
            Heritage people = lineage.getParentHeritage();
            boolean allowed = people == Heritage.WIZARDKIND || people == Heritage.VEELA;
            assertEquals(allowed, MagicalCondition.VAMPIRISM.canBeCarriedBy(people, lineage),
                    lineage + " of " + people);
        }
        assertNull(ConditionRules.toAcquire(Heritage.VEELA, HeritageVariant.VEELA_FULL, null, ConditionOrigin.SIRED));
        assertNull(ConditionRules.toAcquire(Heritage.WIZARDKIND, HeritageVariant.SQUIB, null, ConditionOrigin.SIRED),
                "a sire's blood takes the body, and a Squib has one");
        assertEquals(ConditionRules.Refusal.CANNOT_CARRY, ConditionRules.toAcquire(Heritage.VAMPIRE,
                HeritageVariant.VAMPIRE_DHAMPIR, null, ConditionOrigin.SIRED));
        assertEquals(ConditionRules.Refusal.CANNOT_CARRY, ConditionRules.toAcquire(Heritage.GOBLIN,
                HeritageVariant.GOBLIN_COMMON, null, ConditionOrigin.SIRED));
    }

    @Test
    void theOtherConditionsStayWithWizardkind() {
        assertFalse(MagicalCondition.LYCANTHROPY.canBeCarriedBy(Heritage.VEELA, HeritageVariant.VEELA_FULL));
        assertFalse(MagicalCondition.OBSCURUS.canBeCarriedBy(Heritage.VEELA, HeritageVariant.VEELA_HALF));
    }

    @Test
    void oneSlotHoldsTheWolfOrTheThirst() {
        assertEquals(ConditionRules.Refusal.OTHER_CONDITION, ConditionRules.toAcquire(Heritage.WIZARDKIND,
                HeritageVariant.HALF_BLOOD, ConditionOrigin.BITTEN, ConditionOrigin.SIRED));
        assertEquals(ConditionRules.Refusal.OTHER_CONDITION, ConditionRules.toAcquire(Heritage.WIZARDKIND,
                HeritageVariant.HALF_BLOOD, ConditionOrigin.SIRED, ConditionOrigin.BITTEN));
        assertEquals(ConditionRules.Refusal.ALREADY_CARRIES, ConditionRules.toAcquire(Heritage.VEELA,
                HeritageVariant.VEELA_HALF, ConditionOrigin.SIRED, ConditionOrigin.SIRED));
        // A turned Veela cannot be moved to Goblin while the condition is on her.
        assertEquals(ConditionRules.Refusal.WOULD_ORPHAN, ConditionRules.toReassign(ConditionOrigin.SIRED,
                Heritage.GOBLIN, HeritageVariant.GOBLIN_COMMON));
    }

    @Test
    void aSiredWizardThirstsBurnsAndChanges() {
        PlayerHeritageData sired = character(Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD, ConditionOrigin.SIRED);

        assertEquals(NutritionPolicy.BLOOD, NutritionPolicyResolver.resolve(sired));
        assertTrue(VampireHeritageHandler.hasSunlightWeakness(sired));
        assertTrue(HeritageTransformService.canTransform(sired));
        assertEquals(Heritage.VAMPIRE, HeritageTransformService.formHeritage(sired));
        assertTrue(VampireSiring.canSire(sired), "a sired vampire can sire in turn");
        assertTrue(sired.canCast(), "vampirism takes the daylight, not the magic");
    }

    @Test
    void theTurnedKeepTheirOwnBodyByDay() {
        assertEquals("human_default", HeritageFormBridge.getDefaultFormId(Heritage.WIZARDKIND,
                HeritageVariant.HALF_BLOOD, ConditionOrigin.SIRED, TransformationState.NORMAL));
        assertEquals("veela_human", HeritageFormBridge.getDefaultFormId(Heritage.VEELA,
                HeritageVariant.VEELA_FULL, ConditionOrigin.SIRED, TransformationState.NORMAL));
        assertEquals("vampire_bat", HeritageFormBridge.getDefaultFormId(Heritage.VEELA,
                HeritageVariant.VEELA_FULL, ConditionOrigin.SIRED, TransformationState.TRANSFORMED));
        assertEquals(List.of("veela_human", "vampire_bat"),
                HeritageFormBridge.getAvailableFormIds(Heritage.VEELA, ConditionOrigin.SIRED));
    }

    @Test
    void aTurnedVeelasToggleIsTheBat() {
        PlayerHeritageData veela = character(Heritage.VEELA, HeritageVariant.VEELA_FULL, ConditionOrigin.SIRED);
        assertEquals(Heritage.VAMPIRE, HeritageTransformService.formHeritage(veela));

        PlayerHeritageData untouched = character(Heritage.VEELA, HeritageVariant.VEELA_FULL, null);
        assertEquals(Heritage.VEELA, HeritageTransformService.formHeritage(untouched));
        assertEquals(NutritionPolicy.VANILLA, NutritionPolicyResolver.resolve(untouched));
    }

    @Test
    void onlyAVampireWhoThirstsAndBurnsCanSire() {
        assertTrue(VampireSiring.canSire(character(Heritage.VAMPIRE, HeritageVariant.VAMPIRE_BORN, null)));
        assertTrue(VampireSiring.canSire(character(Heritage.VAMPIRE, HeritageVariant.VAMPIRE_TURNED, null)));
        assertFalse(VampireSiring.canSire(character(Heritage.VAMPIRE, HeritageVariant.VAMPIRE_DHAMPIR, null)),
                "a Dhampir has the thirst and not the rest");
        assertFalse(VampireSiring.canSire(character(Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD, null)));
        assertFalse(VampireSiring.canSire(character(Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD,
                ConditionOrigin.BITTEN)));
    }

    @Test
    void everyWordOfItIsWritten() throws IOException {
        Path enUs = Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang", "en_us.json");
        JsonObject lang;
        try (Reader reader = Files.newBufferedReader(enUs)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (String key : List.of(MagicalCondition.VAMPIRISM.getTranslationKey(),
                MagicalCondition.VAMPIRISM.getDescriptionTranslationKey(),
                ConditionOrigin.SIRED.getTranslationKey(), ConditionOrigin.SIRED.getDescriptionTranslationKey())) {
            assertTrue(lang.has(key), "missing " + key);
        }
        for (String suffix : List.of("offered", "offer", "lapsed", "not_a_sire", "too_thirsty", "turned", "sired")) {
            String key = "message.wizards_and_beasts.vampire.sire." + suffix;
            assertTrue(lang.has(key), "missing " + key);
        }
    }
}
