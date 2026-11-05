package at.koopro.wizardsandbeasts.item.armor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

/**
 * A wardrobe accessory worn in its own Curios slot (WARDROBE_RENAISSANCE Phase E2): belts in
 * {@code belt}, gloves in {@code hands}, scarves in {@code necklace}, hats in {@code head} — the slot
 * comes from the item's {@code wearables} entry. Purely clothing: no stats.
 *
 * <p>One class and one {@code AccessoryRenderer} for every accessory cut: the model name, default
 * livery and the bone group it draws ({@link ArmorType#CHESTPLATE} for body and arms,
 * {@link ArmorType#HELMET} for the head) come in from the registry.
 */
public class AccessoryItem extends WizardArmorItem {

    private final String assetName;
    private final Identifier defaultLivery;

    public AccessoryItem(Properties properties, String assetName, String defaultLivery, ArmorType bones) {
        super(properties, bones == ArmorType.HELMET ? WizardArmorMaterials.WIZARD_HAT : WizardArmorMaterials.STUDENT_ROBE,
                bones, Wear.CURIO);
        this.assetName = assetName;
        this.defaultLivery = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, defaultLivery);
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.AccessoryRenderer";
    }

    @Override
    protected Class<?>[] rendererArgTypes() {
        return new Class<?>[]{String.class};
    }

    @Override
    protected Object[] rendererArgs() {
        return new Object[]{assetName};
    }

    @Override
    public @Nullable Identifier defaultLivery() {
        return defaultLivery;
    }
}
