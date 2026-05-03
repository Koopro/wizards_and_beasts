package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.BroomItem;
import at.koopro.wizardsandbeasts.item.DebugWandItem;
import at.koopro.wizardsandbeasts.item.InkItem;
import at.koopro.wizardsandbeasts.item.MaraudersMapItem;
import at.koopro.wizardsandbeasts.item.MorphWandItem;
import at.koopro.wizardsandbeasts.item.ParchmentItem;
import at.koopro.wizardsandbeasts.item.WandItem;
import at.koopro.wizardsandbeasts.item.WandBlankItem;
import at.koopro.wizardsandbeasts.item.WandCoreMaterialItem;
import at.koopro.wizardsandbeasts.item.currency.CoinItem;
import at.koopro.wizardsandbeasts.item.currency.LeprechaunGoldItem;
import at.koopro.wizardsandbeasts.item.wizarding.BertieBottsBeansItem;
import at.koopro.wizardsandbeasts.item.wizarding.BezoarItem;
import at.koopro.wizardsandbeasts.item.wizarding.BrewItem;
import at.koopro.wizardsandbeasts.item.wizarding.ChocolateFrogItem;
import at.koopro.wizardsandbeasts.item.wizarding.CloakItem;
import at.koopro.wizardsandbeasts.item.wizarding.DeluminatorItem;
import at.koopro.wizardsandbeasts.item.wizarding.DittanyItem;
import at.koopro.wizardsandbeasts.item.wizarding.ExtendableEarsItem;
import at.koopro.wizardsandbeasts.item.wizarding.FamousWizardCardItem;
import at.koopro.wizardsandbeasts.item.wizarding.FlooPowderItem;
import at.koopro.wizardsandbeasts.item.wizarding.MooncalfDungItem;
import at.koopro.wizardsandbeasts.item.wizarding.OmniocularsItem;
import at.koopro.wizardsandbeasts.item.wizarding.PortkeyItem;
import at.koopro.wizardsandbeasts.item.wizarding.RemembrallItem;
import at.koopro.wizardsandbeasts.item.wizarding.TimeTurnerItem;
import at.koopro.wizardsandbeasts.item.wizarding.WizardingProjectileItem;
import at.koopro.wizardsandbeasts.item.wizarding.WizardingQuickConsumableItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
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
            ITEMS.registerItem("debug_wand", DebugWandItem::new,
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<MorphWandItem> MORPH_WAND =
            ITEMS.registerItem("morph_wand", MorphWandItem::new,
                    new Item.Properties().stacksTo(1));

    // --- Broom ---

    public static final DeferredItem<BroomItem> BROOM_ITEM =
            ITEMS.registerItem("broom", BroomItem::new,
                    new Item.Properties().stacksTo(1));

    // --- Wand ---

    public static final DeferredItem<WandItem> WAND =
            ITEMS.registerItem("wand", WandItem::new,
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<WandBlankItem> WAND_BLANK =
            ITEMS.registerItem("wand_blank", WandBlankItem::new,
                    new Item.Properties().stacksTo(1));

    // --- Wand Core Materials ---

    public static final DeferredItem<WandCoreMaterialItem> PHOENIX_FEATHER =
            ITEMS.registerItem("phoenix_feather", props -> new WandCoreMaterialItem(props,
                    Component.literal("Source key: fawkes")), new Item.Properties());
    public static final DeferredItem<WandCoreMaterialItem> DRAGON_HEARTSTRING =
            ITEMS.registerItem("dragon_heartstring", props -> new WandCoreMaterialItem(props,
                    Component.literal("Source key: hungarian_horntail")), new Item.Properties());
    public static final DeferredItem<WandCoreMaterialItem> UNICORN_HAIR =
            ITEMS.registerItem("unicorn_hair", props -> new WandCoreMaterialItem(props,
                    Component.literal("Fragile core material. Handle with care.")), new Item.Properties());
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
            ITEMS.registerItem("marauders_map", MaraudersMapItem::new,
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<ParchmentItem> PARCHMENT =
            ITEMS.registerItem("parchment", ParchmentItem::new,
                    new Item.Properties());

    public static final DeferredItem<InkItem> INK_BOTTLE =
            ITEMS.registerItem("ink_bottle", InkItem::new,
                    new Item.Properties());

    // --- Currency ---

    public static final DeferredItem<CoinItem> KNUT =
            ITEMS.registerItem("knut", props -> new CoinItem(props, "knut"),
                    new Item.Properties());
    public static final DeferredItem<CoinItem> SICKLE =
            ITEMS.registerItem("sickle", props -> new CoinItem(props, "sickle"),
                    new Item.Properties());
    public static final DeferredItem<CoinItem> GALLEON =
            ITEMS.registerItem("galleon", props -> new CoinItem(props, "galleon"),
                    new Item.Properties());
    public static final DeferredItem<LeprechaunGoldItem> LEPRECHAUN_GOLD =
            ITEMS.registerItem("leprechaun_gold", LeprechaunGoldItem::new,
                    new Item.Properties());
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
            ITEMS.registerItem("brew", BrewItem::new,
                    new Item.Properties().stacksTo(16));

    // --- Wizarding World: food & drink (instant use; see WizardingQuickConsumableItem) ---

    public static final DeferredItem<WizardingQuickConsumableItem> BUTTERBEER =
            ITEMS.registerItem("butterbeer",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.3f, 28, ItemUseAnimation.DRINK, 10,
                            new MobEffectInstance(MobEffects.REGENERATION, 160, 0)),
                    new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> PUMPKIN_JUICE =
            ITEMS.registerItem("pumpkin_juice",
                    props -> new WizardingQuickConsumableItem(props, 4, 0.6f, 28, ItemUseAnimation.DRINK, 0),
                    new Item.Properties());

    public static final DeferredItem<ChocolateFrogItem> CHOCOLATE_FROG =
            ITEMS.registerItem("chocolate_frog", ChocolateFrogItem::new, new Item.Properties());

    public static final DeferredItem<FamousWizardCardItem> FAMOUS_WIZARD_CARD =
            ITEMS.registerItem("famous_wizard_card", FamousWizardCardItem::new, new Item.Properties().stacksTo(16));

    public static final DeferredItem<BertieBottsBeansItem> BERTIE_BOTTS_EVERY_FLAVOUR_BEANS =
            ITEMS.registerItem("bertie_botts_every_flavour_beans", BertieBottsBeansItem::new, new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> DROOBLES_BEST_BLOWING_GUM =
            ITEMS.registerItem("droobles_best_blowing_gum",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.1f, 16, ItemUseAnimation.EAT, 8,
                            new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0)),
                    new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> FIREWHISKY =
            ITEMS.registerItem("firewhisky",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.05f, 30, ItemUseAnimation.DRINK, 20,
                            new MobEffectInstance(MobEffects.STRENGTH, 160, 0),
                            new MobEffectInstance(MobEffects.BLINDNESS, 100, 0)),
                    new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> GILLYWEED =
            ITEMS.registerItem("gillyweed",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.1f, 20, ItemUseAnimation.EAT, 15,
                            new MobEffectInstance(MobEffects.WATER_BREATHING, 800, 0)),
                    new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> DIRIGIBLE_PLUM =
            ITEMS.registerItem("dirigible_plum",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.25f, 16, ItemUseAnimation.EAT, 12,
                            new MobEffectInstance(MobEffects.LEVITATION, 40, 0)),
                    new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> TREACLE_TART =
            ITEMS.registerItem("treacle_tart",
                    props -> new WizardingQuickConsumableItem(props, 7, 0.9f, 24, ItemUseAnimation.EAT, 0),
                    new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> PUMPKIN_PASTY =
            ITEMS.registerItem("pumpkin_pasty",
                    props -> new WizardingQuickConsumableItem(props, 5, 0.6f, 20, ItemUseAnimation.EAT, 0),
                    new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> FIZZING_WHIZZBEE =
            ITEMS.registerItem("fizzing_whizzbee",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.3f, 16, ItemUseAnimation.EAT, 10,
                            new MobEffectInstance(MobEffects.JUMP_BOOST, 240, 0)),
                    new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> PEPPERMINT_TOAD =
            ITEMS.registerItem("peppermint_toad",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.3f, 16, ItemUseAnimation.EAT, 10,
                            new MobEffectInstance(MobEffects.SPEED, 200, 0)),
                    new Item.Properties());

    public static final DeferredItem<DittanyItem> DITTANY =
            ITEMS.registerItem("dittany", DittanyItem::new, new Item.Properties());

    // --- Wizarding World: magizoology & materials ---

    public static final DeferredItem<Item> OCCAMY_EGGSHELL =
            ITEMS.registerSimpleItem("occamy_eggshell");
    public static final DeferredItem<BezoarItem> BEZOAR =
            ITEMS.registerItem("bezoar", BezoarItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> DEMIGUISE_HAIR =
            ITEMS.registerSimpleItem("demiguise_hair");
    public static final DeferredItem<MooncalfDungItem> MOONCALF_DUNG =
            ITEMS.registerItem("mooncalf_dung", MooncalfDungItem::new, new Item.Properties());
    public static final DeferredItem<WizardingProjectileItem> ERUMPENT_HORN =
            ITEMS.registerItem("erumpent_horn", WizardingProjectileItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> MANDRAKE =
            ITEMS.registerSimpleItem("mandrake");

    // --- Wizarding World: gear ---

    public static final DeferredItem<RemembrallItem> REMEMBRALL =
            ITEMS.registerItem("remembrall", RemembrallItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<OmniocularsItem> OMNI_OCULARS =
            ITEMS.registerItem("omnioculars", OmniocularsItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<DeluminatorItem> DELUMINATOR =
            ITEMS.registerItem("deluminator", DeluminatorItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<TimeTurnerItem> TIME_TURNER =
            ITEMS.registerItem("time_turner", TimeTurnerItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> SNEAKOSCOPE =
            ITEMS.registerSimpleItem("sneakoscope", new Item.Properties().stacksTo(1));
    public static final DeferredItem<PortkeyItem> PORTKEY =
            ITEMS.registerItem("portkey", PortkeyItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<CloakItem> INVISIBILITY_CLOAK =
            ITEMS.registerItem("invisibility_cloak",
                    props -> new CloakItem(props.stacksTo(1).equippable(EquipmentSlot.CHEST), false), new Item.Properties());
    public static final DeferredItem<CloakItem> DEATHLY_HALLOW_CLOAK =
            ITEMS.registerItem("deathly_hallow_cloak",
                    props -> new CloakItem(props.stacksTo(1).equippable(EquipmentSlot.CHEST), true), new Item.Properties());

    // --- Wizarding World: pranks & misc ---

    public static final DeferredItem<WizardingProjectileItem> PERUVIAN_DARKNESS_POWDER =
            ITEMS.registerItem("peruvian_instant_darkness_powder", WizardingProjectileItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<WizardingProjectileItem> DECOY_DETONATOR =
            ITEMS.registerItem("decoy_detonator", WizardingProjectileItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<ExtendableEarsItem> EXTENDABLE_EARS =
            ITEMS.registerItem("extendable_ears", ExtendableEarsItem::new, new Item.Properties());
    public static final DeferredItem<FlooPowderItem> FLOO_POWDER =
            ITEMS.registerItem("floo_powder", FlooPowderItem::new, new Item.Properties());

    // --- Spawn Eggs ---

    public static final DeferredItem<SpawnEggItem> GOBLIN_TELLER_SPAWN_EGG =
            ITEMS.registerItem("goblin_teller_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.GOBLIN_TELLER.get())));

    public static final DeferredItem<SpawnEggItem> NIFFLER_SPAWN_EGG =
            ITEMS.registerItem("niffler_spawn_egg",
                    properties -> new SpawnEggItem(properties.spawnEgg(ModEntities.NIFFLER.get())));

    // Wood set block items are registered via WoodSet.register() in ModBlocks
}
