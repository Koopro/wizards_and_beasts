package at.koopro.wizardsandbeasts.client.legilimency.state;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

public final class ClientLegilimencyVisionState {
    private static @Nullable BlockPos markerPos;
    private static int ticksRemaining;

    private ClientLegilimencyVisionState() {
    }

    public static void set(BlockPos pos, int durationTicks) {
        markerPos = pos;
        ticksRemaining = Math.max(0, durationTicks);
    }

    public static void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
            if (ticksRemaining == 0) {
                markerPos = null;
            }
        }
    }

    public static @Nullable BlockPos markerPos() {
        return markerPos;
    }
}
