package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.client.spell.ui.SpellRejectReasonFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellRejectReasonFormatterTest {

    @Test
    void toHudLabel_mapsCanonicalCooldownReason() {
        assertEquals("Spell recharging", SpellRejectReasonFormatter.toHudLabel("cooldown_active:lumos"));
    }

    @Test
    void toHudLabel_mapsCanonicalDarkFormReason() {
        assertEquals("Ability requires dark form",
                SpellRejectReasonFormatter.toHudLabel("obscurial_dark_only_spell_outside_dark_form:obscurus_surge"));
    }

    @Test
    void toHudLabel_mapsWandBondReasons() {
        assertEquals("Wand has not chosen you", SpellRejectReasonFormatter.toHudLabel("wand_not_bonded"));
        assertEquals("Wand serves another", SpellRejectReasonFormatter.toHudLabel("wand_wrong_master"));
    }
}
