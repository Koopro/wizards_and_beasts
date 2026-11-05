package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.item.armor.AccessoryItem;
import at.koopro.wizardsandbeasts.item.armor.AurorRobeItem;
import at.koopro.wizardsandbeasts.item.armor.CapeItem;
import at.koopro.wizardsandbeasts.item.armor.DeathEaterMaskItem;
import at.koopro.wizardsandbeasts.item.armor.DeathEaterRobeItem;
import at.koopro.wizardsandbeasts.item.armor.StudentRobeItem;
import at.koopro.wizardsandbeasts.item.armor.WizardRobeItem;
import at.koopro.wizardsandbeasts.item.armor.RobeCutItem;
import at.koopro.wizardsandbeasts.item.armor.ScholarGownItem;
import at.koopro.wizardsandbeasts.item.armor.WizardArmorItem;
import at.koopro.wizardsandbeasts.item.armor.WizardHatItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.List;

/**
 * The wizarding wardrobe: robes, hats and masks.
 *
 * <p>Two kinds of piece share these GeckoLib models (see {@link WizardArmorItem.Wear}):
 * <ul>
 *   <li><b>Clothing</b> — each robe's {@code _chest} piece (the robe body, sleeves, skirt and hood),
 *       the wizard hat and every mask. Worn in Curios slots; what they grant and which armour they
 *       cover is data ({@code data/wizards_and_beasts/data_maps/item/wearables.json}). A netherite
 *       chestplate can be worn under a robe and keeps all of its protection.</li>
 *   <li><b>Armour</b> — each robe's {@code _legs} and {@code _boots}: real trousers and boots in the
 *       vanilla slots, with real ARMOR/ARMOR_TOUGHNESS modifiers, durability and repair.</li>
 * </ul>
 * The ids never changed, so stacks from before the split load as the same items; a robe body or hat
 * still sitting in an armour slot is moved into its Curios slot on login by
 * {@link at.koopro.wizardsandbeasts.equipment.EquipmentMigration}.
 *
 * <p>Nothing here is house- or allegiance-gated at registration. The student set is meant to be
 * free for everyone; the Auror and Death Eater sets are gated, if at all, by what can craft or drop
 * them, not by the item.
 */
public final class ArmorItemRegistry {

    // --- Student set ---------------------------------------------------------------
    // Free for everyone: no house lock, no module gate.

    public static final DeferredItem<StudentRobeItem> STUDENT_ROBE_CHEST =
            ModItems.ITEMS.registerItem("student_robe_chest", props -> new StudentRobeItem(props, ArmorType.CHESTPLATE));
    public static final DeferredItem<StudentRobeItem> STUDENT_ROBE_LEGS =
            ModItems.ITEMS.registerItem("student_robe_legs", props -> new StudentRobeItem(props, ArmorType.LEGGINGS));
    public static final DeferredItem<StudentRobeItem> STUDENT_ROBE_BOOTS =
            ModItems.ITEMS.registerItem("student_robe_boots", props -> new StudentRobeItem(props, ArmorType.BOOTS));

    /** Neutral pointed hat — not part of any set, wearable with all of them. */
    public static final DeferredItem<WizardHatItem> WIZARD_HAT =
            ModItems.ITEMS.registerItem("wizard_hat", WizardHatItem::new);

    // --- Auror set -----------------------------------------------------------------

    public static final DeferredItem<AurorRobeItem> AUROR_ROBE_CHEST =
            ModItems.ITEMS.registerItem("auror_robe_chest", props -> new AurorRobeItem(props, ArmorType.CHESTPLATE));
    public static final DeferredItem<AurorRobeItem> AUROR_ROBE_LEGS =
            ModItems.ITEMS.registerItem("auror_robe_legs", props -> new AurorRobeItem(props, ArmorType.LEGGINGS));
    public static final DeferredItem<AurorRobeItem> AUROR_ROBE_BOOTS =
            ModItems.ITEMS.registerItem("auror_robe_boots", props -> new AurorRobeItem(props, ArmorType.BOOTS));

    // --- Death Eater set -----------------------------------------------------------

