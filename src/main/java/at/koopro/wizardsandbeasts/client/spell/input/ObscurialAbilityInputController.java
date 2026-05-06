package at.koopro.wizardsandbeasts.client.spell.input;

import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.network.ObscurialAbilityUseC2SPacket;
import at.koopro.wizardsandbeasts.type.ObscurialAbility;
import at.koopro.wizardsandbeasts.type.ObscurialRules;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ObscurialAbilityInputController {
    private ObscurialAbilityInputController() {}

    public static void handleGameplayBindings(PlayerHeritageData typeData) {
        if (!ObscurialRules.isObscurial(typeData) || !ObscurialRules.isDarkForm(typeData)) {
            return;
        }
        if (SpellKeyBindings.OBSCURIAL_ABILITY_PRIMARY.consumeClick()) {
            ClientPacketDistributor.sendToServer(new ObscurialAbilityUseC2SPacket(ObscurialAbility.SURGE.spellId()));
        }
        if (SpellKeyBindings.OBSCURIAL_ABILITY_SECONDARY.consumeClick()) {
            ClientPacketDistributor.sendToServer(new ObscurialAbilityUseC2SPacket(ObscurialAbility.GRASP.spellId()));
        }
    }
}
