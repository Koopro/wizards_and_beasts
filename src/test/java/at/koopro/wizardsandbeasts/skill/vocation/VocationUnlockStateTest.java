package at.koopro.wizardsandbeasts.skill.vocation;

import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.vocation.VocationHelper.Band;
import at.koopro.wizardsandbeasts.skill.vocation.VocationHelper.UnlockState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the §5 Mastery-cap / opposition mechanic through the pure {@code unlockState} core (no MC bootstrap,
 * no Player). Uses hand-built {@link VocationDefinition}s + {@link VocationRegistry} and code-built {@link Skill}
 * nodes — JSON parsing and attribute application are covered elsewhere / at runtime.
 */
class VocationUnlockStateTest {

    private static final Identifier DUELIST = id("duelist");
    private static final Identifier DARK = id("dark_arts");
    private static final Identifier HEALER = id("healer"); // not registered — opposition target only

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("wizards_and_beasts", path);
    }

    private static VocationDefinition vocation(Identifier id, SkillTreeId tree, List<Identifier> oppositions) {
        return new VocationDefinition(id, tree, "k", "k", "k", 1, Optional.empty(),
                oppositions, List.of(), List.of());
    }

    private static Skill node(SkillTreeId tree, int tier) {
        return Skill.builder("n" + tree.name() + tier, "n").tree(tree).tier(tier).build();
    }

    @BeforeEach
    void setup() {
        VocationRegistry.replaceAll(Map.of(
                DUELIST, vocation(DUELIST, SkillTreeId.SPELL_MASTERY, List.of()),
                DARK, vocation(DARK, SkillTreeId.DARK_ARTS, List.of(HEALER))));
    }

    @Test
    void band_splitsAtFoundationMaxTier() {
        assertEquals(Band.FOUNDATION, VocationHelper.band(node(SkillTreeId.SPELL_MASTERY, 1)));
        assertEquals(Band.MASTERY, VocationHelper.band(node(SkillTreeId.SPELL_MASTERY, 2)));
    }

    @Test
    void foundation_alwaysAllowedWithoutCommitment() {
        assertEquals(UnlockState.ALLOWED,
                VocationHelper.unlockState(Optional.empty(), Optional.empty(), node(SkillTreeId.SPELL_MASTERY, 1)));
    }

    @Test
    void mastery_lockedWithoutCommitment() {
        assertEquals(UnlockState.LOCKED_NOT_COMMITTED,
                VocationHelper.unlockState(Optional.empty(), Optional.empty(), node(SkillTreeId.SPELL_MASTERY, 2)));
    }

    @Test
    void mastery_fullForPrimary() {
        assertEquals(UnlockState.ALLOWED,
                VocationHelper.unlockState(Optional.of(DUELIST), Optional.empty(), node(SkillTreeId.SPELL_MASTERY, 3)));
    }

    @Test
    void mastery_secondaryOnlyFirstTier() {
        // first Mastery tier (foundationMaxTier + 1 == 2) allowed; deeper tier capped
        assertEquals(UnlockState.ALLOWED,
                VocationHelper.unlockState(Optional.of(DARK), Optional.of(DUELIST), node(SkillTreeId.SPELL_MASTERY, 2)));
        assertEquals(UnlockState.LOCKED_CAPPED_SECONDARY,
                VocationHelper.unlockState(Optional.of(DARK), Optional.of(DUELIST), node(SkillTreeId.SPELL_MASTERY, 3)));
    }

    @Test
    void opposition_hardLocksIncludingFoundation() {
        // Healer (opposed to dark_arts) committed → dark_arts nodes locked even at Foundation.
        assertEquals(UnlockState.LOCKED_OPPOSED,
                VocationHelper.unlockState(Optional.of(HEALER), Optional.empty(), node(SkillTreeId.DARK_ARTS, 1)));
    }

    @Test
    void opposition_isSymmetric() {
        assertTrue(VocationRegistry.areOpposed(DARK, HEALER));
        assertTrue(VocationRegistry.areOpposed(HEALER, DARK));
        assertFalse(VocationRegistry.areOpposed(DUELIST, DARK));
    }

    @Test
    void unownedTree_isUngated() {
        // No Vocation owns GOBLIN_CRAFT → always allowed regardless of tier.
        assertEquals(UnlockState.ALLOWED,
                VocationHelper.unlockState(Optional.of(DUELIST), Optional.empty(), node(SkillTreeId.GOBLIN_CRAFT, 5)));
    }
}
