package at.koopro.wizardsandbeasts.item.armor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the wizarding wardrobe is real armour rather than a costume.
 *
 * <p>Worth a test because none of it is legible in the source: 1.21.11 has no {@code ArmorItem} to
 * read numbers off, and a piece's protection comes from a {@link ArmorMaterial#defense()} lookup
 * keyed by {@link ArmorType}. A map missing the slot a piece was registered into is not a compile
 * error and not a crash — {@code createAttributes} reads it with {@code getOrDefault(type, 0)}, so
 * the robe would simply grant nothing, and would look exactly like a working one until someone was
 * hit while wearing it.
 *
 * <p>Asserted against the materials rather than the items: {@code Properties#humanoidArmor} calls
 * {@code repairable(TagKey)}, which reaches for a bootstrap registration lookup and throws
 * "Registry is already frozen" outside the registration phase. Armour can therefore only be built
 * while the item registry is open — true in game, impossible in a unit test. Everything the mod
 * itself decides lives in the material record, and that is what is checked here.
 */
class WizardArmorMaterialsTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        // Attributes.ARMOR and the ItemTags constants both reach BuiltInRegistries.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // -- protection ------------------------------------------------------------------------------

    @Test
    void theStudentSetIsLeatherGrade() {
        assertEquals(3.0, armor(WizardArmorMaterials.STUDENT_ROBE, ArmorType.CHESTPLATE));
        assertEquals(2.0, armor(WizardArmorMaterials.STUDENT_ROBE, ArmorType.LEGGINGS));
        assertEquals(1.0, armor(WizardArmorMaterials.STUDENT_ROBE, ArmorType.BOOTS));
    }

    @Test
    void theAurorSetIsIronGrade() {
        assertEquals(6.0, armor(WizardArmorMaterials.AUROR_ROBE, ArmorType.CHESTPLATE));
        assertEquals(5.0, armor(WizardArmorMaterials.AUROR_ROBE, ArmorType.LEGGINGS));
        assertEquals(2.0, armor(WizardArmorMaterials.AUROR_ROBE, ArmorType.BOOTS));
    }

    @Test
    void theDeathEaterRobeMatchesTheAurorRobeButIsTougher() {
        ArmorMaterial deathEater = WizardArmorMaterials.DEATH_EATER_ROBE;
        ArmorMaterial auror = WizardArmorMaterials.AUROR_ROBE;

        assertEquals(armor(auror, ArmorType.CHESTPLATE), armor(deathEater, ArmorType.CHESTPLATE));
        assertEquals(0.0F, auror.toughness());
        assertTrue(deathEater.toughness() > auror.toughness(),
                "death eater toughness " + deathEater.toughness() + " should beat " + auror.toughness());
        assertEquals(1.0, total(deathEater, ArmorType.CHESTPLATE, Attributes.ARMOR_TOUGHNESS));
        assertEquals(0.05, total(deathEater, ArmorType.CHESTPLATE, Attributes.KNOCKBACK_RESISTANCE), 1.0E-6);
    }

    @Test
    void headwearProtectsLittleAndTheMaskProtectsMoreThanTheHat() {
        assertEquals(1.0, armor(WizardArmorMaterials.WIZARD_HAT, ArmorType.HELMET));
        assertEquals(2.0, armor(WizardArmorMaterials.DEATH_EATER_MASK, ArmorType.HELMET));
        assertEquals(0.5, total(WizardArmorMaterials.DEATH_EATER_MASK, ArmorType.HELMET, Attributes.ARMOR_TOUGHNESS));
    }

    @Test
    void headwearGrantsNothingInTheSlotsItCannotOccupy() {
        // helmetOnly() leaves the other keys out entirely — the guard is that the absent key reads
        // as zero rather than blowing up the attribute build.
        for (ArmorType type : ArmorType.values()) {
            if (type != ArmorType.HELMET) {
                assertEquals(0.0, armor(WizardArmorMaterials.WIZARD_HAT, type), type + " on a hat");
                assertEquals(0.0, armor(WizardArmorMaterials.DEATH_EATER_MASK, type), type + " on a mask");
            }
        }
    }

    // -- slots and wear --------------------------------------------------------------------------

    @Test
    void protectionIsScopedToTheSlotThePieceOccupies() {
        ItemAttributeModifiers modifiers = WizardArmorMaterials.STUDENT_ROBE.createAttributes(ArmorType.CHESTPLATE);
        assertTrue(modifiers.modifiers().stream()
                        .allMatch(entry -> entry.slot().test(EquipmentSlot.CHEST)),
                "a chest piece must not modify anything off the chest slot");
        assertTrue(modifiers.modifiers().stream()
                        .noneMatch(entry -> entry.slot().test(EquipmentSlot.MAINHAND)),
                "armour must grant nothing from the hand");
    }

    @Test
    void everyPieceWearsOutAndTheSchoolRobeWearsOutFastest() {
        int student = ArmorType.CHESTPLATE.getDurability(WizardArmorMaterials.STUDENT_ROBE.durability());
        int auror = ArmorType.CHESTPLATE.getDurability(WizardArmorMaterials.AUROR_ROBE.durability());
        int hat = ArmorType.HELMET.getDurability(WizardArmorMaterials.WIZARD_HAT.durability());

        assertTrue(student > 0 && hat > 0, "armour with no durability would be unbreakable");
        assertTrue(student < auror, "student robe " + student + " should be flimsier than auror robe " + auror);
    }

    // -- repair and sound ------------------------------------------------------------------------

    @Test
    void clothIsRepairedWithLeatherAndTheIronGradeSetsWithIron() {
        assertEquals(ItemTags.REPAIRS_LEATHER_ARMOR, WizardArmorMaterials.STUDENT_ROBE.repairIngredient());
        assertEquals(ItemTags.REPAIRS_LEATHER_ARMOR, WizardArmorMaterials.WIZARD_HAT.repairIngredient());
        assertEquals(ItemTags.REPAIRS_IRON_ARMOR, WizardArmorMaterials.AUROR_ROBE.repairIngredient());
        assertEquals(ItemTags.REPAIRS_IRON_ARMOR, WizardArmorMaterials.DEATH_EATER_ROBE.repairIngredient());
        assertEquals(ItemTags.REPAIRS_IRON_ARMOR, WizardArmorMaterials.DEATH_EATER_MASK.repairIngredient());
    }

    // -- equipment assets ------------------------------------------------------------------------

    @Test
    void everyMaterialNamesADistinctAssetInTheModsOwnNamespace() {
        ArmorMaterial[] all = all();
        for (ArmorMaterial material : all) {
            assertEquals(WizardsAndBeastsMod.MODID, material.assetId().identifier().getNamespace(),
                    material.assetId() + " must not fall back to the minecraft namespace");
        }
        long distinct = java.util.Arrays.stream(all).map(ArmorMaterial::assetId).distinct().count();
        assertEquals(all.length, distinct, "two materials sharing an asset id would render as each other");
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static ArmorMaterial[] all() {
        return new ArmorMaterial[]{
                WizardArmorMaterials.STUDENT_ROBE,
                WizardArmorMaterials.AUROR_ROBE,
                WizardArmorMaterials.DEATH_EATER_ROBE,
                WizardArmorMaterials.WIZARD_HAT,
                WizardArmorMaterials.DEATH_EATER_MASK};
    }

    private static double armor(ArmorMaterial material, ArmorType type) {
        return total(material, type, Attributes.ARMOR);
    }

    /** What the piece actually grants, read back off the attribute modifiers the game will apply. */
    private static double total(ArmorMaterial material, ArmorType type, Holder<Attribute> attribute) {
        return material.createAttributes(type).modifiers().stream()
                .filter(entry -> entry.attribute().value() == attribute.value())
                .mapToDouble(entry -> entry.modifier().amount())
                .sum();
    }
}
