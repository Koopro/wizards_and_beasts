package at.koopro.wizardsandbeasts.client.spell.trace;

import at.koopro.wizardsandbeasts.network.spell.SpellTraceOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellTraceResultS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.NullMarked;

/**
 * Routes the two tracing payloads to the screen. A new challenge replaces an open one but keeps the
 * screen that was open before the first — the skill web, usually — so closing returns the player there.
 */
@NullMarked
public final class SpellTraceClient {

    /** "Steady hand": the server's setting, told with every challenge (SpellTraceOpenS2CPayload.steadyHand). */
    static boolean steadyHand;

    private SpellTraceClient() {
    }

    public static void open(SpellTraceOpenS2CPayload pkt) {
        Minecraft mc = Minecraft.getInstance();
        Screen previous = mc.screen instanceof SpellTraceScreen open ? open.previous() : mc.screen;
        steadyHand = pkt.steadyHand();
        mc.setScreen(new SpellTraceScreen(previous, pkt));
    }

    public static void result(SpellTraceResultS2CPayload pkt) {
        if (Minecraft.getInstance().screen instanceof SpellTraceScreen screen
                && screen.challengeId() == pkt.challengeId()) {
            screen.onResult(pkt);
        }
    }
}
