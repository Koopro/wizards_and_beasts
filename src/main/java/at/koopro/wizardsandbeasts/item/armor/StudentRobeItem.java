package at.koopro.wizardsandbeasts.item.armor;

import net.minecraft.world.item.equipment.ArmorType;

/**
 * Plain Hogwarts school robe. No house lock and no gate of any kind — this is the set a first-year
 * is expected to own, so every piece of it is freely craftable and freely wearable.
 *
 * <p>Chest, legs and boots are three items sharing one renderer and one model; the house-coloured
 * variants, when they land, are a texture swap inside {@code StudentRobeRenderer} rather than more
 * items here.
 */
public class StudentRobeItem extends WizardArmorItem {

    public StudentRobeItem(Properties properties, ArmorType armorType) {
        super(properties, WizardArmorMaterials.STUDENT_ROBE, armorType);
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.StudentRobeRenderer";
    }
}
