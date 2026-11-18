package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.network.pose.WandInspectC2SPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Drives {@link SpellKeyBindings#WAND_INSPECT}.
 *
 * <p>The press is consumed either way; the request goes out only with a wand in hand and not in use, so a bare key
 * press is not traffic. The server checks again and decides — the client never plays the gesture on its own, so the
 * inspecting player and everyone watching see it start on the same moment.
 */
@NullMarked
public final class WandInspectInput {

    private WandInspectInput() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        boolean pressed = false;
        while (SpellKeyBindings.WAND_INSPECT.consumeClick()) {
            pressed = true;
        }
        if (pressed && mc.player.getMainHandItem().getItem() instanceof WandItem && !mc.player.isUsingItem()) {
            ClientPacketDistributor.sendToServer(new WandInspectC2SPayload());
        }
    }
}
