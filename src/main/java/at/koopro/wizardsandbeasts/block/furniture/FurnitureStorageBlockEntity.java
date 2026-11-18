package at.koopro.wizardsandbeasts.block.furniture;

import at.koopro.wizardsandbeasts.registry.FurnitureBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NullMarked;

/**
 * The contents of a storage piece. Its size comes from its block ({@link StorageFurnitureBlock#rows()}), its screen is
 * vanilla's chest screen of that many rows, and it takes a loot table like a chest, so a template can stock a
 * wardrobe. The container's own removal spills it when the block goes.
 */
@NullMarked
public class FurnitureStorageBlockEntity extends RandomizableContainerBlockEntity {

    private NonNullList<ItemStack> items;

    public FurnitureStorageBlockEntity(BlockPos pos, BlockState state) {
        super(FurnitureBlocks.FURNITURE_STORAGE.get(), pos, state);
        this.items = NonNullList.withSize(rows() * 9, ItemStack.EMPTY);
    }

    private int rows() {
        return getBlockState().getBlock() instanceof StorageFurnitureBlock storage ? storage.rows() : 3;
    }

    @Override
    public int getContainerSize() {
        return rows() * 9;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        int rows = rows();
        MenuType<ChestMenu> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 4 -> MenuType.GENERIC_9x4;
            default -> MenuType.GENERIC_9x3;
        };
        return new ChestMenu(type, id, inventory, this, rows);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, items);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, items);
        }
    }
}
