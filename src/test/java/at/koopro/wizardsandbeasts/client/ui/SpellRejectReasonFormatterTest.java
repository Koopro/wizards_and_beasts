package at.koopro.wizardsandbeasts.client.ui;

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
}
