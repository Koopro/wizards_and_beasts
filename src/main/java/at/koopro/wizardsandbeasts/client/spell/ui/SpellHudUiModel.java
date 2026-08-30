package at.koopro.wizardsandbeasts.client.spell.ui;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;

import org.jspecify.annotations.Nullable;
import java.util.Map;

public record SpellHudUiModel(
        boolean canRenderSpellHud,
        PlayerSpellData spellData,
        @Nullable String topRejectReason
) {
    public static SpellHudUiModel from(boolean canRenderSpellHud, PlayerSpellData spellData) {
        return new SpellHudUiModel(canRenderSpellHud, spellData, findTopRejectReason(spellData.getRejectCounts()));
    }

    private static String findTopRejectReason(Map<String, Integer> rejectCounts) {
        if (rejectCounts.isEmpty()) return null;
        return rejectCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
