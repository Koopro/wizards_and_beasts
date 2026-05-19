package at.koopro.wizardsandbeasts.entity.niffler;

import at.koopro.wizardsandbeasts.registry.ModMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.NonNull;

public class NifflerPouchMenu extends AbstractContainerMenu {

    private final NifflerPouchInventory pouch;
    private final NifflerEntity niffler;
    private final boolean isBaby;

    public NifflerPouchMenu(int containerId, Inventory playerInventory, NifflerEntity niffler) {
        super(ModMenuTypes.NIFFLER_POUCH.get(), containerId);
        this.niffler = niffler;
        this.pouch = niffler.getPouch();
        this.isBaby = niffler.isBaby();

        int slots = pouch.getContainerSize(); // 27 adult, 9 baby

        // Pouch slots
        if (slots == 27) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new Slot(pouch, row * 9 + col, 8 + col * 18, 18 + row * 18));
                }
            }
        } else {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(pouch, col, 8 + col * 18, 18));
            }
        }

        // Player inventory (rows)
        int inventoryTop = (slots == 27) ? 84 : 50;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, inventoryTop + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, inventoryTop + 58));
        }
    }

    public static NifflerPouchMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        int entityId = buf.readInt();
        Level level = inv.player.level();
        if (level.getEntity(entityId) instanceof NifflerEntity niffler) {
            return new NifflerPouchMenu(containerId, inv, niffler);
        }
        throw new IllegalStateException("No NifflerEntity with id " + entityId);
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return niffler.isAlive() && niffler.distanceToSqr(player) < 64.0;
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();
            int pouchSize = pouch.getContainerSize();
            if (index < pouchSize) {
                if (!moveItemStackTo(slotStack, pouchSize, slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (!moveItemStackTo(slotStack, 0, pouchSize, false)) return ItemStack.EMPTY;
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    public NifflerEntity getNiffler() { return niffler; }
    public boolean isBabyPouch() { return isBaby; }
}
