package at.koopro.wizardsandbeasts.client.ability;

import at.koopro.wizardsandbeasts.client.ability.wheel.AbilityWheelScreen;
import at.koopro.wizardsandbeasts.network.ability.AbilityUseC2SPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Client input driver for the ability framework. Fires the use/quick keys (server resolves the armed
 * selection / pinned ability) and opens the {@link AbilityWheelScreen} while the wheel key is held. All
 * inputs are inert while a screen is open; the wheel screen manages its own release/close.
 */
@NullMarked
public final class AbilityWheelController {

    private AbilityWheelController() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }

        while (AbilityFrameworkKeyBindings.ABILITY_USE.consumeClick()) {
            ClientPacketDistributor.sendToServer(new AbilityUseC2SPayload(false));
        }
        while (AbilityFrameworkKeyBindings.ABILITY_QUICK.consumeClick()) {
            ClientPacketDistributor.sendToServer(new AbilityUseC2SPayload(true));
        }

        if (!AbilityFrameworkKeyBindings.ABILITY_WHEEL.isUnbound()
                && AbilityFrameworkKeyBindings.ABILITY_WHEEL.isDown()) {
            mc.setScreen(new AbilityWheelScreen());
        }
    }
}
