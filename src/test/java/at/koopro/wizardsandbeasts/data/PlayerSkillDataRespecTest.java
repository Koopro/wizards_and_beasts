package at.koopro.wizardsandbeasts.data;

import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Point spending, unlearning and the free-respec token, through save/load as a relog or a death copy would. */
class PlayerSkillDataRespecTest {

    @BeforeEach
    void web() {
        SkillTrees.replaceAll(List.of(
                Skill.builder("root_node", "root").tree(SkillTreeId.SPELL_MASTERY).cost(1).root(true).build(),
                Skill.builder("deep_node", "deep").tree(SkillTreeId.SPELL_MASTERY).cost(3).maxLevel(2).build()));
    }

    @AfterEach
    void clear() {
        SkillTrees.replaceAll(List.of());
    }

    @Test
    void unlearningALevelRefundsExactlyItsCost() {
        PlayerSkillData data = new PlayerSkillData();
        data.addSkillPoints(10);
        data.spendSkillPoints(1);
        data.setSkillLevel("root_node", 1);
        data.spendSkillPoints(6);
        data.setSkillLevel("deep_node", 2);
        assertEquals(3, data.getSkillPoints());

        assertEquals(3, data.unlearnLevel("deep_node"));
        assertEquals(1, data.getSkillLevel("deep_node"), "one level, not the node");
        assertEquals(6, data.getSkillPoints());
        assertEquals(3, data.unlearnLevel("deep_node"));
        assertFalse(data.hasSkill("deep_node"));
        assertEquals(0, data.unlearnLevel("deep_node"), "nothing left to unlearn");
        assertEquals(9, data.getSkillPoints());
        assertEquals(10, data.getTotalPointsEarned(), "unlearning never touches what was earned");
    }

    @Test
    void theFreeRespecTokenSurvivesSaveAndLoad() {
        PlayerSkillData data = new PlayerSkillData();
        assertTrue(data.isFreeRespecAvailable(), "a new character is owed one free respec");
        data.markFreeRespecUsed();
        data.setSkillLevel("root_node", 1);

        PlayerSkillData reloaded = new PlayerSkillData();
        CompoundTag tag = data.save();
        reloaded.load(tag);
        assertFalse(reloaded.isFreeRespecAvailable());
        assertEquals(Map.of("root_node", 1), reloaded.getUnlockedSkills());
        assertFalse(data.copy().isFreeRespecAvailable(), "a death copy keeps it spent");
    }

    @Test
    void anOldSaveWithoutTheTokenIsOwedOne() {
        CompoundTag legacy = new PlayerSkillData().save();
        legacy.remove("FreeRespecUsed");
        PlayerSkillData data = new PlayerSkillData();
        data.load(legacy);
        assertTrue(data.isFreeRespecAvailable());
    }

    @Test
    void theClientMirrorTakesTheTokenFromSync() {
        PlayerSkillData mirror = new PlayerSkillData();
        mirror.applySync(4, 9, Map.of("root_node", 1), false);
        assertFalse(mirror.isFreeRespecAvailable());
        assertEquals(4, mirror.getSkillPoints());
    }
}
