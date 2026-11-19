package at.koopro.wizardsandbeasts.equipment.curios;

import at.koopro.wizardsandbeasts.equipment.WardrobeLayout;
import at.koopro.wizardsandbeasts.registry.ModMenuTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * The wardrobe's container (UI renaissance P6, 2026-11-18): the player's own Curios slots, laid out as an armoire.
 *
 * <p>No storage of its own: every wardrobe slot sits on the player's Curios stack handler (worn or cosmetic), so what
 * goes in is in Curios, saved and synced by Curios. Admission is Curios' — {@link CuriosApi#isStackValid} (which runs
 * the {@code wizards_and_beasts:wearable} validator) plus the item's own {@code ICurio#canEquip} — and removal is
 * Curios' handler's own rule (binding curse outside creative, {@code ICurio#canUnequip}, its unequip event), all on
 * the server. API only: the build compiles against
 * Curios' API jar so no Curios internals leak into the mod.
 *
 * <p>Slot order: per {@link WardrobeLayout#PLACES}, a worn slot then a look slot (20), then the player's 27 + 9. A
 * place with no Curios slot behind it (a datapack removed it) or no cosmetic twin becomes a sealed slot that holds
 * and accepts nothing, so the client and server always build the same number of slots.
 */
@NullMarked
public final class WardrobeMenu extends AbstractContainerMenu {

    private static final int INV_START = WardrobeLayout.WARDROBE_SLOTS;
    private static final int INV_END = INV_START + 36;

    private final Player owner;

    public WardrobeMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.WARDROBE.get(), containerId);
        this.owner = playerInventory.player;
        ICuriosItemHandler curios = CuriosApi.getCuriosInventoryOrNull(owner);
        for (WardrobeLayout.Place place : WardrobeLayout.PLACES) {
            ICurioStacksHandler handler = curios == null ? null : curios.getStacksHandler(place.slotId()).orElse(null);
            addSlot(slotFor(handler, place, false, place.wornX()));
            addSlot(slotFor(handler, place, true, place.lookX()));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        WardrobeLayout.INV_X + col * 18, WardrobeLayout.INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, WardrobeLayout.INV_X + col * 18, WardrobeLayout.HOTBAR_Y));
        }
    }

    public static WardrobeMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        return new WardrobeMenu(containerId, playerInventory);
    }

    private Slot slotFor(@Nullable ICurioStacksHandler handler, WardrobeLayout.Place place, boolean look, int x) {
        int y = place.y();
        if (handler == null || place.index() >= handler.getSlots() || (look && !handler.hasCosmetic())) {
            return new SealedSlot(x, y);
        }
        return new WardrobeSlot(owner, handler, place.slotId(), place.index(), look, x, y);
    }

    /** Whether place {@code i}'s piece is drawn on the player (Curios' render flag); true when there is none. */
    public boolean shown(int placeIndex) {
        return !(slots.get(placeIndex * 2) instanceof WardrobeSlot slot) || slot.rendered();
    }

    /** Whether place {@code i} is a real Curios slot whose rendering Curios lets the player toggle. */
    public boolean canToggle(int placeIndex) {
        return slots.get(placeIndex * 2) instanceof WardrobeSlot slot && slot.handler.canToggleRendering();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < INV_START) {
            // Out of the wardrobe: into the inventory, hotbar last.
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Into the wardrobe: the first empty *worn* slot that takes it. Looks are placed by hand only, so a
            // shift-click never swaps a real robe for a costume by surprise.
            boolean moved = false;
            for (int i = 0; i < INV_START && !moved; i += 2) {
                Slot target = slots.get(i);
                if (!target.hasItem() && target.mayPlace(stack)) {
                    moved = moveItemStackTo(stack, i, i + 1, false);
                }
            }
            if (!moved) {
                int hotbar = INV_END - 9;
                if (index < hotbar ? !moveItemStackTo(stack, hotbar, INV_END, false)
                        : !moveItemStackTo(stack, INV_START, hotbar, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == before.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && player.isAlive();
    }

    /** One Curios slot: worn or look, on the player's own stack handler. */
    static final class WardrobeSlot extends SlotItemHandler {
        private final Player player;
        final ICurioStacksHandler handler;
        private final String slotId;
        private final int curioIndex;
        private final boolean look;

        WardrobeSlot(Player player, ICurioStacksHandler handler, String slotId, int index, boolean look, int x, int y) {
            super(look ? handler.getCosmeticStacks() : handler.getStacks(), index, x, y);
            this.player = player;
            this.handler = handler;
            this.slotId = slotId;
            this.curioIndex = index;
            this.look = look;
        }

        private SlotContext context() {
            return new SlotContext(slotId, player, curioIndex, look, rendered());
        }

        boolean rendered() {
            NonNullList<Boolean> renders = handler.getRenders();
            return curioIndex >= renders.size() || renders.get(curioIndex);
        }

        @Override
        public boolean isActive() {
            NonNullList<Boolean> active = handler.getActiveStates();
            return curioIndex >= active.size() || active.get(curioIndex);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty() || !isActive()) {
                return false;
            }
            SlotContext context = context();
            if (!CuriosApi.isStackValid(context, stack)) {
                return false;
            }
            // A look grants nothing, so only a worn piece is asked whether it may be put on.
            return look || CuriosApi.getCurio(stack).map(curio -> curio.canEquip(context)).orElse(true);
        }

        @Override
        public boolean mayPickup(Player who) {
            // Curios' handler decides removal itself (SlotItemHandler asks it to simulate an extract): the binding
            // curse outside creative, ICurio#canUnequip and Curios' CurioCanUnequipEvent. Not repeated here.
            return !getItem().isEmpty() && super.mayPickup(who);
        }
    }

    /** A place with nothing behind it: holds nothing, takes nothing, is never drawn as a target. */
    private static final class SealedSlot extends Slot {
        SealedSlot(int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}
