package at.koopro.wizardsandbeasts.datagen;

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

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModItems;
import at.koopro.wizardsandbeasts.registry.WoodSet;

public class ModModelProvider extends ModelProvider {

    public ModModelProvider(PackOutput output) {
        super(output, WizardsAndBeastsMod.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.declareCustomModelItem(ModItems.DEBUG_WAND.get());
        itemModels.declareCustomModelItem(ModItems.MORPH_WAND.get());
        itemModels.declareCustomModelItem(ModItems.WAND.get());
        itemModels.declareCustomModelItem(ModItems.BROOM_ITEM.get());
        itemModels.declareCustomModelItem(ModItems.CLEANSWEEP_SEVEN.get());
        itemModels.declareCustomModelItem(ModItems.COMET_260.get());
        itemModels.declareCustomModelItem(ModItems.NIMBUS_2000.get());
        itemModels.declareCustomModelItem(ModItems.NIMBUS_2001.get());
        itemModels.declareCustomModelItem(ModItems.FIREBOLT.get());
        itemModels.declareCustomModelItem(ModItems.FIREBOLT_SUPREME.get());
        itemModels.declareCustomModelItem(ModItems.OAKSHAFT_79.get());
        itemModels.declareCustomModelItem(ModItems.BROOM_POLISH.get());
        itemModels.declareCustomModelItem(ModItems.ENCHANTED_TWIG_BUNDLE.get());
        itemModels.declareCustomModelItem(ModItems.MARAUDERS_MAP.get());
        itemModels.declareCustomModelItem(ModItems.DELUMINATOR.get());

        itemModels.declareCustomModelItem(ModItems.PHOENIX_FEATHER.get());
        itemModels.declareCustomModelItem(ModItems.DRAGON_HEARTSTRING.get());
        itemModels.declareCustomModelItem(ModItems.UNICORN_HAIR.get());
        itemModels.declareCustomModelItem(ModItems.THESTRAL_TAIL_HAIR.get());
        itemModels.declareCustomModelItem(ModItems.VEELA_HAIR.get());
        itemModels.declareCustomModelItem(ModItems.TROLL_WHISKER.get());
        itemModels.declareCustomModelItem(ModItems.WAMPUS_CAT_HAIR.get());
        itemModels.declareCustomModelItem(ModItems.THUNDERBIRD_TAIL_FEATHER.get());
        itemModels.declareCustomModelItem(ModItems.ROUGAROU_HAIR.get());
        itemModels.declareCustomModelItem(ModItems.WHITE_RIVER_MONSTER_SPINE.get());

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
        blockModels.createCrossBlock(ModBlocks.DELUMINATOR_LIGHT.get(), BlockModelGenerators.PlantType.NOT_TINTED, torchCross);
        // Keep these in datagen so validation does not fail when new torch variants are present.
        // Runtime visuals still come from custom model json where provided.
        blockModels.createTrivialBlock(ModBlocks.UNLIT_TORCH.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.UNLIT_WALL_TORCH.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.UNLIT_SOUL_TORCH.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.UNLIT_SOUL_WALL_TORCH.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.UNLIT_COPPER_TORCH.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.UNLIT_COPPER_WALL_TORCH.get(), TexturedModel.LEAVES);

        blockModels.createTrivialBlock(ModBlocks.BRASS_CAULDRON.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.WIZARDING_COPPER_CAULDRON.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.PEWTER_CAULDRON.get(), TexturedModel.LEAVES);

        blockModels.createTrivialBlock(ModBlocks.FLOO_GRATE.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.SPELL_TEACHER.get(), TexturedModel.LEAVES);

        java.util.List<net.minecraft.world.item.Item> wizardingItems = java.util.List.of(
                ModItems.FAMOUS_WIZARD_CARD.get(), ModItems.DITTANY.get(), ModItems.OCCAMY_EGGSHELL.get(),
                ModItems.BEZOAR.get(), ModItems.DEMIGUISE_HAIR.get(), ModItems.MOONCALF_DUNG.get(),
                ModItems.ERUMPENT_HORN.get(), ModItems.MANDRAKE.get(), ModItems.REMEMBRALL.get(),
                ModItems.OMNI_OCULARS.get(), ModItems.SNEAKOSCOPE.get(),
                ModItems.PORTKEY.get(), ModItems.PERUVIAN_DARKNESS_POWDER.get(), ModItems.DECOY_DETONATOR.get(),
                ModItems.EXTENDABLE_EARS.get(), ModItems.FLOO_POWDER.get());
        for (net.minecraft.world.item.Item item : wizardingItems) {
            itemModels.generateFlatItem(item, ModelTemplates.FLAT_ITEM);
        }

        // Consumables use custom item models so they can point at vanilla textures while art is pending.
        itemModels.declareCustomModelItem(ModItems.BREW.get());
        itemModels.declareCustomModelItem(ModItems.BUTTERBEER.get());
        itemModels.declareCustomModelItem(ModItems.PUMPKIN_JUICE.get());
        itemModels.declareCustomModelItem(ModItems.CHOCOLATE_FROG.get());
        itemModels.declareCustomModelItem(ModItems.BERTIE_BOTTS_EVERY_FLAVOUR_BEANS.get());
        itemModels.declareCustomModelItem(ModItems.DROOBLES_BEST_BLOWING_GUM.get());
        itemModels.declareCustomModelItem(ModItems.FIREWHISKY.get());
        itemModels.declareCustomModelItem(ModItems.GILLYWEED.get());
        itemModels.declareCustomModelItem(ModItems.DIRIGIBLE_PLUM.get());
        itemModels.declareCustomModelItem(ModItems.TREACLE_TART.get());
        itemModels.declareCustomModelItem(ModItems.PUMPKIN_PASTY.get());
        itemModels.declareCustomModelItem(ModItems.FIZZING_WHIZZBEE.get());
        itemModels.declareCustomModelItem(ModItems.PEPPERMINT_TOAD.get());
        itemModels.declareCustomModelItem(ModItems.INVISIBILITY_CLOAK.get());
        itemModels.declareCustomModelItem(ModItems.DEATHLY_HALLOW_CLOAK.get());
        itemModels.declareCustomModelItem(ModItems.TIME_TURNER.get());

        blockModels.createTrivialBlock(ModBlocks.UNLIT_LANTERN.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.UNLIT_SOUL_LANTERN.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.UNLIT_COPPER_LANTERN.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.UNLIT_GLOWSTONE.get(), TexturedModel.LEAVES);
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