    public static final DeferredItem<DeathEaterRobeItem> DEATH_EATER_ROBE_CHEST =
            ModItems.ITEMS.registerItem("death_eater_robe_chest", props -> new DeathEaterRobeItem(props, ArmorType.CHESTPLATE));
    public static final DeferredItem<DeathEaterRobeItem> DEATH_EATER_ROBE_LEGS =
            ModItems.ITEMS.registerItem("death_eater_robe_legs", props -> new DeathEaterRobeItem(props, ArmorType.LEGGINGS));
    public static final DeferredItem<DeathEaterRobeItem> DEATH_EATER_ROBE_BOOTS =
            ModItems.ITEMS.registerItem("death_eater_robe_boots", props -> new DeathEaterRobeItem(props, ArmorType.BOOTS));

    // --- Wardrobe cuts (body only) -----------------------------------------------------
    // WARDROBE_RENAISSANCE Phase C: worn in the Curios body slot over whatever the wearer has on.

    public static final DeferredItem<WizardRobeItem> WIZARD_ROBE_CHEST =
            ModItems.ITEMS.registerItem("wizard_robe_chest", WizardRobeItem::new);
    public static final DeferredItem<ScholarGownItem> SCHOLAR_GOWN_CHEST =
            ModItems.ITEMS.registerItem("scholar_gown_chest", ScholarGownItem::new);

    // --- Phase E3 body cuts ------------------------------------------------------------

    public static final DeferredItem<RobeCutItem> TRAVEL_COAT_CHEST = ModItems.ITEMS.registerItem("travel_coat_chest",
            props -> new RobeCutItem(props, "travel_coat", "canvas_umber"));
    public static final DeferredItem<RobeCutItem> HEALER_COAT_CHEST = ModItems.ITEMS.registerItem("healer_coat_chest",
            props -> new RobeCutItem(props, "healer_coat", "ward_green"));
    public static final DeferredItem<RobeCutItem> MINISTRY_ROBE_CHEST = ModItems.ITEMS.registerItem("ministry_robe_chest",
            props -> new RobeCutItem(props, "ministry_robe", "ministry_plum"));
    public static final DeferredItem<RobeCutItem> MASTER_MANTLE_CHEST = ModItems.ITEMS.registerItem("master_mantle_chest",
            props -> new RobeCutItem(props, "master_mantle", "master_crimson"));

    // --- Capes and cloaks (back slot) ---------------------------------------------------
    // WARDROBE_RENAISSANCE Phase E1: worn over a robe, swung by vanilla's own cape physics.

    public static final DeferredItem<CapeItem> SCHOOL_CAPE =
            ModItems.ITEMS.registerItem("school_cape", props -> new CapeItem(props, "school_cape", "school_plain"));
    public static final DeferredItem<CapeItem> TRAVEL_CLOAK =
            ModItems.ITEMS.registerItem("travel_cloak", props -> new CapeItem(props, "travel_cloak", "umber"));

    // --- Accessories (belt, hands, necklace, head slots) ---------------------------------
    // WARDROBE_RENAISSANCE Phase E2: cosmetic, each outside every robe cut's envelope.

    public static final DeferredItem<AccessoryItem> LEATHER_BELT = ModItems.ITEMS.registerItem("leather_belt",
            props -> new AccessoryItem(props, "leather_belt", "leather_brown", ArmorType.CHESTPLATE));
    public static final DeferredItem<AccessoryItem> DUTY_BELT = ModItems.ITEMS.registerItem("duty_belt",
            props -> new AccessoryItem(props, "duty_belt", "auror_office", ArmorType.CHESTPLATE));
    public static final DeferredItem<AccessoryItem> SCARF = ModItems.ITEMS.registerItem("scarf",
            props -> new AccessoryItem(props, "scarf", "school_plain", ArmorType.CHESTPLATE));
    public static final DeferredItem<AccessoryItem> GLOVES = ModItems.ITEMS.registerItem("gloves",
            props -> new AccessoryItem(props, "gloves", "dragonhide", ArmorType.CHESTPLATE));
    public static final DeferredItem<AccessoryItem> PROFESSOR_HAT = ModItems.ITEMS.registerItem("professor_hat",
            props -> new AccessoryItem(props, "professor_hat", "faculty", ArmorType.HELMET));
    public static final DeferredItem<AccessoryItem> TRAVEL_HAT = ModItems.ITEMS.registerItem("travel_hat",
            props -> new AccessoryItem(props, "travel_hat", "umber", ArmorType.HELMET));

