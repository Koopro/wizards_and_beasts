package at.koopro.wizardsandbeasts.datagen;

import java.util.Set;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
import at.koopro.wizardsandbeasts.registry.ArmorItemRegistry;
import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import at.koopro.wizardsandbeasts.registry.BroomItemRegistry;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.registry.LoreItemRegistry;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;

public class ModModelProvider extends ModelProvider {

    /**
     * Canon stubs that carry a hand-authored cuboid model in {@code src/main/resources}
     * rather than a flat sprite.
     *
     * <p>The models and their box-UV textures are built by {@code tools/item_models_3d.py};
     * this is the half of that contract datagen owns. It has to be stated rather than
     * discovered because a data provider has no view of the resource tree — and the failure
     * mode of getting it wrong is silent: {@code src/generated/resources} is a second
     * resource root, so an item that is both declared here and flat-generated ends up with
     * two model files and the game loads whichever {@code processResources} copied first.
     * The tool re-derives this list on every run and refuses to finish if the two disagree.
     */
    private static final Set<String> HAND_MODELLED_CANON = Set.of(
            "acromantula_venom", "advanced_potion_making", "auto_answer_quill", "beaters_bat",
            "beginners_guide_to_transfiguration", "blood_replenishing_potion", "bludger",
            "brass_scales", "broomstick_servicing_kit", "canary_cream", "chocolate_bar",
            "collapsible_cauldron", "da_galleon", "daily_prophet", "dragon_hide_gloves",
            "draught_of_peace", "dungbomb", "elixir_of_life", "essence_of_dittany", "exploding_snap",
            "fainting_fancies", "fantastic_beasts_and_where_to_find_them", "fever_fudge",
            "filibusters_fireworks", "flesh_eating_slug_repellent", "goblet_of_fire", "gobstones",
            "golden_egg", "golden_snitch", "gubraithian_fire", "headless_hat", "howler",
            "magical_draughts_and_potions", "memory_vial", "mirror_of_erised",
            "monster_book_of_monsters", "moste_potente_potions", "mrs_skowers_mess_remover",
            "murtlap_essence", "nosebleed_nougat", "one_thousand_magical_herbs_and_fungi",
            "opal_necklace", "portable_swamp", "probity_probe", "puking_pastilles",
            "punching_telescope", "quaffle", "quick_quotes_quill", "quidditch_robes",
            "quidditch_through_the_ages", "quill", "revealer", "secrecy_sensor",
            "secrets_of_the_darkest_art", "self_stirring_cauldron", "shield_cloak", "shield_gloves",
            "shield_hat", "shrunken_head", "skiving_snackbox", "sorting_hat", "spellotape",
            "standard_book_of_spells", "sword_of_gryffindor", "tales_of_beedle_the_bard", "telescope",
            "the_quibbler", "ton_tongue_toffee", "triwizard_cup", "u_no_poo", "unfogging_the_future",
            "vanishing_cabinet", "wildfire_whiz_bangs", "wizarding_wireless", "wizards_chess_set");

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
        itemModels.generateFlatItem(MiscItemRegistry.DUELLING_DUMMY.get(), ModelTemplates.FLAT_ITEM);

        // Worn armour — robes, hats and masks. Flat inventory sprites only; the worn layer is
        // an equipment asset, not an item model, and is not authored yet.
        // Worn armour — flat inventory sprites. Looped over the registry's own roster rather
        // than listed, so adding a piece cannot fail datagen by being forgotten here. The worn
        // layer is an equipment asset, not an item model, and is unrelated to these.
        for (var piece : ArmorItemRegistry.ALL) {
            itemModels.generateFlatItem(piece.get(), ModelTemplates.FLAT_ITEM);
        }

        // Catalogued canon stubs. Looped rather than listed so adding one to
        // CanonItemRegistry cannot fail datagen by being forgotten here; the ones that have
        // grown a real cuboid model are declared instead of flat-generated, because a stub
        // emitted here would be a second, competing model file for the same item.
        for (var stub : CanonItemRegistry.ALL) {
            if (HAND_MODELLED_CANON.contains(stub.getId().getPath())) {
                itemModels.declareCustomModelItem(stub.get());
            } else {
                itemModels.generateFlatItem(stub.get(), ModelTemplates.FLAT_ITEM);
            }
        }
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

