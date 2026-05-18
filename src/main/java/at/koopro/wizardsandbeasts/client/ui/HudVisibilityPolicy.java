package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.util.WandHelper;
import net.minecraft.client.Minecraft;

public final class HudVisibilityPolicy {
    private HudVisibilityPolicy() {}

    public static boolean shouldRenderObscurialHud(PlayerHeritageData typeData) {
        if (typeData.getSelectedHeritage() != Heritage.OBSCURIAL) return false;
        String form = typeData.getActiveFormId();
        return ObscurialUiFlags.FORM_DARK.equals(form) || ObscurialUiFlags.FORM_HUMAN.equals(form);
    }

    public static boolean canUseWandMagic(PlayerHeritageData typeData) {
        Heritage type = typeData.getSelectedHeritage();
        HeritageVariant subtype = typeData.getSelectedHeritageVariant();
        return type != null && type.canUseWand() && (subtype == null || !subtype.hasTag("no_wand"));
    }

    public static boolean shouldRenderSpellHud(Minecraft mc, PlayerHeritageData typeData) {
        if (mc.player == null) return false;
        if (!canUseWandMagic(typeData)) return false;
        if (ObscurialUiFlags.FORM_DARK.equals(typeData.getActiveFormId())) return false;
        return WandHelper.isHoldingWand(mc.player);
    }
}
