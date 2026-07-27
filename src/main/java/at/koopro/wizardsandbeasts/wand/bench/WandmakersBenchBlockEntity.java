package at.koopro.wizardsandbeasts.wand.bench;

import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import com.mojang.serialization.Codec;
import at.koopro.wizardsandbeasts.wand.registry.BenchEnhancerDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceConfigLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

import java.util.ArrayList;
import java.util.List;

public class WandmakersBenchBlockEntity extends BlockEntity {
    private final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(3);
    private float cachedTierScore = 0.0f;
    private List<BlockPos> detectedEnhancers = new ArrayList<>();
    private int selectedFlexibilityOrdinal = WandFlexibility.PLIANT.ordinal();

    public WandmakersBenchBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.WANDMAKERS_BENCH.get(), pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        recalcTierScore();
    }

    public void recalcTierScore() {
        Level level = getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        HolderLookup.RegistryLookup<BenchEnhancerDefinition> lookup =
                serverLevel.registryAccess().lookupOrThrow(WandDatapackRegistries.BENCH_ENHANCER_REGISTRY);
        int radius = WandResonanceConfigLoader.getConfig(serverLevel.registryAccess()).scanRadius();
        BenchMultiblockScanner.ScanResult result =
                BenchMultiblockScanner.scanEnhancersWithPositions(serverLevel, worldPosition, lookup, radius);
        this.cachedTierScore = result.tierScore();
        this.detectedEnhancers = result.enhancers();
    }

    public int getSelectedFlexibilityOrdinal() {
        return selectedFlexibilityOrdinal;
    }

    public void setSelectedFlexibilityOrdinal(int selectedFlexibilityOrdinal) {
        int max = WandFlexibility.values().length - 1;
        this.selectedFlexibilityOrdinal = Math.max(0, Math.min(max, selectedFlexibilityOrdinal));
        setChanged();
    }

    public float getCachedTierScore() {
        return cachedTierScore;
    }

    public List<BlockPos> getDetectedEnhancers() {
        return detectedEnhancers;
    }

    public ItemStacksResourceHandler getInventory() {
        return inventory;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("flexibility_ordinal", Codec.INT, selectedFlexibilityOrdinal);
        for (int i = 0; i < 3; i++) {
            output.store("slot_" + i, ItemStack.CODEC, getSlotStack(i));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        selectedFlexibilityOrdinal = input.read("flexibility_ordinal", Codec.INT).orElse(WandFlexibility.PLIANT.ordinal());
        for (int i = 0; i < 3; i++) {
            setSlotStack(i, input.read("slot_" + i, ItemStack.CODEC).orElse(ItemStack.EMPTY));
        }
    }

    private ItemStack getSlotStack(int slot) {
        int count = (int) inventory.getAmountAsLong(slot);
        if (count <= 0) return ItemStack.EMPTY;
        return inventory.getResource(slot).toStack(count);
    }

    private void setSlotStack(int slot, ItemStack stack) {
        inventory.set(slot, ItemResource.of(stack), stack.getCount());
    }
}
