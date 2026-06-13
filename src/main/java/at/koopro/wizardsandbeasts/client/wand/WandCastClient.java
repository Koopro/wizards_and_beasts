package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.client.spell.gui.ImperioCommandScreen;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSignatureSpellState;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.network.spell.SpellCastC2SPayload;
import at.koopro.wizardsandbeasts.spell.core.SpellIds;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Client-only wand release hooks (keeps {@link at.koopro.wizardsandbeasts.item.wand.WandItem} free of client imports).
 */
public final class WandCastClient {

    private WandCastClient() {}

    /**
     * Wand release on the client: opens the Imperio command menu when applicable,
     * otherwise sends the normal cast packet. Invoked from {@code WandItem} via
     * {@code ClientClassBridge} so the common item class carries no client imports.
     *
     * @return always true (boolean to fit the reflection bridge signature)
     */
    public static boolean castOrOpenImperioMenu() {
        if (!tryOpenImperioCommandMenu()) {
            ClientPacketDistributor.sendToServer(new SpellCastC2SPayload());
        }
        return true;
    }

    /**
     * @return true if the normal {@code SpellCastC2SPayload} should be skipped
     */
    public static boolean tryOpenImperioCommandMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return false;
        }
        String activeId = ClientSpellDataState.get().getActiveSpellId();
        if (activeId == null || !SpellIds.matches(activeId, "imperio")) {
            return false;
        }
        @Nullable UUID victim = ClientSignatureSpellState.getImperioBoundVictim();
        if (victim == null) {
            return false;
        }
        mc.setScreen(new ImperioCommandScreen());
        return true;
    }
}
