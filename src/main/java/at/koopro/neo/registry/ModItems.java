package at.koopro.neo.registry;

import at.koopro.neo.Neo;
import at.koopro.neo.item.BroomItem;
import at.koopro.neo.item.DebugWandItem;
import at.koopro.neo.item.InkItem;
import at.koopro.neo.item.MaraudersMapItem;
import at.koopro.neo.item.MorphWandItem;
import at.koopro.neo.item.ParchmentItem;
import at.koopro.neo.item.WandItem;
import at.koopro.neo.item.currency.CoinItem;
import at.koopro.neo.item.currency.LeprechaunGoldItem;
import at.koopro.neo.item.wizarding.BertieBottsBeansItem;
import at.koopro.neo.item.wizarding.BezoarItem;
import at.koopro.neo.item.wizarding.BrewItem;
import at.koopro.neo.item.wizarding.ChocolateFrogItem;
import at.koopro.neo.item.wizarding.DeluminatorItem;
import at.koopro.neo.item.wizarding.DittanyItem;
import at.koopro.neo.item.wizarding.ExtendableEarsItem;
import at.koopro.neo.item.wizarding.FamousWizardCardItem;
import at.koopro.neo.item.wizarding.FlooPowderItem;
import at.koopro.neo.item.wizarding.MooncalfDungItem;
import at.koopro.neo.item.wizarding.OmniocularsItem;
import at.koopro.neo.item.wizarding.PortkeyItem;
import at.koopro.neo.item.wizarding.RemembrallItem;
import at.koopro.neo.item.wizarding.WizardingProjectileItem;
import at.koopro.neo.item.wizarding.WizardingQuickConsumableItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Neo.MODID);

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

    // --- Wand Core Materials ---

    public static final DeferredItem<Item> PHOENIX_FEATHER =
            ITEMS.registerSimpleItem("phoenix_feather");
    public static final DeferredItem<Item> DRAGON_HEARTSTRING =
            ITEMS.registerSimpleItem("dragon_heartstring");
    public static final DeferredItem<Item> UNICORN_HAIR =
            ITEMS.registerSimpleItem("unicorn_hair");
    public static final DeferredItem<Item> THESTRAL_TAIL_HAIR =
            ITEMS.registerSimpleItem("thestral_tail_hair");

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

    // --- Brewing pillar (data-driven via at.koopro.neo.brew.*) ---

    /**
     * Generic potion-bottle item. The actual brew is identified by a
     * {@link at.koopro.neo.registry.ModDataComponents#BREW_ID} data component
     * on the {@link net.minecraft.world.item.ItemStack}. Use
     * {@link BrewItem#of(at.koopro.neo.brew.Brew)} to build a stack.
     */
    public static final DeferredItem<BrewItem> BREW =
            ITEMS.registerItem("brew", BrewItem::new,
                    new Item.Properties().stacksTo(16));

    // --- Wizarding World: food & drink (instant use; see WizardingQuickConsumableItem) ---

    public static final DeferredItem<WizardingQuickConsumableItem> BUTTERBEER =
            ITEMS.registerItem("butterbeer",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.15f,
                            new MobEffectInstance(MobEffects.REGENERATION, 100, 0)),
                    new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> PUMPKIN_JUICE =
            ITEMS.registerItem("pumpkin_juice",
                    props -> new WizardingQuickConsumableItem(props, 3, 1.2f),
                    new Item.Properties());

    public static final DeferredItem<ChocolateFrogItem> CHOCOLATE_FROG =
            ITEMS.registerItem("chocolate_frog", ChocolateFrogItem::new, new Item.Properties());

    public static final DeferredItem<FamousWizardCardItem> FAMOUS_WIZARD_CARD =
            ITEMS.registerItem("famous_wizard_card", FamousWizardCardItem::new, new Item.Properties().stacksTo(16));

    public static final DeferredItem<BertieBottsBeansItem> BERTIE_BOTTS_EVERY_FLAVOUR_BEANS =
            ITEMS.registerItem("bertie_botts_every_flavour_beans", BertieBottsBeansItem::new, new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> DROOBLES_BEST_BLOWING_GUM =
            ITEMS.registerItem("droobles_best_blowing_gum",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.1f,
                            new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0)),
                    new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> FIREWHISKY =
            ITEMS.registerItem("firewhisky",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.05f,
                            new MobEffectInstance(MobEffects.STRENGTH, 200, 0),
                            new MobEffectInstance(MobEffects.BLINDNESS, 200, 0)),
                    new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> GILLYWEED =
            ITEMS.registerItem("gillyweed",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.1f,
                            new MobEffectInstance(MobEffects.WATER_BREATHING, 600, 0)),
                    new Item.Properties());

    public static final DeferredItem<WizardingQuickConsumableItem> DIRIGIBLE_PLUM =
            ITEMS.registerItem("dirigible_plum",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.25f,
                            new MobEffectInstance(MobEffects.LEVITATION, 60, 0)),
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
    public static final DeferredItem<Item> SNEAKOSCOPE =
            ITEMS.registerSimpleItem("sneakoscope", new Item.Properties().stacksTo(1));
    public static final DeferredItem<PortkeyItem> PORTKEY =
            ITEMS.registerItem("portkey", PortkeyItem::new, new Item.Properties().stacksTo(1));

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
