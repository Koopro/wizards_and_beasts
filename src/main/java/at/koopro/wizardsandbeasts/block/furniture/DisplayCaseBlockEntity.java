package at.koopro.wizardsandbeasts.block.furniture;

import at.koopro.wizardsandbeasts.registry.FurnitureBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NullMarked;

/** The one thing in a display case: saved with the block, sent to clients so they can draw it, spilled when broken. */
@NullMarked
public class DisplayCaseBlockEntity extends BlockEntity {

    private ItemStack shown = ItemStack.EMPTY;

    public DisplayCaseBlockEntity(BlockPos pos, BlockState state) {
        super(FurnitureBlocks.DISPLAY_CASE.get(), pos, state);
    }

    public ItemStack shown() {
        return shown;
    }

    /** Shows {@code stack} (empty to clear) and returns what was shown before. */
    public ItemStack show(ItemStack stack) {
        ItemStack before = shown;
        shown = stack;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return before;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        shown = input.read("shown", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!shown.isEmpty()) {
            output.store("shown", ItemStack.OPTIONAL_CODEC, shown);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !shown.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), shown);
        }
    }
}
