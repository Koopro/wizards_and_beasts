package at.koopro.wizardsandbeasts.item.armor;

import net.minecraft.world.item.equipment.ArmorType;

/** Ministry Auror field robe: the Department of Magical Law Enforcement's duelling kit. */
public class AurorRobeItem extends WizardArmorItem {

    public AurorRobeItem(Properties properties, ArmorType armorType) {
        super(properties, WizardArmorMaterials.AUROR_ROBE, armorType);
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.AurorRobeRenderer";
    }
}
