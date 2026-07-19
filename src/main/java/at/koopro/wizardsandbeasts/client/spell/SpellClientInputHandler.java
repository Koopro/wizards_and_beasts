package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.client.legilimency.state.ClientLegilimencyVisionState;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSignatureSpellState;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.heritage.ObscurialInputController;
import at.koopro.wizardsandbeasts.client.spell.input.SpellInputController;
import at.koopro.wizardsandbeasts.client.ui.HudVisibilityPolicy;
import at.koopro.wizardsandbeasts.client.ui.InputPolicy;
import at.koopro.wizardsandbeasts.network.form.AnimagusAbilityC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioResistC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellLeviosaAdjustC2SPayload;
import at.koopro.wizardsandbeasts.spell.core.CastType;
import at.koopro.wizardsandbeasts.spell.core.SpellIds;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class SpellClientInputHandler {
    private static final float LEVIOSA_SCROLL_STEP = 0.75f;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!InputPolicy.canProcessGameplayInput(mc)) return;
        var typeData = ClientHeritageDataState.get();
        boolean canUseWandMagic = HudVisibilityPolicy.canUseWandMagic(typeData);
        SpellInputController.handleGameplayBindings(mc, canUseWandMagic);
        ObscurialInputController.handleGameplayBindings(typeData);
        // Apparition / Legilimency / Animagus form now trigger through the ability wheel
        // (AbilityWheelController); only the per-form beast ability keeps a dedicated bind.
        if (SpellKeyBindings.ANIMAGUS_ABILITY.consumeClick()) {
            ClientPacketDistributor.sendToServer(new AnimagusAbilityC2SPayload());
        }
        ClientLegilimencyVisionState.tick();
        if (mc.player != null && mc.screen == null
                && ClientSignatureSpellState.isImperioControlled()
                && mc.options.keyShift.consumeClick()) {
            ClientPacketDistributor.sendToServer(new ImperioResistC2SPayload());
        }
    }

    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!mc.player.isUsingItem()) return;
        if (event.getScrollDeltaY() == 0.0) return;
        if (!isLeviosaChannelActive()) return;
        event.setCanceled(true);
        float delta = (float) event.getScrollDeltaY() * LEVIOSA_SCROLL_STEP;
        ClientPacketDistributor.sendToServer(new SpellLeviosaAdjustC2SPayload(delta));
    }

    private static boolean isLeviosaChannelActive() {
        Spell activeSpell = ClientSpellDataState.get().getActiveSpell();
        if (activeSpell == null) return false;
        SpellProperties props = activeSpell.getProperties();
        if (props == null || props.getCastType() != CastType.BEAM_CHANNEL) return false;
        String id = activeSpell.getId();
        return SpellIds.matches(id, "wingardium_leviosa");
    }
}
