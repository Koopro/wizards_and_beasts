package at.koopro.wizardsandbeasts.wand.gui;

import at.koopro.wizardsandbeasts.item.wand.WandBlankItem;
import at.koopro.wizardsandbeasts.item.wand.WandCoreMaterialItem;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModMenuTypes;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;

public class WandmakersBenchMenu extends AbstractContainerMenu {
    private final WandmakersBenchBlockEntity bench;
    private final Level level;
    private final ContainerLevelAccess access;
    private final List<Identifier> enhancerBlockIds;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) (bench.getCachedTierScore() * 100.0f);
                case 1 -> bench.getSelectedFlexibilityOrdinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 1) {
                bench.setSelectedFlexibilityOrdinal(value);
                updateCraftingResult();
                broadcastChanges();
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public WandmakersBenchMenu(int containerId, Inventory playerInventory, WandmakersBenchBlockEntity bench,
                               List<Identifier> enhancerBlockIds) {
        super(ModMenuTypes.WANDMAKERS_BENCH.get(), containerId);
        this.bench = bench;
        this.level = playerInventory.player.level();
        this.access = ContainerLevelAccess.create(level, bench.getBlockPos());
        this.enhancerBlockIds = List.copyOf(enhancerBlockIds);

        ItemStacksResourceHandler handler = bench.getInventory();
        addSlot(new WandBlankSlot(handler, 0, 44, 35));
        addSlot(new CoreSlot(handler, 1, 80, 35));
        addSlot(new OutputSlot(this, handler, 2, 134, 35));

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 112 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 170));
        }

        addDataSlots(dataAccess);
        updateCraftingResult();
    }

    public static WandmakersBenchMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int enhCount = buf.readVarInt();
        List<Identifier> enh = new ArrayList<>(enhCount);
        for (int i = 0; i < enhCount; i++) {
            enh.add(Identifier.parse(buf.readUtf(320)));
        }
        if (!(inv.player.level().getBlockEntity(pos) instanceof WandmakersBenchBlockEntity be)) {
            throw new IllegalStateException("Missing wandmakers bench at " + pos);
        }
        return new WandmakersBenchMenu(containerId, inv, be, enh);
    }

    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(bench.getBlockPos());
        buf.writeVarInt(enhancerBlockIds.size());
        for (Identifier id : enhancerBlockIds) {
            buf.writeUtf(id.toString(), 320);
        }
    }

    public List<Identifier> getEnhancerBlockIds() {
        return enhancerBlockIds;
    }

    public WandmakersBenchBlockEntity getBench() {
        return bench;
    }

    public int getTierScoreScaled() {
        return dataAccess.get(0);
    }

    public int getFlexibilityOrdinal() {
        return dataAccess.get(1);
    }

    public void setFlexibilityOrdinalFromServer(int ordinal) {
        bench.setSelectedFlexibilityOrdinal(ordinal);
        updateCraftingResult();
        broadcastChanges();
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
        if (!level.isClientSide() && (slotId == 0 || slotId == 1)) {
            updateCraftingResult();
        }
    }

    public void updateCraftingResult() {
        if (level.isClientSide()) {
            return;
        }
        ItemStacksResourceHandler handler = bench.getInventory();
        ItemStack blank = slotToStack(handler, 0);
        ItemStack coreStack = slotToStack(handler, 1);
        ItemStack out = slotToStack(handler, 2);
        if (blank.isEmpty() || coreStack.isEmpty()) {
            if (!out.isEmpty()) {
                handler.set(2, ItemResource.of(ItemStack.EMPTY), 0);
            }
            return;
        }
        Identifier wood = WandComponents.getWood(blank);
        Identifier coreId = WandCoreMaterialItem.getCoreKey(coreStack);
        if (wood == null || coreId == null) {
            handler.set(2, ItemResource.of(ItemStack.EMPTY), 0);
            return;
        }
        Optional<WandmakingRecipe> recipeOpt = findRecipe(wood, coreId);
        if (recipeOpt.isEmpty()) {
            handler.set(2, ItemResource.of(ItemStack.EMPTY), 0);
            return;
        }
        WandmakingRecipe recipe = recipeOpt.get();
        if (bench.getCachedTierScore() < recipe.minimumBenchTier()) {
            handler.set(2, ItemResource.of(ItemStack.EMPTY), 0);
            return;
        }
        WandFlexibility flex = WandFlexibility.values()[bench.getSelectedFlexibilityOrdinal()];
        float len = recipe.resultLengthMin()
                + level.random.nextFloat() * (recipe.resultLengthMax() - recipe.resultLengthMin());
        ItemStack wand = new ItemStack(WandItemRegistry.WAND.get());
        wand.set(WandComponents.WAND_WOOD.get(), wood);
        wand.set(WandComponents.WAND_CORE.get(), coreId);
        wand.set(WandComponents.WAND_FLEXIBILITY.get(), flex);
        wand.set(WandComponents.WAND_LENGTH.get(), len);
        wand.set(WandComponents.WAND_INTEGRITY.get(), recipe.resultIntegrity());
        wand.set(WandComponents.WAND_MASTER.get(), Optional.empty());
        ModDataComponents.refreshElderWandMarker(wand);
        handler.set(2, ItemResource.of(wand), wand.getCount());
        bench.setChanged();
    }

    private Optional<WandmakingRecipe> findRecipe(Identifier wood, Identifier core) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        for (RecipeHolder<?> holder : serverLevel.getServer().getRecipeManager().getRecipes()) {
            if (holder.value() instanceof WandmakingRecipe r
                    && r.woodKey().equals(wood) && r.coreKey().equals(core)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();
            if (index == 2) {
                if (!moveItemStackTo(stack, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, itemstack);
            } else if (index >= 3) {
                if (stack.getItem() instanceof WandBlankItem) {
                    if (!moveItemStackTo(stack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (WandCoreMaterialItem.isBenchCore(stack)) {
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
            updateCraftingResult();
        }
        return itemstack;
    }

    private static ItemStack slotToStack(ItemStacksResourceHandler handler, int slot) {
        int count = (int) handler.getAmountAsLong(slot);
        if (count <= 0) return ItemStack.EMPTY;
        return handler.getResource(slot).toStack(count);
    }

    private static class WandBlankSlot extends ResourceHandlerSlot {
        WandBlankSlot(ItemStacksResourceHandler handler, int id, int x, int y) {
            super(handler, handler::set, id, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof WandBlankItem;
        }
    }

    private static class CoreSlot extends ResourceHandlerSlot {
        CoreSlot(ItemStacksResourceHandler handler, int id, int x, int y) {
            super(handler, handler::set, id, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return WandCoreMaterialItem.isBenchCore(stack);
        }
    }

    private static class OutputSlot extends ResourceHandlerSlot {
        private final WandmakersBenchMenu menu;
        private final ItemStacksResourceHandler handler;

        OutputSlot(WandmakersBenchMenu menu, ItemStacksResourceHandler handler, int id, int x, int y) {
            super(handler, handler::set, id, x, y);
            this.menu = menu;
            this.handler = handler;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            try (Transaction tx = Transaction.openRoot()) {
                handler.extract(0, handler.getResource(0), 1, tx);
                handler.extract(1, handler.getResource(1), 1, tx);
                tx.commit();
            }
            super.onTake(player, stack);
            menu.updateCraftingResult();
        }
    }
}
