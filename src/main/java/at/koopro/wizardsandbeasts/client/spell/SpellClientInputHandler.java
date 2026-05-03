package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.client.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.client.state.ClientTypeDataState;
import at.koopro.wizardsandbeasts.client.spell.input.ObscurialInputController;
import at.koopro.wizardsandbeasts.client.spell.input.SpellInputController;
import at.koopro.wizardsandbeasts.client.ui.HudVisibilityPolicy;
import at.koopro.wizardsandbeasts.client.ui.InputPolicy;
import at.koopro.wizardsandbeasts.network.SpellLeviosaAdjustC2SPacket;
import at.koopro.wizardsandbeasts.spell.CastType;
import at.koopro.wizardsandbeasts.spell.SpellIds;
import at.koopro.wizardsandbeasts.spell.Spell;
import at.koopro.wizardsandbeasts.spell.SpellProperties;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class SpellClientInputHandler {
    private static final float LEVIOSA_SCROLL_STEP = 0.75f;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!InputPolicy.canProcessGameplayInput(mc)) return;
        var typeData = ClientTypeDataState.get();
        boolean canUseWandMagic = HudVisibilityPolicy.canUseWandMagic(typeData);
        SpellInputController.handleGameplayBindings(mc, canUseWandMagic);
        ObscurialInputController.handleGameplayBindings(typeData);
    }

    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!mc.player.isUsingItem()) return;
        if (event.getScrollDeltaY() == 0.0) return;
        if (!isLeviosaChannelActive()) return;
        event.setCanceled(true);
        float delta = (float) event.getScrollDeltaY() * LEVIOSA_SCROLL_STEP;
        ClientPacketDistributor.sendToServer(new SpellLeviosaAdjustC2SPacket(delta));
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
