package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.MandrakeCropBlock;
import at.koopro.wizardsandbeasts.world.ModConfiguredFeatures;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
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

    public static final DeferredBlock<Block> DEVILS_SNARE = WizardingWorldBlockRegistry.DEVILS_SNARE;
    public static final DeferredBlock<? extends Block> MALLOWSWEET = WizardingWorldBlockRegistry.MALLOWSWEET;
    public static final DeferredBlock<? extends Block> GRYFFINDOR_BANNER = WizardingWorldBlockRegistry.GRYFFINDOR_BANNER;
    public static final DeferredBlock<? extends Block> SLYTHERIN_BANNER = WizardingWorldBlockRegistry.SLYTHERIN_BANNER;
    public static final DeferredBlock<? extends Block> RAVENCLAW_BANNER = WizardingWorldBlockRegistry.RAVENCLAW_BANNER;
    public static final DeferredBlock<? extends Block> HUFFLEPUFF_BANNER = WizardingWorldBlockRegistry.HUFFLEPUFF_BANNER;
    public static final DeferredBlock<? extends Block> FLOATING_CANDLE = WizardingWorldBlockRegistry.FLOATING_CANDLE;
    public static final DeferredBlock<Block> BRASS_CAULDRON = WizardingWorldBlockRegistry.BRASS_CAULDRON;
    public static final DeferredBlock<Block> WIZARDING_COPPER_CAULDRON = WizardingWorldBlockRegistry.WIZARDING_COPPER_CAULDRON;
    public static final DeferredBlock<Block> PEWTER_CAULDRON = WizardingWorldBlockRegistry.PEWTER_CAULDRON;
    public static final DeferredBlock<Block> FLOO_GRATE = WizardingWorldBlockRegistry.FLOO_GRATE;
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

    public static final WoodSet[] ALL_WOOD_SETS = { ELDER, YEW, HOLLY, ROWAN };
}