        createHouseBanner(blockModels, ModBlocks.GRYFFINDOR_BANNER.get());
        createHouseBanner(blockModels, ModBlocks.SLYTHERIN_BANNER.get());
        createHouseBanner(blockModels, ModBlocks.RAVENCLAW_BANNER.get());
        createHouseBanner(blockModels, ModBlocks.HUFFLEPUFF_BANNER.get());

        TextureMapping torchCross = TextureMapping.cross(Identifier.withDefaultNamespace("block/torch"));
        // The placed candle used to draw the vanilla torch cross, so the block in the world
        // was a plain torch and floating_candle.png only ever showed up on the item. Pointing
        // the cross at our own sprite fixed the wrong texture but not the wrong shape: a
        // cross is two flat billboards, which is right for a sapling and wrong for a candle
        // — in world it read as a paper cut-out, and the item was that same sheet extruded
        // into a wafer. It is a real candle now, wax and flame.
        propBlock(blockModels, ModBlocks.FLOATING_CANDLE.get(), floatingCandleModel(),
                floatingCandleTexture(), false);
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

        eggshellBlock(blockModels);
        blockModels.createTrivialBlock(ModBlocks.FLOO_GRATE.get(), TexturedModel.LEAVES);
        propBlock(blockModels, ModBlocks.SPELL_TEACHER.get(), lecternModel(),
                sideTop(ModBlocks.SPELL_TEACHER.get(), ""), false);

        java.util.List<net.minecraft.world.item.Item> wizardingItems = java.util.List.of(
                ConsumableItemRegistry.BEZOAR.get(), ConsumableItemRegistry.MOONCALF_DUNG.get(),
                ConsumableItemRegistry.ERUMPENT_HORN.get(), ConsumableItemRegistry.MANDRAKE.get(),
                ConsumableItemRegistry.BABY_MANDRAKE.get(), MiscItemRegistry.EARMUFFS.get(),
                TrinketItemRegistry.PORTKEY.get(), TrinketItemRegistry.PERUVIAN_DARKNESS_POWDER.get(), TrinketItemRegistry.DECOY_DETONATOR.get(),
                TrinketItemRegistry.EXTENDABLE_EARS.get(), MiscItemRegistry.FLOO_POWDER.get());
        for (net.minecraft.world.item.Item item : wizardingItems) {
            itemModels.generateFlatItem(item, ModelTemplates.FLAT_ITEM);
        }

        // Dittany and the wizard card moved off generateFlatItem: both now ship a hand-written
        // cuboid model from tools/item_models_3d.py, and leaving them in the flat list would emit
        // a second, competing models/item/<id>.json into the generated pack.
        // The Sneakoscope ships a hand-written items/ definition: a minecraft:range_dispatch over
        // eight pre-rotated spin frames, driven by SneakoscopeSpinProperty. generateFlatItem would
        // emit a competing single-model items/sneakoscope.json over the top of it.
        itemModels.declareCustomModelItem(TrinketItemRegistry.SNEAKOSCOPE.get());

        // The hair ships a hand-written items/ definition: a range_dispatch over
        // minecraft:use_duration through the fade frames. generateFlatItem would emit a
        // competing single-model items/demiguise_hair.json over the top of it.
        itemModels.declareCustomModelItem(ConsumableItemRegistry.DEMIGUISE_HAIR.get());

        itemModels.declareCustomModelItem(ConsumableItemRegistry.DITTANY.get());
        itemModels.declareCustomModelItem(ConsumableItemRegistry.FAMOUS_WIZARD_CARD.get());

        // Same reason, for the two that grew a cuboid model in tools/item_models_3d.py: the
        // Remembrall and the Omnioculars are objects you look *into*, which a flat sprite
        // cannot show. Both were already emitting a model into each resource root before
        // that, so declaring them also settles which one the game loads.
        itemModels.declareCustomModelItem(TrinketItemRegistry.REMEMBRALL.get());
        itemModels.declareCustomModelItem(TrinketItemRegistry.OMNI_OCULARS.get());

        // The brew ships a hand-written items/ definition: a minecraft:model carrying a
        // wizards_and_beasts:brew tint source, which is what paints the liquid the colour of whatever
        // brew is in the bottle. It cannot be generated here — the tint source's MapCodec is registered
        // from client mod-bus code that runData never fires, so datagen has no way to serialise it. The
        // declaration stays so datagen does not emit a competing flat model; src/main wins the merge.
        itemModels.declareCustomModelItem(ConsumableItemRegistry.BREW.get());
        // Consumables use custom item models so they can point at vanilla textures while art is pending.
        itemModels.declareCustomModelItem(ConsumableItemRegistry.BUTTERBEER.get());
        itemModels.generateFlatItem(ConsumableItemRegistry.EMPTY_BUTTERBEER_MUG.get(),
                net.minecraft.client.data.models.model.ModelTemplates.FLAT_ITEM);
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
        createFlooFlames(blockModels, ModBlocks.FLOO_FLAMES.get());
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

