package at.koopro.wizardsandbeasts.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.data.loot.BlockLootSubProvider;

import at.koopro.wizardsandbeasts.block.location.DiagonAlleyBlocks;
import at.koopro.wizardsandbeasts.block.location.GringottsBlocks;
import at.koopro.wizardsandbeasts.block.location.HogwartsBlocks;
import at.koopro.wizardsandbeasts.block.location.HogsmeadeBlocks;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper;
import at.koopro.wizardsandbeasts.block.location.MinistryBlocks;
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

        // Existing blocks that were missing loot entries — fixed alongside location blocks.
        dropSelf(ModBlocks.WANDMAKERS_BENCH.get());
        dropSelf(ModBlocks.WARDING_STONE.get());
        dropSelf(ModBlocks.POCKET_CONFIGURATOR.get());
        dropSelf(ModBlocks.FLOO_FIREPLACE.get());
        dropSelf(ModBlocks.EXAMINATION_DESK.get());

        // Placed trunks drop nothing via loot — TrunkBlock.playerWillDestroy hand-drops the BlockItem
        // with its packed POCKET_CASE_ID component (dropSelf would drop a plain item AND double up).
        add(ModBlocks.ENCHANTED_TRUNK.get(), noDrop());
        add(ModBlocks.EXPANDED_TRUNK.get(), noDrop());
        add(ModBlocks.MASTERS_TRUNK.get(), noDrop());
        add(ModBlocks.MOODYS_TRUNK.get(), noDrop());
        add(ModBlocks.NEWTS_CASE.get(), noDrop());

        generateLocationBlocksLoot();
    }

    private void generateLocationBlocksLoot() {
        // Ministry of Magic
        genVariantLoot(MinistryBlocks.MINISTRY_BLACK_MARBLE);
        dropSelf(MinistryBlocks.MINISTRY_BLACK_MARBLE_PILLAR.block().get());
        genStairLoot(MinistryBlocks.MINISTRY_BLACK_MARBLE_TILES);
        genVariantLoot(MinistryBlocks.MINISTRY_GILDED_BLACK_MARBLE);
        dropSelf(MinistryBlocks.MINISTRY_GILDED_TRIM.block().get());
        genStairLoot(MinistryBlocks.MINISTRY_DARK_TILE);
        genSlabLoot(MinistryBlocks.MINISTRY_FLOOR_TILE);
        dropSelf(MinistryBlocks.MINISTRY_WALL_PANEL.block().get());

        // Hogwarts Castle
        genVariantLoot(HogwartsBlocks.HOGWARTS_STONE);
        genVariantLoot(HogwartsBlocks.HOGWARTS_STONE_BRICKS);
        dropSelf(HogwartsBlocks.HOGWARTS_CRACKED_STONE_BRICKS.block().get());
        genVariantLoot(HogwartsBlocks.HOGWARTS_MOSSY_STONE_BRICKS);
        dropSelf(HogwartsBlocks.HOGWARTS_STONE_PILLAR.block().get());
        genVariantLoot(HogwartsBlocks.HOGWARTS_DARK_STONE);
        genSlabLoot(HogwartsBlocks.HOGWARTS_FLAGSTONE);
        genSlabLoot(HogwartsBlocks.HOGWARTS_FLOOR_TILE);
        dropSelf(HogwartsBlocks.ENCHANTED_CEILING_TILE.block().get());

        // Diagon Alley
        genVariantLoot(DiagonAlleyBlocks.DIAGON_BRICK);
        genStairLoot(DiagonAlleyBlocks.DIAGON_BRICK_TILES);
        genVariantLoot(DiagonAlleyBlocks.DIAGON_WORN_BRICK);
        genVariantLoot(DiagonAlleyBlocks.DIAGON_COBBLESTONE);
        genStairLoot(DiagonAlleyBlocks.DIAGON_SHOPFRONT_WOOD);
        genStairLoot(DiagonAlleyBlocks.DIAGON_SHOPFRONT_PLANKS);
        genStairLoot(DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_GREEN);
        genStairLoot(DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_PURPLE);
        dropSelf(DiagonAlleyBlocks.DIAGON_STREET_STONE.get());
        add(DiagonAlleyBlocks.DIAGON_STREET_STONE_SLAB.get(), this::createSlabItemTable);
        dropSelf(DiagonAlleyBlocks.DIAGON_STREET_STONE_PRESSURE_PLATE.get());

        // Hogsmeade
        genVariantLoot(HogsmeadeBlocks.HOGSMEADE_STONE);
        genVariantLoot(HogsmeadeBlocks.HOGSMEADE_STONE_BRICKS);
        genVariantLoot(HogsmeadeBlocks.HOGSMEADE_WORN_STONE);
        genStairLoot(HogsmeadeBlocks.THREE_BROOMSTICKS_TIMBER);
        genStairLoot(HogsmeadeBlocks.THREE_BROOMSTICKS_PLANKS);
        genStairLoot(HogsmeadeBlocks.HONEYDUKES_PASTEL_PINK);
        genStairLoot(HogsmeadeBlocks.HONEYDUKES_PASTEL_YELLOW);
        genStairLoot(HogsmeadeBlocks.HOGSMEADE_ROOF_TILE);
        genVariantLoot(HogsmeadeBlocks.HOGSMEADE_CHIMNEY_BRICK);

        // Gringotts Bank
        genVariantLoot(GringottsBlocks.GRINGOTTS_WHITE_MARBLE);
        dropSelf(GringottsBlocks.GRINGOTTS_WHITE_MARBLE_PILLAR.block().get());
        genStairLoot(GringottsBlocks.GRINGOTTS_WHITE_MARBLE_TILES);
        genVariantLoot(GringottsBlocks.GRINGOTTS_PALE_MARBLE);
        dropSelf(GringottsBlocks.GRINGOTTS_GOLD_TRIM.block().get());
        genVariantLoot(GringottsBlocks.GRINGOTTS_IRON_VAULT_STONE);
        genVariantLoot(GringottsBlocks.GRINGOTTS_VAULT_BRICKS);
        genVariantLoot(GringottsBlocks.GRINGOTTS_GOBLIN_STONEWORK);
        genSlabLoot(GringottsBlocks.GRINGOTTS_COUNTING_FLOOR);
    }

    private void genVariantLoot(LocationBlockHelper.VariantSet s) {
        dropSelf(s.base().get());
        add(s.slab().get(), this::createSlabItemTable);
        dropSelf(s.stairs().get());
        dropSelf(s.wall().get());
    }

    private void genStairLoot(LocationBlockHelper.StairSet s) {
        dropSelf(s.base().get());
        add(s.slab().get(), this::createSlabItemTable);
        dropSelf(s.stairs().get());
    }

    private void genSlabLoot(LocationBlockHelper.SlabSet s) {
        dropSelf(s.base().get());
        add(s.slab().get(), this::createSlabItemTable);
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
