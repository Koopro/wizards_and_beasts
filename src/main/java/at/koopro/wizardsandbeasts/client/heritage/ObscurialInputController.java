package at.koopro.wizardsandbeasts.client.heritage;

import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.client.ui.InputPolicy;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.network.form.ObscurialStressVentC2SPayload;
import at.koopro.wizardsandbeasts.network.form.ObscurialToggleFormC2SPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ObscurialInputController {
    private ObscurialInputController() {}

    public static void handleGameplayBindings(PlayerHeritageData typeData) {
        if (SpellKeyBindings.OBSCURIAL_TOGGLE.consumeClick() && InputPolicy.canToggleObscurialForm(typeData)) {
            ClientPacketDistributor.sendToServer(new ObscurialToggleFormC2SPayload());
        }
        if (SpellKeyBindings.OBSCURIAL_STRESS_VENT.consumeClick() && InputPolicy.canUseStressVent(typeData)) {
            ClientPacketDistributor.sendToServer(new ObscurialStressVentC2SPayload());
        }
        ObscurialAbilityInputController.handleGameplayBindings(typeData);
    }
}
