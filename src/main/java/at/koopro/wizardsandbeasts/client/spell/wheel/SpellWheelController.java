package at.koopro.wizardsandbeasts.client.spell.wheel;

import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.client.ui.HudVisibilityPolicy;
import at.koopro.wizardsandbeasts.client.ui.InputPolicy;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Opens {@link SpellWheelScreen} on the rising edge of {@link SpellKeyBindings#SPELL_WHEEL}.
 *
 * <p>Separate from {@code SpellInputController} (which drives the other spell binds through
 * {@code consumeClick}) because the wheel needs the <b>physical</b> key state, not the queued clicks:
 * {@code KeyMapping.isDown()} does not report a key held across an open {@link net.minecraft.client.gui.screens.Screen},
 * and the wheel's whole gesture is "still held / just released". Polled raw here, exactly as
 * {@code AbilityWheelController} does for its own wheel.
 */
@NullMarked
public final class SpellWheelController {

    /** Physical key state last tick — the wheel opens on the rising edge, never while merely held. */
    private static boolean wheelWasDown;
    /** Set when the wheel closes on its own key, so the still-held key cannot immediately reopen it. */
    private static boolean suppressOpenUntilRelease;

    private SpellWheelController() {}

    /** Called by the wheel when its own key closed it. */
    public static void suppressOpenUntilRelease() {
        suppressOpenUntilRelease = true;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // Read before the screen check so the edge stays accurate while the wheel is open.
        boolean down = isWheelKeyDown(mc);
        boolean rising = down && !wheelWasDown && !suppressOpenUntilRelease;
        wheelWasDown = down;
        if (!down) {
            suppressOpenUntilRelease = false;
        }

        if (!rising || mc.player == null || mc.screen != null) {
            return;
        }
        if (!InputPolicy.canProcessGameplayInput(mc)) {
            return;
        }
        // Both gates the wheel would otherwise sidestep: a disabled module, and a heritage that casts
        // without a wand at all. The server refuses the assign either way; this keeps a player from
        // being shown a menu whose every entry would bounce.
        if (!ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
            return;
        }
        if (!HudVisibilityPolicy.canUseWandMagic(ClientHeritageDataState.get())) {
            return;
        }
        mc.setScreen(new SpellWheelScreen());
    }

    private static boolean isWheelKeyDown(Minecraft mc) {
        InputConstants.Key key = SpellKeyBindings.SPELL_WHEEL.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() == InputConstants.UNKNOWN.getValue()) {
            return false;
        }
        return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
    }
}
