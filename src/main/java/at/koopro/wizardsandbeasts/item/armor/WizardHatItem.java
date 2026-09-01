package at.koopro.wizardsandbeasts.item.armor;

import net.minecraft.world.item.equipment.ArmorType;

/** Neutral pointed hat. Head slot, house-agnostic, and barely armour at all. */
public class WizardHatItem extends WizardArmorItem {

    public WizardHatItem(Properties properties) {
        super(properties, WizardArmorMaterials.WIZARD_HAT, ArmorType.HELMET);
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.WizardHatRenderer";
    }
}
