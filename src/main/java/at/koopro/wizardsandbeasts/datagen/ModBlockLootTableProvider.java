package at.koopro.wizardsandbeasts.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.advancements.criterion.StatePropertiesPredicate;
import net.minecraft.data.loot.BlockLootSubProvider;

import at.koopro.wizardsandbeasts.block.location.DiagonAlleyBlocks;
import at.koopro.wizardsandbeasts.block.location.GringottsBlocks;
import at.koopro.wizardsandbeasts.block.location.HogwartsBlocks;
import at.koopro.wizardsandbeasts.block.location.HogsmeadeBlocks;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper;
import at.koopro.wizardsandbeasts.block.location.MinistryBlocks;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WoodSet;

import org.jspecify.annotations.Nullable;

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
            generateWoodSetLoot(woodSet, herbFor(woodSet));
        }

        // Vanilla crop drops: the Mandrake itself only when fully grown, seeds otherwise. Dropping seeds
        // at every age (the previous dropOther) left the Mandrake item unobtainable in survival, which
        // took the Mandrake Restoration Draught and the Animagus ritual with it.
        add(ModBlocks.MANDRAKE_CROP.get(), block -> createCropDrops(
                block,
                ConsumableItemRegistry.MANDRAKE.get(),
                ModBlocks.MANDRAKE_SEEDS.get(),
                LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                        .setProperties(StatePropertiesPredicate.Builder.properties()
                                .hasProperty(CropBlock.AGE, ModBlocks.MANDRAKE_CROP.get().getMaxAge()))));
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
        // Both tents were registered without a loot entry, which hard-failed the whole datagen run
        // ("Missing loottable ... for wizards_and_beasts:tent_canvas"). They are a /setblock-only preview
        // scaffold with no BlockItem, so the honest table is an empty one — dropSelf would serialize air.
        add(ModBlocks.TENT_CANVAS.get(), noDrop());
        add(ModBlocks.TENT_GRAND.get(), noDrop());

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

    private void generateWoodSetLoot(WoodSet woodSet, @Nullable Item herb) {
        dropSelf(woodSet.log().get());
        dropSelf(woodSet.strippedLog().get());
        dropSelf(woodSet.wood().get());
        dropSelf(woodSet.strippedWood().get());
        dropSelf(woodSet.planks().get());
        add(woodSet.slab().get(), this::createSlabItemTable);
        dropSelf(woodSet.stairs().get());
        add(woodSet.leaves().get(), block -> createMagicalLeavesDrops(block, woodSet.sapling().get(), herb));
        dropSelf(woodSet.sapling().get());
    }

    /**
     * The herb a wood set's foliage yields, or {@code null} for none. Dittany and Dirigible Plums were
     * registered as brewing/consumable items with no survival source anywhere, which is what left the
     * Wiggenweld Potion uncraftable; magical foliage is the source that needs no new block or feature.
     */
    private static @Nullable Item herbFor(WoodSet woodSet) {
        return switch (woodSet.name()) {
            case "rowan", "holly" -> ConsumableItemRegistry.DITTANY.get();
            case "elder", "yew" -> ConsumableItemRegistry.DIRIGIBLE_PLUM.get();
            default -> null;
        };
    }

    /** Standard leaves drops, plus an apple-rate herb pool when the set carries one. */
    private LootTable.Builder createMagicalLeavesDrops(Block leaves, Block sapling, @Nullable Item herb) {
        LootTable.Builder table = createLeavesDrops(leaves, sapling, NORMAL_LEAVES_SAPLING_CHANCES);
        if (herb == null) {
            return table;
        }
        HolderLookup.RegistryLookup<Enchantment> enchantments = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return table.withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .when(this.doesNotHaveSilkTouch())
                .add(this.applyExplosionCondition(leaves, LootItem.lootTableItem(herb))
                        .when(BonusLevelTableCondition.bonusLevelFlatChance(
                                enchantments.getOrThrow(Enchantments.FORTUNE),
                                0.02F, 0.022222223F, 0.025F, 0.033333335F, 0.1F))));
    }
}
