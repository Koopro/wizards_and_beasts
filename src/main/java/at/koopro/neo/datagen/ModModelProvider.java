package at.koopro.neo.datagen;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import at.koopro.neo.Neo;
import at.koopro.neo.registry.ModBlocks;
import at.koopro.neo.registry.ModItems;
import at.koopro.neo.registry.WoodSet;

public class ModModelProvider extends ModelProvider {

    public ModModelProvider(PackOutput output) {
        super(output, Neo.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.declareCustomModelItem(ModItems.DEBUG_WAND.get());
        itemModels.declareCustomModelItem(ModItems.MORPH_WAND.get());
        itemModels.declareCustomModelItem(ModItems.WAND.get());
        itemModels.declareCustomModelItem(ModItems.BROOM_ITEM.get());
        itemModels.declareCustomModelItem(ModItems.MARAUDERS_MAP.get());

        itemModels.declareCustomModelItem(ModItems.PHOENIX_FEATHER.get());
        itemModels.declareCustomModelItem(ModItems.DRAGON_HEARTSTRING.get());
        itemModels.declareCustomModelItem(ModItems.UNICORN_HAIR.get());
        itemModels.declareCustomModelItem(ModItems.THESTRAL_TAIL_HAIR.get());

        itemModels.declareCustomModelItem(ModItems.PARCHMENT.get());
        itemModels.declareCustomModelItem(ModItems.INK_BOTTLE.get());

        itemModels.declareCustomModelItem(ModItems.GOBLIN_TELLER_SPAWN_EGG.get());
        itemModels.declareCustomModelItem(ModItems.NIFFLER_SPAWN_EGG.get());

        itemModels.declareCustomModelItem(ModItems.KNUT.get());
        itemModels.declareCustomModelItem(ModItems.SICKLE.get());
        itemModels.declareCustomModelItem(ModItems.GALLEON.get());
        itemModels.generateFlatItem(ModItems.LEPRECHAUN_GOLD.get(), net.minecraft.client.data.models.model.ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DRAGOT.get(), net.minecraft.client.data.models.model.ModelTemplates.FLAT_ITEM);

        for (WoodSet woodSet : ModBlocks.ALL_WOOD_SETS) {
            generateWoodSet(blockModels, woodSet);
        }

        generateWizardingWorld(blockModels, itemModels);
    }

    private void generateWizardingWorld(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        TextureMapping cobwebCross = TextureMapping.cross(Identifier.withDefaultNamespace("block/cobweb"));
        blockModels.createCrossBlockWithDefaultItem(ModBlocks.DEVILS_SNARE.get(),
                BlockModelGenerators.PlantType.NOT_TINTED, cobwebCross);

        blockModels.createCropBlock(ModBlocks.MANDRAKE_CROP.get(), BlockStateProperties.AGE_7,
                new int[] { 0, 1, 1, 2, 2, 3, 3, 3 });

        blockModels.createCrossBlockWithDefaultItem(ModBlocks.MALLOWSWEET.get(), BlockModelGenerators.PlantType.NOT_TINTED);

        blockModels.createTrivialBlock(ModBlocks.GRYFFINDOR_BANNER.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.SLYTHERIN_BANNER.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.RAVENCLAW_BANNER.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.HUFFLEPUFF_BANNER.get(), TexturedModel.LEAVES);

        TextureMapping torchCross = TextureMapping.cross(Identifier.withDefaultNamespace("block/torch"));
        blockModels.createCrossBlockWithDefaultItem(ModBlocks.FLOATING_CANDLE.get(),
                BlockModelGenerators.PlantType.NOT_TINTED, torchCross);

        blockModels.createTrivialBlock(ModBlocks.BRASS_CAULDRON.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.WIZARDING_COPPER_CAULDRON.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.PEWTER_CAULDRON.get(), TexturedModel.LEAVES);

        blockModels.createTrivialBlock(ModBlocks.FLOO_GRATE.get(), TexturedModel.LEAVES);

        java.util.List<net.minecraft.world.item.Item> wizardingItems = java.util.List.of(
                ModItems.BUTTERBEER.get(), ModItems.PUMPKIN_JUICE.get(), ModItems.CHOCOLATE_FROG.get(),
                ModItems.FAMOUS_WIZARD_CARD.get(), ModItems.BERTIE_BOTTS_EVERY_FLAVOUR_BEANS.get(),
                ModItems.DROOBLES_BEST_BLOWING_GUM.get(), ModItems.FIREWHISKY.get(), ModItems.GILLYWEED.get(),
                ModItems.DIRIGIBLE_PLUM.get(), ModItems.DITTANY.get(), ModItems.OCCAMY_EGGSHELL.get(),
                ModItems.BEZOAR.get(), ModItems.DEMIGUISE_HAIR.get(), ModItems.MOONCALF_DUNG.get(),
                ModItems.ERUMPENT_HORN.get(), ModItems.MANDRAKE.get(), ModItems.REMEMBRALL.get(),
                ModItems.OMNI_OCULARS.get(), ModItems.DELUMINATOR.get(), ModItems.SNEAKOSCOPE.get(),
                ModItems.PORTKEY.get(), ModItems.PERUVIAN_DARKNESS_POWDER.get(), ModItems.DECOY_DETONATOR.get(),
                ModItems.EXTENDABLE_EARS.get(), ModItems.FLOO_POWDER.get(),
                ModItems.BREW.get());
        for (net.minecraft.world.item.Item item : wizardingItems) {
            itemModels.generateFlatItem(item, ModelTemplates.FLAT_ITEM);
        }
    }

    private void generateWoodSet(BlockModelGenerators blockModels, WoodSet woodSet) {
        Block log = woodSet.log().get();
        Block strippedLog = woodSet.strippedLog().get();
        Block wood = woodSet.wood().get();
        Block strippedWood = woodSet.strippedWood().get();
        Block planks = woodSet.planks().get();
        Block slab = woodSet.slab().get();
        Block stairs = woodSet.stairs().get();
        Block leaves = woodSet.leaves().get();
        Block sapling = woodSet.sapling().get();

        blockModels.woodProvider(log).log(log).wood(wood);
        blockModels.woodProvider(strippedLog).log(strippedLog).wood(strippedWood);

        blockModels.family(planks).slab(slab).stairs(stairs);

        blockModels.createTrivialBlock(leaves, TexturedModel.LEAVES);

        blockModels.createCrossBlockWithDefaultItem(sapling, BlockModelGenerators.PlantType.NOT_TINTED);
    }
}
