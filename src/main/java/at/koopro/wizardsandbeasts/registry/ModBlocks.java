package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.DeluminatorLightBlock;
import at.koopro.wizardsandbeasts.block.FloatingCandleBlock;
import at.koopro.wizardsandbeasts.block.HouseBannerBlock;
import at.koopro.wizardsandbeasts.block.MallowsweetBlock;
import at.koopro.wizardsandbeasts.block.MandrakeCropBlock;
import at.koopro.wizardsandbeasts.block.SpellTeacherBlock;
import at.koopro.wizardsandbeasts.block.UnlitTorchBlock;
import at.koopro.wizardsandbeasts.block.UnlitWallTorchBlock;
import at.koopro.wizardsandbeasts.wand.block.WandmakersBenchBlock;
import at.koopro.wizardsandbeasts.world.ModConfiguredFeatures;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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
                    BlockBehaviour.Properties.of()
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

    public static final DeferredBlock<Block> DEVILS_SNARE =
            BLOCKS.registerBlock("devils_snare", Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.COBWEB));

    public static final DeferredBlock<MallowsweetBlock> MALLOWSWEET =
            BLOCKS.registerBlock("mallowsweet", MallowsweetBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.PLANT)
                            .noCollision()
                            .instabreak()
                            .sound(SoundType.GRASS)
                            .offsetType(BlockBehaviour.OffsetType.XZ)
                            .pushReaction(PushReaction.DESTROY));

    public static final DeferredBlock<HouseBannerBlock> GRYFFINDOR_BANNER =
            BLOCKS.registerBlock("gryffindor_banner", HouseBannerBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instabreak().sound(SoundType.WOOL).noOcclusion());
    public static final DeferredBlock<HouseBannerBlock> SLYTHERIN_BANNER =
            BLOCKS.registerBlock("slytherin_banner", HouseBannerBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).instabreak().sound(SoundType.WOOL).noOcclusion());
    public static final DeferredBlock<HouseBannerBlock> RAVENCLAW_BANNER =
            BLOCKS.registerBlock("ravenclaw_banner", HouseBannerBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).instabreak().sound(SoundType.WOOL).noOcclusion());
    public static final DeferredBlock<HouseBannerBlock> HUFFLEPUFF_BANNER =
            BLOCKS.registerBlock("hufflepuff_banner", HouseBannerBlock::new,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).instabreak().sound(SoundType.WOOL).noOcclusion());

    public static final DeferredBlock<FloatingCandleBlock> FLOATING_CANDLE =
            BLOCKS.registerBlock("floating_candle", FloatingCandleBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOL)
                            .noCollision()
                            .instabreak()
                            .lightLevel(s -> 14)
                            .sound(SoundType.CANDLE)
                            .noOcclusion());

    public static final DeferredBlock<Block> BRASS_CAULDRON =
            BLOCKS.registerBlock("brass_cauldron", Block::new,
                    BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<Block> WIZARDING_COPPER_CAULDRON =
            BLOCKS.registerBlock("wizarding_copper_cauldron", Block::new,
                    BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<Block> PEWTER_CAULDRON =
            BLOCKS.registerBlock("pewter_cauldron", Block::new,
                    BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.COPPER).noOcclusion());

    public static final DeferredBlock<Block> FLOO_GRATE =
            BLOCKS.registerBlock("floo_grate", Block::new,
                    BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.COPPER).noOcclusion());

    public static final DeferredBlock<SpellTeacherBlock> SPELL_TEACHER =
            BLOCKS.registerBlock("spell_teacher", SpellTeacherBlock::new,
                    BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.WOOD));

    public static final DeferredBlock<WandmakersBenchBlock> WANDMAKERS_BENCH =
            BLOCKS.registerBlock("wandmakers_bench", WandmakersBenchBlock::new,
                    BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.WOOD));

    /** Block items for wizarding blocks (required for loot/datagen: {@code dropSelf} uses {@link Block#asItem()}). */
    public static final DeferredItem<BlockItem> DEVILS_SNARE_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("devils_snare", DEVILS_SNARE);
    public static final DeferredItem<BlockItem> MALLOWSWEET_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("mallowsweet", MALLOWSWEET);
    public static final DeferredItem<BlockItem> GRYFFINDOR_BANNER_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("gryffindor_banner", GRYFFINDOR_BANNER);
    public static final DeferredItem<BlockItem> SLYTHERIN_BANNER_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("slytherin_banner", SLYTHERIN_BANNER);
    public static final DeferredItem<BlockItem> RAVENCLAW_BANNER_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("ravenclaw_banner", RAVENCLAW_BANNER);
    public static final DeferredItem<BlockItem> HUFFLEPUFF_BANNER_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("hufflepuff_banner", HUFFLEPUFF_BANNER);
    public static final DeferredItem<BlockItem> FLOATING_CANDLE_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("floating_candle", FLOATING_CANDLE);
    public static final DeferredItem<BlockItem> BRASS_CAULDRON_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("brass_cauldron", BRASS_CAULDRON);
    public static final DeferredItem<BlockItem> WIZARDING_COPPER_CAULDRON_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("wizarding_copper_cauldron", WIZARDING_COPPER_CAULDRON);
    public static final DeferredItem<BlockItem> PEWTER_CAULDRON_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("pewter_cauldron", PEWTER_CAULDRON);
    public static final DeferredItem<BlockItem> FLOO_GRATE_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("floo_grate", FLOO_GRATE);
    public static final DeferredItem<BlockItem> SPELL_TEACHER_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("spell_teacher", SPELL_TEACHER);
    public static final DeferredItem<BlockItem> WANDMAKERS_BENCH_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("wandmakers_bench", WANDMAKERS_BENCH);

    // --- Deluminator blocks (no block items — placed/removed only by the Deluminator) ---

    public static final DeferredBlock<DeluminatorLightBlock> DELUMINATOR_LIGHT =
            BLOCKS.registerBlock("deluminator_light", DeluminatorLightBlock::new,
                    BlockBehaviour.Properties.of()
                            .noCollision()
                            .noOcclusion()
                            .replaceable()
                            .instabreak()
                            .lightLevel(s -> 15)
                            .pushReaction(PushReaction.DESTROY));

    public static final DeferredBlock<UnlitTorchBlock> UNLIT_TORCH =
            BLOCKS.registerBlock("unlit_torch",
                    props -> new UnlitTorchBlock(ParticleTypes.FLAME, props),
                    BlockBehaviour.Properties.of()
                            .noCollision()
                            .instabreak()
                            .sound(SoundType.WOOD));

    public static final DeferredBlock<UnlitWallTorchBlock> UNLIT_WALL_TORCH =
            BLOCKS.registerBlock("unlit_wall_torch",
                    props -> new UnlitWallTorchBlock(ParticleTypes.FLAME, props),
                    BlockBehaviour.Properties.of()
                            .noCollision()
                            .instabreak()
                            .sound(SoundType.WOOD));

    public static final DeferredBlock<UnlitTorchBlock> UNLIT_SOUL_TORCH =
            BLOCKS.registerBlock("unlit_soul_torch",
                    props -> new UnlitTorchBlock(ParticleTypes.SOUL_FIRE_FLAME, props),
                    BlockBehaviour.Properties.of()
                            .noCollision()
                            .instabreak()
                            .sound(SoundType.WOOD));

    public static final DeferredBlock<UnlitWallTorchBlock> UNLIT_SOUL_WALL_TORCH =
            BLOCKS.registerBlock("unlit_soul_wall_torch",
                    props -> new UnlitWallTorchBlock(ParticleTypes.SOUL_FIRE_FLAME, props),
                    BlockBehaviour.Properties.of()
                            .noCollision()
                            .instabreak()
                            .sound(SoundType.WOOD));

    public static final DeferredBlock<UnlitTorchBlock> UNLIT_COPPER_TORCH =
            BLOCKS.registerBlock("unlit_copper_torch",
                    props -> new UnlitTorchBlock(ParticleTypes.FLAME, props),
                    BlockBehaviour.Properties.of()
                            .noCollision()
                            .instabreak()
                            .sound(SoundType.WOOD));

    public static final DeferredBlock<UnlitWallTorchBlock> UNLIT_COPPER_WALL_TORCH =
            BLOCKS.registerBlock("unlit_copper_wall_torch",
                    props -> new UnlitWallTorchBlock(ParticleTypes.FLAME, props),
                    BlockBehaviour.Properties.of()
                            .noCollision()
                            .instabreak()
                            .sound(SoundType.WOOD));

    public static final DeferredBlock<LanternBlock> UNLIT_LANTERN =
            BLOCKS.registerBlock("unlit_lantern", LanternBlock::new,
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(3.5F)
                            .sound(SoundType.LANTERN));

    public static final DeferredBlock<LanternBlock> UNLIT_SOUL_LANTERN =
            BLOCKS.registerBlock("unlit_soul_lantern", LanternBlock::new,
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(3.5F)
                            .sound(SoundType.LANTERN));

    public static final DeferredBlock<LanternBlock> UNLIT_COPPER_LANTERN =
            BLOCKS.registerBlock("unlit_copper_lantern", LanternBlock::new,
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(3.5F)
                            .sound(SoundType.LANTERN));

    public static final DeferredBlock<Block> UNLIT_GLOWSTONE =
            BLOCKS.registerBlock("unlit_glowstone", Block::new,
                    BlockBehaviour.Properties.of()
                            .strength(0.3F)
                            .sound(SoundType.GLASS));

    public static final WoodSet[] ALL_WOOD_SETS = { ELDER, YEW, HOLLY, ROWAN };
}
