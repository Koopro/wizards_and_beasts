package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.RobeHood;
import at.koopro.wizardsandbeasts.network.armor.HoodToggleC2SPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Drives {@link WardrobeKeyBindings#HOOD_TOGGLE}.
 *
 * <p>The keypress is consumed whether or not a hooded robe is worn, but the payload is only sent
 * when there is something to toggle: the key is bound for every player, and a bare {@code H} on a
 * player in a leather tunic should not be traffic.
 */
@NullMarked
public final class WardrobeInputHandler {

    private WardrobeInputHandler() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.screen != null) {
            return;
        }

        boolean pressed = false;

        while (WardrobeKeyBindings.HOOD_TOGGLE.consumeClick()) {
            pressed = true;
        }

        if (pressed && !RobeHood.toggleTarget(mc.player).isEmpty()) {
            ClientPacketDistributor.sendToServer(new HoodToggleC2SPayload());
        }
    }
}
