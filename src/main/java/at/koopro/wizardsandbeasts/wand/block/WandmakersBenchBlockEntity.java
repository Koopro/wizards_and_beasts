package at.koopro.wizardsandbeasts.wand.block;

import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import at.koopro.wizardsandbeasts.wand.registry.BenchEnhancerDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public class WandmakersBenchBlockEntity extends BlockEntity {
    private final ItemStackHandler inventory = new ItemStackHandler(3);
    private float cachedTierScore = 0.0f;
    private List<BlockPos> detectedEnhancers = new ArrayList<>();

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
        serverLevel.registryAccess().lookup(WandDatapackRegistries.BENCH_ENHANCER_REGISTRY).ifPresent(registry -> {
            BenchMultiblockScanner.ScanResult result = BenchMultiblockScanner.scanEnhancersWithPositions(serverLevel, worldPosition, registry);
            this.cachedTierScore = result.tierScore();
            this.detectedEnhancers = result.enhancers();
        });
    }

    public float getCachedTierScore() {
        return cachedTierScore;
    }

    public List<BlockPos> getDetectedEnhancers() {
        return detectedEnhancers;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < 3; i++) {
            output.store("slot_" + i, ItemStack.CODEC, inventory.getStackInSlot(i));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < 3; i++) {
            inventory.setStackInSlot(i, input.read("slot_" + i, ItemStack.CODEC).orElse(ItemStack.EMPTY));
        }
    }
}
