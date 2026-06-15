package at.koopro.wizardsandbeasts.floo.call;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * A live "head in the fire" Floo call session. Transient — not persisted across restarts
 * (calls end when the server stops). The caller's body stays at the origin grate while
 * their voice is bridged to the remote grate.
 */
public final class FlooCall {

    public final UUID caller;
    public final Identifier originDim;
    public final BlockPos originPos;
    public final String targetAddress;
    public final Identifier targetDim;
    public final BlockPos targetPos;
    public int ticksRemaining;

    public FlooCall(@NonNull UUID caller,
                    @NonNull Identifier originDim, @NonNull BlockPos originPos,
                    @NonNull String targetAddress, @NonNull Identifier targetDim, @NonNull BlockPos targetPos,
                    int ticksRemaining) {
        this.caller = caller;
        this.originDim = originDim;
        this.originPos = originPos;
        this.targetAddress = targetAddress;
        this.targetDim = targetDim;
        this.targetPos = targetPos;
        this.ticksRemaining = ticksRemaining;
    }
}
