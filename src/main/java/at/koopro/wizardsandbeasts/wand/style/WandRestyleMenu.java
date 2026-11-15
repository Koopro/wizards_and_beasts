package at.koopro.wizardsandbeasts.wand.style;

import at.koopro.wizardsandbeasts.registry.ModMenuTypes;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import at.koopro.wizardsandbeasts.wand.refine.WandRefineMenu;
import at.koopro.wizardsandbeasts.wand.style.WandRestyle.Aspect;
import at.koopro.wizardsandbeasts.wand.style.WandRestyle.Cost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * The bench's third role: restyling a finished wand. The wand goes in, the player picks a look for each aspect
 * ({@link WandRestyle}), and the restyled wand waits in the output slot; taking it pays for every changed aspect
 * from the player's inventory. Only looks change.
 *
 * <p>The choice is one index per aspect in the menu's data, set by button clicks ({@link #clickMenuButton}). The
 * screen runs the same click locally before sending it, so its preview moves the moment a choice changes and the
 * server, running the same rules, arrives at the same wand.
 */
@NullMarked
public class WandRestyleMenu extends AbstractContainerMenu {

    public static final int GUI_W = 270;
    public static final int GUI_H = 266;
    public static final int WAND_X = 14;
    public static final int RESULT_X = 88;
    public static final int STATION_Y = 150;
    public static final int INV_X = 54;
    public static final int INV_Y = 184;
    public static final int HOTBAR_Y = 242;

    /** Button ids: {@code aspect * STRIDE + index}. */
    public static final int STRIDE = 64;

    public enum Status { NO_WAND, NO_CHANGE, CANNOT_AFFORD, READY }

    private static final int DATA_STATUS = Aspect.values().length;

    private final WandmakersBenchBlockEntity bench;
    private final Level level;
    private final ContainerLevelAccess access;
    private final Player player;
    private final SimpleContainer input = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            WandRestyleMenu.this.slotsChanged(this);
        }
    };
    private final ResultContainer result = new ResultContainer();
    private final int[] data = new int[Aspect.values().length + 1];
    private final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) { return data[index]; }
        @Override public void set(int index, int value) { data[index] = value; }
        @Override public int getCount() { return data.length; }
    };

    public WandRestyleMenu(int containerId, Inventory playerInventory, WandmakersBenchBlockEntity bench) {
        super(ModMenuTypes.WAND_RESTYLE.get(), containerId);
        this.bench = bench;
        this.player = playerInventory.player;
        this.level = player.level();
        this.access = ContainerLevelAccess.create(level, bench.getBlockPos());

        addSlot(new Slot(input, 0, WAND_X, STATION_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return WandRefineMenu.isFinishedWand(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(result, 0, RESULT_X, STATION_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }

            @Override
            public boolean mayPickup(Player taker) {
                return canAfford(taker);
            }

            @Override
            public void onTake(Player taker, ItemStack stack) {
                pay(taker);
                input.removeItem(0, 1);
                super.onTake(taker, stack);
                if (taker instanceof ServerPlayer sp) {
                    at.koopro.wizardsandbeasts.owl.career.CareerWork.record(sp,
                            at.koopro.wizardsandbeasts.owl.career.CareerWork.Kind.WANDCRAFT);
                }
                for (Aspect aspect : Aspect.values()) {
                    data[aspect.ordinal()] = WandRestyle.KEEP;
                }
                refresh();
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
        addDataSlots(dataAccess);
        refresh();
    }

    public static WandRestyleMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (!(inv.player.level().getBlockEntity(pos) instanceof WandmakersBenchBlockEntity be)) {
            throw new IllegalStateException("Missing wandmakers bench at " + pos);
        }
        return new WandRestyleMenu(containerId, inv, be);
    }

    /** Opens the restyle role of this bench for the player. */
    public static void open(ServerPlayer player, WandmakersBenchBlockEntity bench) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new WandRestyleMenu(id, inventory, bench),
                Component.translatable("gui.wizards_and_beasts.wand_restyle")),
                buf -> buf.writeBlockPos(bench.getBlockPos()));
    }

    public WandmakersBenchBlockEntity bench() {
        return bench;
    }

    // ── the choice ───────────────────────────────────────────────────────

    /** The index chosen for each aspect, in {@link Aspect} order. */
    public int[] selection() {
        int[] out = new int[Aspect.values().length];
        System.arraycopy(data, 0, out, 0, out.length);
        return out;
    }

    public Status status() {
        Status[] values = Status.values();
        return values[Math.floorMod(data[DATA_STATUS], values.length)];
    }

    public ItemStack wand() {
        return input.getItem(0);
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        int aspect = id / STRIDE;
        int index = id % STRIDE;
        if (id < 0 || aspect >= Aspect.values().length || index >= WandRestyle.size(Aspect.values()[aspect])) {
            return false;
        }
        data[aspect] = index;
        refresh();
        return true;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == input) {
            refresh();
        }
    }

    /** Re-derives the output and the status from the wand, the choice and what the player carries. Server side. */
    public void refresh() {
        if (level.isClientSide()) {
            return;
        }
        ItemStack wand = input.getItem(0);
        int[] pick = selection();
        Status status;
        ItemStack out = ItemStack.EMPTY;
        if (!WandRefineMenu.isFinishedWand(wand)) {
            status = Status.NO_WAND;
        } else if (!WandRestyle.changes(pick)) {
            status = Status.NO_CHANGE;
        } else {
            out = WandRestyle.apply(level.registryAccess(), wand, pick);
            status = canAfford(player) ? Status.READY : Status.CANNOT_AFFORD;
        }
        data[DATA_STATUS] = status.ordinal();
        result.setItem(0, out);
        broadcastChanges();
    }

    /** Whether the player carries every changed aspect's price. Creative players pay nothing. */
    public boolean canAfford(Player who) {
        if (who.getAbilities().instabuild) {
            return true;
        }
        List<ItemStack> items = inventory(who);
        for (Cost cost : WandRestyle.cost(selection())) {
            if (WandRestyle.held(items, cost) < cost.count()) {
                return false;
            }
        }
        return true;
    }

    private void pay(Player who) {
        if (who.getAbilities().instabuild) {
            return;
        }
        Inventory inv = who.getInventory();
        for (Cost cost : WandRestyle.cost(selection())) {
            int owed = cost.count();
            for (int i = 0; i < inv.getContainerSize() && owed > 0; i++) {
                ItemStack stack = inv.getItem(i);
                if (cost.matches(stack)) {
                    int take = Math.min(owed, stack.getCount());
                    stack.shrink(take);
                    owed -= take;
                }
            }
        }
    }

    private static List<ItemStack> inventory(Player who) {
        Inventory inv = who.getInventory();
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            out.add(inv.getItem(i));
        }
        return out;
    }

    @Override
    public boolean stillValid(Player who) {
        return stillValid(access, who, bench.getBlockState().getBlock());
    }

    @Override
    public void removed(Player who) {
        super.removed(who);
        access.execute((lvl, pos) -> clearContainer(who, input));
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 1) {
            if (!slot.mayPickup(who) || !moveItemStackTo(stack, 2, 38, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
        } else if (index >= 2) {
            if (!WandRefineMenu.isFinishedWand(stack) || !moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 2, 38, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(who, stack);
        return copy;
    }
}
