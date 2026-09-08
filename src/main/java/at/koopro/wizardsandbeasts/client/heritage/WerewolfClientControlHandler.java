package at.koopro.wizardsandbeasts.client.heritage;

import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfState;
import at.koopro.wizardsandbeasts.mixin.client.ClientInputAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The client half of loss of control: the werewolf's own keyboard stops answering.
 *
 * <p>This is a courtesy layer, not the enforcement. The server drives the body and snaps back a player
 * who walks away from the drive regardless of what the client does (see {@code FeralController}); what
 * this adds is that a cooperating client does not spend the night visibly fighting its own server —
 * without it, held movement keys and the server's velocity packets pull the player in two directions
 * and the result stutters.
 *
 * <p>Two things are taken:
 * <ul>
 *   <li><b>Movement input.</b> Both halves of it — the key presses (which are what gets sent to the
 *       server) <em>and</em> the derived move vector (which is what actually moves the local player).
 *       Clearing only the first is the trap here; see {@link ClientInputAccessor}.</li>
 *   <li><b>Container screens.</b> Opening one is refused. Only {@link AbstractContainerScreen} is
 *       blocked, so chat, the pause menu, the death screen and every non-inventory screen still work —
 *       a player whose keys have stopped answering must always be able to reach the menu that lets them
 *       leave.</li>
 * </ul>
 *
 * <p>Camera control is not taken here. It is taken from the server, which pushes absolute rotation
 * packets at the client on an interval; doing it in both places would compound into a fight.
 */
public final class WerewolfClientControlHandler {

    private WerewolfClientControlHandler() {}

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!isFeral()) {
            return;
        }
        ClientInput input = event.getInput();
        input.keyPresses = Input.EMPTY;
        ((ClientInputAccessor) input).wandb$setMoveVector(Vec2.ZERO);
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (isFeral() && event.getNewScreen() instanceof AbstractContainerScreen<?>) {
            event.setCanceled(true);
        }
    }

    /**
     * Reads the flag the server synced, not a local re-derivation of "is it a full moon".
     *
     * <p>The custom-flag map rides {@code HeritageDataSyncS2CPayload} to the owning client already, so
     * this is the same authority the server enforces on — which is the only way the two halves cannot
     * disagree.
     */
    private static boolean isFeral() {
        return WerewolfState.isLossOfControl(ClientHeritageDataState.get());
    }
}
