package at.koopro.wizardsandbeasts.datagen;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplate;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.location.DiagonAlleyBlocks;
import at.koopro.wizardsandbeasts.block.location.GringottsBlocks;
import at.koopro.wizardsandbeasts.block.location.HogwartsBlocks;
import at.koopro.wizardsandbeasts.block.location.HogsmeadeBlocks;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper;
import at.koopro.wizardsandbeasts.block.location.MinistryBlocks;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WoodSet;
import at.koopro.wizardsandbeasts.registry.BroomItemRegistry;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.registry.LoreItemRegistry;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;

public class ModModelProvider extends ModelProvider {

    public ModModelProvider(PackOutput output) {
        super(output, WizardsAndBeastsMod.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.declareCustomModelItem(WandItemRegistry.DEBUG_WAND.get());
        itemModels.declareCustomModelItem(WandItemRegistry.MORPH_WAND.get());
        itemModels.declareCustomModelItem(WandItemRegistry.WAND.get());
        itemModels.declareCustomModelItem(BroomItemRegistry.BROOM_ITEM.get());
        itemModels.declareCustomModelItem(BroomItemRegistry.CLEANSWEEP_SEVEN.get());
        itemModels.declareCustomModelItem(BroomItemRegistry.COMET_260.get());
        itemModels.declareCustomModelItem(BroomItemRegistry.NIMBUS_2000.get());
        itemModels.declareCustomModelItem(BroomItemRegistry.NIMBUS_2001.get());
        itemModels.declareCustomModelItem(BroomItemRegistry.FIREBOLT.get());
        itemModels.declareCustomModelItem(BroomItemRegistry.FIREBOLT_SUPREME.get());
        itemModels.declareCustomModelItem(BroomItemRegistry.OAKSHAFT_79.get());
        itemModels.declareCustomModelItem(BroomItemRegistry.BROOM_POLISH.get());
        itemModels.declareCustomModelItem(BroomItemRegistry.ENCHANTED_TWIG_BUNDLE.get());
        itemModels.declareCustomModelItem(MiscItemRegistry.MARAUDERS_MAP.get());
        itemModels.declareCustomModelItem(MiscItemRegistry.DELUMINATOR.get());
        itemModels.generateFlatItem(MiscItemRegistry.BLINDFOLD.get(), ModelTemplates.FLAT_ITEM);
        // All trunks + Newt's Case are now blocks — their item models come from the block-model
        // generation in generateWizardingWorld().

        itemModels.declareCustomModelItem(WandItemRegistry.PHOENIX_FEATHER.get());
        itemModels.declareCustomModelItem(WandItemRegistry.DRAGON_HEARTSTRING.get());
        itemModels.declareCustomModelItem(WandItemRegistry.UNICORN_HAIR.get());
        itemModels.declareCustomModelItem(WandItemRegistry.THESTRAL_TAIL_HAIR.get());
        itemModels.declareCustomModelItem(WandItemRegistry.VEELA_HAIR.get());
        itemModels.declareCustomModelItem(WandItemRegistry.TROLL_WHISKER.get());
        itemModels.declareCustomModelItem(WandItemRegistry.WAMPUS_CAT_HAIR.get());
        itemModels.declareCustomModelItem(WandItemRegistry.THUNDERBIRD_TAIL_FEATHER.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.ROUGAROU_HAIR.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.WHITE_RIVER_MONSTER_SPINE.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.HIDEBEHIND_SHADOW_ESSENCE.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.HIDEBEHIND_CLAW.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.GHOUL_SLIME.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.GOLDEN_SNIDGET_FEATHER.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.GRANIAN_HAIR.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.HORNED_SERPENT_GEM.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.PUKWUDGIE_VENOM_SAC.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.YETI_FUR.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.MATAGOT_ESSENCE.get());

        itemModels.declareCustomModelItem(MiscItemRegistry.PARCHMENT.get());
        itemModels.declareCustomModelItem(MiscItemRegistry.INK_BOTTLE.get());

        // Spawn eggs: 1.21.10 removed minecraft:item/template_spawn_egg, so every egg is a plain
        // flat item with its own generated texture (textures/item/<id>_spawn_egg.png).
        itemModels.generateFlatItem(MiscItemRegistry.GOBLIN_TELLER_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(MiscItemRegistry.NIFFLER_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(MiscItemRegistry.BOWTRUCKLE_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(MiscItemRegistry.CORNISH_PIXIE_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(MiscItemRegistry.THESTRAL_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(MiscItemRegistry.PHOENIX_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(MiscItemRegistry.AUGUREY_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(MiscItemRegistry.MOONCALF_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(MiscItemRegistry.STREELER_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(MiscItemRegistry.RUNESPOOR_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(MiscItemRegistry.HIDEBEHIND_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        for (var egg : at.koopro.wizardsandbeasts.registry.ModCreatures.SPAWN_EGGS.values()) {
            itemModels.generateFlatItem(egg.get(), ModelTemplates.FLAT_ITEM);
        }
        itemModels.declareCustomModelItem(MiscItemRegistry.MINISTRY_HANDBOOK.get());

        itemModels.declareCustomModelItem(CurrencyItemRegistry.KNUT.get());
        itemModels.declareCustomModelItem(CurrencyItemRegistry.SICKLE.get());
        itemModels.declareCustomModelItem(CurrencyItemRegistry.GALLEON.get());
        itemModels.generateFlatItem(CurrencyItemRegistry.LEPRECHAUN_GOLD.get(), net.minecraft.client.data.models.model.ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(CurrencyItemRegistry.DRAGOT.get(), net.minecraft.client.data.models.model.ModelTemplates.FLAT_ITEM);

        itemModels.declareCustomModelItem(WandItemRegistry.WAND_BLANK.get());
        itemModels.declareCustomModelItem(MiscItemRegistry.BESTIARY.get());
        itemModels.generateFlatItem(CurrencyItemRegistry.COUNTERFEIT_GALLEON.get(), net.minecraft.client.data.models.model.ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ConsumableItemRegistry.CONJURED_SPOILED_FOOD.get(), net.minecraft.client.data.models.model.ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(TrinketItemRegistry.MINISTRY_LICENSE_SCROLL.get(), net.minecraft.client.data.models.model.ModelTemplates.FLAT_ITEM);
        itemModels.declareCustomModelItem(DarkArtefactItemRegistry.RESURRECTION_STONE.get());
        itemModels.declareCustomModelItem(DarkArtefactItemRegistry.RIDDLES_DIARY.get());
        itemModels.declareCustomModelItem(DarkArtefactItemRegistry.MARVOLO_GAUNTS_RING.get());
        itemModels.declareCustomModelItem(DarkArtefactItemRegistry.SLYTHERINS_LOCKET.get());
        itemModels.declareCustomModelItem(DarkArtefactItemRegistry.HUFFLEPUFFS_CUP.get());
        itemModels.declareCustomModelItem(DarkArtefactItemRegistry.RAVENCLAWS_DIADEM.get());
        itemModels.declareCustomModelItem(DarkArtefactItemRegistry.PHILOSOPHERS_STONE.get());
        itemModels.declareCustomModelItem(TrinketItemRegistry.PENSIEVE.get());
        itemModels.declareCustomModelItem(TrinketItemRegistry.TWO_WAY_MIRROR.get());
        itemModels.declareCustomModelItem(TrinketItemRegistry.HAND_OF_GLORY.get());
        itemModels.declareCustomModelItem(TrinketItemRegistry.DARK_MARK_BRAND.get());
        itemModels.declareCustomModelItem(TrinketItemRegistry.HERMIONES_BEADED_BAG.get());
        itemModels.declareCustomModelItem(TrinketItemRegistry.FOE_GLASS.get());
        itemModels.declareCustomModelItem(TrinketItemRegistry.BLOOD_PACT_VIAL.get());

        // Lore tomes — hand-authored item models exist; declare so datagen validation passes.
        itemModels.declareCustomModelItem(LoreItemRegistry.A_HISTORY_OF_MAGIC.get());
        itemModels.declareCustomModelItem(LoreItemRegistry.HOGWARTS_A_HISTORY.get());
        itemModels.declareCustomModelItem(LoreItemRegistry.RISE_AND_FALL_OF_THE_DARK_ARTS.get());

        for (WoodSet woodSet : ModBlocks.ALL_WOOD_SETS) {
            generateWoodSet(blockModels, woodSet);
        }

        generateWizardingWorld(blockModels, itemModels);
        generateLocationBlocks(blockModels);
    }

    private void generateWizardingWorld(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        TextureMapping cobwebCross = TextureMapping.cross(Identifier.withDefaultNamespace("block/cobweb"));
        blockModels.createCrossBlockWithDefaultItem(ModBlocks.DEVILS_SNARE.get(),
                BlockModelGenerators.PlantType.NOT_TINTED, cobwebCross);

        blockModels.createCropBlock(ModBlocks.MANDRAKE_CROP.get(), BlockStateProperties.AGE_7,
                new int[] { 0, 1, 1, 2, 2, 3, 3, 3 });

        blockModels.createCrossBlockWithDefaultItem(ModBlocks.MALLOWSWEET.get(), BlockModelGenerators.PlantType.NOT_TINTED);

        // House banners hang as a crossed cloth pennant (readable from every side with no facing
        // property), not the solid leaves cube they used to render as — that fat cube also clashed
        // with the block's slim collision box. Default texture resolves to block/<house>_banner.
        blockModels.createCrossBlockWithDefaultItem(ModBlocks.GRYFFINDOR_BANNER.get(), BlockModelGenerators.PlantType.NOT_TINTED);
        blockModels.createCrossBlockWithDefaultItem(ModBlocks.SLYTHERIN_BANNER.get(), BlockModelGenerators.PlantType.NOT_TINTED);
        blockModels.createCrossBlockWithDefaultItem(ModBlocks.RAVENCLAW_BANNER.get(), BlockModelGenerators.PlantType.NOT_TINTED);
        blockModels.createCrossBlockWithDefaultItem(ModBlocks.HUFFLEPUFF_BANNER.get(), BlockModelGenerators.PlantType.NOT_TINTED);

        TextureMapping torchCross = TextureMapping.cross(Identifier.withDefaultNamespace("block/torch"));
        // The placed candle used to draw the vanilla torch cross, so the block in the world
        // was a plain torch and floating_candle.png only ever showed up on the item.
        blockModels.createCrossBlockWithDefaultItem(ModBlocks.FLOATING_CANDLE.get(),
                BlockModelGenerators.PlantType.NOT_TINTED,
                TextureMapping.cross(Identifier.fromNamespaceAndPath(
                        WizardsAndBeastsMod.MODID, "block/floating_candle")));
        blockModels.createCrossBlock(ModBlocks.DELUMINATOR_LIGHT.get(), BlockModelGenerators.PlantType.NOT_TINTED, torchCross);
        // These were emitting TexturedModel.LEAVES — a full cube — so every unlit torch
        // rendered as a solid block with the torch sprite tiled over all six faces.
        // createNormalTorch emits the real torch/wall-torch models (and the flat item
        // model) from the same texture, exactly as vanilla does for its own torches.
        blockModels.createNormalTorch(ModBlocks.UNLIT_TORCH.get(), ModBlocks.UNLIT_WALL_TORCH.get());
        blockModels.createNormalTorch(ModBlocks.UNLIT_SOUL_TORCH.get(), ModBlocks.UNLIT_SOUL_WALL_TORCH.get());
        blockModels.createNormalTorch(ModBlocks.UNLIT_COPPER_TORCH.get(), ModBlocks.UNLIT_COPPER_WALL_TORCH.get());

        // A cube-shaped cauldron gave no sign there was anything in it, which is the entire
        // point of a cauldron. These get a real footed vessel with an open rim, so the brew
        // surface is visible down inside it.
        ExtendedModelTemplate cauldron = cauldronModel();
        for (Block pot : java.util.List.of(ModBlocks.BRASS_CAULDRON.get(),
                ModBlocks.WIZARDING_COPPER_CAULDRON.get(), ModBlocks.PEWTER_CAULDRON.get())) {
            propBlock(blockModels, pot, cauldron, sideTop(pot, "_side"), false);
        }

        blockModels.createTrivialBlock(ModBlocks.FLOO_GRATE.get(), TexturedModel.LEAVES);
        propBlock(blockModels, ModBlocks.SPELL_TEACHER.get(), lecternModel(),
                sideTop(ModBlocks.SPELL_TEACHER.get(), ""), false);

        java.util.List<net.minecraft.world.item.Item> wizardingItems = java.util.List.of(
                ConsumableItemRegistry.OCCAMY_EGGSHELL.get(),
                ConsumableItemRegistry.BEZOAR.get(), ConsumableItemRegistry.DEMIGUISE_HAIR.get(), ConsumableItemRegistry.MOONCALF_DUNG.get(),
                ConsumableItemRegistry.ERUMPENT_HORN.get(), ConsumableItemRegistry.MANDRAKE.get(), TrinketItemRegistry.REMEMBRALL.get(),
                TrinketItemRegistry.OMNI_OCULARS.get(), TrinketItemRegistry.SNEAKOSCOPE.get(),
                TrinketItemRegistry.PORTKEY.get(), TrinketItemRegistry.PERUVIAN_DARKNESS_POWDER.get(), TrinketItemRegistry.DECOY_DETONATOR.get(),
                TrinketItemRegistry.EXTENDABLE_EARS.get(), MiscItemRegistry.FLOO_POWDER.get());
        for (net.minecraft.world.item.Item item : wizardingItems) {
            itemModels.generateFlatItem(item, ModelTemplates.FLAT_ITEM);
        }

        // Dittany and the wizard card moved off generateFlatItem: both now ship a hand-written
        // cuboid model from tools/item_models_3d.py, and leaving them in the flat list would emit
        // a second, competing models/item/<id>.json into the generated pack.
        itemModels.declareCustomModelItem(ConsumableItemRegistry.DITTANY.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.FAMOUS_WIZARD_CARD.get());

        // Consumables use custom item models so they can point at vanilla textures while art is pending.
        itemModels.declareCustomModelItem(ConsumableItemRegistry.BREW.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.BUTTERBEER.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.PUMPKIN_JUICE.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.CHOCOLATE_FROG.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.BERTIE_BOTTS_EVERY_FLAVOUR_BEANS.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.DROOBLES_BEST_BLOWING_GUM.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.FIREWHISKY.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.GILLYWEED.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.DIRIGIBLE_PLUM.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.TREACLE_TART.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.PUMPKIN_PASTY.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.FIZZING_WHIZZBEE.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.PEPPERMINT_TOAD.get());
        itemModels.declareCustomModelItem(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get());
        itemModels.declareCustomModelItem(DarkArtefactItemRegistry.DEATHLY_HALLOW_CLOAK.get());
        itemModels.declareCustomModelItem(TrinketItemRegistry.TIME_TURNER.get());

        // Same cube problem as the torches. All three are real LanternBlocks, so they
        // carry the HANGING property to dispatch on, and get both the standing and
        // hanging variants built from the 16x48 lantern texture layout.
        createLanternWithoutItem(blockModels, ModBlocks.UNLIT_LANTERN.get());
        createLanternWithoutItem(blockModels, ModBlocks.UNLIT_SOUL_LANTERN.get());
        createLanternWithoutItem(blockModels, ModBlocks.UNLIT_COPPER_LANTERN.get());
        blockModels.createTrivialBlock(ModBlocks.UNLIT_GLOWSTONE.get(), TexturedModel.LEAVES);

        blockModels.createTrivialBlock(ModBlocks.WARDING_STONE.get(), TexturedModel.CUBE);
        blockModels.createTrivialBlock(ModBlocks.POCKET_CONFIGURATOR.get(), TexturedModel.CUBE);

        // Placed trunks. TrunkBlock has carried a FACING property all along, but with one
        // sprite on all six faces there was nothing to orient — the lid and the lock now
        // give it meaning.
        ExtendedModelTemplate trunk = trunkModel();
        for (Block chest : java.util.List.of(ModBlocks.ENCHANTED_TRUNK.get(), ModBlocks.EXPANDED_TRUNK.get(),
                ModBlocks.MASTERS_TRUNK.get(), ModBlocks.MOODYS_TRUNK.get(), ModBlocks.NEWTS_CASE.get())) {
            propBlock(blockModels, chest, trunk, sideFrontTop(chest), true);
        }

        // Tents render entirely through GeoBlockRenderer (RenderShape.INVISIBLE), so these models are never
        // drawn — they exist so the blocks have a blockstate at all (no "Missing model for variant" spam)
        // and so break/step particles pick up the tent texture.
        blockModels.createTrivialBlock(ModBlocks.TENT_CANVAS.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.TENT_GRAND.get(), TexturedModel.LEAVES);

        blockModels.createTrivialBlock(ModBlocks.WANDMAKERS_BENCH.get(), TexturedModel.LEAVES);
        createFlooFireplace(blockModels, ModBlocks.FLOO_FIREPLACE.get());
        propBlock(blockModels, ModBlocks.EXAMINATION_DESK.get(), deskModel(),
                sideTop(ModBlocks.EXAMINATION_DESK.get(), ""), false);
    }

    // --- Location decorative block datagen ---

    private void generateLocationBlocks(BlockModelGenerators blockModels) {
        // Ministry of Magic
        genVariants(blockModels, MinistryBlocks.MINISTRY_BLACK_MARBLE);
        genPillar(blockModels, MinistryBlocks.MINISTRY_BLACK_MARBLE_PILLAR);
        genStairs(blockModels, MinistryBlocks.MINISTRY_BLACK_MARBLE_TILES);
        genVariants(blockModels, MinistryBlocks.MINISTRY_GILDED_BLACK_MARBLE);
        genBlock(blockModels, MinistryBlocks.MINISTRY_GILDED_TRIM);
        genStairs(blockModels, MinistryBlocks.MINISTRY_DARK_TILE);
        genSlab(blockModels, MinistryBlocks.MINISTRY_FLOOR_TILE);
        genBlock(blockModels, MinistryBlocks.MINISTRY_WALL_PANEL);

        // Hogwarts Castle
        genVariants(blockModels, HogwartsBlocks.HOGWARTS_STONE);
        genVariants(blockModels, HogwartsBlocks.HOGWARTS_STONE_BRICKS);
        genBlock(blockModels, HogwartsBlocks.HOGWARTS_CRACKED_STONE_BRICKS);
        genVariants(blockModels, HogwartsBlocks.HOGWARTS_MOSSY_STONE_BRICKS);
        genPillar(blockModels, HogwartsBlocks.HOGWARTS_STONE_PILLAR);
        genVariants(blockModels, HogwartsBlocks.HOGWARTS_DARK_STONE);
        genSlab(blockModels, HogwartsBlocks.HOGWARTS_FLAGSTONE);
        genSlab(blockModels, HogwartsBlocks.HOGWARTS_FLOOR_TILE);
        genBlock(blockModels, HogwartsBlocks.ENCHANTED_CEILING_TILE);

        // Diagon Alley
        genVariants(blockModels, DiagonAlleyBlocks.DIAGON_BRICK);
        genStairs(blockModels, DiagonAlleyBlocks.DIAGON_BRICK_TILES);
        genVariants(blockModels, DiagonAlleyBlocks.DIAGON_WORN_BRICK);
        genVariants(blockModels, DiagonAlleyBlocks.DIAGON_COBBLESTONE);
        genStairs(blockModels, DiagonAlleyBlocks.DIAGON_SHOPFRONT_WOOD);
        genStairs(blockModels, DiagonAlleyBlocks.DIAGON_SHOPFRONT_PLANKS);
        genStairs(blockModels, DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_GREEN);
        genStairs(blockModels, DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_PURPLE);
        // Diagon Street Stone: base + slab + pressure plate
        blockModels.family(DiagonAlleyBlocks.DIAGON_STREET_STONE.get())
                .slab(DiagonAlleyBlocks.DIAGON_STREET_STONE_SLAB.get())
                .pressurePlate(DiagonAlleyBlocks.DIAGON_STREET_STONE_PRESSURE_PLATE.get());

        // Hogsmeade
        genVariants(blockModels, HogsmeadeBlocks.HOGSMEADE_STONE);
        genVariants(blockModels, HogsmeadeBlocks.HOGSMEADE_STONE_BRICKS);
        genVariants(blockModels, HogsmeadeBlocks.HOGSMEADE_WORN_STONE);
        genStairs(blockModels, HogsmeadeBlocks.THREE_BROOMSTICKS_TIMBER);
        genStairs(blockModels, HogsmeadeBlocks.THREE_BROOMSTICKS_PLANKS);
        genStairs(blockModels, HogsmeadeBlocks.HONEYDUKES_PASTEL_PINK);
        genStairs(blockModels, HogsmeadeBlocks.HONEYDUKES_PASTEL_YELLOW);
        genStairs(blockModels, HogsmeadeBlocks.HOGSMEADE_ROOF_TILE);
        genVariants(blockModels, HogsmeadeBlocks.HOGSMEADE_CHIMNEY_BRICK);

        // Gringotts Bank
        genVariants(blockModels, GringottsBlocks.GRINGOTTS_WHITE_MARBLE);
        genPillar(blockModels, GringottsBlocks.GRINGOTTS_WHITE_MARBLE_PILLAR);
        genStairs(blockModels, GringottsBlocks.GRINGOTTS_WHITE_MARBLE_TILES);
        genVariants(blockModels, GringottsBlocks.GRINGOTTS_PALE_MARBLE);
        genBlock(blockModels, GringottsBlocks.GRINGOTTS_GOLD_TRIM);
        genVariants(blockModels, GringottsBlocks.GRINGOTTS_IRON_VAULT_STONE);
        genVariants(blockModels, GringottsBlocks.GRINGOTTS_VAULT_BRICKS);
        genVariants(blockModels, GringottsBlocks.GRINGOTTS_GOBLIN_STONEWORK);
        genSlab(blockModels, GringottsBlocks.GRINGOTTS_COUNTING_FLOOR);
    }

    private void genVariants(BlockModelGenerators b, LocationBlockHelper.VariantSet s) {
        b.family(s.base().get()).slab(s.slab().get()).stairs(s.stairs().get()).wall(s.wall().get());
    }

    private void genStairs(BlockModelGenerators b, LocationBlockHelper.StairSet s) {
        b.family(s.base().get()).slab(s.slab().get()).stairs(s.stairs().get());
    }

    private void genSlab(BlockModelGenerators b, LocationBlockHelper.SlabSet s) {
        b.family(s.base().get()).slab(s.slab().get());
    }

    private void genBlock(BlockModelGenerators b, LocationBlockHelper.BlockEntry e) {
        b.createTrivialBlock(e.block().get(), TexturedModel.CUBE);
    }

    // Uses woodProvider so the RotatedPillarBlock gets correct axis-rotation blockstate
    // with separate top (end) and side textures — same mechanism as logs.
    private void genPillar(BlockModelGenerators b, LocationBlockHelper.PillarEntry e) {
        b.woodProvider(e.block().get()).log(e.block().get());
    }

    // --- Wood set datagen ---

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

    // --- Prop geometry -------------------------------------------------------------
    //
    // These blocks are furniture, but every one of them used to render as a plain 16-cubed
    // cube with a single sprite on all six faces — a trunk's flank was stretched over its
    // lid, and the fireplace's hearth opening appeared on the back. The geometry is built
    // here rather than hand-written as JSON on purpose: a hand-authored model in
    // src/main/resources silently *wins* over the generated one (build.gradle:136 adds
    // src/generated as a second resource root and :139 excludes duplicates, main first),
    // while ModelProvider still demands a generated blockstate for every block. Keeping
    // both sides in datagen leaves exactly one source of truth.
    //
    // Faces with no explicit uv() inherit vanilla's automatic, world-aligned UVs, so each
    // element samples the part of the texture that sits at its own height.

    private static ExtendedModelTemplateBuilder prop(TextureSlot... slots) {
        ExtendedModelTemplateBuilder builder = ExtendedModelTemplateBuilder.builder()
                .parent(Identifier.withDefaultNamespace("block/block"));
        for (TextureSlot slot : slots) {
            builder.requiredTextureSlot(slot);
        }
        return builder;
    }

    /** Body plus a slightly inset lid, with the lock plate on the front face. */
    private static ExtendedModelTemplate trunkModel() {
        return prop(TextureSlot.PARTICLE, TextureSlot.SIDE, TextureSlot.FRONT, TextureSlot.TOP)
                .element(e -> e.from(1, 0, 1).to(15, 9, 15)
                        .textureAll(TextureSlot.SIDE)
                        .face(Direction.NORTH, f -> f.texture(TextureSlot.FRONT))
                        .face(Direction.DOWN, f -> f.texture(TextureSlot.SIDE).cullface(Direction.DOWN)))
                .element(e -> e.from(1, 9, 1).to(15, 14, 15)
                        .textureAll(TextureSlot.SIDE)
                        .face(Direction.NORTH, f -> f.texture(TextureSlot.FRONT))
                        .face(Direction.UP, f -> f.texture(TextureSlot.TOP)))
                .build();
    }

    /** Footed vessel with a raised rim, left open so the brew surface reads from above. */
    private static ExtendedModelTemplate cauldronModel() {
        ExtendedModelTemplateBuilder builder =
                prop(TextureSlot.PARTICLE, TextureSlot.SIDE, TextureSlot.TOP)
                        .element(e -> e.from(5, 0, 5).to(11, 3, 11)
                                .textureAll(TextureSlot.SIDE)
                                .face(Direction.DOWN, f -> f.texture(TextureSlot.SIDE).cullface(Direction.DOWN)))
                        .element(e -> e.from(2, 3, 2).to(14, 12, 14)
                                .textureAll(TextureSlot.SIDE)
                                .face(Direction.UP, f -> f.texture(TextureSlot.TOP)));
        // Rim as four walls rather than a slab: a slab would cap the pot and hide the brew.
        float[][] rim = {
                { 1, 1, 15, 2 },   // north
                { 1, 14, 15, 15 }, // south
                { 1, 2, 2, 14 },   // west
                { 14, 2, 15, 14 }, // east
        };
        for (float[] r : rim) {
            builder.element(e -> e.from(r[0], 12, r[1]).to(r[2], 14, r[3])
                    .textureAll(TextureSlot.SIDE));
        }
        return builder.build();
    }

    /** Table: a top slab on four corner legs. */
    private static ExtendedModelTemplate deskModel() {
        ExtendedModelTemplateBuilder builder =
                prop(TextureSlot.PARTICLE, TextureSlot.SIDE, TextureSlot.TOP)
                        .element(e -> e.from(0, 12, 0).to(16, 15, 16)
                                .textureAll(TextureSlot.SIDE)
                                .face(Direction.UP, f -> f.texture(TextureSlot.TOP)));
        float[][] legs = { { 1, 1 }, { 12, 1 }, { 1, 12 }, { 12, 12 } };
        for (float[] leg : legs) {
            builder.element(e -> e.from(leg[0], 0, leg[1]).to(leg[0] + 3, 12, leg[1] + 3)
                    .textureAll(TextureSlot.SIDE)
                    .face(Direction.DOWN, f -> f.texture(TextureSlot.SIDE).cullface(Direction.DOWN)));
        }
        return builder.build();
    }

    /** Lectern: footed column under a reading surface tilted toward the reader. */
    private static ExtendedModelTemplate lecternModel() {
        return prop(TextureSlot.PARTICLE, TextureSlot.SIDE, TextureSlot.TOP)
                .element(e -> e.from(3, 0, 3).to(13, 2, 13)
                        .textureAll(TextureSlot.SIDE)
                        .face(Direction.DOWN, f -> f.texture(TextureSlot.SIDE).cullface(Direction.DOWN)))
                .element(e -> e.from(5, 2, 5).to(11, 10, 11)
                        .textureAll(TextureSlot.SIDE))
                .element(e -> e.from(2, 10, 2).to(14, 13, 14)
                        .rotation(r -> r.singleAxis(Direction.Axis.X, 22.5F).origin(8, 11, 8))
                        .textureAll(TextureSlot.SIDE)
                        .face(Direction.UP, f -> f.texture(TextureSlot.TOP)))
                .build();
    }

    /**
     * Hearth you can see into: back wall, two jambs, a lintel and a raised floor, leaving a
     * recess open to the north. The model's front is north because that is the face
     * {@link BlockModelGenerators#ROTATION_HORIZONTAL_FACING} treats as unrotated.
     */
    private static ExtendedModelTemplate fireplaceModel() {
        return prop(TextureSlot.PARTICLE, TextureSlot.SIDE, TextureSlot.FRONT, TextureSlot.TOP)
                .element(e -> e.from(0, 0, 13).to(16, 16, 16)
                        .textureAll(TextureSlot.SIDE)
                        // The inner face of the back wall is the fire you look at.
                        .face(Direction.NORTH, f -> f.texture(TextureSlot.FRONT))
                        .face(Direction.SOUTH, f -> f.texture(TextureSlot.SIDE).cullface(Direction.SOUTH)))
                .element(e -> e.from(0, 0, 0).to(3, 16, 13)
                        .textureAll(TextureSlot.SIDE)
                        .face(Direction.WEST, f -> f.texture(TextureSlot.SIDE).cullface(Direction.WEST)))
                .element(e -> e.from(13, 0, 0).to(16, 16, 13)
                        .textureAll(TextureSlot.SIDE)
                        .face(Direction.EAST, f -> f.texture(TextureSlot.SIDE).cullface(Direction.EAST)))
                .element(e -> e.from(3, 13, 0).to(13, 16, 13)
                        .textureAll(TextureSlot.SIDE)
                        .face(Direction.UP, f -> f.texture(TextureSlot.TOP).cullface(Direction.UP)))
                .element(e -> e.from(3, 0, 0).to(13, 2, 13)
                        .textureAll(TextureSlot.SIDE)
                        .face(Direction.DOWN, f -> f.texture(TextureSlot.SIDE).cullface(Direction.DOWN)))
                .build();
    }

    /**
     * Emits a custom-geometry model, its blockstate (rotated to follow HORIZONTAL_FACING
     * when the block has that property) and the matching item model.
     *
     * <p>Unlike {@code createTrivialBlock}, neither {@code createHorizontallyRotatedBlock}
     * nor a raw {@link MultiVariantGenerator} registers an item model, and every block here
     * has a {@code BlockItem} — without the explicit call datagen fails with
     * "Missing item model definitions for: [...]".
     */
    private void propBlock(BlockModelGenerators blockModels, Block block,
                           ExtendedModelTemplate template, TextureMapping mapping, boolean rotated) {
        Identifier model = template.create(block, mapping, blockModels.modelOutput);
        MultiVariantGenerator generator =
                MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model));
        blockModels.blockStateOutput.accept(
                rotated ? generator.with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING) : generator);
        blockModels.registerSimpleItemModel(block, model);
    }

    /**
     * The hearth, dispatched on LIT and HORIZONTAL_FACING.
     *
     * <p>Deliberately not vanilla's {@code createFurnace}, which is otherwise the same
     * shape: that helper hardcodes a {@code <block>_front_on} texture name, while the lit
     * hearth already ships as {@code floo_fireplace_lit.png} — an eight-frame animated
     * strip owned by {@code tools/animate_textures.py}. Pointing at it directly keeps the
     * animation rather than renaming an asset another generator regenerates.
     */
    private void createFlooFireplace(BlockModelGenerators blockModels, Block block) {
        ExtendedModelTemplate template = fireplaceModel();
        Identifier surround = TextureMapping.getBlockTexture(block, "_side");
        TextureMapping unlit = new TextureMapping()
                .put(TextureSlot.PARTICLE, surround)
                .put(TextureSlot.SIDE, surround)
                .put(TextureSlot.FRONT, TextureMapping.getBlockTexture(block))
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"));

        Identifier off = template.create(block, unlit, blockModels.modelOutput);
        Identifier on = template.createWithSuffix(block, "_lit",
                unlit.copyAndUpdate(TextureSlot.FRONT, TextureMapping.getBlockTexture(block, "_lit")),
                blockModels.modelOutput);

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block)
                        .with(BlockModelGenerators.createBooleanModelDispatch(
                                BlockStateProperties.LIT,
                                BlockModelGenerators.plainVariant(on),
                                BlockModelGenerators.plainVariant(off)))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(block, off);
    }

    /** side/front/top mapping off {@code <block>}, {@code <block>_front} and {@code <block>_top}. */
    private static TextureMapping sideFrontTop(Block block) {
        return new TextureMapping()
                .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(block))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block))
                .put(TextureSlot.FRONT, TextureMapping.getBlockTexture(block, "_front"))
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"));
    }

    /**
     * side/top mapping off {@code <block><sideSuffix>} and {@code <block>_top}. The suffix is
     * a parameter because the cauldrons already ship their flank as {@code _side}, while the
     * desk and lectern keep theirs under the bare block name.
     */
    private static TextureMapping sideTop(Block block, String sideSuffix) {
        Identifier side = sideSuffix.isEmpty()
                ? TextureMapping.getBlockTexture(block)
                : TextureMapping.getBlockTexture(block, sideSuffix);
        return new TextureMapping()
                .put(TextureSlot.PARTICLE, side)
                .put(TextureSlot.SIDE, side)
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"));
    }

    /**
     * {@code BlockModelGenerators.createLantern} minus its flat-item-model step.
     *
     * <p>The unlit lanterns are placed by the Deluminator and never held, so they are registered as
     * blocks with no {@code BlockItem}. Vanilla's helper calls {@code registerSimpleFlatItemModel} on
     * {@code block.asItem()}, which for an item-less block resolves to {@code minecraft:air} — the
     * first lantern silently emits a model for {@code item/air} and the second one dies with
     * "Duplicate model definition". Everything else it does is reproduced here verbatim.
     */
    private static void createLanternWithoutItem(BlockModelGenerators blockModels, Block lantern) {
        var standing = BlockModelGenerators.plainVariant(
                TexturedModel.LANTERN.create(lantern, blockModels.modelOutput));
        var hanging = BlockModelGenerators.plainVariant(
                TexturedModel.HANGING_LANTERN.create(lantern, blockModels.modelOutput));
        blockModels.blockStateOutput.accept(
                net.minecraft.client.data.models.blockstates.MultiVariantGenerator.dispatch(lantern)
                        .with(BlockModelGenerators.createBooleanModelDispatch(
                                BlockStateProperties.HANGING, hanging, standing)));
    }
}
