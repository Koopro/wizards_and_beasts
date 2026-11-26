package at.koopro.wizardsandbeasts.wand.gui;

import at.koopro.wizardsandbeasts.item.wand.WandBlankItem;
import at.koopro.wizardsandbeasts.item.wand.WandCoreMaterialItem;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModMenuTypes;
import at.koopro.wizardsandbeasts.wand.bench.BenchMaking;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

import java.util.List;

/**
 * The bench's making side: the blank, the core, the finished wand, the flexibility to shape for, and the next act
 * with its odds. Pressing the act's button ({@link #beginAct}) closes the screen and starts the act's beat on the
 * player's client; the making itself is {@link BenchMaking} and {@link BenchCraft}.
 *
 * <p>Until the 2026-11-26 remake the output slot held a preview that became the wand when taken, and the bench was
 * graded by a tier summed from the blocks around it. Now the wand is made act by act and lands in the output slot
 * only when it is finished, and the tools on the bench, the maker's skill and the pair's affinity set the odds.
 * The screen is P5's to remake (one screen with Make / Mend / Restyle); this menu already holds what it will show.
 */
public class WandmakersBenchMenu extends AbstractContainerMenu {
    private final WandmakersBenchBlockEntity bench;
    private final Level level;
    private final Player player;
    private final ContainerLevelAccess access;

    // ── Geometry, shared with WandmakersBenchScreen and tools/bench_screen.py ──
    //
    // The screen is the bench top seen from above (P5 look gate): the blank between the lathe's centres, the core in
    // a glass dish, the finished wand in an open wand box. The art places its wells at these numbers; the slots use
    // the same ones, so no slot can sit off its socket. Slot x/y are the item's top-left.

    /** The bench top (232 x 150) and the parchment inventory strip under its front edge. */
    public static final int GUI_W = 232;
    public static final int GUI_H = 238;
    public static final int TOP_H = 150;
    public static final int BLANK_X = 54;
    public static final int BLANK_Y = 54;
    public static final int CORE_X = 132;
    public static final int CORE_Y = 54;
    public static final int OUTPUT_X = 186;
    public static final int OUTPUT_Y = 54;
    /**
     * Player inventory, centred on the strip; the hotbar a vanilla 4px gap below. No caption: the parchment strip is
     * plainly the player's own, and without one the screen fits a 240px-tall window (720p at GUI scale 3).
     */
    public static final int INV_X = 36;
    public static final int INV_Y = 154;
    public static final int HOTBAR_Y = INV_Y + 3 * 18 + 4;

