package at.koopro.wizardsandbeasts.wand.refine;

import at.koopro.wizardsandbeasts.item.wand.WandBlankItem;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModMenuTypes;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.wand.WandAppearance;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.wand.customization.WandModule;
import at.koopro.wizardsandbeasts.wand.customization.WandModuleRegistry;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Action;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Material;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Status;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Refining a finished wand at the bench: the wand where a blank would go, the material where a core would go, the
 * action chosen on the row a blank's flexibility is chosen on, and the refined wand waiting in the output slot.
 * Taking it pays: the material is spent and the wand you put in is the one you take out, changed. The rules are
 * {@link WandRefinement}'s; this decides nothing on its own.
 */
public class WandRefineMenu extends AbstractContainerMenu {

    private static final int DATA_TIER = 0;
    private static final int DATA_ACTION = 1;
    private static final int DATA_STATUS = 2;
    private static final int DATA_REQUIRED_TIER = 3;

    private final WandmakersBenchBlockEntity bench;
    private final Level level;
    private final ContainerLevelAccess access;
    private final Player player;
    private final SimpleContainer inputs = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            WandRefineMenu.this.slotsChanged(this);
        }
    };
    private final ResultContainer result = new ResultContainer();
    private final int[] data = new int[4];
    private final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int index) { return data[index]; }
        @Override public void set(int index, int value) { data[index] = value; }
        @Override public int getCount() { return data.length; }
    };

    public WandRefineMenu(int containerId, Inventory playerInventory, WandmakersBenchBlockEntity bench) {
        super(ModMenuTypes.WAND_REFINE.get(), containerId);
        this.bench = bench;
        this.player = playerInventory.player;
        this.level = player.level();
        this.access = ContainerLevelAccess.create(level, bench.getBlockPos());

        addSlot(new Slot(inputs, 0, 44, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return isFinishedWand(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(inputs, 1, 80, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return !(stack.getItem() instanceof WandItem); }
        });
        addSlot(new Slot(result, 0, 134, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }

            @Override
            public void onTake(Player taker, ItemStack stack) {
                // The refined wand is the wand put in: spend that and one unit of material, never more.
                inputs.removeItem(0, 1);
                inputs.removeItem(1, 1);
                super.onTake(taker, stack);
                if (taker instanceof ServerPlayer sp) {
                    at.koopro.wizardsandbeasts.owl.career.CareerWork.record(sp,
                            at.koopro.wizardsandbeasts.owl.career.CareerWork.Kind.WANDCRAFT);
                }
                refresh();
            }
        });
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 112 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 170));
        }
        addDataSlots(dataAccess);
        refresh();
    }

    public static WandRefineMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (!(inv.player.level().getBlockEntity(pos) instanceof WandmakersBenchBlockEntity be)) {
            throw new IllegalStateException("Missing wandmakers bench at " + pos);
        }
        return new WandRefineMenu(containerId, inv, be);
    }

    public static boolean isFinishedWand(ItemStack stack) {
        return stack.getItem() instanceof WandItem && WandStatsResolver.resolveWoodId(stack) != null;
    }

    // ── synced state, for the screen ──────────────────────────────────────

    public int tierScaled() { return data[DATA_TIER]; }
    public Action action() { return Action.values()[Math.floorMod(data[DATA_ACTION], Action.values().length)]; }
    public Status status() { return Status.values()[Math.floorMod(data[DATA_STATUS], Status.values().length)]; }
    public int requiredTierScaled() { return data[DATA_REQUIRED_TIER]; }

    /** The client chose an action; the server holds it (the screen never assumes it was accepted). */
    public void chooseAction(int ordinal) {
        if (ordinal >= 0 && ordinal < Action.values().length) {
            data[DATA_ACTION] = ordinal;
            refresh();
        }
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == inputs) {
            refresh();
        }
    }

    /** Re-derives the status and the output from the inputs, the bench and the wandmaker. Server side only. */
    public void refresh() {
        if (level.isClientSide()) {
            return;
        }
        bench.recalcTierScore();
        WandRefinement.Bench rules = new WandRefinement.Bench(bench.getCachedTierScore(), enhancers());
        Action action = action();
        ItemStack wand = inputs.getItem(0);
        ItemStack material = inputs.getItem(1);
        boolean hasWand = isFinishedWand(wand);
        boolean knowsGrain = player instanceof ServerPlayer sp
                && SkillSystemAPI.getSkillData(sp).hasSkill(WandRefinement.GRAIN_NODE);
        Status status = WandRefinement.check(action, rules, hasWand, knowsGrain, classify(material, wand),
                hasWand ? WandComponents.getIntegrity(wand) : 1.0f, hasWand ? WandComponents.getFlexibility(wand) : null);
        ItemStack out = ItemStack.EMPTY;
        if (status == Status.READY) {
            out = apply(action, rules, wand);
            if (out.isEmpty()) {
                status = Status.NOTHING_TO_DO;
            }
        }
        data[DATA_TIER] = (int) (rules.tier() * 100.0f);
        data[DATA_STATUS] = status.ordinal();
        data[DATA_REQUIRED_TIER] = (int) (action.requiredTier() * 100.0f);
        result.setItem(0, out);
        broadcastChanges();
    }

    private Set<Identifier> enhancers() {
        Set<Identifier> ids = new HashSet<>();
        for (BlockPos pos : bench.getDetectedEnhancers()) {
            ids.add(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()));
        }
        return ids;
    }

    static Material classify(ItemStack material, ItemStack wand) {
        if (material.isEmpty()) {
            return Material.NONE;
        }
        if (material.is(Items.HONEYCOMB)) {
            return Material.HONEYCOMB;
        }
        Identifier wood = WandBlankItem.wandWoodOf(material);
        if (wood != null && wood.equals(WandStatsResolver.resolveWoodId(wand))) {
            return Material.OWN_WOOD_LOG;
        }
        return material.is(ItemTags.LOGS) || wood != null ? Material.OTHER_LOG : Material.OTHER;
    }

    /** The refined copy; wood, core, bond and everything else on the stack are carried over untouched. */
    private ItemStack apply(Action action, WandRefinement.Bench rules, ItemStack wand) {
        ItemStack out = wand.copyWithCount(1);
        switch (action) {
            case POLISH -> out.set(WandComponents.WAND_INTEGRITY.get(),
                    Math.min(1.0f, WandComponents.getIntegrity(wand) + WandRefinement.polishAmount(rules)));
            case STIFFER, SUPPLER -> {
                Optional<WandFlexibility> next = WandRefinement.refit(WandComponents.getFlexibility(wand), action);
                if (next.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                out.set(WandComponents.WAND_FLEXIBILITY.get(), next.get());
            }
            case HANDLE, TIP, ORNAMENT -> {
                WandSlot slot = action == Action.HANDLE ? WandSlot.HANDLE : action == Action.TIP ? WandSlot.TIP : WandSlot.ORNAMENT;
                WandConfiguration config = WandAppearance.configuration(level.registryAccess(), wand);
                List<Identifier> choices = WandModuleRegistry.getAllForSlot(slot).stream().map(WandModule::id).toList();
                Optional<Identifier> next = WandRefinement.nextModule(choices, config.getModule(slot).orElse(null),
                        slot == WandSlot.ORNAMENT);
                out.set(WandComponents.WAND_CONFIGURATION.get(),
                        next.map(id -> config.withModule(slot, id)).orElseGet(() -> config.withoutModule(slot)));
            }
        }
        return out;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, bench.getBlockState().getBlock());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((lvl, pos) -> clearContainer(player, inputs));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 2) {
            if (!moveItemStackTo(stack, 3, 39, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
        } else if (index >= 3) {
            int target = isFinishedWand(stack) ? 0 : 1;
            if (!moveItemStackTo(stack, target, target + 1, false)) {
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
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }
}
