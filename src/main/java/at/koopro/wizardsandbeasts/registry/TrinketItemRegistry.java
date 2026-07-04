package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.item.bloodpact.BloodPactVialItem;
import at.koopro.wizardsandbeasts.item.projectile.WizardingProjectileItem;
import at.koopro.wizardsandbeasts.item.trinket.DarkMarkItem;
import at.koopro.wizardsandbeasts.item.trinket.ExtendableEarsItem;
import at.koopro.wizardsandbeasts.item.trinket.FoeGlassItem;
import at.koopro.wizardsandbeasts.item.trinket.HandOfGloryItem;
import at.koopro.wizardsandbeasts.item.trinket.HermionesBagItem;
import at.koopro.wizardsandbeasts.item.trinket.OmniocularsItem;
import at.koopro.wizardsandbeasts.item.trinket.PensieveItem;
import at.koopro.wizardsandbeasts.item.trinket.PortkeyItem;
import at.koopro.wizardsandbeasts.item.trinket.RemembrallItem;
import at.koopro.wizardsandbeasts.item.trinket.TimeTurnerItem;
import at.koopro.wizardsandbeasts.item.trinket.TwoWayMirrorItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

public final class TrinketItemRegistry {

    public static final DeferredItem<RemembrallItem> REMEMBRALL =
            ModItems.ITEMS.registerItem("remembrall", props -> new RemembrallItem(props.stacksTo(1)));
    public static final DeferredItem<OmniocularsItem> OMNI_OCULARS =
            ModItems.ITEMS.registerItem("omnioculars", props -> new OmniocularsItem(props.stacksTo(1)));
    public static final DeferredItem<TimeTurnerItem> TIME_TURNER =
            ModItems.ITEMS.registerItem("time_turner", props -> new TimeTurnerItem(props.stacksTo(1)));
    public static final DeferredItem<Item> SNEAKOSCOPE =
            ModItems.ITEMS.registerItem("sneakoscope", props -> new Item(props.stacksTo(1)));
    public static final DeferredItem<PortkeyItem> PORTKEY =
            ModItems.ITEMS.registerItem("portkey", props -> new PortkeyItem(props.stacksTo(1)));
    public static final DeferredItem<Item> MINISTRY_LICENSE_SCROLL =
            ModItems.ITEMS.registerItem("ministry_license_scroll", props -> new Item(props.stacksTo(16)));
    public static final DeferredItem<ExtendableEarsItem> EXTENDABLE_EARS =
            ModItems.ITEMS.registerItem("extendable_ears", ExtendableEarsItem::new);
    public static final DeferredItem<FoeGlassItem> FOE_GLASS =
            ModItems.ITEMS.registerItem("foe_glass", props -> new FoeGlassItem(props.stacksTo(1)));
    public static final DeferredItem<DarkMarkItem> DARK_MARK_BRAND =
            ModItems.ITEMS.registerItem("dark_mark_brand", props -> new DarkMarkItem(props.stacksTo(1)));
    public static final DeferredItem<TwoWayMirrorItem> TWO_WAY_MIRROR =
            ModItems.ITEMS.registerItem("two_way_mirror", props -> new TwoWayMirrorItem(props.stacksTo(1)));
    public static final DeferredItem<HandOfGloryItem> HAND_OF_GLORY =
            ModItems.ITEMS.registerItem("hand_of_glory", props -> new HandOfGloryItem(props.stacksTo(1)));
    public static final DeferredItem<PensieveItem> PENSIEVE =
            ModItems.ITEMS.registerItem("pensieve", props -> new PensieveItem(props.stacksTo(1)));
    public static final DeferredItem<HermionesBagItem> HERMIONES_BEADED_BAG =
            ModItems.ITEMS.registerItem("hermiones_beaded_bag", props -> new HermionesBagItem(props.stacksTo(1)));
    public static final DeferredItem<BloodPactVialItem> BLOOD_PACT_VIAL =
            ModItems.ITEMS.registerItem("blood_pact_vial", props -> new BloodPactVialItem(props.stacksTo(1)));
    // Newt's Case is now a placed block — see ModBlocks.NEWTS_CASE / NEWTS_CASE_ITEM.
    public static final DeferredItem<WizardingProjectileItem> PERUVIAN_DARKNESS_POWDER =
            ModItems.ITEMS.registerItem("peruvian_instant_darkness_powder", props -> new WizardingProjectileItem(props.stacksTo(16)));
    public static final DeferredItem<WizardingProjectileItem> DECOY_DETONATOR =
            ModItems.ITEMS.registerItem("decoy_detonator", props -> new WizardingProjectileItem(props.stacksTo(16)));

    private TrinketItemRegistry() {}

    public static void init() {}
}
