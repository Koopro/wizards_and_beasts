package at.koopro.neo.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import at.koopro.neo.Neo;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Neo.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + Neo.MODID + ".main"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> new ItemStack(ModItems.WAND.get()))
                    .withSearchBar()
                    .displayItems((parameters, output) -> {
                        // Magical Tools
                        output.accept(ModItems.WAND.get());
                        output.accept(ModItems.BROOM_ITEM.get());
                        output.accept(ModItems.MARAUDERS_MAP.get());

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
                        output.accept(ModItems.SNEAKOSCOPE.get());
                        output.accept(ModItems.PORTKEY.get());
                        output.accept(ModItems.PERUVIAN_DARKNESS_POWDER.get());
                        output.accept(ModItems.DECOY_DETONATOR.get());
                        output.accept(ModItems.EXTENDABLE_EARS.get());
                        output.accept(ModItems.FLOO_POWDER.get());
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
