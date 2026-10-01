package at.koopro.wizardsandbeasts.spell.beam;

import at.koopro.wizardsandbeasts.visual.beam.BeamVisualDefaults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The beam look table and the beam channel name the same spells: a new held-channel spell without a look would draw
 * nothing and never show in the Visuals browser; a look for a spell the channel refuses would be an editor for nothing.
 */
class BeamSpellCoverageTest {

    @Test
    void everyChannelSpellHasALookAndEveryLookAChannel() {
        for (String spell : BeamVisualDefaults.SPELLS) {
            assertTrue(WandBeamSpellIds.isHeldChannelSpell(spell), spell + " has a look but the channel refuses it");
            assertTrue(WandBeamSpellIds.isHeldChannelSpell("wizards_and_beasts:" + spell), spell + " namespaced");
            assertTrue(BeamVisualDefaults.forSpell(spell, 0xFFFFFF) != null, spell);
        }
    }
}
