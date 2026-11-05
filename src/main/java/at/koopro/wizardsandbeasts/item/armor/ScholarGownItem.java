package at.koopro.wizardsandbeasts.item.armor;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

/**
 * Scholar's Gown: floor length and open over a waistcoat, a velvet yoke, the hood worn down and
 * lined in the faculty colour, wide wrist bells. A body piece only (WARDROBE_RENAISSANCE §B6, C6).
 * Rank 1 adds the chain of office (Professor). Default livery {@code faculty}.
 */
public class ScholarGownItem extends WizardArmorItem {

    public ScholarGownItem(Properties properties) {
        super(properties, WizardArmorMaterials.STUDENT_ROBE, ArmorType.CHESTPLATE, Wear.CURIO);
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.ScholarGownRenderer";
    }

    @Override
    public @Nullable Identifier defaultLivery() {
        return bodyLivery("faculty");
    }
}
