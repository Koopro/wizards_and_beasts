package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.client.spell.ui.SpellRejectReasonFormatter;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The diagnostic labeller holds no English of its own — it resolves through the same key map the live
 * reject line uses. These assertions are what stop a second, drifting vocabulary from growing back:
 * every label must be a translatable key, not a sentence baked into Java.
 */
class SpellRejectReasonFormatterTest {

    @Test
    void codeResolvesToItsLangKey() {
        assertEquals("wandcraft.cast.reject.cooldown", key("cooldown_active"));
        assertEquals("wandcraft.cast.requires_bond", key(SpellRejectCodes.WAND_NOT_BONDED));
        assertEquals("wandcraft.cast.wrong_master", key(SpellRejectCodes.WAND_WRONG_MASTER));
    }

    @Test
    void detailSuffixIsStripped() {
        assertEquals("wandcraft.cast.reject.cooldown", key("cooldown_active:lumos"));
        assertEquals("wandcraft.cast.reject.dark_form_required",
                key("obscurial_dark_only_spell_outside_dark_form:obscurus_surge"));
    }

    /**
     * Site-owned codes have no live line to draw, but a summary of what a player keeps hitting still
     * has to name them — so the formatter reads the full key map, not the live-render lookup.
     */
    @Test
    void siteOwnedCodesStillGetALabel_unlikeTheLiveRejectLine() {
        assertEquals("wandcraft.ability.reject.requirements",
                key(SpellRejectCodes.ABILITY_REQUIREMENTS_UNMET));
    }

    @Test
    void blankReasonHasItsOwnKey() {
        assertEquals("wandcraft.cast.reject.none", key(""));
    }

    @Test
    void unmappedCodeFallsBackToTheCodeItself_notAFriendlySentence() {
        ComponentContents contents = SpellRejectReasonFormatter.toHudLabel("brand_new_gate:lumos").getContents();
        PlainTextContents plain = assertInstanceOf(PlainTextContents.class, contents,
                "an unrecognised code must surface as the raw code so it can be identified");
        assertEquals("brand_new_gate", plain.text());
    }

    private static String key(String reason) {
        Component label = SpellRejectReasonFormatter.toHudLabel(reason);
        ComponentContents contents = label.getContents();
        assertInstanceOf(TranslatableContents.class, contents,
                "reject labels must be translatable, not hardcoded English");
        return ((TranslatableContents) contents).getKey();
    }
}
