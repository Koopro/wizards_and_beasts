package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.type.WizSubtype;
import at.koopro.wizardsandbeasts.type.WizType;
import at.koopro.wizardsandbeasts.util.WandHelper;
import net.minecraft.client.Minecraft;

public final class HudVisibilityPolicy {
    private HudVisibilityPolicy() {}

    public static boolean shouldRenderObscurialHud(PlayerTypeData typeData) {
        if (typeData.getSelectedType() != WizType.OBSCURIAL) return false;
        String form = typeData.getActiveFormId();
        return ObscurialUiFlags.FORM_DARK.equals(form) || ObscurialUiFlags.FORM_HUMAN.equals(form);
    }

    public static boolean canUseWandMagic(PlayerTypeData typeData) {
        WizType type = typeData.getSelectedType();
        WizSubtype subtype = typeData.getSelectedSubtype();
        return type != null && type.canUseWand() && subtype != WizSubtype.SQUIB;
    }

    public static boolean shouldRenderSpellHud(Minecraft mc, PlayerTypeData typeData) {
        if (mc.player == null) return false;
        if (!canUseWandMagic(typeData)) return false;
        if (ObscurialUiFlags.FORM_DARK.equals(typeData.getActiveFormId())) return false;
        return WandHelper.isHoldingWand(mc.player);
    }
}
