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

        blockModels.createTrivialBlock(ModBlocks.GRYFFINDOR_BANNER.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.SLYTHERIN_BANNER.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.RAVENCLAW_BANNER.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.HUFFLEPUFF_BANNER.get(), TexturedModel.LEAVES);

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

        blockModels.createTrivialBlock(ModBlocks.BRASS_CAULDRON.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.WIZARDING_COPPER_CAULDRON.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.PEWTER_CAULDRON.get(), TexturedModel.LEAVES);

        blockModels.createTrivialBlock(ModBlocks.FLOO_GRATE.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.SPELL_TEACHER.get(), TexturedModel.LEAVES);

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

        // Placed trunks — placeholder cube models + item models until bespoke trunk art lands.
        blockModels.createTrivialBlock(ModBlocks.ENCHANTED_TRUNK.get(), TexturedModel.CUBE);
        blockModels.createTrivialBlock(ModBlocks.EXPANDED_TRUNK.get(), TexturedModel.CUBE);
        blockModels.createTrivialBlock(ModBlocks.MASTERS_TRUNK.get(), TexturedModel.CUBE);
        blockModels.createTrivialBlock(ModBlocks.MOODYS_TRUNK.get(), TexturedModel.CUBE);
        blockModels.createTrivialBlock(ModBlocks.NEWTS_CASE.get(), TexturedModel.CUBE);

        // Tents render entirely through GeoBlockRenderer (RenderShape.INVISIBLE), so these models are never
        // drawn — they exist so the blocks have a blockstate at all (no "Missing model for variant" spam)
        // and so break/step particles pick up the tent texture.
        blockModels.createTrivialBlock(ModBlocks.TENT_CANVAS.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.TENT_GRAND.get(), TexturedModel.LEAVES);

        blockModels.createTrivialBlock(ModBlocks.WANDMAKERS_BENCH.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.FLOO_FIREPLACE.get(), TexturedModel.LEAVES);
        blockModels.createTrivialBlock(ModBlocks.EXAMINATION_DESK.get(), TexturedModel.LEAVES);
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
