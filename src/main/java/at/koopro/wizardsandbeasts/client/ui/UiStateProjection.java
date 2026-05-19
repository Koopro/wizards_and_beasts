package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.client.spell.ui.SpellHudUiModel;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.client.Minecraft;

public final class UiStateProjection {
    private UiStateProjection() {}

    public static ObscurialUiModel obscurialHud() {
        PlayerHeritageData typeData = ClientHeritageDataState.get();
        return new ObscurialUiModel(
                typeData.getSelectedHeritage() == Heritage.OBSCURIAL,
                typeData.getActiveFormId(),
                parseMeter(typeData.getFlag(ObscurialUiFlags.FLAG_DRAIN), ObscurialUiFlags.MAX_METER),
                parseMeter(typeData.getFlag(ObscurialUiFlags.FLAG_CHARGE), ObscurialUiFlags.MAX_METER),
                parseMeter(typeData.getFlag(ObscurialUiFlags.FLAG_STRESS), 0.0f),
                parseLong(typeData.getFlag(ObscurialUiFlags.FLAG_LOCKOUT_UNTIL), 0L),
                parseLong(typeData.getFlag(ObscurialUiFlags.FLAG_VENT_COOLDOWN_UNTIL), 0L),
                "true".equals(typeData.getFlag(ObscurialUiFlags.FLAG_RAGE_ACTIVE))
        );
    }

    public static SpellHudUiModel spellHud(Minecraft mc) {
        PlayerHeritageData typeData = ClientHeritageDataState.get();
        PlayerSpellData spellData = ClientSpellDataState.get();
        boolean canRender = HudVisibilityPolicy.shouldRenderSpellHud(mc, typeData);
        return SpellHudUiModel.from(canRender, spellData);
    }

    private static float parseMeter(String value, float fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            float parsed = Float.parseFloat(value);
            return Math.max(0.0f, Math.min(ObscurialUiFlags.MAX_METER, parsed));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
