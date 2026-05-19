package at.koopro.wizardsandbeasts.block.floo;

import at.koopro.wizardsandbeasts.network.floo.FlooBlockSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

public class FlooFireplaceBlockEntity extends BlockEntity {

    public static final int LIT_TIMEOUT_TICKS = 600;

    private String networkAddress = "";
    private boolean registered = false;
    private boolean enabled = true;
    private int litTicksRemaining = 0;

    public FlooFireplaceBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        super(ModBlockEntities.FLOO_FIREPLACE.get(), pos, state);
    }

    public static void serverTick(@NonNull Level level, @NonNull BlockPos pos,
                                   @NonNull BlockState state, @NonNull FlooFireplaceBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!state.getValue(FlooFireplaceBlock.LIT)) return;
        be.litTicksRemaining--;
        if (be.litTicksRemaining <= 0) {
            be.litTicksRemaining = 0;
            level.setBlock(pos, state.setValue(FlooFireplaceBlock.LIT, false), 3);
            be.setChanged();
            FlooBlockSyncS2CPayload.sendToNear(sl, pos, false, 0);
        }
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("networkAddress", Codec.STRING, networkAddress);
        output.store("registered", Codec.BOOL, registered);
        output.store("enabled", Codec.BOOL, enabled);
        output.store("litTicksRemaining", Codec.INT, litTicksRemaining);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        networkAddress = input.read("networkAddress", Codec.STRING).orElse("");
        registered = input.read("registered", Codec.BOOL).orElse(false);
        enabled = input.read("enabled", Codec.BOOL).orElse(true);
        litTicksRemaining = input.read("litTicksRemaining", Codec.INT).orElse(0);
    }

    @NonNull
    public String getNetworkAddress() {
        return networkAddress;
    }

    public void setNetworkAddress(@NonNull String networkAddress) {
        this.networkAddress = networkAddress;
        setChanged();
    }

    public boolean isRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
        setChanged();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        setChanged();
    }

    public int getLitTicksRemaining() {
        return litTicksRemaining;
    }

    public void setLitTicksRemaining(int litTicksRemaining) {
        this.litTicksRemaining = litTicksRemaining;
        setChanged();
    }
}
