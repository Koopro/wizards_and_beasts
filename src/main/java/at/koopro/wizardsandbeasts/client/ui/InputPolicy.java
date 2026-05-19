package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.client.Minecraft;

public final class InputPolicy {
    private InputPolicy() {}

    public static boolean canProcessGameplayInput(Minecraft mc) {
        return mc.player != null && mc.screen == null;
    }

    public static boolean canToggleObscurialForm(PlayerHeritageData typeData) {
        return typeData.getSelectedHeritage() == Heritage.OBSCURIAL;
    }

    public static boolean canUseStressVent(PlayerHeritageData typeData) {
        if (typeData.getSelectedHeritage() != Heritage.OBSCURIAL) return false;
        return !ObscurialUiFlags.FORM_DARK.equals(typeData.getActiveFormId());
    }
}
