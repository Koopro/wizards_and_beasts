package at.koopro.wizardsandbeasts.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.location.DiagonAlleyBlocks;
import at.koopro.wizardsandbeasts.block.location.GringottsBlocks;
import at.koopro.wizardsandbeasts.block.location.HogwartsBlocks;
import at.koopro.wizardsandbeasts.block.location.HogsmeadeBlocks;
import at.koopro.wizardsandbeasts.block.location.MinistryBlocks;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.BroomItemRegistry;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + WizardsAndBeastsMod.MODID + ".main"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> new ItemStack(WandItemRegistry.WAND.get()))
                    .withSearchBar()
                    .displayItems((parameters, output) -> {
                        // Debug tools
                        output.accept(WandItemRegistry.DEBUG_WAND.get());
                        output.accept(WandItemRegistry.MORPH_WAND.get());

                        // Magical Tools
                        output.accept(WandItemRegistry.WAND.get());
                        if (ModuleManager.isEnabled(Module.BROOM_FLIGHT)) {
                            output.accept(BroomItemRegistry.BROOM_ITEM.get());
                            output.accept(BroomItemRegistry.CLEANSWEEP_SEVEN.get());
                            output.accept(BroomItemRegistry.COMET_260.get());
                            output.accept(BroomItemRegistry.NIMBUS_2000.get());
                            output.accept(BroomItemRegistry.NIMBUS_2001.get());
                            output.accept(BroomItemRegistry.FIREBOLT.get());
                            output.accept(BroomItemRegistry.FIREBOLT_SUPREME.get());
                            output.accept(BroomItemRegistry.OAKSHAFT_79.get());
                            output.accept(BroomItemRegistry.BROOM_POLISH.get());
                            output.accept(BroomItemRegistry.ENCHANTED_TWIG_BUNDLE.get());
                        }
                        output.accept(MiscItemRegistry.MARAUDERS_MAP.get());
                        output.accept(MiscItemRegistry.BESTIARY.get());

                        // Wand Cores
                        output.accept(WandItemRegistry.PHOENIX_FEATHER.get());
                        output.accept(WandItemRegistry.DRAGON_HEARTSTRING.get());
                        output.accept(WandItemRegistry.UNICORN_HAIR.get());
                        output.accept(WandItemRegistry.THESTRAL_TAIL_HAIR.get());

                        // Crafting Materials
                        output.accept(MiscItemRegistry.PARCHMENT.get());
                        output.accept(MiscItemRegistry.INK_BOTTLE.get());

                        // Lore tomes — studyable for KNOWLEDGE / History of Magic OWL credit
                        output.accept(LoreItemRegistry.A_HISTORY_OF_MAGIC.get());
                        output.accept(LoreItemRegistry.HOGWARTS_A_HISTORY.get());
                        output.accept(LoreItemRegistry.RISE_AND_FALL_OF_THE_DARK_ARTS.get());

                        // Currency
                        output.accept(CurrencyItemRegistry.GALLEON.get());
                        output.accept(CurrencyItemRegistry.SICKLE.get());
                        output.accept(CurrencyItemRegistry.KNUT.get());
                        output.accept(CurrencyItemRegistry.DRAGOT.get());
                        output.accept(CurrencyItemRegistry.LEPRECHAUN_GOLD.get());

                        // Spawn Eggs
                        output.accept(MiscItemRegistry.GOBLIN_TELLER_SPAWN_EGG.get());
                        output.accept(MiscItemRegistry.NIFFLER_SPAWN_EGG.get());

                        // Wizarding World — food & drink
                        output.accept(ConsumableItemRegistry.BUTTERBEER.get());
                        output.accept(ConsumableItemRegistry.PUMPKIN_JUICE.get());
                        output.accept(ConsumableItemRegistry.CHOCOLATE_FROG.get());
                        output.accept(ConsumableItemRegistry.FAMOUS_WIZARD_CARD.get());
                        output.accept(ConsumableItemRegistry.BERTIE_BOTTS_EVERY_FLAVOUR_BEANS.get());
                        output.accept(ConsumableItemRegistry.DROOBLES_BEST_BLOWING_GUM.get());
                        output.accept(ConsumableItemRegistry.FIREWHISKY.get());
                        output.accept(ConsumableItemRegistry.GILLYWEED.get());
                        output.accept(ConsumableItemRegistry.DIRIGIBLE_PLUM.get());
                        output.accept(ConsumableItemRegistry.TREACLE_TART.get());
                        output.accept(ConsumableItemRegistry.PUMPKIN_PASTY.get());
                        output.accept(ConsumableItemRegistry.FIZZING_WHIZZBEE.get());
                        output.accept(ConsumableItemRegistry.PEPPERMINT_TOAD.get());
                        output.accept(ConsumableItemRegistry.DITTANY.get());
                        // Magizoology & materials
                        output.accept(ConsumableItemRegistry.OCCAMY_EGGSHELL.get());
                        output.accept(ConsumableItemRegistry.BEZOAR.get());
                        output.accept(ConsumableItemRegistry.DEMIGUISE_HAIR.get());
                        output.accept(ConsumableItemRegistry.MOONCALF_DUNG.get());
                        output.accept(ConsumableItemRegistry.ERUMPENT_HORN.get());
                        output.accept(ConsumableItemRegistry.MANDRAKE.get());
                        output.accept(ModBlocks.MANDRAKE_SEEDS.get());
                        // Gear & misc
                        output.accept(TrinketItemRegistry.REMEMBRALL.get());
                        output.accept(TrinketItemRegistry.OMNI_OCULARS.get());
                        output.accept(MiscItemRegistry.DELUMINATOR.get());
                        output.accept(TrinketItemRegistry.TIME_TURNER.get());
                        output.accept(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get());
                        output.accept(DarkArtefactItemRegistry.DEATHLY_HALLOW_CLOAK.get());
                        output.accept(TrinketItemRegistry.SNEAKOSCOPE.get());
                        output.accept(TrinketItemRegistry.PORTKEY.get());
                        if (ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
                            output.accept(DarkArtefactItemRegistry.ENCHANTED_TRUNK.get());
                            output.accept(DarkArtefactItemRegistry.EXPANDED_TRUNK.get());
                            output.accept(DarkArtefactItemRegistry.MASTERS_TRUNK.get());
                            output.accept(TrinketItemRegistry.MINISTRY_LICENSE_SCROLL.get());
                        }
                        output.accept(TrinketItemRegistry.PERUVIAN_DARKNESS_POWDER.get());
                        output.accept(TrinketItemRegistry.DECOY_DETONATOR.get());
                        output.accept(TrinketItemRegistry.EXTENDABLE_EARS.get());
                        output.accept(MiscItemRegistry.FLOO_POWDER.get());
                        output.accept(ConsumableItemRegistry.BREW.get());

                        // ── Horcrux Vessels ──────────────────────────────────────────
                        if (ModuleManager.isEnabled(Module.DARK_ARTS)) {
                            output.accept(DarkArtefactItemRegistry.RIDDLES_DIARY.get());
                            output.accept(DarkArtefactItemRegistry.MARVOLO_GAUNTS_RING.get());
                            output.accept(DarkArtefactItemRegistry.SLYTHERINS_LOCKET.get());
                            output.accept(DarkArtefactItemRegistry.HUFFLEPUFFS_CUP.get());
                            output.accept(DarkArtefactItemRegistry.RAVENCLAWS_DIADEM.get());
                        }

                        // ── Stub Artefacts (gated until modules are implemented) ──────
                        if (ModuleManager.isEnabled(Module.DARK_ARTS)) {
                            output.accept(DarkArtefactItemRegistry.RESURRECTION_STONE.get());
                            output.accept(DarkArtefactItemRegistry.PHILOSOPHERS_STONE.get());
                            output.accept(TrinketItemRegistry.PENSIEVE.get());
                            output.accept(TrinketItemRegistry.TWO_WAY_MIRROR.get());
                            output.accept(TrinketItemRegistry.HERMIONES_BEADED_BAG.get());
                            output.accept(TrinketItemRegistry.FOE_GLASS.get());
                            output.accept(TrinketItemRegistry.HAND_OF_GLORY.get());
                            output.accept(TrinketItemRegistry.DARK_MARK_BRAND.get());
                            output.accept(TrinketItemRegistry.BLOOD_PACT_VIAL.get());
                        }
                        if (ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) {
                            output.accept(DarkArtefactItemRegistry.MOODYS_TRUNK.get());
                            output.accept(TrinketItemRegistry.NEWTS_CASE_ITEM.get());
                        }

                        // Wizarding blocks
                        output.accept(ModBlocks.DEVILS_SNARE.get());
                        output.accept(ModBlocks.MALLOWSWEET.get());
                        output.accept(ModBlocks.GRYFFINDOR_BANNER.get());
                        output.accept(ModBlocks.SLYTHERIN_BANNER.get());
                        output.accept(ModBlocks.RAVENCLAW_BANNER.get());
                        output.accept(ModBlocks.HUFFLEPUFF_BANNER.get());
                        output.accept(ModBlocks.FLOATING_CANDLE.get());
                        output.accept(ModBlocks.BRASS_CAULDRON.get());
                        output.accept(ModBlocks.WIZARDING_COPPER_CAULDRON.get());
                        output.accept(ModBlocks.PEWTER_CAULDRON.get());
                        output.accept(ModBlocks.FLOO_GRATE.get());
                        output.accept(ModBlocks.FLOO_FIREPLACE.get());
                        output.accept(ModBlocks.SPELL_TEACHER.get());

                        // Location decorative blocks
                        // TODO: move to dedicated DECORATIVE_BLOCKS creative tab
                        // Ministry of Magic
                        output.accept(MinistryBlocks.MINISTRY_BLACK_MARBLE.baseItem().get());
                        output.accept(MinistryBlocks.MINISTRY_BLACK_MARBLE.slabItem().get());
                        output.accept(MinistryBlocks.MINISTRY_BLACK_MARBLE.stairsItem().get());
                        output.accept(MinistryBlocks.MINISTRY_BLACK_MARBLE.wallItem().get());
                        output.accept(MinistryBlocks.MINISTRY_BLACK_MARBLE_PILLAR.item().get());
                        output.accept(MinistryBlocks.MINISTRY_BLACK_MARBLE_TILES.baseItem().get());
                        output.accept(MinistryBlocks.MINISTRY_BLACK_MARBLE_TILES.slabItem().get());
                        output.accept(MinistryBlocks.MINISTRY_BLACK_MARBLE_TILES.stairsItem().get());
                        output.accept(MinistryBlocks.MINISTRY_GILDED_BLACK_MARBLE.baseItem().get());
                        output.accept(MinistryBlocks.MINISTRY_GILDED_BLACK_MARBLE.slabItem().get());
                        output.accept(MinistryBlocks.MINISTRY_GILDED_BLACK_MARBLE.stairsItem().get());
                        output.accept(MinistryBlocks.MINISTRY_GILDED_BLACK_MARBLE.wallItem().get());
                        output.accept(MinistryBlocks.MINISTRY_GILDED_TRIM.item().get());
                        output.accept(MinistryBlocks.MINISTRY_DARK_TILE.baseItem().get());
                        output.accept(MinistryBlocks.MINISTRY_DARK_TILE.slabItem().get());
                        output.accept(MinistryBlocks.MINISTRY_DARK_TILE.stairsItem().get());
                        output.accept(MinistryBlocks.MINISTRY_FLOOR_TILE.baseItem().get());
                        output.accept(MinistryBlocks.MINISTRY_FLOOR_TILE.slabItem().get());
                        output.accept(MinistryBlocks.MINISTRY_WALL_PANEL.item().get());
                        // Hogwarts Castle
                        output.accept(HogwartsBlocks.HOGWARTS_STONE.baseItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_STONE.slabItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_STONE.stairsItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_STONE.wallItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_STONE_BRICKS.baseItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_STONE_BRICKS.slabItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_STONE_BRICKS.stairsItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_STONE_BRICKS.wallItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_CRACKED_STONE_BRICKS.item().get());
                        output.accept(HogwartsBlocks.HOGWARTS_MOSSY_STONE_BRICKS.baseItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_MOSSY_STONE_BRICKS.slabItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_MOSSY_STONE_BRICKS.stairsItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_MOSSY_STONE_BRICKS.wallItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_STONE_PILLAR.item().get());
                        output.accept(HogwartsBlocks.HOGWARTS_DARK_STONE.baseItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_DARK_STONE.slabItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_DARK_STONE.stairsItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_DARK_STONE.wallItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_FLAGSTONE.baseItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_FLAGSTONE.slabItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_FLOOR_TILE.baseItem().get());
                        output.accept(HogwartsBlocks.HOGWARTS_FLOOR_TILE.slabItem().get());
                        output.accept(HogwartsBlocks.ENCHANTED_CEILING_TILE.item().get());
                        // Diagon Alley
                        output.accept(DiagonAlleyBlocks.DIAGON_BRICK.baseItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_BRICK.slabItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_BRICK.stairsItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_BRICK.wallItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_BRICK_TILES.baseItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_BRICK_TILES.slabItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_BRICK_TILES.stairsItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_WORN_BRICK.baseItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_WORN_BRICK.slabItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_WORN_BRICK.stairsItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_WORN_BRICK.wallItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_COBBLESTONE.baseItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_COBBLESTONE.slabItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_COBBLESTONE.stairsItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_COBBLESTONE.wallItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_SHOPFRONT_WOOD.baseItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_SHOPFRONT_WOOD.slabItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_SHOPFRONT_WOOD.stairsItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_SHOPFRONT_PLANKS.baseItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_SHOPFRONT_PLANKS.slabItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_SHOPFRONT_PLANKS.stairsItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_GREEN.baseItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_GREEN.slabItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_GREEN.stairsItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_PURPLE.baseItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_PURPLE.slabItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_PURPLE.stairsItem().get());
                        output.accept(DiagonAlleyBlocks.DIAGON_STREET_STONE_ITEM.get());
                        output.accept(DiagonAlleyBlocks.DIAGON_STREET_STONE_SLAB_ITEM.get());
                        output.accept(DiagonAlleyBlocks.DIAGON_STREET_STONE_PRESSURE_PLATE_ITEM.get());
                        // Hogsmeade
                        output.accept(HogsmeadeBlocks.HOGSMEADE_STONE.baseItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_STONE.slabItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_STONE.stairsItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_STONE.wallItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_STONE_BRICKS.baseItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_STONE_BRICKS.slabItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_STONE_BRICKS.stairsItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_STONE_BRICKS.wallItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_WORN_STONE.baseItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_WORN_STONE.slabItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_WORN_STONE.stairsItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_WORN_STONE.wallItem().get());
                        output.accept(HogsmeadeBlocks.THREE_BROOMSTICKS_TIMBER.baseItem().get());
                        output.accept(HogsmeadeBlocks.THREE_BROOMSTICKS_TIMBER.slabItem().get());
                        output.accept(HogsmeadeBlocks.THREE_BROOMSTICKS_TIMBER.stairsItem().get());
                        output.accept(HogsmeadeBlocks.THREE_BROOMSTICKS_PLANKS.baseItem().get());
                        output.accept(HogsmeadeBlocks.THREE_BROOMSTICKS_PLANKS.slabItem().get());
                        output.accept(HogsmeadeBlocks.THREE_BROOMSTICKS_PLANKS.stairsItem().get());
                        output.accept(HogsmeadeBlocks.HONEYDUKES_PASTEL_PINK.baseItem().get());
                        output.accept(HogsmeadeBlocks.HONEYDUKES_PASTEL_PINK.slabItem().get());
                        output.accept(HogsmeadeBlocks.HONEYDUKES_PASTEL_PINK.stairsItem().get());
                        output.accept(HogsmeadeBlocks.HONEYDUKES_PASTEL_YELLOW.baseItem().get());
                        output.accept(HogsmeadeBlocks.HONEYDUKES_PASTEL_YELLOW.slabItem().get());
                        output.accept(HogsmeadeBlocks.HONEYDUKES_PASTEL_YELLOW.stairsItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_ROOF_TILE.baseItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_ROOF_TILE.slabItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_ROOF_TILE.stairsItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_CHIMNEY_BRICK.baseItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_CHIMNEY_BRICK.slabItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_CHIMNEY_BRICK.stairsItem().get());
                        output.accept(HogsmeadeBlocks.HOGSMEADE_CHIMNEY_BRICK.wallItem().get());
                        // Gringotts Bank
                        output.accept(GringottsBlocks.GRINGOTTS_WHITE_MARBLE.baseItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_WHITE_MARBLE.slabItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_WHITE_MARBLE.stairsItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_WHITE_MARBLE.wallItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_WHITE_MARBLE_PILLAR.item().get());
                        output.accept(GringottsBlocks.GRINGOTTS_WHITE_MARBLE_TILES.baseItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_WHITE_MARBLE_TILES.slabItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_WHITE_MARBLE_TILES.stairsItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_PALE_MARBLE.baseItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_PALE_MARBLE.slabItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_PALE_MARBLE.stairsItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_PALE_MARBLE.wallItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_GOLD_TRIM.item().get());
                        output.accept(GringottsBlocks.GRINGOTTS_IRON_VAULT_STONE.baseItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_IRON_VAULT_STONE.slabItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_IRON_VAULT_STONE.stairsItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_IRON_VAULT_STONE.wallItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_VAULT_BRICKS.baseItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_VAULT_BRICKS.slabItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_VAULT_BRICKS.stairsItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_VAULT_BRICKS.wallItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_GOBLIN_STONEWORK.baseItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_GOBLIN_STONEWORK.slabItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_GOBLIN_STONEWORK.stairsItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_GOBLIN_STONEWORK.wallItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_COUNTING_FLOOR.baseItem().get());
                        output.accept(GringottsBlocks.GRINGOTTS_COUNTING_FLOOR.slabItem().get());

                        // Wood Sets
                        for (WoodSet ws : ModBlocks.ALL_WOOD_SETS) {
                            output.accept(ws.logItem().get());
                            output.accept(ws.strippedLogItem().get());
                            output.accept(ws.woodItem().get());
                            output.accept(ws.strippedWoodItem().get());
                            output.accept(ws.planksItem().get());
                            output.accept(ws.slabItem().get());
                            output.accept(ws.stairsItem().get());
                            output.accept(ws.leavesItem().get());
                            output.accept(ws.saplingItem().get());
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
