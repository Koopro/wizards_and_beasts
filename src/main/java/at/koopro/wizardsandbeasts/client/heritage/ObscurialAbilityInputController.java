package at.koopro.wizardsandbeasts.client.heritage;

import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.network.spell.ObscurialAbilityUseC2SPayload;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialAbility;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ObscurialAbilityInputController {
    private ObscurialAbilityInputController() {}

    public static void handleGameplayBindings(PlayerHeritageData typeData) {
        if (!ObscurialRules.isObscurial(typeData) || !ObscurialRules.isDarkForm(typeData)) {
            return;
        }
        if (SpellKeyBindings.OBSCURIAL_ABILITY_PRIMARY.consumeClick()) {
            ClientPacketDistributor.sendToServer(new ObscurialAbilityUseC2SPayload(ObscurialAbility.SURGE.spellId()));
        }
        if (SpellKeyBindings.OBSCURIAL_ABILITY_SECONDARY.consumeClick()) {
            ClientPacketDistributor.sendToServer(new ObscurialAbilityUseC2SPayload(ObscurialAbility.GRASP.spellId()));
        }
    }
}
