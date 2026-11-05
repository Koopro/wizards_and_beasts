package at.koopro.wizardsandbeasts.item.armor;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

/**
 * Everyday Robe: worn open over a waistcoat and shirt, a cloth sash at the waist, calf length.
 * A body piece only (WARDROBE_RENAISSANCE §B6, C4) — trousers and shoes are whatever the wearer has.
 * Default livery {@code slate}; {@code moss} and later regional colourways are liveries of this cut.
 */
public class WizardRobeItem extends WizardArmorItem {

    public WizardRobeItem(Properties properties) {
        super(properties, WizardArmorMaterials.STUDENT_ROBE, ArmorType.CHESTPLATE, Wear.CURIO);
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.WizardRobeRenderer";
    }

    @Override
    public @Nullable Identifier defaultLivery() {
        return bodyLivery("slate");
    }
}