    /** The plain issue mask. Statistically identical to the five castings below. */
    public static final DeferredItem<DeathEaterMaskItem> DEATH_EATER_MASK =
            ModItems.ITEMS.registerItem("death_eater_mask",
                    props -> new DeathEaterMaskItem(props, DeathEaterMaskItem.BASE_VARIANT));

    public static final DeferredItem<DeathEaterMaskItem> DEATH_EATER_MASK_1 =
            ModItems.ITEMS.registerItem("death_eater_mask_1", props -> new DeathEaterMaskItem(props, 1));
    public static final DeferredItem<DeathEaterMaskItem> DEATH_EATER_MASK_2 =
            ModItems.ITEMS.registerItem("death_eater_mask_2", props -> new DeathEaterMaskItem(props, 2));
    public static final DeferredItem<DeathEaterMaskItem> DEATH_EATER_MASK_3 =
            ModItems.ITEMS.registerItem("death_eater_mask_3", props -> new DeathEaterMaskItem(props, 3));
    public static final DeferredItem<DeathEaterMaskItem> DEATH_EATER_MASK_4 =
            ModItems.ITEMS.registerItem("death_eater_mask_4", props -> new DeathEaterMaskItem(props, 4));
    public static final DeferredItem<DeathEaterMaskItem> DEATH_EATER_MASK_5 =
            ModItems.ITEMS.registerItem("death_eater_mask_5", props -> new DeathEaterMaskItem(props, 5));

    /**
     * Every mask in variant order, base first.
     *
     * <p>Held here so the creative tab, and later the mask-selection UI, iterate one list instead of
     * each keeping a hand-copied roster that goes stale the moment a sixth casting is added.
     */
    public static final List<DeferredItem<DeathEaterMaskItem>> ALL_MASKS = List.of(
            DEATH_EATER_MASK,
            DEATH_EATER_MASK_1,
            DEATH_EATER_MASK_2,
            DEATH_EATER_MASK_3,
            DEATH_EATER_MASK_4,
            DEATH_EATER_MASK_5);

    /**
     * Every worn armour item, in set order.
     *
     * <p>Exists so the inventory-sprite check has a roster to walk. These items shipped for
     * weeks with worn models and no item icon at all — correct on the body, a missing model
     * in the hotbar — precisely because nothing enumerated them.
     */
    public static final List<DeferredItem<? extends WizardArmorItem>> ALL = List.of(
            STUDENT_ROBE_CHEST, STUDENT_ROBE_LEGS, STUDENT_ROBE_BOOTS, WIZARD_HAT,
            AUROR_ROBE_CHEST, AUROR_ROBE_LEGS, AUROR_ROBE_BOOTS,
            DEATH_EATER_ROBE_CHEST, DEATH_EATER_ROBE_LEGS, DEATH_EATER_ROBE_BOOTS,
            WIZARD_ROBE_CHEST, SCHOLAR_GOWN_CHEST, SCHOOL_CAPE, TRAVEL_CLOAK,
            TRAVEL_COAT_CHEST, HEALER_COAT_CHEST, MINISTRY_ROBE_CHEST, MASTER_MANTLE_CHEST,
            LEATHER_BELT, DUTY_BELT, SCARF, GLOVES, PROFESSOR_HAT, TRAVEL_HAT,
            DEATH_EATER_MASK, DEATH_EATER_MASK_1, DEATH_EATER_MASK_2,
            DEATH_EATER_MASK_3, DEATH_EATER_MASK_4, DEATH_EATER_MASK_5);

    private ArmorItemRegistry() {}

    public static void init() {}
}
