package at.koopro.wizardsandbeasts.entity.niffler;

import java.util.List;
import java.util.ArrayList;
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
    private final int rows;

    public NifflerPouchMenu(int containerId, Inventory playerInventory, NifflerEntity niffler) {
        super(ModMenuTypes.NIFFLER_POUCH.get(), containerId);
        this.niffler = niffler;
        this.pouch = niffler.getPouch();
        this.isBaby = niffler.isBaby();

        int slots = pouch.getContainerSize(); // 27 adult, 9 baby
        this.rows = rowsFor(slots);
        List<int[]> at = slotOrigins(rows);
        for (int i = 0; i < rows * 9; i++) {
            addSlot(new Slot(pouch, i, at.get(i)[0], at.get(i)[1]));
        }
        int base = rows * 9;
        for (int i = 0; i < 27; i++) {
            int[] o = at.get(base + i);
            addSlot(new Slot(playerInventory, i + 9, o[0], o[1]));
        }
        for (int col = 0; col < 9; col++) {
            int[] o = at.get(base + 27 + col);
            addSlot(new Slot(playerInventory, col, o[0], o[1]));
        }
    }

    // ── Geometry, shared with NifflerPouchScreen ──────────────────────────────
    //
    // Slot x/y are the item's top-left; the screen draws each frame one pixel out from these numbers.

    public static final int GUI_W = 180;
    public static final int POUCH_X = 10;
    public static final int POUCH_Y = 24;
    public static final int INV_X = 10;

    /** Pouch rows: three for an adult's 27 slots, one for a baby's 9. */
    public static int rowsFor(int slots) {
        return slots >= 27 ? 3 : 1;
    }

    /** Bottom edge of the fur around the pouch. */
    public static int furBottom(int rows) {
        return POUCH_Y + rows * 18 + 9;
    }

    public static int invY(int rows) {
        return furBottom(rows) + 14;
    }

    public static int hotbarY(int rows) {
        return invY(rows) + 3 * 18 + 4;
    }

    public static int guiHeight(int rows) {
        return hotbarY(rows) + 18 + 9;
    }

    /** Every slot's origin in slot-index order: the pouch row by row, the 27 inventory slots, the hotbar. */
    public static List<int[]> slotOrigins(int rows) {
        List<int[]> out = new ArrayList<>(rows * 9 + 36);
        for (int i = 0; i < rows * 9; i++) {
            out.add(new int[]{POUCH_X + (i % 9) * 18, POUCH_Y + (i / 9) * 18});
        }
        for (int i = 0; i < 27; i++) {
            out.add(new int[]{INV_X + (i % 9) * 18, invY(rows) + (i / 9) * 18});
        }
        for (int col = 0; col < 9; col++) {
            out.add(new int[]{INV_X + col * 18, hotbarY(rows)});
        }
        return out;
    }

    /** How many pouch rows this menu has. */
    public int pouchRows() {
        return rows;
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
