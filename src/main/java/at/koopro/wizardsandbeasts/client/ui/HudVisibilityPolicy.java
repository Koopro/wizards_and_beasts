package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.util.WandHelper;
import net.minecraft.client.Minecraft;

public final class HudVisibilityPolicy {
    private HudVisibilityPolicy() {}

    public static boolean shouldRenderObscurialHud(PlayerHeritageData typeData) {
        if (!typeData.hasCondition(at.koopro.wizardsandbeasts.heritage.MagicalCondition.OBSCURUS)) return false;
        String form = typeData.getActiveFormId();
        return ObscurialUiFlags.FORM_DARK.equals(form) || ObscurialUiFlags.FORM_HUMAN.equals(form);
    }

    public static boolean canUseWandMagic(PlayerHeritageData typeData) {
        return typeData.canUseWand();
    }

    public static boolean shouldRenderSpellHud(Minecraft mc, PlayerHeritageData typeData) {
        if (mc.player == null) return false;
        if (!canUseWandMagic(typeData)) return false;
        if (ObscurialUiFlags.FORM_DARK.equals(typeData.getActiveFormId())) return false;
        return WandHelper.isHoldingWand(mc.player);
    }
}