    /**
     * Every slot's origin, in slot-index order: blank, core, output, the 27 inventory slots, the 9 hotbar
     * slots. {@code quickMoveStack} relies on that order (0-2 bench, 3-29 inventory, 30-38 hotbar).
     */
    public static List<int[]> slotOrigins() {
        List<int[]> out = new java.util.ArrayList<>(39);
        out.add(new int[]{BLANK_X, BLANK_Y});
        out.add(new int[]{CORE_X, CORE_Y});
        out.add(new int[]{OUTPUT_X, OUTPUT_Y});
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                out.add(new int[]{INV_X + col * 18, INV_Y + row * 18});
            }
        }
        for (int col = 0; col < 9; ++col) {
            out.add(new int[]{INV_X + col * 18, HOTBAR_Y});
        }
        return out;
    }

    private static final int DATA_FLEXIBILITY = 0;
    private static final int DATA_STATUS = 1;
    private static final int DATA_STAGE = 2;
    private static final int DATA_ACT = 3;
    private static final int DATA_WINDOW = 4;
    private static final int DATA_FORGIVE = 5;
    /** The act's risk with every beat missed, in tenths of a percent; -1 while the bench cannot tell. */
    private static final int DATA_RISK = 6;
    private static final int DATA_TOOLS = 7;
    private static final int DATA_COUNT = 8;

    /** How often an open menu re-reads the bench, for tools laid or taken by someone else. */
    private static final int REFRESH_INTERVAL = 10;

    /** Synced to the client through {@link ContainerData}; filled on the server only. */
    private final int[] dataBacking = new int[DATA_COUNT];

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return dataBacking[index];
        }

        @Override
        public void set(int index, int value) {
            dataBacking[index] = value;
        }

        @Override
        public int getCount() {
            return dataBacking.length;
        }
    };

    private int refreshClock;

    public WandmakersBenchMenu(int containerId, Inventory playerInventory, WandmakersBenchBlockEntity bench) {
        super(ModMenuTypes.WANDMAKERS_BENCH.get(), containerId);
        this.bench = bench;
        this.player = playerInventory.player;
        this.level = player.level();
        this.access = ContainerLevelAccess.create(level, bench.getBlockPos());

        ItemStacksResourceHandler handler = bench.getInventory();
        List<int[]> at = slotOrigins();
        addSlot(new WandBlankSlot(bench, handler, 0, at.get(0)[0], at.get(0)[1]));
        addSlot(new CoreSlot(bench, handler, 1, at.get(1)[0], at.get(1)[1]));
        addSlot(new OutputSlot(this, handler, 2, at.get(2)[0], at.get(2)[1]));

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int[] o = at.get(3 + row * 9 + col);
                addSlot(new Slot(playerInventory, col + row * 9 + 9, o[0], o[1]));
            }
        }
        for (int col = 0; col < 9; ++col) {
            int[] o = at.get(30 + col);
            addSlot(new Slot(playerInventory, col, o[0], o[1]));
        }

        addDataSlots(dataAccess);
        refresh();
    }

    public static WandmakersBenchMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (!(inv.player.level().getBlockEntity(pos) instanceof WandmakersBenchBlockEntity be)) {
            throw new IllegalStateException("Missing wandmakers bench at " + pos);
        }
        return new WandmakersBenchMenu(containerId, inv, be);
    }

    public WandmakersBenchBlockEntity getBench() {
        return bench;
    }

    public int getFlexibilityOrdinal() {
        return dataBacking[DATA_FLEXIBILITY];
    }

    /** What the bench says about its next act. */
    public BenchMaking.Status getStatus() {
        return BenchMaking.Status.values()[Math.floorMod(dataBacking[DATA_STATUS], BenchMaking.Status.values().length)];
    }

    public BenchCraft.Stage getStage() {
        return BenchCraft.Stage.values()[Math.floorMod(dataBacking[DATA_STAGE], BenchCraft.Stage.values().length)];
    }

    public BenchCraft.Act getNextAct() {
        return BenchCraft.Act.values()[Math.floorMod(dataBacking[DATA_ACT], BenchCraft.Act.values().length)];
    }

    /** Ticks either side of a beat that still hit. Meaningful when {@link #getRiskPermille()} is not -1. */
    public int getWindow() {
        return dataBacking[DATA_WINDOW];
    }

    public int getForgive() {
        return dataBacking[DATA_FORGIVE];
    }

    /** The next act's risk with every beat missed, in tenths of a percent, or -1 when the bench cannot tell yet. */
    public int getRiskPermille() {
        return dataBacking[DATA_RISK];
    }

    public int getToolMask() {
        return dataBacking[DATA_TOOLS];
    }

    public void setFlexibilityOrdinalFromServer(int ordinal) {
        bench.setSelectedFlexibilityOrdinal(ordinal);
        refresh();
        broadcastChanges();
    }

    /** The player pressed the act's button. The menu closes when the act begins. */
    public void beginAct(ServerPlayer serverPlayer) {
        if (serverPlayer != player) {
            return;
        }
        // On success the act closes this menu; only a refusal leaves it open to say why.
        if (BenchMaking.begin(bench, serverPlayer) != BenchMaking.Status.READY) {
            refresh();
            broadcastChanges();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.WANDMAKERS_BENCH.get())
                && player.distanceToSqr((double) bench.getBlockPos().getX() + 0.5,
                (double) bench.getBlockPos().getY() + 0.5,
                (double) bench.getBlockPos().getZ() + 0.5) <= 8.0 * 8.0;
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        super.clicked(slotId, button, clickType, player);
        if (!level.isClientSide() && slotId >= 0 && slotId <= 2) {
            refresh();
        }
    }

    @Override
    public void broadcastChanges() {
        if (!level.isClientSide() && ++refreshClock >= REFRESH_INTERVAL) {
            refreshClock = 0;
            refresh();
        }
        super.broadcastChanges();
    }

    /** Re-reads the bench, the maker and the next act's odds into the synced data. Server side only. */
    public void refresh() {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        dataBacking[DATA_FLEXIBILITY] = bench.getSelectedFlexibilityOrdinal();
        dataBacking[DATA_STAGE] = bench.stage().ordinal();
        dataBacking[DATA_TOOLS] = bench.toolMask();
        BenchMaking.Next next = BenchMaking.next(bench, serverPlayer);
        dataBacking[DATA_STATUS] = next.status().ordinal();
        dataBacking[DATA_ACT] = next.act().ordinal();
        dataBacking[DATA_WINDOW] = next.odds().map(BenchCraft.Odds::window).orElse(0);
        dataBacking[DATA_FORGIVE] = next.odds().map(BenchCraft.Odds::forgive).orElse(0);
        dataBacking[DATA_RISK] = next.odds().map(o -> Math.round(o.risk() * 1000f)).orElse(-1);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem() && slot.mayPickup(player)) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();
            if (index == 2) {
                if (!moveItemStackTo(stack, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, itemstack);
            } else if (index >= 3) {
                if (stack.getItem() instanceof WandBlankItem && slots.get(0).mayPlace(stack)) {
                    if (!moveItemStackTo(stack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (WandCoreMaterialItem.isBenchCore(stack) && slots.get(1).mayPlace(stack)) {
                    if (!moveItemStackTo(stack, 1, 2, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index < 30) {
                    if (!moveItemStackTo(stack, 30, 39, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(stack, 3, 30, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 3, 39, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        if (!level.isClientSide()) {
            refresh();
        }
        return itemstack;
    }

    /** The blank. Locked while a making is under way: it is the wand being made. */
    private static class WandBlankSlot extends ResourceHandlerSlot {
        private final WandmakersBenchBlockEntity bench;

        WandBlankSlot(WandmakersBenchBlockEntity bench, ItemStacksResourceHandler handler, int id, int x, int y) {
            super(handler, handler::set, id, x, y);
            this.bench = bench;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof WandBlankItem && bench.stage() == BenchCraft.Stage.NONE;
        }

        @Override
        public boolean mayPickup(Player player) {
            return bench.stage() == BenchCraft.Stage.NONE;
        }
    }

    /** The core. Free to change until it is married; locked while the marrying is being played. */
    private static class CoreSlot extends ResourceHandlerSlot {
        private final WandmakersBenchBlockEntity bench;

        CoreSlot(WandmakersBenchBlockEntity bench, ItemStacksResourceHandler handler, int id, int x, int y) {
            super(handler, handler::set, id, x, y);
            this.bench = bench;
        }

        private boolean marrying() {
            WandmakersBenchBlockEntity.PendingAct pending = bench.pending();
            return pending != null && pending.odds().act() == BenchCraft.Act.MARRY;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return WandCoreMaterialItem.isBenchCore(stack) && !marrying();
        }

        @Override
        public boolean mayPickup(Player player) {
            return !marrying();
        }
    }

    /** The finished wand: taken, never placed. */
    private static class OutputSlot extends ResourceHandlerSlot {
        private final WandmakersBenchMenu menu;

        OutputSlot(WandmakersBenchMenu menu, ItemStacksResourceHandler handler, int id, int x, int y) {
            super(handler, handler::set, id, x, y);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);
            menu.refresh();
        }
    }
}
