package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.item.consumable.BertieBottsBeansItem;
import at.koopro.wizardsandbeasts.item.consumable.BezoarItem;
import at.koopro.wizardsandbeasts.item.consumable.ChocolateFrogItem;
import at.koopro.wizardsandbeasts.item.consumable.DittanyItem;
import at.koopro.wizardsandbeasts.item.consumable.MooncalfDungItem;
import at.koopro.wizardsandbeasts.item.consumable.WizardingQuickConsumableItem;
import at.koopro.wizardsandbeasts.item.projectile.WizardingProjectileItem;
import at.koopro.wizardsandbeasts.item.spell.gamp.ConjuredSpoiledFoodItem;
import at.koopro.wizardsandbeasts.item.trinket.FamousWizardCardItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.neoforged.neoforge.registries.DeferredItem;

public final class ConsumableItemRegistry {

    /**
     * Generic potion-bottle item. The actual brew is identified by a
     * {@link at.koopro.wizardsandbeasts.registry.ModDataComponents#BREW_ID} data component
     * on the {@link net.minecraft.world.item.ItemStack}. Use
     * {@link BrewItem#of(at.koopro.wizardsandbeasts.brew.Brew)} to build a stack.
     */
    public static final DeferredItem<BrewItem> BREW =
            ModItems.ITEMS.registerItem("brew", props -> new BrewItem(props.stacksTo(16)));

    // --- Wizarding World: food & drink (instant use; see WizardingQuickConsumableItem) ---

