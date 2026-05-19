package at.koopro.wizardsandbeasts.entity.niffler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * The Niffler's belly pouch — a SimpleContainer with NBT persistence.
 * Adults hold 27 slots; babies hold 9 slots (controlled at construction time).
 */
public class NifflerPouchInventory extends SimpleContainer {

    public NifflerPouchInventory(int slots) {
        super(slots);
    }

    public boolean isFull() {
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) return false;
        }
        return true;
    }

    public void save(ValueOutput output) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) items.add(stack);
        }
        output.store("PouchInventory", ItemStack.CODEC.listOf(), items);
    }

    public void load(ValueInput input) {
        clearContent();
        List<ItemStack> items = input.read("PouchInventory", ItemStack.CODEC.listOf())
                .orElse(List.of());
        for (int i = 0; i < items.size() && i < getContainerSize(); i++) {
            setItem(i, items.get(i));
        }
    }

    public void dropAll(NifflerEntity owner) {
        if (!(owner.level() instanceof ServerLevel serverLevel)) return;
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) {
                owner.spawnAtLocation(serverLevel, stack);
            }
        }
        clearContent();
    }
}
