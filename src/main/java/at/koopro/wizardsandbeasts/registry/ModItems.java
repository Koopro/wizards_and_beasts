package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.broom.item.BroomItem;
import at.koopro.wizardsandbeasts.item.darkartefact.GauntRingItem;
import at.koopro.wizardsandbeasts.item.darkartefact.HufflepuffsCupItem;
import at.koopro.wizardsandbeasts.item.darkartefact.RavenclawsDiademItem;
import at.koopro.wizardsandbeasts.item.darkartefact.RiddlesDiaryItem;
import at.koopro.wizardsandbeasts.item.darkartefact.SlytherinsLocketItem;
import at.koopro.wizardsandbeasts.item.hallow.ResurrectionStoneItem;
import at.koopro.wizardsandbeasts.bloodpact.item.BloodPactVialItem;
import at.koopro.wizardsandbeasts.item.trinket.DarkMarkItem;
import at.koopro.wizardsandbeasts.item.trinket.FoeGlassItem;
import at.koopro.wizardsandbeasts.item.trinket.HandOfGloryItem;
import at.koopro.wizardsandbeasts.item.trinket.HermionesBagItem;
import at.koopro.wizardsandbeasts.trunk.item.MoodysTrunkItem;
import at.koopro.wizardsandbeasts.item.trinket.NewtsCaseItem;
import at.koopro.wizardsandbeasts.item.trinket.PensieveItem;
import at.koopro.wizardsandbeasts.item.consumable.PhilosophersStoneItem;
import at.koopro.wizardsandbeasts.item.trinket.TwoWayMirrorItem;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.bestiary.item.BestiaryItem;
import at.koopro.wizardsandbeasts.wand.item.DebugWandItem;
import at.koopro.wizardsandbeasts.item.InkItem;
import at.koopro.wizardsandbeasts.map.item.MaraudersMapItem;
import at.koopro.wizardsandbeasts.wand.item.MorphWandItem;
import at.koopro.wizardsandbeasts.item.ParchmentItem;
import at.koopro.wizardsandbeasts.item.SimpleTooltipItem;
import at.koopro.wizardsandbeasts.wand.item.WandItem;
import at.koopro.wizardsandbeasts.wand.item.WandBlankItem;
import at.koopro.wizardsandbeasts.wand.item.WandCoreMaterialItem;
import at.koopro.wizardsandbeasts.spell.gamp.item.ConjuredSpoiledFoodItem;
import at.koopro.wizardsandbeasts.spell.gamp.item.CounterfeitGalleonItem;
import at.koopro.wizardsandbeasts.currency.item.CoinItem;
import at.koopro.wizardsandbeasts.currency.item.LeprechaunGoldItem;
import at.koopro.wizardsandbeasts.item.consumable.BertieBottsBeansItem;
import at.koopro.wizardsandbeasts.item.consumable.BezoarItem;
import at.koopro.wizardsandbeasts.brew.item.BrewItem;
import at.koopro.wizardsandbeasts.item.consumable.ChocolateFrogItem;
import at.koopro.wizardsandbeasts.cloak.item.CloakItem;
import at.koopro.wizardsandbeasts.deluminator.item.DeluminatorItem;
import at.koopro.wizardsandbeasts.item.consumable.DittanyItem;
import at.koopro.wizardsandbeasts.item.trinket.ExtendableEarsItem;
import at.koopro.wizardsandbeasts.item.trinket.FamousWizardCardItem;
import at.koopro.wizardsandbeasts.floo.item.FlooPowderItem;
import at.koopro.wizardsandbeasts.item.consumable.MooncalfDungItem;
import at.koopro.wizardsandbeasts.item.trinket.OmniocularsItem;
import at.koopro.wizardsandbeasts.item.trinket.PortkeyItem;
import at.koopro.wizardsandbeasts.trunk.item.EnchantedTrunkItem;
import at.koopro.wizardsandbeasts.trunk.TrunkTier;
import at.koopro.wizardsandbeasts.item.trinket.RemembrallItem;
import at.koopro.wizardsandbeasts.item.trinket.TimeTurnerItem;
import at.koopro.wizardsandbeasts.item.projectile.WizardingProjectileItem;
import at.koopro.wizardsandbeasts.item.consumable.WizardingQuickConsumableItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WizardsAndBeastsMod.MODID);

    private ModItems() {
    }

    public static <T extends Item> DeferredItem<T> register(String name, Supplier<T> itemSupplier) {
        return ITEMS.register(name, itemSupplier);
    }

    // --- Debug tools ---

    public static final DeferredItem<DebugWandItem> DEBUG_WAND =
            ITEMS.registerItem("debug_wand", props -> new DebugWandItem(props.stacksTo(1)));

    public static final DeferredItem<MorphWandItem> MORPH_WAND =
            ITEMS.registerItem("morph_wand", props -> new MorphWandItem(props.stacksTo(1)));

    // --- Broom ---

    public static final DeferredItem<BroomItem> BROOM_ITEM = BroomItemRegistry.BROOM_ITEM;
    public static final DeferredItem<BroomItem> CLEANSWEEP_SEVEN = BroomItemRegistry.CLEANSWEEP_SEVEN;
    public static final DeferredItem<BroomItem> COMET_260 = BroomItemRegistry.COMET_260;
    public static final DeferredItem<BroomItem> NIMBUS_2000 = BroomItemRegistry.NIMBUS_2000;
    public static final DeferredItem<BroomItem> NIMBUS_2001 = BroomItemRegistry.NIMBUS_2001;
    public static final DeferredItem<BroomItem> FIREBOLT = BroomItemRegistry.FIREBOLT;
    public static final DeferredItem<BroomItem> FIREBOLT_SUPREME = BroomItemRegistry.FIREBOLT_SUPREME;
    public static final DeferredItem<BroomItem> OAKSHAFT_79 = BroomItemRegistry.OAKSHAFT_79;

    public static final DeferredItem<SimpleTooltipItem> BROOM_POLISH = BroomItemRegistry.BROOM_POLISH;
    public static final DeferredItem<SimpleTooltipItem> ENCHANTED_TWIG_BUNDLE = BroomItemRegistry.ENCHANTED_TWIG_BUNDLE;

    // --- Wand ---

    public static final DeferredItem<WandItem> WAND =
            ITEMS.registerItem("wand", props -> new WandItem(props
                    .stacksTo(1)
                    .component(WandComponents.WAND_CONFIGURATION.get(), WandConfiguration.DEFAULT)));

    public static final DeferredItem<WandBlankItem> WAND_BLANK =
            ITEMS.registerItem("wand_blank", props -> new WandBlankItem(props.stacksTo(1)));

    // --- Wand Core Materials ---

    public static final DeferredItem<WandCoreMaterialItem> PHOENIX_FEATHER =
            ITEMS.registerItem("phoenix_feather", props -> new WandCoreMaterialItem(props,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "phoenix_feather"),
                    Component.literal("Source key: fawkes")));
    public static final DeferredItem<WandCoreMaterialItem> DRAGON_HEARTSTRING =
            ITEMS.registerItem("dragon_heartstring", props -> new WandCoreMaterialItem(props,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dragon_heartstring"),
                    Component.literal("Source key: hungarian_horntail")));
    public static final DeferredItem<WandCoreMaterialItem> UNICORN_HAIR =
            ITEMS.registerItem("unicorn_hair", props -> new WandCoreMaterialItem(props,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "unicorn_hair"),
                    Component.literal("Fragile core material. Handle with care.")));
    public static final DeferredItem<Item> THESTRAL_TAIL_HAIR =
            ITEMS.registerSimpleItem("thestral_tail_hair");
    public static final DeferredItem<Item> VEELA_HAIR =
            ITEMS.registerSimpleItem("veela_hair");
    public static final DeferredItem<Item> TROLL_WHISKER =
            ITEMS.registerSimpleItem("troll_whisker");
    public static final DeferredItem<Item> WAMPUS_CAT_HAIR =
            ITEMS.registerSimpleItem("wampus_cat_hair");
    public static final DeferredItem<Item> THUNDERBIRD_TAIL_FEATHER =
            ITEMS.registerSimpleItem("thunderbird_tail_feather");
    public static final DeferredItem<Item> ROUGAROU_HAIR =
            ITEMS.registerSimpleItem("rougarou_hair");
    public static final DeferredItem<Item> WHITE_RIVER_MONSTER_SPINE =
            ITEMS.registerSimpleItem("white_river_monster_spine");

    // --- Marauder's Map ---

    public static final DeferredItem<MaraudersMapItem> MARAUDERS_MAP =
            ITEMS.registerItem("marauders_map", props -> new MaraudersMapItem(props.stacksTo(1)));

    public static final DeferredItem<BestiaryItem> BESTIARY =
            ITEMS.registerItem("bestiary", props -> new BestiaryItem(props.stacksTo(1)));

    public static final DeferredItem<ParchmentItem> PARCHMENT =
            ITEMS.registerItem("parchment", ParchmentItem::new);

    public static final DeferredItem<InkItem> INK_BOTTLE =
            ITEMS.registerItem("ink_bottle", InkItem::new);

    // --- Currency ---

    public static final DeferredItem<CoinItem> KNUT =
            ITEMS.registerItem("knut", props -> new CoinItem(props, "knut"));
    public static final DeferredItem<CoinItem> SICKLE =
            ITEMS.registerItem("sickle", props -> new CoinItem(props, "sickle"));
    public static final DeferredItem<CoinItem> GALLEON =
            ITEMS.registerItem("galleon", props -> new CoinItem(props, "galleon"));
    public static final DeferredItem<CounterfeitGalleonItem> COUNTERFEIT_GALLEON =
            ITEMS.registerItem("counterfeit_galleon", props -> new CounterfeitGalleonItem(props.stacksTo(64)));
    public static final DeferredItem<LeprechaunGoldItem> LEPRECHAUN_GOLD =
            ITEMS.registerItem("leprechaun_gold", LeprechaunGoldItem::new);
    public static final DeferredItem<Item> DRAGOT =
            ITEMS.registerSimpleItem("dragot");

    // --- Brewing pillar (data-driven via at.koopro.wizardsandbeasts.brew.*) ---

    /**
     * Generic potion-bottle item. The actual brew is identified by a
     * {@link at.koopro.wizardsandbeasts.registry.ModDataComponents#BREW_ID} data component
     * on the {@link net.minecraft.world.item.ItemStack}. Use
     * {@link BrewItem#of(at.koopro.wizardsandbeasts.brew.Brew)} to build a stack.
     */
    public static final DeferredItem<BrewItem> BREW =
            ITEMS.registerItem("brew", props -> new BrewItem(props.stacksTo(16)));

    // --- Wizarding World: food & drink (instant use; see WizardingQuickConsumableItem) ---

    public static final DeferredItem<WizardingQuickConsumableItem> BUTTERBEER =
            ITEMS.registerItem("butterbeer",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.3f, 28, ItemUseAnimation.DRINK, 10,
                            new MobEffectInstance(MobEffects.REGENERATION, 160, 0)));
    public static final DeferredItem<ConjuredSpoiledFoodItem> CONJURED_SPOILED_FOOD =
            ITEMS.registerItem("conjured_spoiled_food", props -> new ConjuredSpoiledFoodItem(props.stacksTo(1)));

    public static final DeferredItem<WizardingQuickConsumableItem> PUMPKIN_JUICE =
            ITEMS.registerItem("pumpkin_juice",
                    props -> new WizardingQuickConsumableItem(props, 4, 0.6f, 28, ItemUseAnimation.DRINK, 0));

    public static final DeferredItem<ChocolateFrogItem> CHOCOLATE_FROG =
            ITEMS.registerItem("chocolate_frog", ChocolateFrogItem::new);

    public static final DeferredItem<FamousWizardCardItem> FAMOUS_WIZARD_CARD =
            ITEMS.registerItem("famous_wizard_card", props -> new FamousWizardCardItem(props.stacksTo(16)));

    public static final DeferredItem<BertieBottsBeansItem> BERTIE_BOTTS_EVERY_FLAVOUR_BEANS =
            ITEMS.registerItem("bertie_botts_every_flavour_beans", BertieBottsBeansItem::new);

    public static final DeferredItem<WizardingQuickConsumableItem> DROOBLES_BEST_BLOWING_GUM =
            ITEMS.registerItem("droobles_best_blowing_gum",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.1f, 16, ItemUseAnimation.EAT, 8,
                            new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0)));

    public static final DeferredItem<WizardingQuickConsumableItem> FIREWHISKY =
            ITEMS.registerItem("firewhisky",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.05f, 30, ItemUseAnimation.DRINK, 20,
                            new MobEffectInstance(MobEffects.STRENGTH, 160, 0),
                            new MobEffectInstance(MobEffects.BLINDNESS, 100, 0)));

    public static final DeferredItem<WizardingQuickConsumableItem> GILLYWEED =
            ITEMS.registerItem("gillyweed",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.1f, 20, ItemUseAnimation.EAT, 15,
                            new MobEffectInstance(MobEffects.WATER_BREATHING, 800, 0)));

    public static final DeferredItem<WizardingQuickConsumableItem> DIRIGIBLE_PLUM =
            ITEMS.registerItem("dirigible_plum",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.25f, 16, ItemUseAnimation.EAT, 12,
                            new MobEffectInstance(MobEffects.LEVITATION, 40, 0)));

    public static final DeferredItem<WizardingQuickConsumableItem> TREACLE_TART =
            ITEMS.registerItem("treacle_tart",
                    props -> new WizardingQuickConsumableItem(props, 7, 0.9f, 24, ItemUseAnimation.EAT, 0));

    public static final DeferredItem<WizardingQuickConsumableItem> PUMPKIN_PASTY =
            ITEMS.registerItem("pumpkin_pasty",
                    props -> new WizardingQuickConsumableItem(props, 5, 0.6f, 20, ItemUseAnimation.EAT, 0));

    public static final DeferredItem<WizardingQuickConsumableItem> FIZZING_WHIZZBEE =
            ITEMS.registerItem("fizzing_whizzbee",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.3f, 16, ItemUseAnimation.EAT, 10,
                            new MobEffectInstance(MobEffects.JUMP_BOOST, 240, 0)));

    public static final DeferredItem<WizardingQuickConsumableItem> PEPPERMINT_TOAD =
            ITEMS.registerItem("peppermint_toad",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.3f, 16, ItemUseAnimation.EAT, 10,
                            new MobEffectInstance(MobEffects.SPEED, 200, 0)));

    public static final DeferredItem<DittanyItem> DITTANY =
            ITEMS.registerItem("dittany", DittanyItem::new);

    // --- Wizarding World: magizoology & materials ---

    public static final DeferredItem<Item> OCCAMY_EGGSHELL =
            ITEMS.registerSimpleItem("occamy_eggshell");
    public static final DeferredItem<BezoarItem> BEZOAR =
            ITEMS.registerItem("bezoar", props -> new BezoarItem(props.stacksTo(16)));
    public static final DeferredItem<Item> DEMIGUISE_HAIR =
            ITEMS.registerSimpleItem("demiguise_hair");
    public static final DeferredItem<MooncalfDungItem> MOONCALF_DUNG =
            ITEMS.registerItem("mooncalf_dung", MooncalfDungItem::new);
    public static final DeferredItem<WizardingProjectileItem> ERUMPENT_HORN =
            ITEMS.registerItem("erumpent_horn", props -> new WizardingProjectileItem(props.stacksTo(16)));
    public static final DeferredItem<Item> MANDRAKE =
            ITEMS.registerSimpleItem("mandrake");

    // --- Wizarding World: gear ---

    public static final DeferredItem<RemembrallItem> REMEMBRALL =
            ITEMS.registerItem("remembrall", props -> new RemembrallItem(props.stacksTo(1)));
    public static final DeferredItem<OmniocularsItem> OMNI_OCULARS =
            ITEMS.registerItem("omnioculars", props -> new OmniocularsItem(props.stacksTo(1)));
    public static final DeferredItem<DeluminatorItem> DELUMINATOR =
            ITEMS.registerItem("deluminator", props -> new DeluminatorItem(props.stacksTo(1)));
    public static final DeferredItem<TimeTurnerItem> TIME_TURNER =
            ITEMS.registerItem("time_turner", props -> new TimeTurnerItem(props.stacksTo(1)));
    public static final DeferredItem<Item> SNEAKOSCOPE =
            ITEMS.registerItem("sneakoscope", props -> new Item(props.stacksTo(1)));
    public static final DeferredItem<PortkeyItem> PORTKEY =
            ITEMS.registerItem("portkey", props -> new PortkeyItem(props.stacksTo(1)));
    public static final DeferredItem<EnchantedTrunkItem> ENCHANTED_TRUNK =
            ITEMS.registerItem("enchanted_trunk", props -> new EnchantedTrunkItem(TrunkTier.TIER_1, props.stacksTo(1)));
    public static final DeferredItem<EnchantedTrunkItem> EXPANDED_TRUNK =
            ITEMS.registerItem("expanded_trunk", props -> new EnchantedTrunkItem(TrunkTier.TIER_2, props.stacksTo(1)));
    public static final DeferredItem<EnchantedTrunkItem> MASTERS_TRUNK =
            ITEMS.registerItem("masters_trunk", props -> new EnchantedTrunkItem(TrunkTier.TIER_3, props.stacksTo(1)));
    public static final DeferredItem<Item> MINISTRY_LICENSE_SCROLL =
            ITEMS.registerItem("ministry_license_scroll", props -> new Item(props.stacksTo(16)));
    public static final DeferredItem<CloakItem> INVISIBILITY_CLOAK =
            ITEMS.registerItem("invisibility_cloak",
                    props -> new CloakItem(props.stacksTo(1).equippable(EquipmentSlot.CHEST), false));
    public static final DeferredItem<CloakItem> DEATHLY_HALLOW_CLOAK =
            ITEMS.registerItem("deathly_hallow_cloak",
                    props -> new CloakItem(props.stacksTo(1).equippable(EquipmentSlot.CHEST), true));

    // --- Wizarding World: pranks & misc ---

    public static final DeferredItem<WizardingProjectileItem> PERUVIAN_DARKNESS_POWDER =
            ITEMS.registerItem("peruvian_instant_darkness_powder", props -> new WizardingProjectileItem(props.stacksTo(16)));
    public static final DeferredItem<WizardingProjectileItem> DECOY_DETONATOR =
            ITEMS.registerItem("decoy_detonator", props -> new WizardingProjectileItem(props.stacksTo(16)));
    public static final DeferredItem<ExtendableEarsItem> EXTENDABLE_EARS =
            ITEMS.registerItem("extendable_ears", ExtendableEarsItem::new);
    public static final DeferredItem<FlooPowderItem> FLOO_POWDER =
            ITEMS.registerItem("floo_powder", FlooPowderItem::new);

    // ── Deathly Hallows ──────────────────────────────────────────

    public static final DeferredItem<ResurrectionStoneItem> RESURRECTION_STONE =
            ITEMS.registerItem("resurrection_stone", props -> new ResurrectionStoneItem(props.stacksTo(1)));

    // ── Horcrux Vessels ──────────────────────────────────────────

    public static final DeferredItem<RiddlesDiaryItem> RIDDLES_DIARY =
            ITEMS.registerItem("riddles_diary", props -> new RiddlesDiaryItem(props.stacksTo(1)));

    public static final DeferredItem<GauntRingItem> MARVOLO_GAUNTS_RING =
            ITEMS.registerItem("marvolo_gaunts_ring", props -> new GauntRingItem(props.stacksTo(1)));

    public static final DeferredItem<SlytherinsLocketItem> SLYTHERINS_LOCKET =
            ITEMS.registerItem("slytherins_locket", props -> new SlytherinsLocketItem(props.stacksTo(1)));

    public static final DeferredItem<HufflepuffsCupItem> HUFFLEPUFFS_CUP =
            ITEMS.registerItem("hufflepuffs_cup", props -> new HufflepuffsCupItem(props.stacksTo(1)));

    public static final DeferredItem<RavenclawsDiademItem> RAVENCLAWS_DIADEM =
            ITEMS.registerItem("ravenclaws_diadem", props -> new RavenclawsDiademItem(props.stacksTo(1)));

    // ── Unique Artefacts ─────────────────────────────────────────

    public static final DeferredItem<PhilosophersStoneItem> PHILOSOPHERS_STONE =
            ITEMS.registerItem("philosophers_stone", props -> new PhilosophersStoneItem(props.stacksTo(1)));

    public static final DeferredItem<PensieveItem> PENSIEVE =
            ITEMS.registerItem("pensieve", props -> new PensieveItem(props.stacksTo(1)));

    public static final DeferredItem<TwoWayMirrorItem> TWO_WAY_MIRROR =
            ITEMS.registerItem("two_way_mirror", props -> new TwoWayMirrorItem(props.stacksTo(1)));

    public static final DeferredItem<HandOfGloryItem> HAND_OF_GLORY =
            ITEMS.registerItem("hand_of_glory", props -> new HandOfGloryItem(props.stacksTo(1)));

    public static final DeferredItem<DarkMarkItem> DARK_MARK_BRAND =
            ITEMS.registerItem("dark_mark_brand", props -> new DarkMarkItem(props.stacksTo(1)));

    // ── Containers ───────────────────────────────────────────────

    public static final DeferredItem<MoodysTrunkItem> MOODYS_TRUNK =
            ITEMS.registerItem("moodys_trunk", props -> new MoodysTrunkItem(props.stacksTo(1)));

    public static final DeferredItem<HermionesBagItem> HERMIONES_BEADED_BAG =
            ITEMS.registerItem("hermiones_beaded_bag", props -> new HermionesBagItem(props.stacksTo(1)));

    // ── Detection & Utility ──────────────────────────────────────

    public static final DeferredItem<FoeGlassItem> FOE_GLASS =
            ITEMS.registerItem("foe_glass", props -> new FoeGlassItem(props.stacksTo(1)));

    // ── Fantastic Beasts Era ─────────────────────────────────────

    public static final DeferredItem<BloodPactVialItem> BLOOD_PACT_VIAL =
            ITEMS.registerItem("blood_pact_vial", props -> new BloodPactVialItem(props.stacksTo(1)));

    public static final DeferredItem<NewtsCaseItem> NEWTS_CASE_ITEM =
            ITEMS.registerItem("newts_case_item", props -> new NewtsCaseItem(props.stacksTo(1)));

    // --- Spawn Eggs ---

    public static final DeferredItem<SpawnEggItem> GOBLIN_TELLER_SPAWN_EGG =
            ITEMS.registerItem("goblin_teller_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.GOBLIN_TELLER.get())));

    public static final DeferredItem<SpawnEggItem> NIFFLER_SPAWN_EGG =
            ITEMS.registerItem("niffler_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.NIFFLER.get())));

    // Wood set block items are registered via WoodSet.register() in ModBlocks
}
