package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.Spell;
import at.koopro.wizardsandbeasts.spell.Spells;

import javax.annotation.Nullable;
import java.util.Map;

public record SpellHudUiModel(
        boolean canRenderSpellHud,
        PlayerSpellData spellData,
        @Nullable String topRejectReason
) {
    public static SpellHudUiModel from(boolean canRenderSpellHud, PlayerSpellData spellData) {
        return new SpellHudUiModel(canRenderSpellHud, spellData, findTopRejectReason(spellData.getRejectCounts()));
    }

    @Nullable
    public Spell activeSpell() {
        String spellId = spellData.getActiveSpellId();
        return spellId == null ? null : Spells.byId(spellId);
    }

    private static String findTopRejectReason(Map<String, Integer> rejectCounts) {
        if (rejectCounts.isEmpty()) return null;
        return rejectCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
