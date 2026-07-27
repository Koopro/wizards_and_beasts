package at.koopro.wizardsandbeasts.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagCopyingItemTagProvider;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.location.DiagonAlleyBlocks;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleTags;
import at.koopro.wizardsandbeasts.registry.BroomItemRegistry;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.registry.LoreItemRegistry;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.registry.WoodSet;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class ModItemTagsProvider extends BlockTagCopyingItemTagProvider {

    public ModItemTagsProvider(PackOutput output,
                               CompletableFuture<HolderLookup.Provider> lookupProvider,
                               CompletableFuture<TagsProvider.TagLookup<Block>> blockTags) {
        super(output, lookupProvider, blockTags, WizardsAndBeastsMod.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        copy(BlockTags.LOGS, ItemTags.LOGS);
        copy(BlockTags.LOGS_THAT_BURN, ItemTags.LOGS_THAT_BURN);
        copy(BlockTags.PLANKS, ItemTags.PLANKS);
        copy(BlockTags.WOODEN_SLABS, ItemTags.WOODEN_SLABS);
        copy(BlockTags.WOODEN_STAIRS, ItemTags.WOODEN_STAIRS);
        copy(BlockTags.SLABS, ItemTags.SLABS);
        copy(BlockTags.STAIRS, ItemTags.STAIRS);
        copy(BlockTags.LEAVES, ItemTags.LEAVES);
        copy(BlockTags.SAPLINGS, ItemTags.SAPLINGS);

        addModuleTags();
    }

    /**
     * Declares which module owns each item.
     *
     * <p>Membership is what {@code ModuleContentIndex} reads to decide whether an item may be reached, so
     * an item left out of every tag stays reachable in all configurations — see that class on failing open.
     * Vanilla-facing items and anything a disabled module would strand are deliberately untagged.
     */
    private void addModuleTags() {
        // Decorative build sets. Taken from the registration helper rather than a hand-written list so a
        // new marble variant joins the tag by existing, not by someone remembering to come back here.
        module(Module.STRUCTURES).add(LocationBlockHelper.allItems().stream()
                .map(item -> (Item) item.get())
                .toArray(Item[]::new));
        add(Module.STRUCTURES,
                DiagonAlleyBlocks.DIAGON_STREET_STONE_ITEM.get(),
                DiagonAlleyBlocks.DIAGON_STREET_STONE_SLAB_ITEM.get(),
                DiagonAlleyBlocks.DIAGON_STREET_STONE_PRESSURE_PLATE_ITEM.get());

        for (WoodSet woodSet : ModBlocks.ALL_WOOD_SETS) {
            add(Module.WANDWOOD,
                    woodSet.logItem().get(), woodSet.strippedLogItem().get(),
                    woodSet.woodItem().get(), woodSet.strippedWoodItem().get(),
                    woodSet.planksItem().get(), woodSet.slabItem().get(), woodSet.stairsItem().get(),
                    woodSet.leavesItem().get(), woodSet.saplingItem().get());
        }

        // Every generic-roster spawn egg, plus the nine bespoke beasts and the Goblin teller.
        module(Module.CREATURES).add(ModCreatures.SPAWN_EGGS.values().stream()
                .map(egg -> (Item) egg.get())
                .toArray(Item[]::new));
        add(Module.CREATURES,
                MiscItemRegistry.GOBLIN_TELLER_SPAWN_EGG.get(), MiscItemRegistry.NIFFLER_SPAWN_EGG.get(),
                MiscItemRegistry.BOWTRUCKLE_SPAWN_EGG.get(), MiscItemRegistry.CORNISH_PIXIE_SPAWN_EGG.get(),
                MiscItemRegistry.THESTRAL_SPAWN_EGG.get(), MiscItemRegistry.PHOENIX_SPAWN_EGG.get(),
                MiscItemRegistry.AUGUREY_SPAWN_EGG.get(), MiscItemRegistry.MOONCALF_SPAWN_EGG.get(),
                MiscItemRegistry.STREELER_SPAWN_EGG.get(), MiscItemRegistry.RUNESPOOR_SPAWN_EGG.get(),
                MiscItemRegistry.HIDEBEHIND_SPAWN_EGG.get());

        // Wand cores sit with WANDS, not MAGIZOOLOGY: a core is only ever an input to wandmaking, and the
        // creative menu already files them under "Wand Cores".
        add(Module.WANDS,
                WandItemRegistry.WAND.get(), WandItemRegistry.WAND_BLANK.get(),
                WandItemRegistry.DEBUG_WAND.get(), WandItemRegistry.MORPH_WAND.get(),
                ModBlocks.WANDMAKERS_BENCH_ITEM.get(),
                WandItemRegistry.PHOENIX_FEATHER.get(), WandItemRegistry.DRAGON_HEARTSTRING.get(),
                WandItemRegistry.UNICORN_HAIR.get(), WandItemRegistry.THUNDERBIRD_TAIL_FEATHER.get(),
                WandItemRegistry.TROLL_WHISKER.get(), WandItemRegistry.VEELA_HAIR.get(),
                WandItemRegistry.WAMPUS_CAT_HAIR.get(), WandItemRegistry.THESTRAL_TAIL_HAIR.get(),
                ConsumableItemRegistry.ROUGAROU_HAIR.get(),
                ConsumableItemRegistry.WHITE_RIVER_MONSTER_SPINE.get());

        add(Module.BROOM_FLIGHT,
                BroomItemRegistry.BROOM_ITEM.get(), BroomItemRegistry.CLEANSWEEP_SEVEN.get(),
                BroomItemRegistry.COMET_260.get(), BroomItemRegistry.NIMBUS_2000.get(),
                BroomItemRegistry.NIMBUS_2001.get(), BroomItemRegistry.FIREBOLT.get(),
                BroomItemRegistry.FIREBOLT_SUPREME.get(), BroomItemRegistry.OAKSHAFT_79.get(),
                BroomItemRegistry.BROOM_POLISH.get(), BroomItemRegistry.ENCHANTED_TWIG_BUNDLE.get());

        add(Module.GRINGOTTS,
                CurrencyItemRegistry.GALLEON.get(), CurrencyItemRegistry.SICKLE.get(),
                CurrencyItemRegistry.KNUT.get(), CurrencyItemRegistry.DRAGOT.get(),
                CurrencyItemRegistry.LEPRECHAUN_GOLD.get(), CurrencyItemRegistry.COUNTERFEIT_GALLEON.get());

        add(Module.DARK_ARTS,
                DarkArtefactItemRegistry.RIDDLES_DIARY.get(), DarkArtefactItemRegistry.MARVOLO_GAUNTS_RING.get(),
                DarkArtefactItemRegistry.SLYTHERINS_LOCKET.get(), DarkArtefactItemRegistry.HUFFLEPUFFS_CUP.get(),
                DarkArtefactItemRegistry.RAVENCLAWS_DIADEM.get(), DarkArtefactItemRegistry.RESURRECTION_STONE.get(),
                DarkArtefactItemRegistry.PHILOSOPHERS_STONE.get(),
                TrinketItemRegistry.PENSIEVE.get(), TrinketItemRegistry.TWO_WAY_MIRROR.get(),
                TrinketItemRegistry.HERMIONES_BEADED_BAG.get(), TrinketItemRegistry.FOE_GLASS.get(),
                TrinketItemRegistry.HAND_OF_GLORY.get(), TrinketItemRegistry.DARK_MARK_BRAND.get(),
                TrinketItemRegistry.BLOOD_PACT_VIAL.get());

        add(Module.POCKET_DIMENSIONS,
                ModBlocks.ENCHANTED_TRUNK_ITEM.get(), ModBlocks.EXPANDED_TRUNK_ITEM.get(),
                ModBlocks.MASTERS_TRUNK_ITEM.get(), ModBlocks.MOODYS_TRUNK_ITEM.get(),
                ModBlocks.NEWTS_CASE_ITEM.get(), ModBlocks.POCKET_CONFIGURATOR_ITEM.get());

        add(Module.FLOO_NETWORK,
                MiscItemRegistry.FLOO_POWDER.get(), ModBlocks.FLOO_GRATE_ITEM.get(),
                ModBlocks.FLOO_FIREPLACE_ITEM.get());

        add(Module.HANDBOOK, MiscItemRegistry.MINISTRY_HANDBOOK.get());
        add(Module.BESTIARY, MiscItemRegistry.BESTIARY.get());
        add(Module.OWLS, ModBlocks.EXAMINATION_DESK_ITEM.get());
        add(Module.MINISTRY, TrinketItemRegistry.MINISTRY_LICENSE_SCROLL.get());

        add(Module.ARTEFACTS,
                TrinketItemRegistry.REMEMBRALL.get(), TrinketItemRegistry.OMNI_OCULARS.get(),
                TrinketItemRegistry.TIME_TURNER.get(), TrinketItemRegistry.SNEAKOSCOPE.get(),
                TrinketItemRegistry.PORTKEY.get(), TrinketItemRegistry.DECOY_DETONATOR.get(),
                TrinketItemRegistry.EXTENDABLE_EARS.get(), TrinketItemRegistry.PERUVIAN_DARKNESS_POWDER.get(),
                MiscItemRegistry.MARAUDERS_MAP.get(), MiscItemRegistry.DELUMINATOR.get(),
                MiscItemRegistry.BLINDFOLD.get(),
                DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get(),
                DarkArtefactItemRegistry.DEATHLY_HALLOW_CLOAK.get());

        add(Module.SCHOLARSHIP,
                MiscItemRegistry.PARCHMENT.get(), MiscItemRegistry.INK_BOTTLE.get(),
                LoreItemRegistry.A_HISTORY_OF_MAGIC.get(), LoreItemRegistry.HOGWARTS_A_HISTORY.get(),
                LoreItemRegistry.RISE_AND_FALL_OF_THE_DARK_ARTS.get());

        add(Module.WIZARDING_FOOD,
                ConsumableItemRegistry.BUTTERBEER.get(), ConsumableItemRegistry.PUMPKIN_JUICE.get(),
                ConsumableItemRegistry.CHOCOLATE_FROG.get(), ConsumableItemRegistry.FAMOUS_WIZARD_CARD.get(),
                ConsumableItemRegistry.BERTIE_BOTTS_EVERY_FLAVOUR_BEANS.get(),
                ConsumableItemRegistry.DROOBLES_BEST_BLOWING_GUM.get(), ConsumableItemRegistry.FIREWHISKY.get(),
                ConsumableItemRegistry.TREACLE_TART.get(), ConsumableItemRegistry.PUMPKIN_PASTY.get(),
                ConsumableItemRegistry.FIZZING_WHIZZBEE.get(), ConsumableItemRegistry.PEPPERMINT_TOAD.get(),
                ConsumableItemRegistry.DIRIGIBLE_PLUM.get());

        add(Module.MAGIZOOLOGY,
                ConsumableItemRegistry.OCCAMY_EGGSHELL.get(), ConsumableItemRegistry.HIDEBEHIND_SHADOW_ESSENCE.get(),
                ConsumableItemRegistry.HIDEBEHIND_CLAW.get(), ConsumableItemRegistry.GHOUL_SLIME.get(),
                ConsumableItemRegistry.GOLDEN_SNIDGET_FEATHER.get(), ConsumableItemRegistry.GRANIAN_HAIR.get(),
                ConsumableItemRegistry.HORNED_SERPENT_GEM.get(), ConsumableItemRegistry.PUKWUDGIE_VENOM_SAC.get(),
                ConsumableItemRegistry.YETI_FUR.get(), ConsumableItemRegistry.MATAGOT_ESSENCE.get(),
                ConsumableItemRegistry.BEZOAR.get(), ConsumableItemRegistry.DEMIGUISE_HAIR.get(),
                ConsumableItemRegistry.MOONCALF_DUNG.get(), ConsumableItemRegistry.ERUMPENT_HORN.get(),
                ConsumableItemRegistry.MANDRAKE.get(), ConsumableItemRegistry.GILLYWEED.get(),
                ConsumableItemRegistry.DITTANY.get(), ConsumableItemRegistry.BREW.get(),
                ModBlocks.MANDRAKE_SEEDS.get());

        add(Module.FURNISHINGS,
                ModBlocks.WARDING_STONE_ITEM.get(), ModBlocks.DEVILS_SNARE_ITEM.get(),
                ModBlocks.MALLOWSWEET_ITEM.get(), ModBlocks.GRYFFINDOR_BANNER_ITEM.get(),
                ModBlocks.SLYTHERIN_BANNER_ITEM.get(), ModBlocks.RAVENCLAW_BANNER_ITEM.get(),
                ModBlocks.HUFFLEPUFF_BANNER_ITEM.get(), ModBlocks.FLOATING_CANDLE_ITEM.get(),
                ModBlocks.BRASS_CAULDRON_ITEM.get(), ModBlocks.WIZARDING_COPPER_CAULDRON_ITEM.get(),
                ModBlocks.PEWTER_CAULDRON_ITEM.get(), ModBlocks.SPELL_TEACHER_ITEM.get());
    }

    private void add(Module module, ItemLike... contents) {
        module(module).add(Arrays.stream(contents).map(ItemLike::asItem).toArray(Item[]::new));
    }

    private net.minecraft.data.tags.TagAppender<Item, Item> module(Module module) {
        return tag(ModuleTags.items(module));
    }
}
