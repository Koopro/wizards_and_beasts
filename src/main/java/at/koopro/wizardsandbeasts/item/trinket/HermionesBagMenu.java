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
 * {@link ModDataComponents#HERMIONES_BAG_INVENTORY} component: loaded on open (server side),
 * written back when the menu closes. The bag cannot be nested inside itself. The menu stays
 * valid only while the owner keeps holding the same bag in the opening hand.
 */
public class HermionesBagMenu extends AbstractContainerMenu {

    public static final int ROWS = 6;
    public static final int COLS = 9;
    public static final int SIZE = ROWS * COLS;

    /** Positional codec — keeps empty slots so item positions survive a save/load round trip. */
    private static final Codec<List<ItemStack>> ITEMS_CODEC = ItemStack.OPTIONAL_CODEC.listOf();
    private static final String ITEMS_KEY = "Items";

    private final Container bag;
    private final Player owner;
    private final InteractionHand hand;

    /** Server constructor — loads the held bag's stored contents. */
    public HermionesBagMenu(int containerId, Inventory playerInventory, InteractionHand hand) {
        this(containerId, playerInventory, hand,
                loadContents(playerInventory.player, hand));
    }

    private HermionesBagMenu(int containerId, Inventory playerInventory, InteractionHand hand, Container bag) {
        super(ModMenuTypes.HERMIONES_BAG.get(), containerId);
        this.owner = playerInventory.player;
        this.hand = hand;
        this.bag = bag;
        checkContainerSize(bag, SIZE);
        bag.startOpen(this.owner);

        // Bag grid (6 x 9)
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                addSlot(new BagSlot(bag, row * COLS + col, 8 + col * 18, 18 + row * 18));
            }
        }
        // Player inventory + hotbar, positioned to match the vanilla generic_54 layout.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 139 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 197));
        }
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

    @Override
    public void removed(@NonNull Player player) {
        super.removed(player);
        bag.stopOpen(player);
        if (!player.level().isClientSide()) {
            ItemStack bagStack = player.getItemInHand(hand);
            if (bagStack.getItem() instanceof HermionesBagItem) {
                List<ItemStack> items = new ArrayList<>(SIZE);
                for (int i = 0; i < SIZE; i++) {
                    items.add(bag.getItem(i));
                }
                RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, player.level().registryAccess());
                Tag itemsTag = ITEMS_CODEC.encodeStart(ops, items).result().orElse(null);
                if (itemsTag != null) {
                    CompoundTag tag = new CompoundTag();
                    tag.put(ITEMS_KEY, itemsTag);
                    bagStack.set(ModDataComponents.HERMIONES_BAG_INVENTORY.get(), tag);
                }
            }
        }
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return player.getItemInHand(hand).getItem() instanceof HermionesBagItem;
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
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
}
