package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.client.spell.gui.ImperioCommandScreen;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSignatureSpellState;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.spell.core.SpellIds;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Client-only wand release hooks (keeps {@link at.koopro.wizardsandbeasts.item.wand.WandItem} free of client imports).
 */
public final class WandCastClient {

    private WandCastClient() {}

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
