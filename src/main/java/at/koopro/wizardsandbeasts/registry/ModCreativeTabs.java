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

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + WizardsAndBeastsMod.MODID + ".main"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> new ItemStack(ModItems.WAND.get()))
                    .withSearchBar()
                    .displayItems((parameters, output) -> {
                        // Debug tools
                        output.accept(ModItems.DEBUG_WAND.get());
                        output.accept(ModItems.MORPH_WAND.get());

                        // Magical Tools
                        output.accept(ModItems.WAND.get());
                        if (ModuleManager.isEnabled(Module.BROOM_FLIGHT)) {
                            output.accept(ModItems.BROOM_ITEM.get());
                            output.accept(ModItems.CLEANSWEEP_SEVEN.get());
                            output.accept(ModItems.COMET_260.get());
                            output.accept(ModItems.NIMBUS_2000.get());
                            output.accept(ModItems.NIMBUS_2001.get());
                            output.accept(ModItems.FIREBOLT.get());
                            output.accept(ModItems.FIREBOLT_SUPREME.get());
                            output.accept(ModItems.OAKSHAFT_79.get());
                            output.accept(ModItems.BROOM_POLISH.get());
                            output.accept(ModItems.ENCHANTED_TWIG_BUNDLE.get());
                        }
                        output.accept(ModItems.MARAUDERS_MAP.get());
                        output.accept(ModItems.BESTIARY.get());

                        // Wand Cores
                        output.accept(ModItems.PHOENIX_FEATHER.get());
                        output.accept(ModItems.DRAGON_HEARTSTRING.get());
                        output.accept(ModItems.UNICORN_HAIR.get());
                        output.accept(ModItems.THESTRAL_TAIL_HAIR.get());

                        // Crafting Materials
                        output.accept(ModItems.PARCHMENT.get());
                        output.accept(ModItems.INK_BOTTLE.get());

                        // Currency
                        output.accept(ModItems.GALLEON.get());
                        output.accept(ModItems.SICKLE.get());
                        output.accept(ModItems.KNUT.get());
                        output.accept(ModItems.DRAGOT.get());
                        output.accept(ModItems.LEPRECHAUN_GOLD.get());

                        // Spawn Eggs
                        output.accept(ModItems.GOBLIN_TELLER_SPAWN_EGG.get());
                        output.accept(ModItems.NIFFLER_SPAWN_EGG.get());

                        // Wizarding World — food & drink
                        output.accept(ModItems.BUTTERBEER.get());
                        output.accept(ModItems.PUMPKIN_JUICE.get());
                        output.accept(ModItems.CHOCOLATE_FROG.get());
                        output.accept(ModItems.FAMOUS_WIZARD_CARD.get());
                        output.accept(ModItems.BERTIE_BOTTS_EVERY_FLAVOUR_BEANS.get());
                        output.accept(ModItems.DROOBLES_BEST_BLOWING_GUM.get());
                        output.accept(ModItems.FIREWHISKY.get());
                        output.accept(ModItems.GILLYWEED.get());
                        output.accept(ModItems.DIRIGIBLE_PLUM.get());
                        output.accept(ModItems.TREACLE_TART.get());
                        output.accept(ModItems.PUMPKIN_PASTY.get());
                        output.accept(ModItems.FIZZING_WHIZZBEE.get());
                        output.accept(ModItems.PEPPERMINT_TOAD.get());
                        output.accept(ModItems.DITTANY.get());
                        // Magizoology & materials
                        output.accept(ModItems.OCCAMY_EGGSHELL.get());
                        output.accept(ModItems.BEZOAR.get());
                        output.accept(ModItems.DEMIGUISE_HAIR.get());
                        output.accept(ModItems.MOONCALF_DUNG.get());
                        output.accept(ModItems.ERUMPENT_HORN.get());
                        output.accept(ModItems.MANDRAKE.get());
                        output.accept(ModBlocks.MANDRAKE_SEEDS.get());
                        // Gear & misc
                        output.accept(ModItems.REMEMBRALL.get());
                        output.accept(ModItems.OMNI_OCULARS.get());
                        output.accept(ModItems.DELUMINATOR.get());
                        output.accept(ModItems.TIME_TURNER.get());
                        output.accept(ModItems.INVISIBILITY_CLOAK.get());
                        output.accept(ModItems.DEATHLY_HALLOW_CLOAK.get());
                        output.accept(ModItems.SNEAKOSCOPE.get());
                        output.accept(ModItems.PORTKEY.get());
                        if (ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
                            output.accept(ModItems.ENCHANTED_TRUNK.get());
                            output.accept(ModItems.EXPANDED_TRUNK.get());
                            output.accept(ModItems.MASTERS_TRUNK.get());
                            output.accept(ModItems.MINISTRY_LICENSE_SCROLL.get());
                        }
                        output.accept(ModItems.PERUVIAN_DARKNESS_POWDER.get());
                        output.accept(ModItems.DECOY_DETONATOR.get());
                        output.accept(ModItems.EXTENDABLE_EARS.get());
                        output.accept(ModItems.FLOO_POWDER.get());
                        output.accept(ModItems.BREW.get());

                        // ── Deathly Hallows ──────────────────────────────────────────
                        if (ModuleManager.isEnabled(Module.WANDS)) {
                            output.accept(ModItems.RESURRECTION_STONE.get());
                        }

                        // ── Horcrux Vessels ──────────────────────────────────────────
                        if (ModuleManager.isEnabled(Module.DARK_ARTS)) {
                            output.accept(ModItems.RIDDLES_DIARY.get());
                            output.accept(ModItems.MARVOLO_GAUNTS_RING.get());
                            output.accept(ModItems.SLYTHERINS_LOCKET.get());
                            output.accept(ModItems.HUFFLEPUFFS_CUP.get());
                            output.accept(ModItems.RAVENCLAWS_DIADEM.get());
                        }

                        // ── Unique Artefacts ─────────────────────────────────────────
                        if (ModuleManager.isEnabled(Module.WANDS)) {
                            output.accept(ModItems.PHILOSOPHERS_STONE.get());
                            output.accept(ModItems.PENSIEVE.get());
                            output.accept(ModItems.TWO_WAY_MIRROR.get());
                            output.accept(ModItems.MOODYS_TRUNK.get());
                            output.accept(ModItems.HERMIONES_BEADED_BAG.get());
                            output.accept(ModItems.FOE_GLASS.get());
                            output.accept(ModItems.NEWTS_CASE_ITEM.get());
                        }
                        if (ModuleManager.isEnabled(Module.DARK_ARTS)) {
                            output.accept(ModItems.HAND_OF_GLORY.get());
                            output.accept(ModItems.DARK_MARK_BRAND.get());
                            output.accept(ModItems.BLOOD_PACT_VIAL.get());
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
