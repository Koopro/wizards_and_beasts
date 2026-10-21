package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.item.armor.AurorRobeItem;
import at.koopro.wizardsandbeasts.item.armor.DeathEaterMaskItem;
import at.koopro.wizardsandbeasts.item.armor.DeathEaterRobeItem;
import at.koopro.wizardsandbeasts.item.armor.StudentRobeItem;
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
            DEATH_EATER_MASK, DEATH_EATER_MASK_1, DEATH_EATER_MASK_2,
            DEATH_EATER_MASK_3, DEATH_EATER_MASK_4, DEATH_EATER_MASK_5);

    private ArmorItemRegistry() {}

    public static void init() {}
}
