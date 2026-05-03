package at.koopro.wizardsandbeasts.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.data.loot.BlockLootSubProvider;

import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WoodSet;

import java.util.Set;

public class ModBlockLootTableProvider extends BlockLootSubProvider {

    public ModBlockLootTableProvider(HolderLookup.Provider lookupProvider) {
        super(Set.of(), FeatureFlags.DEFAULT_FLAGS, lookupProvider);
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries()
                .stream()
                .map(e -> (Block) e.value())
                .toList();
    }

    @Override
    protected void generate() {
        for (WoodSet woodSet : ModBlocks.ALL_WOOD_SETS) {
            generateWoodSetLoot(woodSet);
        }

        dropOther(ModBlocks.MANDRAKE_CROP.get(), ModBlocks.MANDRAKE_SEEDS.get());
        dropSelf(ModBlocks.DEVILS_SNARE.get());
        dropSelf(ModBlocks.MALLOWSWEET.get());
        dropSelf(ModBlocks.GRYFFINDOR_BANNER.get());
        dropSelf(ModBlocks.SLYTHERIN_BANNER.get());
        dropSelf(ModBlocks.RAVENCLAW_BANNER.get());
        dropSelf(ModBlocks.HUFFLEPUFF_BANNER.get());
        dropSelf(ModBlocks.FLOATING_CANDLE.get());
        dropSelf(ModBlocks.BRASS_CAULDRON.get());
        dropSelf(ModBlocks.WIZARDING_COPPER_CAULDRON.get());
        dropSelf(ModBlocks.PEWTER_CAULDRON.get());
        dropSelf(ModBlocks.FLOO_GRATE.get());
        dropSelf(ModBlocks.SPELL_TEACHER.get());
        add(ModBlocks.DELUMINATOR_LIGHT.get(), noDrop());
        dropOther(ModBlocks.UNLIT_TORCH.get(), Blocks.TORCH);
        dropOther(ModBlocks.UNLIT_WALL_TORCH.get(), Blocks.TORCH);
        dropOther(ModBlocks.UNLIT_COPPER_TORCH.get(), Blocks.COPPER_TORCH);
        dropOther(ModBlocks.UNLIT_COPPER_WALL_TORCH.get(), Blocks.COPPER_TORCH);
        dropOther(ModBlocks.UNLIT_SOUL_TORCH.get(), Blocks.SOUL_TORCH);
        dropOther(ModBlocks.UNLIT_SOUL_WALL_TORCH.get(), Blocks.SOUL_TORCH);
        dropOther(ModBlocks.UNLIT_LANTERN.get(), Blocks.LANTERN);
        dropOther(ModBlocks.UNLIT_COPPER_LANTERN.get(), Blocks.LANTERN);
        dropOther(ModBlocks.UNLIT_SOUL_LANTERN.get(), Blocks.SOUL_LANTERN);
        dropOther(ModBlocks.UNLIT_GLOWSTONE.get(), Blocks.GLOWSTONE);
    }

    private void generateWoodSetLoot(WoodSet woodSet) {
        dropSelf(woodSet.log().get());
        dropSelf(woodSet.strippedLog().get());
        dropSelf(woodSet.wood().get());
        dropSelf(woodSet.strippedWood().get());
        dropSelf(woodSet.planks().get());
        add(woodSet.slab().get(), this::createSlabItemTable);
        dropSelf(woodSet.stairs().get());
        add(woodSet.leaves().get(), block -> createLeavesDrops(block, woodSet.sapling().get(), NORMAL_LEAVES_SAPLING_CHANCES));
        dropSelf(woodSet.sapling().get());
    }
}
