package at.koopro.wizardsandbeasts.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.location.DiagonAlleyBlocks;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleTags;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WoodSet;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagsProvider extends BlockTagsProvider {

    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, WizardsAndBeastsMod.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        for (WoodSet woodSet : ModBlocks.ALL_WOOD_SETS) {
            addWoodSetTags(woodSet);
        }
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                ModBlocks.BRASS_CAULDRON.get(),
                ModBlocks.WIZARDING_COPPER_CAULDRON.get(),
                ModBlocks.PEWTER_CAULDRON.get(),
                ModBlocks.FLOO_GRATE.get(),
                ModBlocks.UNLIT_LANTERN.get(),
                ModBlocks.UNLIT_COPPER_LANTERN.get(),
                ModBlocks.UNLIT_SOUL_LANTERN.get(),
                ModBlocks.UNLIT_GLOWSTONE.get());
        tag(BlockTags.MINEABLE_WITH_AXE).add(
                ModBlocks.UNLIT_TORCH.get(),
                ModBlocks.UNLIT_WALL_TORCH.get(),
                ModBlocks.UNLIT_COPPER_TORCH.get(),
                ModBlocks.UNLIT_COPPER_WALL_TORCH.get(),
                ModBlocks.UNLIT_SOUL_TORCH.get(),
                ModBlocks.UNLIT_SOUL_WALL_TORCH.get());

        addModuleTags();
    }

    /**
     * Declares which module owns each block, mirroring the item side.
     *
     * <p>This governs whether a block may be <em>interacted with</em>, never whether it drops: a placed
     * block of a switched-off module goes inert and stays in the world, and breaking it still returns it.
     */
    private void addModuleTags() {
        tag(ModuleTags.blocks(Module.STRUCTURES)).add(LocationBlockHelper.allBlocks().stream()
                .map(block -> (Block) block.get())
                .toArray(Block[]::new));
        // Diagon's street stone registers straight into ModBlocks rather than through the helper, so it
        // is not in allBlocks() and has to be named here alongside its items.
        tag(ModuleTags.blocks(Module.STRUCTURES)).add(
                DiagonAlleyBlocks.DIAGON_STREET_STONE.get(),
                DiagonAlleyBlocks.DIAGON_STREET_STONE_SLAB.get(),
                DiagonAlleyBlocks.DIAGON_STREET_STONE_PRESSURE_PLATE.get());

        tag(ModuleTags.blocks(Module.POCKET_DIMENSIONS)).add(
                ModBlocks.ENCHANTED_TRUNK.get(), ModBlocks.EXPANDED_TRUNK.get(),
                ModBlocks.MASTERS_TRUNK.get(), ModBlocks.MOODYS_TRUNK.get(),
                ModBlocks.NEWTS_CASE.get(), ModBlocks.POCKET_CONFIGURATOR.get(),
                ModBlocks.TENT_CANVAS.get(), ModBlocks.TENT_GRAND.get());

        for (WoodSet woodSet : ModBlocks.ALL_WOOD_SETS) {
            tag(ModuleTags.blocks(Module.WANDWOOD)).add(
                    woodSet.log().get(), woodSet.strippedLog().get(),
                    woodSet.wood().get(), woodSet.strippedWood().get(),
                    woodSet.planks().get(), woodSet.slab().get(), woodSet.stairs().get(),
                    woodSet.leaves().get(), woodSet.sapling().get());
        }

        tag(ModuleTags.blocks(Module.FURNISHINGS)).add(
                ModBlocks.WARDING_STONE.get(), ModBlocks.DEVILS_SNARE.get(), ModBlocks.MALLOWSWEET.get(),
                ModBlocks.GRYFFINDOR_BANNER.get(), ModBlocks.SLYTHERIN_BANNER.get(),
                ModBlocks.RAVENCLAW_BANNER.get(), ModBlocks.HUFFLEPUFF_BANNER.get(),
                ModBlocks.FLOATING_CANDLE.get(), ModBlocks.BRASS_CAULDRON.get(),
                ModBlocks.WIZARDING_COPPER_CAULDRON.get(), ModBlocks.PEWTER_CAULDRON.get(),
                ModBlocks.SPELL_TEACHER.get());

        tag(ModuleTags.blocks(Module.FLOO_NETWORK)).add(
                ModBlocks.FLOO_GRATE.get(), ModBlocks.FLOO_FIREPLACE.get());

        tag(ModuleTags.blocks(Module.WANDS)).add(ModBlocks.WANDMAKERS_BENCH.get());
        tag(ModuleTags.blocks(Module.OWLS)).add(ModBlocks.EXAMINATION_DESK.get());
        tag(ModuleTags.blocks(Module.MAGIZOOLOGY)).add(ModBlocks.MANDRAKE_CROP.get());

        // The Deluminator's paired light blocks: they only ever exist because a Deluminator made them.
        tag(ModuleTags.blocks(Module.ARTEFACTS)).add(
                ModBlocks.DELUMINATOR_LIGHT.get(), ModBlocks.UNLIT_TORCH.get(), ModBlocks.UNLIT_WALL_TORCH.get(),
                ModBlocks.UNLIT_SOUL_TORCH.get(), ModBlocks.UNLIT_SOUL_WALL_TORCH.get(),
                ModBlocks.UNLIT_COPPER_TORCH.get(), ModBlocks.UNLIT_COPPER_WALL_TORCH.get(),
                ModBlocks.UNLIT_LANTERN.get(), ModBlocks.UNLIT_SOUL_LANTERN.get(),
                ModBlocks.UNLIT_COPPER_LANTERN.get(), ModBlocks.UNLIT_GLOWSTONE.get());
    }

    private void addWoodSetTags(WoodSet woodSet) {
        Block log = woodSet.log().get();
        Block strippedLog = woodSet.strippedLog().get();
        Block woodBlock = woodSet.wood().get();
        Block strippedWood = woodSet.strippedWood().get();
        Block planks = woodSet.planks().get();
        Block slab = woodSet.slab().get();
        Block stairs = woodSet.stairs().get();
        Block leaves = woodSet.leaves().get();
        Block sapling = woodSet.sapling().get();

        tag(BlockTags.LOGS).add(log, strippedLog, woodBlock, strippedWood);
        tag(BlockTags.LOGS_THAT_BURN).add(log, strippedLog, woodBlock, strippedWood);

        tag(BlockTags.PLANKS).add(planks);
        tag(BlockTags.WOODEN_SLABS).add(slab);
        tag(BlockTags.WOODEN_STAIRS).add(stairs);
        tag(BlockTags.SLABS).add(slab);
        tag(BlockTags.STAIRS).add(stairs);

        tag(BlockTags.LEAVES).add(leaves);
        tag(BlockTags.SAPLINGS).add(sapling);

        tag(BlockTags.MINEABLE_WITH_AXE).add(log, strippedLog, woodBlock, strippedWood, planks, slab, stairs);
        tag(BlockTags.MINEABLE_WITH_HOE).add(leaves);
    }
}
