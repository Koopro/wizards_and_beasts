package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.network.debug.DebugInspectResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/**
 * Client landing pads for the debug-panel payloads. Kept beside the state they write, the way every
 * other client handler in this mod is.
 */
@NullMarked
public final class DebugClientPayloadHandlers {

    private DebugClientPayloadHandlers() {}

    public static void setDebugMode(boolean enabled) {
        ClientDebugPanelState.setDebugMode(enabled);
    }

    public static void setInspectResult(DebugInspectResultPayload payload) {
        if (payload.lines().isEmpty()) {
            ClientDebugPanelState.clearTarget();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        ClientDebugPanelState.setTarget(new Vec3(payload.x(), payload.y(), payload.z()),
                payload.title(), payload.lines(), now);
    }
}
