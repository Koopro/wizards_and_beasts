package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.type.WizType;
import net.minecraft.client.Minecraft;

public final class InputPolicy {
    private InputPolicy() {}

    public static boolean canProcessGameplayInput(Minecraft mc) {
        return mc.player != null && mc.screen == null;
    }

    public static boolean canToggleObscurialForm(PlayerTypeData typeData) {
        return typeData.getSelectedType() == WizType.OBSCURIAL;
    }

    public static boolean canUseStressVent(PlayerTypeData typeData) {
        if (typeData.getSelectedType() != WizType.OBSCURIAL) return false;
        return !ObscurialUiFlags.FORM_DARK.equals(typeData.getActiveFormId());
    }
}
