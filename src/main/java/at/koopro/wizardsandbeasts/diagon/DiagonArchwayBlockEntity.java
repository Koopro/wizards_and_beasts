package at.koopro.wizardsandbeasts.diagon;

import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NullMarked;

/**
 * One brick of the archway's own story: when it last moved, which ring of the opening it belongs to (how far it is from
 * the tapped brick), which side of the arch it folds into and how far it slides to get there, and the wall's normal
 * (the axis it turns about is in the wall, square to its fold). The renderer plays the brick's part from these; the
 * block's {@code OPEN} state says which way it went.
 */
@NullMarked
public final class DiagonArchwayBlockEntity extends BlockEntity {

    private long changedAt = Long.MIN_VALUE;
    private int ring;
    private float outX;
    private float outY;
    private float outZ;
    private float slide;
    private float normalX;
    private float normalZ;

    public DiagonArchwayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DIAGON_ARCHWAY.get(), pos, state);
    }

    /** Starts this brick's part of an opening or a closing at game time {@code now}. */
    void start(long now, int ring, float outX, float outY, float outZ, float slide, float normalX, float normalZ) {
        this.changedAt = now;
        this.ring = ring;
        this.outX = outX;
        this.outY = outY;
        this.outZ = outZ;
        this.slide = slide;
        this.normalX = normalX;
        this.normalZ = normalZ;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public long changedAt() {
        return changedAt;
    }

    public int ring() {
        return ring;
    }

    public float outX() {
        return outX;
    }

    public float outY() {
        return outY;
    }

    public float outZ() {
        return outZ;
    }

    /** How far the brick slides to vanish into the side of the arch, in blocks. */
    public float slide() {
        return slide;
    }

    /** The wall's normal, horizontal (the arch stands in a vertical wall). */
    public float normalX() {
        return normalX;
    }

    public float normalZ() {
        return normalZ;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("changed_at", changedAt);
        output.putInt("ring", ring);
        output.putFloat("out_x", outX);
        output.putFloat("out_y", outY);
        output.putFloat("out_z", outZ);
        output.putFloat("slide", slide);
        output.putFloat("normal_x", normalX);
        output.putFloat("normal_z", normalZ);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        changedAt = input.getLongOr("changed_at", Long.MIN_VALUE);
        ring = input.getIntOr("ring", 0);
        outX = input.getFloatOr("out_x", 0f);
        outY = input.getFloatOr("out_y", 0f);
        outZ = input.getFloatOr("out_z", 0f);
        slide = input.getFloatOr("slide", 1f);
        normalX = input.getFloatOr("normal_x", 1f);
        normalZ = input.getFloatOr("normal_z", 0f);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
