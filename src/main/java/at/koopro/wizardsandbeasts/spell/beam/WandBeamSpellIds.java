package at.koopro.wizardsandbeasts.spell.beam;

import at.koopro.wizardsandbeasts.spell.core.*;

import java.util.Set;

final class WandBeamSpellIds {
    private static final String LEVIOSA_ID = "wingardium_leviosa";
    private static final String AGUAMENTI_ID = "aguamenti";

    private static final Set<String> HELD_CHANNEL_SPELL_IDS = Set.of(
            "crucio",
            "avada_kedavra",
            LEVIOSA_ID,
            AGUAMENTI_ID,
            SpellIds.namespaced("crucio"),
            SpellIds.namespaced("avada_kedavra"),
            SpellIds.namespaced(LEVIOSA_ID),
            SpellIds.namespaced(AGUAMENTI_ID)
    );

    private WandBeamSpellIds() {}

    static boolean isHeldChannelSpell(String spellId) {
        return HELD_CHANNEL_SPELL_IDS.contains(spellId);
    }

    static boolean isLeviosa(String spellId) {
        return SpellIds.matches(spellId, LEVIOSA_ID);
    }

    static boolean isAguamenti(String spellId) {
        return SpellIds.matches(spellId, AGUAMENTI_ID);
    }
}
