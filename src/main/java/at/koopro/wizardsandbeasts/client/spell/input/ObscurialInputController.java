package at.koopro.wizardsandbeasts.client.spell.input;

import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.client.ui.InputPolicy;
import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.network.ObscurialStressVentC2SPacket;
import at.koopro.wizardsandbeasts.network.ObscurialToggleFormC2SPacket;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ObscurialInputController {
    private ObscurialInputController() {}

    public static void handleGameplayBindings(PlayerTypeData typeData) {
        if (SpellKeyBindings.OBSCURIAL_TOGGLE.consumeClick() && InputPolicy.canToggleObscurialForm(typeData)) {
            ClientPacketDistributor.sendToServer(new ObscurialToggleFormC2SPacket());
        }
        if (SpellKeyBindings.OBSCURIAL_STRESS_VENT.consumeClick() && InputPolicy.canUseStressVent(typeData)) {
            ClientPacketDistributor.sendToServer(new ObscurialStressVentC2SPacket());
        }
        ObscurialAbilityInputController.handleGameplayBindings(typeData);
    }
}
