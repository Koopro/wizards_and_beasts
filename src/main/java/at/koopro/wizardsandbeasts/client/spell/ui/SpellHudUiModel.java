package at.koopro.wizardsandbeasts.client.spell.ui;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;

public record SpellHudUiModel(
        boolean canRenderSpellHud,
        PlayerSpellData spellData
) {
    public static SpellHudUiModel from(boolean canRenderSpellHud, PlayerSpellData spellData) {
        return new SpellHudUiModel(canRenderSpellHud, spellData);
    }
}