    /**
     * Footed vessel with a raised rim. This is the <em>inventory icon</em>, not the placed block.
     *
     * <p>A placed cauldron is {@link net.minecraft.world.level.block.RenderShape#INVISIBLE} and drawn
     * by its GeckoLib rig, which is what gives it a hollow inside and therefore a real empty state.
     * This model survives because the block item still needs a shape in the hand and the inventory,
     * and break and step particles still sample it.
     *
     * <p>It used to carry {@code tintindex 0} on the opening, painted by a block colour handler. Both
     * are gone: the tinted face was the pot's <em>lid</em>, invisible to anyone not standing directly
     * over the block, so it could never do the job it was added for.
     */
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
    /**
     * The Occamy eggshell, which is the one prop whose block and item want different art.
     *
     * <p>{@link #propBlock} points the item model at the block model, which is right for a cauldron
     * or a lectern — you want to see the thing you are about to place. It is wrong here: the shell
     * ships a hand-drawn 16x16 inventory sprite of a whole silver egg, and pointing the item at a
     * 6x5 sub-cube would leave that art with no consumer at all. So the block takes the two new
     * block textures and the item keeps its sprite.
     */
    private void eggshellBlock(BlockModelGenerators blockModels) {
        Block block = ModBlocks.OCCAMY_EGGSHELL.get();
        Identifier model = eggshellModel().create(block, sideTop(block, ""), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleFlatItemModel(block.asItem());
    }

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
     * One half of a hanging banner: a single quad a half-pixel off the back face.
     *
     * <p>The cloth's outline — inset sides, swallowtail hem, the gap either side of the
     * hanging rod — is alpha in the texture, not geometry, so this stays one element and the
     * model needs {@code render_type: cutout} to honour it. The quad spans the full block
     * width because the texture's own transparent margins do the insetting; narrowing the
     * element instead would squeeze the sprite and bend the emblem.
     *
     * <p>Authored facing north, the face {@link BlockModelGenerators#ROTATION_HORIZONTAL_FACING}
     * leaves unrotated, which also matches the identity entry of {@code Shapes.rotateHorizontal}
     * used for the block's shape.
     */
    private static ExtendedModelTemplate bannerHalfModel() {
        return prop(TextureSlot.PARTICLE, TextureSlot.TEXTURE)
                .renderType("minecraft:cutout")
                .element(e -> e.from(0, 0, 15.5F).to(16, 16, 16)
                        .textureAll(TextureSlot.TEXTURE))
                .build();
    }

    /**
     * Banners are two blocks tall, so the halves are separate models chosen by
     * {@code DOUBLE_BLOCK_HALF} and then rotated as one by facing — chaining the two
     * dispatches gives the full 4x2 product.
     *
     * <p>The item is a flat sprite rather than the block model: half a banner in the hand
     * would be meaningless, so {@code <block>_item} is a whole banner drawn small.
     */
    private void createHouseBanner(BlockModelGenerators blockModels, Block block) {
        Identifier upper = bannerHalfModel().createWithSuffix(block, "_top",
                bannerTexture(block, "_top"), blockModels.modelOutput);
        Identifier lower = bannerHalfModel().createWithSuffix(block, "_bottom",
                bannerTexture(block, "_bottom"), blockModels.modelOutput);

        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
                .with(PropertyDispatch.initial(BlockStateProperties.DOUBLE_BLOCK_HALF)
                        .select(DoubleBlockHalf.LOWER, BlockModelGenerators.plainVariant(lower))
                        .select(DoubleBlockHalf.UPPER, BlockModelGenerators.plainVariant(upper)))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));

        blockModels.registerSimpleItemModel(block,
                blockModels.createFlatItemModelWithBlockTexture(block.asItem(), block, "_item"));
    }

