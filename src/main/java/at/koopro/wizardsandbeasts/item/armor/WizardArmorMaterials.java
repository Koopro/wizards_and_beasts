package at.koopro.wizardsandbeasts.item.armor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.EnumMap;
import java.util.Map;

/**
 * Armour materials for the wizarding wardrobe, in the 1.21.11 shape.
 *
 * <p>{@code ArmorItem} no longer exists — since 1.21.5 an armour piece is a plain {@link
 * net.minecraft.world.item.Item} whose {@code Properties} were run through {@code humanoidArmor()},
 * which derives durability, the ARMOR/ARMOR_TOUGHNESS/KNOCKBACK_RESISTANCE attribute modifiers, the
 * enchantability, the {@code EQUIPPABLE} component and the repair tag from an {@link ArmorMaterial}
 * record. Nothing here is a {@code Holder<ArmorMaterial>}: the record is passed by value and only
 * the equip sound is a holder.
 *
 * <p>Each material names a {@link EquipmentAsset} key in the mod's own namespace, which is what the
 * body renderer looks up to find the worn layer. Those assets ({@code assets/wizards_and_beasts/
 * equipment/*.json}) are not authored yet; an unknown key resolves to the manager's MISSING info, so
 * the piece equips and grants its stats but draws nothing on the body — deliberate until the
 * GeckoLib layer lands.
 */
public final class WizardArmorMaterials {

    /** Hogwarts school robe. Leather-grade protection, but half of leather's already-short life. */
    public static final ArmorMaterial STUDENT_ROBE = new ArmorMaterial(
            3,
            defense(1, 2, 3, 1),
            15,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            0.0F,
            0.0F,
            ItemTags.REPAIRS_LEATHER_ARMOR,
            asset("student_robe"));

    /** Auror field robe: dragonhide-lined duelling wear, iron-grade, repaired with iron. */
    public static final ArmorMaterial AUROR_ROBE = new ArmorMaterial(
            15,
            defense(2, 5, 6, 2),
            10,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            0.0F,
            0.0F,
            ItemTags.REPAIRS_IRON_ARMOR,
            asset("auror_robe"));

    /**
     * Death Eater robe. Same iron-grade plating as the Auror's, but the dark enchantments on it
     * bite deeper: a point of toughness and a sliver of knockback resistance the Auror kit lacks.
     */
    public static final ArmorMaterial DEATH_EATER_ROBE = new ArmorMaterial(
            15,
            defense(2, 5, 6, 2),
            12,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            1.0F,
            0.05F,
            ItemTags.REPAIRS_IRON_ARMOR,
            asset("death_eater_robe"));

    /** Pointed felt hat. Worn for the look, not the cover — one point of armour and little else. */
    public static final ArmorMaterial WIZARD_HAT = new ArmorMaterial(
            2,
            helmetOnly(1),
            15,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            0.0F,
            0.0F,
            ItemTags.REPAIRS_LEATHER_ARMOR,
            asset("wizard_hat"));

    /** Wrought silver mask. Metal, so it clanks on equip and takes iron to beat back into shape. */
    public static final ArmorMaterial DEATH_EATER_MASK = new ArmorMaterial(
            12,
            helmetOnly(2),
            10,
            SoundEvents.ARMOR_EQUIP_IRON,
            0.5F,
            0.0F,
            ItemTags.REPAIRS_IRON_ARMOR,
            asset("death_eater_mask"));

    private WizardArmorMaterials() {}

    /**
     * Defense points per slot, in vanilla's boots/leggings/chestplate/helmet order.
     *
     * <p>{@link ArmorType#BODY} is left out on purpose — it is the wolf/horse slot, and
     * {@code ArmorMaterial#createAttributes} reads the map with {@code getOrDefault(type, 0)}, so an
     * absent key is simply zero rather than a lookup failure.
     */
    private static Map<ArmorType, Integer> defense(int boots, int leggings, int chestplate, int helmet) {
        EnumMap<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
        map.put(ArmorType.BOOTS, boots);
        map.put(ArmorType.LEGGINGS, leggings);
        map.put(ArmorType.CHESTPLATE, chestplate);
        map.put(ArmorType.HELMET, helmet);
        return map;
    }

    /** Headwear-only material: every other slot is worth nothing because no such piece exists. */
    private static Map<ArmorType, Integer> helmetOnly(int helmet) {
        EnumMap<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
        map.put(ArmorType.HELMET, helmet);
        return map;
    }

    /**
     * An equipment-asset key in the mod's namespace.
     *
     * <p>{@code EquipmentAssets.createId} exists but hardcodes the {@code minecraft} namespace, so
     * it cannot be reused here.
     */
    private static ResourceKey<EquipmentAsset> asset(String name) {
        return ResourceKey.create(EquipmentAssets.ROOT_ID,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, name));
    }
}
