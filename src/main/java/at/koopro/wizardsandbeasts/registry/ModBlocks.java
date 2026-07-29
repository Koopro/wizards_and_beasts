package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.ExaminationDeskBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.block.MandrakeCropBlock;
import at.koopro.wizardsandbeasts.block.trunk.PocketConfiguratorBlock;
import at.koopro.wizardsandbeasts.block.trunk.TentCanvasBlock;
import at.koopro.wizardsandbeasts.block.trunk.TentGrandBlock;
import at.koopro.wizardsandbeasts.block.trunk.TrunkBlock;
import at.koopro.wizardsandbeasts.trunk.TrunkArchetype;
import at.koopro.wizardsandbeasts.trunk.TrunkTier;
import at.koopro.wizardsandbeasts.block.WardingStoneBlock;
import at.koopro.wizardsandbeasts.block.location.DiagonAlleyBlocks;
import at.koopro.wizardsandbeasts.block.location.GringottsBlocks;
import at.koopro.wizardsandbeasts.block.location.HogwartsBlocks;
import at.koopro.wizardsandbeasts.block.location.HogsmeadeBlocks;
import at.koopro.wizardsandbeasts.block.location.MinistryBlocks;
import at.koopro.wizardsandbeasts.world.ModConfiguredFeatures;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WizardsAndBeastsMod.MODID);

    private ModBlocks() {
    }

    private static final TreeGrower ELDER_TREE_GROWER =
            new TreeGrower("elder", Optional.empty(), Optional.of(ModConfiguredFeatures.ELDER_TREE_KEY), Optional.empty());
    private static final TreeGrower YEW_TREE_GROWER =
            new TreeGrower("yew", Optional.empty(), Optional.of(ModConfiguredFeatures.YEW_TREE_KEY), Optional.empty());
    private static final TreeGrower HOLLY_TREE_GROWER =
            new TreeGrower("holly", Optional.empty(), Optional.of(ModConfiguredFeatures.HOLLY_TREE_KEY), Optional.empty());
    private static final TreeGrower ROWAN_TREE_GROWER =
            new TreeGrower("rowan", Optional.empty(), Optional.of(ModConfiguredFeatures.ROWAN_TREE_KEY), Optional.empty());

    private static BlockBehaviour.Properties logProps() {
        return BlockBehaviour.Properties.of()
                .strength(2.0F)
                .sound(SoundType.WOOD);
    }

    private static BlockBehaviour.Properties planksProps() {
        return BlockBehaviour.Properties.of()
                .strength(2.0F, 3.0F)
                .sound(SoundType.WOOD);
    }

    private static BlockBehaviour.Properties leavesProps() {
        return BlockBehaviour.Properties.of()
                .strength(0.2F)
                .randomTicks()
                .sound(SoundType.GRASS)
                .noOcclusion()
                .isSuffocating((state, getter, pos) -> false)
                .isViewBlocking((state, getter, pos) -> false)
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties saplingProps() {
        return BlockBehaviour.Properties.of()
                .randomTicks()
                .instabreak()
                .sound(SoundType.GRASS);
    }

    /**
     * Registered before wood sets so {@link ModItems} can reference it when {@link ModItems} is loaded
     * from {@link WoodSet#register}.
     */
    public static final DeferredBlock<MandrakeCropBlock> MANDRAKE_CROP =
            BLOCKS.registerBlock("mandrake_crop", MandrakeCropBlock::new,
                    () -> BlockBehaviour.Properties.of()
                            .noCollision()
                            .noOcclusion()
                            .randomTicks()
                            .instabreak()
                            .sound(SoundType.CROP)
                            .pushReaction(PushReaction.DESTROY));

    /** Registered here with {@link DeferredBlock} reference (no early {@code .get()}) to avoid init-order issues with wood sets. */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MANDRAKE_SEEDS =
            ModItems.ITEMS.registerSimpleBlockItem("mandrake_seeds", MANDRAKE_CROP);

    // --- Wood sets ---

    public static final WoodSet ELDER = WoodSet.register(BLOCKS, ModItems.ITEMS,
            "elder", logProps(), planksProps(), leavesProps(), saplingProps(), ELDER_TREE_GROWER);
    public static final WoodSet YEW = WoodSet.register(BLOCKS, ModItems.ITEMS,
            "yew", logProps(), planksProps(), leavesProps(), saplingProps(), YEW_TREE_GROWER);
    public static final WoodSet HOLLY = WoodSet.register(BLOCKS, ModItems.ITEMS,
            "holly", logProps(), planksProps(), leavesProps(), saplingProps(), HOLLY_TREE_GROWER);
    public static final WoodSet ROWAN = WoodSet.register(BLOCKS, ModItems.ITEMS,
            "rowan", logProps(), planksProps(), leavesProps(), saplingProps(), ROWAN_TREE_GROWER);

    // --- Wizarding world ---

    public static final DeferredBlock<Block> DEVILS_SNARE = WizardingWorldBlockRegistry.DEVILS_SNARE;
    public static final DeferredBlock<? extends Block> MALLOWSWEET = WizardingWorldBlockRegistry.MALLOWSWEET;
    public static final DeferredBlock<? extends Block> GRYFFINDOR_BANNER = WizardingWorldBlockRegistry.GRYFFINDOR_BANNER;
    public static final DeferredBlock<? extends Block> SLYTHERIN_BANNER = WizardingWorldBlockRegistry.SLYTHERIN_BANNER;
    public static final DeferredBlock<? extends Block> RAVENCLAW_BANNER = WizardingWorldBlockRegistry.RAVENCLAW_BANNER;
    public static final DeferredBlock<? extends Block> HUFFLEPUFF_BANNER = WizardingWorldBlockRegistry.HUFFLEPUFF_BANNER;
    public static final DeferredBlock<? extends Block> FLOATING_CANDLE = WizardingWorldBlockRegistry.FLOATING_CANDLE;
    public static final DeferredBlock<? extends Block> BRASS_CAULDRON = WizardingWorldBlockRegistry.BRASS_CAULDRON;
    public static final DeferredBlock<? extends Block> WIZARDING_COPPER_CAULDRON = WizardingWorldBlockRegistry.WIZARDING_COPPER_CAULDRON;
    public static final DeferredBlock<? extends Block> PEWTER_CAULDRON = WizardingWorldBlockRegistry.PEWTER_CAULDRON;
    /**
     * @deprecated Decorative prop only — no travel logic. Use {@link #FLOO_FIREPLACE}.
     *             Will be removed in beta.
     */
    @Deprecated
    public static final DeferredBlock<? extends Block> FLOO_GRATE = WizardingWorldBlockRegistry.FLOO_GRATE;
    public static final DeferredBlock<? extends Block> SPELL_TEACHER = WizardingWorldBlockRegistry.SPELL_TEACHER;
    public static final DeferredBlock<? extends Block> WANDMAKERS_BENCH = WizardingWorldBlockRegistry.WANDMAKERS_BENCH;

    public static final DeferredItem<BlockItem> DEVILS_SNARE_ITEM = WizardingWorldBlockRegistry.DEVILS_SNARE_ITEM;
    public static final DeferredItem<BlockItem> MALLOWSWEET_ITEM = WizardingWorldBlockRegistry.MALLOWSWEET_ITEM;
    public static final DeferredItem<BlockItem> GRYFFINDOR_BANNER_ITEM = WizardingWorldBlockRegistry.GRYFFINDOR_BANNER_ITEM;
    public static final DeferredItem<BlockItem> SLYTHERIN_BANNER_ITEM = WizardingWorldBlockRegistry.SLYTHERIN_BANNER_ITEM;
    public static final DeferredItem<BlockItem> RAVENCLAW_BANNER_ITEM = WizardingWorldBlockRegistry.RAVENCLAW_BANNER_ITEM;
    public static final DeferredItem<BlockItem> HUFFLEPUFF_BANNER_ITEM = WizardingWorldBlockRegistry.HUFFLEPUFF_BANNER_ITEM;
    public static final DeferredItem<BlockItem> FLOATING_CANDLE_ITEM = WizardingWorldBlockRegistry.FLOATING_CANDLE_ITEM;
    public static final DeferredItem<BlockItem> BRASS_CAULDRON_ITEM = WizardingWorldBlockRegistry.BRASS_CAULDRON_ITEM;
    public static final DeferredItem<BlockItem> WIZARDING_COPPER_CAULDRON_ITEM = WizardingWorldBlockRegistry.WIZARDING_COPPER_CAULDRON_ITEM;
    public static final DeferredItem<BlockItem> PEWTER_CAULDRON_ITEM = WizardingWorldBlockRegistry.PEWTER_CAULDRON_ITEM;
    public static final DeferredItem<BlockItem> FLOO_GRATE_ITEM = WizardingWorldBlockRegistry.FLOO_GRATE_ITEM;
    public static final DeferredItem<BlockItem> SPELL_TEACHER_ITEM = WizardingWorldBlockRegistry.SPELL_TEACHER_ITEM;
    public static final DeferredItem<BlockItem> WANDMAKERS_BENCH_ITEM = WizardingWorldBlockRegistry.WANDMAKERS_BENCH_ITEM;

    // --- Deluminator blocks (no block items — placed/removed only by the Deluminator) ---

    public static final DeferredBlock<? extends Block> DELUMINATOR_LIGHT = DeluminatorBlockRegistry.DELUMINATOR_LIGHT;
    public static final DeferredBlock<? extends Block> UNLIT_TORCH = DeluminatorBlockRegistry.UNLIT_TORCH;
    public static final DeferredBlock<? extends Block> UNLIT_WALL_TORCH = DeluminatorBlockRegistry.UNLIT_WALL_TORCH;
    public static final DeferredBlock<? extends Block> UNLIT_SOUL_TORCH = DeluminatorBlockRegistry.UNLIT_SOUL_TORCH;
    public static final DeferredBlock<? extends Block> UNLIT_SOUL_WALL_TORCH = DeluminatorBlockRegistry.UNLIT_SOUL_WALL_TORCH;
    public static final DeferredBlock<? extends Block> UNLIT_COPPER_TORCH = DeluminatorBlockRegistry.UNLIT_COPPER_TORCH;
    public static final DeferredBlock<? extends Block> UNLIT_COPPER_WALL_TORCH = DeluminatorBlockRegistry.UNLIT_COPPER_WALL_TORCH;
    public static final DeferredBlock<? extends Block> UNLIT_LANTERN = DeluminatorBlockRegistry.UNLIT_LANTERN;
    public static final DeferredBlock<? extends Block> UNLIT_SOUL_LANTERN = DeluminatorBlockRegistry.UNLIT_SOUL_LANTERN;
    public static final DeferredBlock<? extends Block> UNLIT_COPPER_LANTERN = DeluminatorBlockRegistry.UNLIT_COPPER_LANTERN;
    public static final DeferredBlock<Block> UNLIT_GLOWSTONE = DeluminatorBlockRegistry.UNLIT_GLOWSTONE;

    // --- Pocket dimension blocks ---

    public static final DeferredBlock<WardingStoneBlock> WARDING_STONE =
            BLOCKS.registerBlock("warding_stone", WardingStoneBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(1.5f, 6.0f).sound(SoundType.STONE).requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> WARDING_STONE_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("warding_stone", WARDING_STONE);

    public static final DeferredBlock<PocketConfiguratorBlock> POCKET_CONFIGURATOR =
            BLOCKS.registerBlock("pocket_configurator", PocketConfiguratorBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> POCKET_CONFIGURATOR_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("pocket_configurator", POCKET_CONFIGURATOR);

    // --- Placed trunks (all tiers) ---
    // Lore: you set the trunk down and climb in. Right-click descends; sneak-click cycles the lock
    // (multi-lock) or toggles Muggle-Worthy (single-lock). Broken/picked, the BlockItem carries the
    // packed pocket reference (POCKET_CASE_ID) so re-placing re-links the same compartments.
    // Signature: TrunkBlock(radiusTier, lockCount, archetype). lockCount is independent of size.

    public static final DeferredBlock<TrunkBlock> ENCHANTED_TRUNK =
            BLOCKS.registerBlock("enchanted_trunk",
                    props -> new TrunkBlock(TrunkTier.TIER_1, 1, TrunkArchetype.FIELD_CAMP, props),
                    () -> BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.WOOD).requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredItem<BlockItem> ENCHANTED_TRUNK_ITEM =
            ModItems.ITEMS.registerItem("enchanted_trunk", props -> new BlockItem(ENCHANTED_TRUNK.get(), props.stacksTo(1)));

    public static final DeferredBlock<TrunkBlock> EXPANDED_TRUNK =
            BLOCKS.registerBlock("expanded_trunk",
                    props -> new TrunkBlock(TrunkTier.TIER_2, 3, TrunkArchetype.MINISTRY_STANDARD, props),
                    () -> BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.WOOD).requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredItem<BlockItem> EXPANDED_TRUNK_ITEM =
            ModItems.ITEMS.registerItem("expanded_trunk", props -> new BlockItem(EXPANDED_TRUNK.get(), props.stacksTo(1)));

    public static final DeferredBlock<TrunkBlock> MASTERS_TRUNK =
            BLOCKS.registerBlock("masters_trunk",
                    props -> new TrunkBlock(TrunkTier.TIER_3, 7, TrunkArchetype.MINISTRY_STANDARD, props),
                    () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.WOOD).requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredItem<BlockItem> MASTERS_TRUNK_ITEM =
            ModItems.ITEMS.registerItem("masters_trunk", props -> new BlockItem(MASTERS_TRUNK.get(), props.stacksTo(1)));

    public static final DeferredBlock<TrunkBlock> MOODYS_TRUNK =
            BLOCKS.registerBlock("moodys_trunk",
                    props -> new TrunkBlock(TrunkTier.TIER_3, 7, TrunkArchetype.SAFEHOUSE, props),
                    () -> BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredItem<BlockItem> MOODYS_TRUNK_ITEM =
            ModItems.ITEMS.registerItem("moodys_trunk", props -> new BlockItem(MOODYS_TRUNK.get(), props.stacksTo(1)));

    // Newt's Case — single large Scamander habitat, Muggle-Worthy toggle on the lock (single-lock).
    public static final DeferredBlock<TrunkBlock> NEWTS_CASE =
            BLOCKS.registerBlock("newts_case_item",
                    props -> new TrunkBlock(TrunkTier.TIER_3, 1, TrunkArchetype.SCAMANDER_SANCTUARY, props),
                    () -> BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.WOOD).requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredItem<BlockItem> NEWTS_CASE_ITEM =
            ModItems.ITEMS.registerItem("newts_case_item", props -> new BlockItem(NEWTS_CASE.get(), props.stacksTo(1)));

    // --- Tent preview harness (exterior GeckoLib geometry only — see AUDIT_PUNCHLIST.md "Tent Models") ---
    // Deliberately no BlockItem: this is a /setblock-only scaffold for viewing the two models in-world.
    // Real item forms, crafting and pocket-space wiring are explicitly out of scope for this pass.
    public static final DeferredBlock<TentCanvasBlock> TENT_CANVAS =
            BLOCKS.registerBlock("tent_canvas", TentCanvasBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.WOOL).noOcclusion());

    public static final DeferredBlock<TentGrandBlock> TENT_GRAND =
            BLOCKS.registerBlock("tent_grand", TentGrandBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.WOOL).noOcclusion());

    // --- Floo Network ---

    // TODO: asset floo_fireplace.png
    public static final DeferredBlock<FlooFireplaceBlock> FLOO_FIREPLACE =
            BLOCKS.registerBlock("floo_fireplace", FlooFireplaceBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(3.5f, 10.0f).sound(SoundType.STONE)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
                            .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 10 : 0));

    public static final DeferredItem<BlockItem> FLOO_FIREPLACE_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("floo_fireplace", FLOO_FIREPLACE);

    // --- Hogwarts structure blocks ---

    /** Placed inside Hogwarts worldgen structure template. Block tag: data/wizards_and_beasts/tags/block/hogwarts_structure_blocks.json */
    public static final DeferredBlock<ExaminationDeskBlock> EXAMINATION_DESK =
            BLOCKS.registerBlock("examination_desk", ExaminationDeskBlock::new,
                    () -> BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.WOOD).requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredItem<BlockItem> EXAMINATION_DESK_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("examination_desk", EXAMINATION_DESK);

    public static final WoodSet[] ALL_WOOD_SETS = { ELDER, YEW, HOLLY, ROWAN };

    // --- Location decorative blocks ---
    // Static initializer forces class loading of each location registry,
    // which triggers DeferredRegister entries before the register event fires.
    // Registration is intentionally unconditional ("gate, don't delete"); the STRUCTURES
    // module gates their crafting recipes via ModuleEnabledCondition in ModRecipeProvider.
    static {
        MinistryBlocks.init();
        HogwartsBlocks.init();
        DiagonAlleyBlocks.init();
        HogsmeadeBlocks.init();
        GringottsBlocks.init();
    }
}
