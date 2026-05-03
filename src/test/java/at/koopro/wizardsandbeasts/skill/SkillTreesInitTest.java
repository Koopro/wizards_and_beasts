package at.koopro.wizardsandbeasts.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTreesInitTest {

    @Test
    void init_validatesPrerequisiteGraph() {
        assertDoesNotThrow(SkillTrees::init);
    }

    @Test
    void allSkillsRegistered() {
        assertTrue(SkillTrees.count() > 0);
    }
}
