package at.koopro.wizardsandbeasts.item.armor;

import net.minecraft.world.item.equipment.ArmorType;

/** Death Eater robe — the Auror kit's mirror, traded for toughness bought with dark enchantment. */
public class DeathEaterRobeItem extends WizardArmorItem {

    public DeathEaterRobeItem(Properties properties, ArmorType armorType) {
        super(properties, WizardArmorMaterials.DEATH_EATER_ROBE, armorType);
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.DeathEaterRobeRenderer";
    }
}
