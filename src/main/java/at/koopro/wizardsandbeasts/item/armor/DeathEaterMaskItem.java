package at.koopro.wizardsandbeasts.item.armor;

import net.minecraft.world.item.equipment.ArmorType;

/**
 * Wrought silver Death Eater mask.
 *
 * <p>Every mask is the same piece of armour and the same model; the variant only picks which face it
 * wears. It is a field on the item rather than a data component because the set is fixed at
 * registration — six distinct items, one per casting — and the renderer is built once per item, so
 * the variant can be baked into the renderer's texture path instead of being resolved per frame.
 */
public class DeathEaterMaskItem extends WizardArmorItem {

    /** The undecorated mask every Death Eater is issued. */
    public static final int BASE_VARIANT = 0;

    private final int variant;

    public DeathEaterMaskItem(Properties properties, int variant) {
        super(properties, WizardArmorMaterials.DEATH_EATER_MASK, ArmorType.HELMET);
        this.variant = variant;
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.DeathEaterMaskRenderer";
    }

    @Override
    protected Class<?>[] rendererArgTypes() {
        return new Class<?>[] { int.class };
    }

    @Override
    protected Object[] rendererArgs() {
        return new Object[] { variant };
    }
}