    public static final DeferredItem<WizardingQuickConsumableItem> BUTTERBEER =
            ModItems.ITEMS.registerItem("butterbeer",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.3f, 28, ItemUseAnimation.DRINK, 10,
                            new MobEffectInstance(MobEffects.REGENERATION, 160, 0)));
    public static final DeferredItem<ConjuredSpoiledFoodItem> CONJURED_SPOILED_FOOD =
            ModItems.ITEMS.registerItem("conjured_spoiled_food", props -> new ConjuredSpoiledFoodItem(props.stacksTo(1)));

    public static final DeferredItem<WizardingQuickConsumableItem> PUMPKIN_JUICE =
            ModItems.ITEMS.registerItem("pumpkin_juice",
                    props -> new WizardingQuickConsumableItem(props, 4, 0.6f, 28, ItemUseAnimation.DRINK, 0));

    public static final DeferredItem<ChocolateFrogItem> CHOCOLATE_FROG =
            ModItems.ITEMS.registerItem("chocolate_frog", ChocolateFrogItem::new);

    public static final DeferredItem<FamousWizardCardItem> FAMOUS_WIZARD_CARD =
            ModItems.ITEMS.registerItem("famous_wizard_card", props -> new FamousWizardCardItem(props.stacksTo(16)));

    public static final DeferredItem<BertieBottsBeansItem> BERTIE_BOTTS_EVERY_FLAVOUR_BEANS =
            ModItems.ITEMS.registerItem("bertie_botts_every_flavour_beans", BertieBottsBeansItem::new);

    public static final DeferredItem<WizardingQuickConsumableItem> DROOBLES_BEST_BLOWING_GUM =
            ModItems.ITEMS.registerItem("droobles_best_blowing_gum",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.1f, 16, ItemUseAnimation.EAT, 8,
                            new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0)));

    public static final DeferredItem<WizardingQuickConsumableItem> FIREWHISKY =
            ModItems.ITEMS.registerItem("firewhisky",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.05f, 30, ItemUseAnimation.DRINK, 20,
                            new MobEffectInstance(MobEffects.STRENGTH, 160, 0),
                            new MobEffectInstance(MobEffects.BLINDNESS, 100, 0)));

    public static final DeferredItem<WizardingQuickConsumableItem> GILLYWEED =
            ModItems.ITEMS.registerItem("gillyweed",
                    props -> new WizardingQuickConsumableItem(props, 1, 0.1f, 20, ItemUseAnimation.EAT, 15,
                            new MobEffectInstance(MobEffects.WATER_BREATHING, 800, 0)));

    public static final DeferredItem<WizardingQuickConsumableItem> DIRIGIBLE_PLUM =
            ModItems.ITEMS.registerItem("dirigible_plum",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.25f, 16, ItemUseAnimation.EAT, 12,
                            new MobEffectInstance(MobEffects.LEVITATION, 40, 0)));

    public static final DeferredItem<WizardingQuickConsumableItem> TREACLE_TART =
            ModItems.ITEMS.registerItem("treacle_tart",
                    props -> new WizardingQuickConsumableItem(props, 7, 0.9f, 24, ItemUseAnimation.EAT, 0));

    public static final DeferredItem<WizardingQuickConsumableItem> PUMPKIN_PASTY =
            ModItems.ITEMS.registerItem("pumpkin_pasty",
                    props -> new WizardingQuickConsumableItem(props, 5, 0.6f, 20, ItemUseAnimation.EAT, 0));

    public static final DeferredItem<WizardingQuickConsumableItem> FIZZING_WHIZZBEE =
            ModItems.ITEMS.registerItem("fizzing_whizzbee",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.3f, 16, ItemUseAnimation.EAT, 10,
                            new MobEffectInstance(MobEffects.JUMP_BOOST, 240, 0)));

    public static final DeferredItem<WizardingQuickConsumableItem> PEPPERMINT_TOAD =
            ModItems.ITEMS.registerItem("peppermint_toad",
                    props -> new WizardingQuickConsumableItem(props, 2, 0.3f, 16, ItemUseAnimation.EAT, 10,
                            new MobEffectInstance(MobEffects.SPEED, 200, 0)));

    public static final DeferredItem<DittanyItem> DITTANY =
            ModItems.ITEMS.registerItem("dittany", DittanyItem::new);

    // --- Wizarding World: magizoology & materials ---

    public static final DeferredItem<Item> OCCAMY_EGGSHELL =
            ModItems.ITEMS.registerSimpleItem("occamy_eggshell");
    public static final DeferredItem<BezoarItem> BEZOAR =
            ModItems.ITEMS.registerItem("bezoar", props -> new BezoarItem(props.stacksTo(16)));
    public static final DeferredItem<Item> DEMIGUISE_HAIR =
            ModItems.ITEMS.registerSimpleItem("demiguise_hair");
    public static final DeferredItem<MooncalfDungItem> MOONCALF_DUNG =
            ModItems.ITEMS.registerItem("mooncalf_dung", MooncalfDungItem::new);
    public static final DeferredItem<WizardingProjectileItem> ERUMPENT_HORN =
            ModItems.ITEMS.registerItem("erumpent_horn", props -> new WizardingProjectileItem(props.stacksTo(16)));
    public static final DeferredItem<Item> MANDRAKE =
            ModItems.ITEMS.registerSimpleItem("mandrake");
    public static final DeferredItem<Item> ROUGAROU_HAIR =
            ModItems.ITEMS.registerSimpleItem("rougarou_hair");
    public static final DeferredItem<Item> WHITE_RIVER_MONSTER_SPINE =
            ModItems.ITEMS.registerSimpleItem("white_river_monster_spine");
    public static final DeferredItem<Item> HIDEBEHIND_SHADOW_ESSENCE =
            ModItems.ITEMS.registerSimpleItem("hidebehind_shadow_essence");
    public static final DeferredItem<Item> HIDEBEHIND_CLAW =
            ModItems.ITEMS.registerSimpleItem("hidebehind_claw");
    public static final DeferredItem<Item> GHOUL_SLIME =
            ModItems.ITEMS.registerSimpleItem("ghoul_slime");
    public static final DeferredItem<Item> GOLDEN_SNIDGET_FEATHER =
            ModItems.ITEMS.registerSimpleItem("golden_snidget_feather");
    public static final DeferredItem<Item> GRANIAN_HAIR =
            ModItems.ITEMS.registerSimpleItem("granian_hair");
    public static final DeferredItem<Item> HORNED_SERPENT_GEM =
            ModItems.ITEMS.registerSimpleItem("horned_serpent_gem");
    public static final DeferredItem<Item> PUKWUDGIE_VENOM_SAC =
            ModItems.ITEMS.registerSimpleItem("pukwudgie_venom_sac");
    public static final DeferredItem<Item> YETI_FUR =
            ModItems.ITEMS.registerSimpleItem("yeti_fur");
    public static final DeferredItem<Item> MATAGOT_ESSENCE =
            ModItems.ITEMS.registerSimpleItem("matagot_essence");

    private ConsumableItemRegistry() {}

    public static void init() {}
}
