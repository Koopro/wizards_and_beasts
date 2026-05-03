package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.client.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.client.state.ClientTypeDataState;
import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.type.WizType;
import net.minecraft.client.Minecraft;

public final class UiStateProjection {
    private UiStateProjection() {}

    public static ObscurialUiModel obscurialHud() {
        PlayerTypeData typeData = ClientTypeDataState.get();
        return new ObscurialUiModel(
                typeData.getSelectedType() == WizType.OBSCURIAL,
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
        PlayerTypeData typeData = ClientTypeDataState.get();
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
