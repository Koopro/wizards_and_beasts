package at.koopro.wizardsandbeasts.spell.tuning;

import at.koopro.wizardsandbeasts.admin.spell.SpellProperty;
import at.koopro.wizardsandbeasts.admin.spell.SpellSettingIds;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tuning layer every spell accessor reads. The load-bearing property is the first test: with nothing
 * overridden and default globals, every accessor returns the authored value exactly, so a server that never
 * opens the Control Center plays as before.
 */
class SpellTuningTest {

    private static final String STUPEFY = "wizards_and_beasts:stupefy";

    @AfterEach
    void reset() {
        SpellTuning.clearRemote();
        SpellTuning.publishLocal(SpellTuningSnapshot.EMPTY);
    }

    @Test
    void untouchedTuningReturnsAuthoredValues() {
        SpellTuning.publishLocal(SpellTuningSnapshot.EMPTY);
        assertTrue(SpellTuning.enabled(STUPEFY));
        assertEquals(40, SpellTuning.cooldownTicks(STUPEFY, 40));
        assertEquals(0, SpellTuning.cooldownTicks(STUPEFY, 0), "a zero cooldown stays zero");
        assertEquals(6.0f, SpellTuning.damage(STUPEFY, 6.0f));
        assertEquals(20.0f, SpellTuning.range(STUPEFY, 20.0f));
        assertFalse(SpellTuning.rangeAdjusted(STUPEFY));
        assertSame(SpellRequirement.NONE, SpellTuning.requirement(STUPEFY, SpellRequirement.NONE));
        assertEquals("skill_a", SpellTuning.requiredSkill(STUPEFY, "skill_a"));
    }

    @Test
    void overridesReplaceAuthoredValues() {
        SpellOverride override = SpellOverride.NONE.withEnabled(Optional.of(false)).withCooldownTicks(Optional.of(10))
                .withDamage(Optional.of(9.0f)).withRange(Optional.of(30.0f))
                .withRequirement(Optional.of("wizards_and_beasts:lumos@proficient")).withRequiredSkill(Optional.of(""));
        SpellTuning.publishLocal(new SpellTuningSnapshot(Map.of(STUPEFY, override), SpellTuningSnapshot.Globals.DEFAULT));
        assertFalse(SpellTuning.enabled(STUPEFY));
        assertEquals(10, SpellTuning.cooldownTicks(STUPEFY, 40));
        assertEquals(9.0f, SpellTuning.damage(STUPEFY, 6.0f));
        assertEquals(30.0f, SpellTuning.range(STUPEFY, 20.0f));
        SpellRequirement requirement = SpellTuning.requirement(STUPEFY, SpellRequirement.NONE);
        assertEquals("wizards_and_beasts:lumos", requirement.getPrerequisiteId());
        assertEquals(Proficiency.PROFICIENT, requirement.getMinProficiency());
        assertNull(SpellTuning.requiredSkill(STUPEFY, "skill_a"), "an empty override removes the requirement");
        assertTrue(SpellTuning.enabled("wizards_and_beasts:lumos"), "other spells are untouched");
    }

    @Test
    void globalMultipliersApplyOnTopOfOverrides() {
        SpellTuning.publishLocal(new SpellTuningSnapshot(
                Map.of(STUPEFY, SpellOverride.NONE.withDamage(Optional.of(10.0f))),
                new SpellTuningSnapshot.Globals(2.0f, 0.5f, 1.5f, true, true)));
        assertEquals(20.0f, SpellTuning.damage(STUPEFY, 6.0f));
        assertEquals(20, SpellTuning.cooldownTicks(STUPEFY, 40));
        assertEquals(1, SpellTuning.cooldownTicks(STUPEFY, 1), "never below one tick once scaled");
        assertEquals(30.0f, SpellTuning.range(STUPEFY, 20.0f));
        assertEquals(0.0f, SpellTuning.range(STUPEFY, 0.0f), "an unused range stays unused");
        assertTrue(SpellTuning.rangeAdjusted(STUPEFY));
    }

    @Test
    void theRemoteLayerWinsWhileConnectedAndClearsOnLeaving() {
        SpellTuning.publishLocal(new SpellTuningSnapshot(Map.of(), new SpellTuningSnapshot.Globals(1.0f, 1.0f, 1.0f, true, true)));
        SpellTuning.acceptRemote(new SpellTuningSnapshot(
                Map.of(STUPEFY, SpellOverride.NONE.withCooldownTicks(Optional.of(5))), SpellTuningSnapshot.Globals.DEFAULT));
        assertEquals(5, SpellTuning.cooldownTicks(STUPEFY, 40), "the server's numbers, not this client's config");
        SpellTuning.clearRemote();
        assertEquals(40, SpellTuning.cooldownTicks(STUPEFY, 40));
    }

    @Test
    void everyPublishBumpsTheVersion() {
        long before = SpellTuning.version();
        SpellTuning.publishLocalGlobals(SpellTuningSnapshot.Globals.DEFAULT);
        assertTrue(SpellTuning.version() > before, "cached range copies are keyed on this");
    }

    @Test
    void emptyOverridesAreDropped() {
        SpellTuningSnapshot snapshot = new SpellTuningSnapshot(Map.of(STUPEFY, SpellOverride.NONE), SpellTuningSnapshot.Globals.DEFAULT);
        assertTrue(snapshot.overrides().isEmpty());
    }

    // ── requirement text ──

    @Test
    void requirementTextRoundTrips() {
        for (String text : new String[] {"none", "wizards_and_beasts:lumos", "wizards_and_beasts:stupefy@mastered",
                "wizards_and_beasts:lumos+wizards_and_beasts:nox@proficient"}) {
            SpellRequirement parsed = SpellRequirementText.parse(text);
            assertNotNull(parsed, text);
            assertEquals(text, SpellRequirementText.format(parsed), text);
        }
    }

    @Test
    void requirementTextNormalisesBareIds() {
        assertEquals("wizards_and_beasts:lumos@novice", SpellRequirementText.format(SpellRequirementText.parse(" Lumos@NOVICE ")));
    }

    @Test
    void malformedRequirementTextIsRejected() {
        for (String junk : new String[] {"lumos@grandmaster", "Not An Id!", "a++b", "@proficient", "x".repeat(3) + "+" + "y+".repeat(9) + "z"}) {
            assertNull(SpellRequirementText.parse(junk), junk);
        }
    }

    // ── setting ids ──

    @Test
    void spellSettingIdsRoundTrip() {
        Identifier id = SpellSettingIds.of(STUPEFY, SpellProperty.COOLDOWN_TICKS);
        assertEquals("spell/wizards_and_beasts/stupefy/cooldown_ticks", id.getPath());
        SpellSettingIds.Parsed parsed = SpellSettingIds.parse(id);
        assertNotNull(parsed);
        assertEquals(STUPEFY, parsed.spellId());
        assertEquals(SpellProperty.COOLDOWN_TICKS, parsed.property());
        assertEquals("othermod:folder/bolt", SpellSettingIds.parse(SpellSettingIds.of("othermod:folder/bolt", SpellProperty.ENABLED)).spellId());
    }

    @Test
    void malformedSpellSettingIdsAreNotSpellSettings() {
        assertNull(SpellSettingIds.parse(Identifier.fromNamespaceAndPath("wizards_and_beasts", "spell/stupefy")));
        assertNull(SpellSettingIds.parse(Identifier.fromNamespaceAndPath("wizards_and_beasts", "spell/ns/stupefy/nonsense")));
        assertNull(SpellSettingIds.parse(Identifier.fromNamespaceAndPath("othermod", "spell/ns/x/enabled")));
    }
}
