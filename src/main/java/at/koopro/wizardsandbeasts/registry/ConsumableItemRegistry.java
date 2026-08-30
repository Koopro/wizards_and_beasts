package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.item.consumable.PeppermintToadItem;
import at.koopro.wizardsandbeasts.item.consumable.FizzingWhizzbeeItem;
import at.koopro.wizardsandbeasts.item.consumable.PumpkinPastyItem;
import at.koopro.wizardsandbeasts.item.consumable.TreacleTartItem;
import at.koopro.wizardsandbeasts.item.consumable.DirigiblePlumItem;
import at.koopro.wizardsandbeasts.item.consumable.GillyweedItem;
import at.koopro.wizardsandbeasts.item.consumable.FirewhiskyItem;
import at.koopro.wizardsandbeasts.item.consumable.DrooblesGumItem;
import at.koopro.wizardsandbeasts.item.consumable.PumpkinJuiceItem;
import at.koopro.wizardsandbeasts.item.consumable.EmptyButterbeerMugItem;
import at.koopro.wizardsandbeasts.item.consumable.ButterbeerItem;
import at.koopro.wizardsandbeasts.item.consumable.GoldenSnidgetFeatherItem;
import at.koopro.wizardsandbeasts.item.consumable.MandrakeItem;
import at.koopro.wizardsandbeasts.item.consumable.DemiguiseHairItem;
import at.koopro.wizardsandbeasts.item.consumable.OccamyEggshellItem;
import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.item.consumable.BertieBottsBeansItem;
import at.koopro.wizardsandbeasts.item.consumable.BezoarItem;
import at.koopro.wizardsandbeasts.item.consumable.ChocolateFrogItem;
import at.koopro.wizardsandbeasts.item.consumable.DittanyItem;
import at.koopro.wizardsandbeasts.item.consumable.MooncalfDungItem;
import at.koopro.wizardsandbeasts.item.projectile.WizardingProjectileItem;
import at.koopro.wizardsandbeasts.item.spell.gamp.ConjuredSpoiledFoodItem;
import at.koopro.wizardsandbeasts.item.trinket.FamousWizardCardItem;
import net.minecraft.world.item.Item;
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

    /** Its own class now: the effects depend on when you last drank and the mug comes back. */
    public static final DeferredItem<ButterbeerItem> BUTTERBEER =
            ModItems.ITEMS.registerItem("butterbeer", props -> new ButterbeerItem(props.stacksTo(16)));

    /** Refillable at anything tagged {@code butterbeer_source}. */
    public static final DeferredItem<EmptyButterbeerMugItem> EMPTY_BUTTERBEER_MUG =
            ModItems.ITEMS.registerItem("empty_butterbeer_mug",
                    props -> new EmptyButterbeerMugItem(props.stacksTo(16)));
    public static final DeferredItem<ConjuredSpoiledFoodItem> CONJURED_SPOILED_FOOD =
            ModItems.ITEMS.registerItem("conjured_spoiled_food", props -> new ConjuredSpoiledFoodItem(props.stacksTo(1)));

    /** The school drink, as against Butterbeer's pub one. Its own class for the same reasons. */
    public static final DeferredItem<PumpkinJuiceItem> PUMPKIN_JUICE =
            ModItems.ITEMS.registerItem("pumpkin_juice", props -> new PumpkinJuiceItem(props.stacksTo(16)));

    public static final DeferredItem<ChocolateFrogItem> CHOCOLATE_FROG =
            ModItems.ITEMS.registerItem("chocolate_frog", ChocolateFrogItem::new);

    public static final DeferredItem<FamousWizardCardItem> FAMOUS_WIZARD_CARD =
            ModItems.ITEMS.registerItem("famous_wizard_card", props -> new FamousWizardCardItem(props.stacksTo(16)));

    public static final DeferredItem<BertieBottsBeansItem> BERTIE_BOTTS_EVERY_FLAVOUR_BEANS =
            ModItems.ITEMS.registerItem("bertie_botts_every_flavour_beans", BertieBottsBeansItem::new);

    /** Its own class: the bubble has a ceiling, a held-jump drift and three ways to pop. */
    public static final DeferredItem<DrooblesGumItem> DROOBLES_BEST_BLOWING_GUM =
            ModItems.ITEMS.registerItem("droobles_best_blowing_gum", DrooblesGumItem::new);

    /** Its own class: courage, a burn that pays for it, and an escalating bill. */
    public static final DeferredItem<FirewhiskyItem> FIREWHISKY =
            ModItems.ITEMS.registerItem("firewhisky", props -> new FirewhiskyItem(props.stacksTo(16)));

    /** Its own class: the transformation, the warning, and the no-stacking rule. */
    public static final DeferredItem<GillyweedItem> GILLYWEED =
            ModItems.ITEMS.registerItem("gillyweed", props -> new GillyweedItem(props.stacksTo(16)));

    /** Its own class: the bob, the sight, and the muddle that pays for it. */
    public static final DeferredItem<DirigiblePlumItem> DIRIGIBLE_PLUM =
            ModItems.ITEMS.registerItem("dirigible_plum", props -> new DirigiblePlumItem(props.stacksTo(16)));

    /** Harry's favourite, and the mod's best plain meal. */
    public static final DeferredItem<TreacleTartItem> TREACLE_TART =
            ModItems.ITEMS.registerItem("treacle_tart", TreacleTartItem::new);

    /** Trolley food. Worth a little more while you are travelling. */
    public static final DeferredItem<PumpkinPastyItem> PUMPKIN_PASTY =
            ModItems.ITEMS.registerItem("pumpkin_pasty", PumpkinPastyItem::new);

    /** Its own class: the float and the pop, not just Jump Boost. */
    public static final DeferredItem<FizzingWhizzbeeItem> FIZZING_WHIZZBEE =
            ModItems.ITEMS.registerItem("fizzing_whizzbee", FizzingWhizzbeeItem::new);

    /** Novelty candy: it keeps hopping, and the mint settles you by one step. */
    public static final DeferredItem<PeppermintToadItem> PEPPERMINT_TOAD =
            ModItems.ITEMS.registerItem("peppermint_toad", PeppermintToadItem::new);

    /** Applied to a wound, yours or somebody else's. Still the Wiggenweld ingredient. */
    public static final DeferredItem<DittanyItem> DITTANY =
            ModItems.ITEMS.registerItem("dittany", props -> new DittanyItem(props.stacksTo(16)));

    // --- Wizarding World: magizoology & materials ---

    /**
     * Places as a small ornament, refines a silver brew when thrown into a working cauldron, and
     * shatters if you land badly holding it. Sixteen to a stack: they are thin and they take room.
     */
    public static final DeferredItem<OccamyEggshellItem> OCCAMY_EGGSHELL =
            ModItems.ITEMS.registerItem("occamy_eggshell",
                    props -> new OccamyEggshellItem(ModBlocks.OCCAMY_EGGSHELL.get(), props.stacksTo(16)));
    public static final DeferredItem<BezoarItem> BEZOAR =
            ModItems.ITEMS.registerItem("bezoar", props -> new BezoarItem(props.stacksTo(16)));
    /**
     * Carries {@code provides_trim_material} so a smithing table can weave it into armour: that
     * component is how vanilla resolves which trim an addition item applies, so declaring it here is
     * the whole of what makes Demiguise Weave craftable.
     */
    public static final DeferredItem<DemiguiseHairItem> DEMIGUISE_HAIR =
            ModItems.ITEMS.registerItem("demiguise_hair", props -> new DemiguiseHairItem(
                    props.component(net.minecraft.core.component.DataComponents.PROVIDES_TRIM_MATERIAL,
                            new net.minecraft.world.item.component.ProvidesTrimMaterial(
                                    at.koopro.wizardsandbeasts.demiguise.Demiguise.WEAVE_MATERIAL))));
    public static final DeferredItem<MooncalfDungItem> MOONCALF_DUNG =
            ModItems.ITEMS.registerItem("mooncalf_dung", MooncalfDungItem::new);
    public static final DeferredItem<WizardingProjectileItem> ERUMPENT_HORN =
            ModItems.ITEMS.registerItem("erumpent_horn", props -> new WizardingProjectileItem(props.stacksTo(16)));
    /** Replantable, and the reason earmuffs exist. */
    public static final DeferredItem<MandrakeItem> MANDRAKE =
            ModItems.ITEMS.registerItem("mandrake", MandrakeItem::new);

    /** Throwable. Shrieks where it lands — a smaller, shorter version of the harvest. */
    public static final DeferredItem<WizardingProjectileItem> BABY_MANDRAKE =
            ModItems.ITEMS.registerItem("baby_mandrake",
                    props -> new WizardingProjectileItem(props.stacksTo(16)));
    public static final DeferredItem<Item> ROUGAROU_HAIR =
            ModItems.ITEMS.registerSimpleItem("rougarou_hair");
    public static final DeferredItem<Item> WHITE_RIVER_MONSTER_SPINE =
            ModItems.ITEMS.registerSimpleItem("white_river_monster_spine");
    /** Roughly half an ender pearl's throw — see {@link WizardingProjectileItem}. */
    private static final float SHADOW_ESSENCE_VELOCITY = 0.75f;

    /**
     * Thrown short, it opens a pool of the creature's own concealment. Brewed with Demiguise hair it
     * becomes Shadow Form. Sixteen to a stack: a pool lasts twelve seconds and a pocketful of them
     * should not be a permanent one.
     */
    public static final DeferredItem<WizardingProjectileItem> HIDEBEHIND_SHADOW_ESSENCE =
            ModItems.ITEMS.registerItem("hidebehind_shadow_essence",
                    props -> new WizardingProjectileItem(props.stacksTo(16), SHADOW_ESSENCE_VELOCITY));
    public static final DeferredItem<Item> HIDEBEHIND_CLAW =
            ModItems.ITEMS.registerSimpleItem("hidebehind_claw");
    public static final DeferredItem<Item> GHOUL_SLIME =
            ModItems.ITEMS.registerSimpleItem("ghoul_slime");
    /**
     * Tail of a Firebolt-tier broom, or held in the off hand to fly one faster. Durable rather than
     * consumable, so it wears out over about twenty minutes aloft instead of vanishing mid-flight.
     */
    public static final DeferredItem<GoldenSnidgetFeatherItem> GOLDEN_SNIDGET_FEATHER =
            ModItems.ITEMS.registerItem("golden_snidget_feather",
                    props -> new GoldenSnidgetFeatherItem(
                            props.durability(at.koopro.wizardsandbeasts.broom.SnidgetFeather.DURABILITY)));
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