    /**
     * A single Occamy shell: one small cuboid, six pixels across and five tall.
     *
     * <p>It used to borrow the <em>item</em> sprite for all six faces, and that was wrong twice
     * over. The item sprite is a whole egg drawn in an inventory frame, so the placed block was an
     * egg decal smeared across a cuboid rather than a broken shell on the ground; and because the
     * template declared no render type, the sprite's transparent corners came back as opaque black
     * under the default {@code solid} pass. The shell now has its own two block textures — a curved
     * silver wall and a hole seen from above — drawn by {@code tools/block_textures.py}.
     *
     * <p>UVs are stated rather than left to the auto-mapper. A {@code from/to} of 5-11 makes vanilla
     * sample the matching 6x5 <em>window</em> of the texture, which crops a 16x16 sprite down to
     * whatever happens to sit in that corner; {@code 0-16} puts the whole authored face on the face.
     *
     * <p>The underside takes the wall texture, not the top: a shell resting on the ground is closed
     * underneath, and the hole belongs on the one face you can see into.
     */
    private static ExtendedModelTemplate eggshellModel() {
        return prop(TextureSlot.PARTICLE, TextureSlot.SIDE, TextureSlot.TOP)
                .renderType("minecraft:cutout")
                .element(e -> e.from(5, 0, 5).to(11, 5, 11)
                        .face(Direction.NORTH, f -> f.texture(TextureSlot.SIDE).uvs(0, 0, 16, 16))
                        .face(Direction.SOUTH, f -> f.texture(TextureSlot.SIDE).uvs(0, 0, 16, 16))
                        .face(Direction.EAST, f -> f.texture(TextureSlot.SIDE).uvs(0, 0, 16, 16))
                        .face(Direction.WEST, f -> f.texture(TextureSlot.SIDE).uvs(0, 0, 16, 16))
                        .face(Direction.UP, f -> f.texture(TextureSlot.TOP).uvs(0, 0, 16, 16))
                        .face(Direction.DOWN, f -> f.texture(TextureSlot.SIDE).uvs(0, 0, 16, 16)))
                .build();
    }

    /**
     * A candle: a square wax column with the flame as two crossed quads above the wick.
     *
     * <p>Geometry follows {@code minecraft:block/template_candle}, but the UVs are read off
     * our own sprite rather than vanilla's packing. {@code floating_candle.png} draws the
     * candle centred in a 16x16 frame — flame on rows 0-4, wick on row 5, wax on rows 6-15,
     * all within columns 6-9 — so the wax box is 4 wide and 10 tall and each part samples the
     * rows it is actually drawn on. That one-texel-per-unit correspondence is why the model
     * needs no new art: the existing eight-frame animation keeps flickering, because the
     * flame quads sample the rows the animation moves.
     */
    private static ExtendedModelTemplate floatingCandleModel() {
        return prop(TextureSlot.PARTICLE, TextureSlot.ALL)
                .renderType("minecraft:cutout")
                .element(e -> e.from(6, 0, 6).to(10, 10, 10)
                        .face(Direction.NORTH, f -> f.texture(TextureSlot.ALL).uvs(6, 6, 10, 16))
                        .face(Direction.SOUTH, f -> f.texture(TextureSlot.ALL).uvs(6, 6, 10, 16))
                        .face(Direction.EAST, f -> f.texture(TextureSlot.ALL).uvs(6, 6, 10, 16))
                        .face(Direction.WEST, f -> f.texture(TextureSlot.ALL).uvs(6, 6, 10, 16))
                        .face(Direction.UP, f -> f.texture(TextureSlot.ALL).uvs(6, 6, 10, 10))
                        .face(Direction.DOWN, f -> f.texture(TextureSlot.ALL).uvs(6, 12, 10, 16)
                                .cullface(Direction.DOWN)))
                // Unshaded, or the two halves of the flame land on different brightnesses and
                // the cross seam becomes the most visible thing about it.
                .element(e -> e.from(6, 10, 8).to(10, 16, 8).shade(false)
                        .rotation(r -> r.origin(8, 10, 8).singleAxis(Direction.Axis.Y, 45F))
                        .face(Direction.NORTH, f -> f.texture(TextureSlot.ALL).uvs(6, 0, 10, 6))
                        .face(Direction.SOUTH, f -> f.texture(TextureSlot.ALL).uvs(6, 0, 10, 6)))
                .element(e -> e.from(6, 10, 8).to(10, 16, 8).shade(false)
                        .rotation(r -> r.origin(8, 10, 8).singleAxis(Direction.Axis.Y, -45F))
                        .face(Direction.NORTH, f -> f.texture(TextureSlot.ALL).uvs(6, 0, 10, 6))
                        .face(Direction.SOUTH, f -> f.texture(TextureSlot.ALL).uvs(6, 0, 10, 6)))
                .build();
    }

