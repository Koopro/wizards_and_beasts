package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModMenuTypes;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Item-backed 54-slot container for Hermione's Bag (Undetectable Extension Charm).
 * <p>
 * Contents persist inside the bag {@link ItemStack} via the
 * {@link ModDataComponents#HERMIONES_BAG_INVENTORY} component. The bag cannot be nested inside itself.
 *
 * <h2>Why the open bag is pinned (2026-09-29, documentation/MULTIPLAYER_AUDIT.md)</h2>
 * Contents used to be written back only when the menu closed, and only into whatever bag was in the hand at that
 * moment, while the open bag's own hotbar slot was an ordinary slot of this menu. So a player could take an item
 * out, pick the bag itself up onto the cursor (the menu then closed with nothing in the hand to write to) and keep
 * both the item and a bag that still listed it. Dying with the bag open did the same. Now:
 * <ul>
 *   <li>the opened stack is remembered, and the menu is valid only while that exact stack is in the hand;</li>
 *   <li>its slot cannot be picked up, placed into, thrown, or swapped by a hotbar key;</li>
 *   <li>every change to the contents is written into that stack at once, so there is never a moment when the
 *       bag and the player's inventory disagree about where an item is.</li>
 * </ul>
 */
public class HermionesBagMenu extends AbstractContainerMenu {

    public static final int ROWS = 6;
    public static final int COLS = 9;
    public static final int SIZE = ROWS * COLS;
    // ── Geometry, shared with HermionesBagScreen ──────────────────────────────
    //
    // Slot x/y are the item's top-left; the screen draws each frame one pixel out from these same numbers.

    /** The whole screen: the bag (with its clasp above) over a parchment strip for the player's inventory. */
    public static final int GUI_W = 180;
    public static final int GUI_H = 239;
    /** The bag's 6x9 grid. */
    public static final int BAG_X = 10;
    public static final int BAG_Y = 24;
    /** Player inventory: three rows, then the hotbar a vanilla 4px gap below. */
    public static final int INV_X = 10;
    public static final int INV_Y = 156;
    public static final int HOTBAR_Y = INV_Y + 3 * 18 + 4;

    /**
     * Every slot's origin in slot-index order: the 54 bag slots row by row, the 27 inventory slots, the 9
     * hotbar slots. {@code quickMoveStack} relies on that order.
     */
    public static List<int[]> slotOrigins() {
        List<int[]> out = new java.util.ArrayList<>(SIZE + 36);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                out.add(new int[]{BAG_X + col * 18, BAG_Y + row * 18});
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                out.add(new int[]{INV_X + col * 18, INV_Y + row * 18});
            }
        }
        for (int col = 0; col < 9; col++) {
            out.add(new int[]{INV_X + col * 18, HOTBAR_Y});
        }
        return out;
    }

    /** The {@code SWAP} button that means "the offhand". */
    private static final int OFFHAND_SWAP_BUTTON = 40;

    /** Positional codec — keeps empty slots so item positions survive a save/load round trip. */
    private static final Codec<List<ItemStack>> ITEMS_CODEC = ItemStack.OPTIONAL_CODEC.listOf();
    private static final String ITEMS_KEY = "Items";

    private final Container bag;
    private final Player owner;
    private final InteractionHand hand;
    /** The bag stack this menu was opened from; its contents are written here and nowhere else. */
    private final ItemStack openedBag;
    /** Hotbar index holding the open bag, or -1 when it is in the offhand (not a slot of this menu). */
    private final int lockedHotbar;
    /** Menu slot index of the open bag, or -1. */
    private final int lockedSlot;

    /** Server constructor — loads the held bag's stored contents and writes every change back into it. */
    public HermionesBagMenu(int containerId, Inventory playerInventory, InteractionHand hand) {
        this(containerId, playerInventory, hand, loadContents(playerInventory.player, hand));
        if (this.bag instanceof SimpleContainer simple && !owner.level().isClientSide()) {
            simple.addListener(changed -> save());
        }
    }

    private HermionesBagMenu(int containerId, Inventory playerInventory, InteractionHand hand, Container bag) {
        super(ModMenuTypes.HERMIONES_BAG.get(), containerId);
        this.owner = playerInventory.player;
        this.hand = hand;
        this.bag = bag;
        this.openedBag = owner.getItemInHand(hand);
        this.lockedHotbar = hand == InteractionHand.MAIN_HAND ? playerInventory.getSelectedSlot() : -1;
        checkContainerSize(bag, SIZE);
        bag.startOpen(this.owner);

        List<int[]> at = slotOrigins();
        // Bag grid (6 x 9)
        for (int i = 0; i < SIZE; i++) {
            addSlot(new BagSlot(bag, i, at.get(i)[0], at.get(i)[1]));
        }
        // Player inventory, then the hotbar.
        for (int i = 0; i < 27; i++) {
            int[] o = at.get(SIZE + i);
            addSlot(new Slot(playerInventory, i + 9, o[0], o[1]));
        }
        int locked = -1;
        for (int col = 0; col < 9; col++) {
            int[] o = at.get(SIZE + 27 + col);
            if (col == lockedHotbar) {
                locked = slots.size();
                addSlot(new PinnedSlot(playerInventory, col, o[0], o[1]));
            } else {
                addSlot(new Slot(playerInventory, col, o[0], o[1]));
            }
        }
        this.lockedSlot = locked;
    }

    /** Menu slot index of the open bag (it cannot be moved while open), or -1 when held in the offhand. */
    public int lockedSlotIndex() {
        return lockedSlot;
    }

    /** Client factory — an empty local container; vanilla syncs slot contents over the menu. */
    public static HermionesBagMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        return new HermionesBagMenu(containerId, inv, hand, new SimpleContainer(SIZE));
    }

    private static Container loadContents(Player player, InteractionHand hand) {
        SimpleContainer container = new SimpleContainer(SIZE);
        ItemStack bagStack = player.getItemInHand(hand);
        CompoundTag tag = bagStack.get(ModDataComponents.HERMIONES_BAG_INVENTORY.get());
        if (tag != null) {
            Tag itemsTag = tag.get(ITEMS_KEY);
            if (itemsTag != null) {
                RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, player.level().registryAccess());
                List<ItemStack> items = ITEMS_CODEC.parse(ops, itemsTag).result().orElse(List.of());
                for (int i = 0; i < items.size() && i < SIZE; i++) {
                    container.setItem(i, items.get(i));
                }
            }
        }
        return container;
    }

    /** Writes the current contents into the bag this menu was opened from. Server side only. */
    private void save() {
        if (owner.level().isClientSide() || !(openedBag.getItem() instanceof HermionesBagItem)) {
            return;
        }
        List<ItemStack> items = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            items.add(bag.getItem(i));
        }
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, owner.level().registryAccess());
        Tag itemsTag = ITEMS_CODEC.encodeStart(ops, items).result().orElse(null);
        if (itemsTag != null) {
            CompoundTag tag = new CompoundTag();
            tag.put(ITEMS_KEY, itemsTag);
            openedBag.set(ModDataComponents.HERMIONES_BAG_INVENTORY.get(), tag);
        }
    }

    @Override
    public void removed(@NonNull Player player) {
        super.removed(player);
        bag.stopOpen(player);
        save();
    }

    /** Valid only while the very stack that was opened is still in the opening hand. */
    @Override
    public boolean stillValid(@NonNull Player player) {
        return player.getItemInHand(hand) == openedBag && openedBag.getItem() instanceof HermionesBagItem;
    }

    /** The open bag cannot be clicked, thrown or hotbar-swapped out from under its own menu. */
    @Override
    public void clicked(int slotId, int button, @NonNull ClickType clickType, @NonNull Player player) {
        if (slotId >= 0 && slotId == lockedSlot) {
            return;
        }
        if (clickType == ClickType.SWAP && (button == lockedHotbar
                || (hand == InteractionHand.OFF_HAND && button == OFFHAND_SWAP_BUTTON))) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem() && slot.mayPickup(player)) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();
            if (index < SIZE) {
                if (!moveItemStackTo(slotStack, SIZE, slots.size(), true)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(slotStack, 0, SIZE, false)) {
                return ItemStack.EMPTY;
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    /** A bag slot that refuses to hold another Hermione's Bag, preventing infinite nesting. */
    private static final class BagSlot extends Slot {
        private BagSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(@NonNull ItemStack stack) {
            return !(stack.getItem() instanceof HermionesBagItem);
        }
    }

    /** The hotbar slot holding the open bag: shown, never moved. */
    private static final class PinnedSlot extends Slot {
        private PinnedSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPickup(@NonNull Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(@NonNull ItemStack stack) {
            return false;
        }
    }
}
