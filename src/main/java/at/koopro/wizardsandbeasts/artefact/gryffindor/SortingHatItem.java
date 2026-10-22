package at.koopro.wizardsandbeasts.artefact.gryffindor;

import at.koopro.wizardsandbeasts.item.armor.WizardArmorItem;
import at.koopro.wizardsandbeasts.item.armor.WizardArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * The Sorting Hat, worn: Godric Gryffindor's own hat, patched and frayed. A head piece like the wizard's hat (worn in
 * the head slot or the Curios head slot) -- and the only way the Sword of Gryffindor comes to anyone
 * ({@link SwordPresentation}).
 */
public class SortingHatItem extends WizardArmorItem {

    public SortingHatItem(Properties properties) {
        super(properties, WizardArmorMaterials.SORTING_HAT, ArmorType.HELMET, Wear.CURIO);
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.SortingHatRenderer";
    }
}