    private static TextureMapping floatingCandleTexture() {
        Identifier texture = Identifier.fromNamespaceAndPath(
                WizardsAndBeastsMod.MODID, "block/floating_candle");
        return new TextureMapping()
                .put(TextureSlot.ALL, texture)
                .put(TextureSlot.PARTICLE, texture);
    }

    private static TextureMapping bannerTexture(Block block, String suffix) {
        Identifier texture = TextureMapping.getBlockTexture(block, suffix);
        return new TextureMapping()
                .put(TextureSlot.TEXTURE, texture)
                .put(TextureSlot.PARTICLE, texture);
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

    /**
     * Green fire, drawn the way vanilla draws fire: four planes leaning inward from the block's
     * faces, unshaded, with ambient occlusion off.
     *
     * <p>Copying that geometry rather than inventing one is the point. Crossed quads — the obvious
     * alternative — read as a plant from every angle, and fire is the one shape a player already
     * knows on sight; borrowing it is how these say "fire" before the colour says "Floo". The lean
     * plus {@code rescale} is what makes the flames widen toward the top instead of standing flat.
     *
     * <p>Shorter than vanilla's, though: vanilla fire runs to 22.4 pixels and licks up past the block
     * it sits in, which is right for something spreading and wrong for something burning in a
     * fireplace. Sixteen keeps it inside its own cube.
     */
    private static ExtendedModelTemplate flamesModel() {
        return prop(TextureSlot.PARTICLE, TextureSlot.TEXTURE)
                .renderType("minecraft:cutout")
                .ambientOcclusion(false)
                .element(e -> e.from(0, 0, 8.8F).to(16, 16, 8.8F).shade(false)
                        .rotation(r -> r.origin(8, 8, 8).singleAxis(Direction.Axis.X, -22.5F).rescale(true))
                        .face(Direction.SOUTH, f -> f.texture(TextureSlot.TEXTURE).uvs(0, 0, 16, 16)))
                .element(e -> e.from(0, 0, 7.2F).to(16, 16, 7.2F).shade(false)
                        .rotation(r -> r.origin(8, 8, 8).singleAxis(Direction.Axis.X, 22.5F).rescale(true))
                        .face(Direction.NORTH, f -> f.texture(TextureSlot.TEXTURE).uvs(0, 0, 16, 16)))
                .element(e -> e.from(8.8F, 0, 0).to(8.8F, 16, 16).shade(false)
                        .rotation(r -> r.origin(8, 8, 8).singleAxis(Direction.Axis.Z, -22.5F).rescale(true))
                        .face(Direction.WEST, f -> f.texture(TextureSlot.TEXTURE).uvs(0, 0, 16, 16)))
                .element(e -> e.from(7.2F, 0, 0).to(7.2F, 16, 16).shade(false)
                        .rotation(r -> r.origin(8, 8, 8).singleAxis(Direction.Axis.Z, 22.5F).rescale(true))
                        .face(Direction.EAST, f -> f.texture(TextureSlot.TEXTURE).uvs(0, 0, 16, 16)))
                .build();
    }

    /**
     * One model for every state of the flames, and no item model.
     *
     * <p>No dispatch on {@code FACING}: the shape is symmetric under the four horizontal rotations,
     * so rotating it would emit three more variants that render identically. The property is on the
     * block because the flames use it to find their hearth, not because they look different facing
     * one way or another.
     *
     * <p>No dispatch on {@code CHARGES} either — a blockstate variant matches on the properties it
     * names and ignores the rest, so this one variant covers all three. How much fire is left shows
     * in the particle density instead ({@code FlooFlamesBlock.animateTick}).
     *
     * <p>{@code registerSimpleItemModel} is deliberately not called: flames have no {@code BlockItem}
     * (they are placed by Floo Powder, never carried), and for an item-less block {@code asItem()}
     * resolves to {@code minecraft:air} — the same trap {@link #createLanternWithoutItem} documents.
     */
    private void createFlooFlames(BlockModelGenerators blockModels, Block block) {
        Identifier texture = TextureMapping.getBlockTexture(block);
        Identifier model = flamesModel().create(block, new TextureMapping()
                .put(TextureSlot.TEXTURE, texture)
                .put(TextureSlot.PARTICLE, texture), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model)));
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
