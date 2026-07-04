package at.koopro.wizardsandbeasts.block.trunk;

import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Server-side state for a placed {@link TrunkBlock}: the packed base case id (from which every
 * per-lock compartment id is derived) and the currently selected lock. Lock count and decoy
 * eligibility are read from the owning {@link TrunkBlock} — they are a property of the block, not
 * persisted here. The overworld return point is intentionally NOT stored on the BE: it is per-player
 * (multiple players may enter the same trunk) and lives in {@code TrunkRegistryData}, captured at
 * entry time as the trunk's own position.
 */
public class TrunkBlockEntity extends BlockEntity {

    private @Nullable UUID baseCaseId;
    private int activeLock = 1;
    private boolean muggleWorthy = false;

    public TrunkBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        super(ModBlockEntities.TRUNK.get(), pos, state);
    }

    private int lockCount() {
        return getBlockState().getBlock() instanceof TrunkBlock trunk ? trunk.lockCount() : 1;
    }

    public @Nullable UUID getBaseCaseId() {
        return baseCaseId;
    }

    public void setBaseCaseId(@NonNull UUID id) {
        this.baseCaseId = id;
        setChanged();
    }

    /** Lazily assigns a base case id the first time a fresh (un-packed) trunk is opened. */
    public @NonNull UUID getOrCreateBaseCaseId() {
        if (baseCaseId == null) {
            baseCaseId = UUID.randomUUID();
            setChanged();
        }
        return baseCaseId;
    }

    /** Clamped to the owning block's lock count (1..N). */
    public int getActiveLock() {
        int count = lockCount();
        if (activeLock < 1) return 1;
        return Math.min(activeLock, count);
    }

    /** Advances to the next lock (wraps N → 1) and returns the new value. */
    public int cycleActiveLock() {
        int count = lockCount();
        int current = getActiveLock();
        activeLock = current >= count ? 1 : current + 1;
        setChanged();
        return activeLock;
    }

    /** Muggle-Worthy disguise: displays as a mundane case, blocks non-owner entry. Single-lock trunks toggle it. */
    public boolean isMuggleWorthy() {
        return muggleWorthy;
    }

    public void setMuggleWorthy(boolean value) {
        this.muggleWorthy = value;
        setChanged();
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (baseCaseId != null) {
            output.store("baseCaseId", UUIDUtil.CODEC, baseCaseId);
        }
        output.store("activeLock", Codec.INT, activeLock);
        output.store("muggleWorthy", Codec.BOOL, muggleWorthy);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        baseCaseId = input.read("baseCaseId", UUIDUtil.CODEC).orElse(null);
        activeLock = input.read("activeLock", Codec.INT).orElse(1);
        muggleWorthy = input.read("muggleWorthy", Codec.BOOL).orElse(false);
    }
}
