package at.koopro.wizardsandbeasts.item.armor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

/**
 * A body-only robe cut worn in the Curios {@code body} slot (WARDROBE_RENAISSANCE Phase E3):
 * travelling coat, healer's coat, Ministry robe, master's mantle. The earlier cuts keep their own
 * classes (their ids and renderers predate the wardrobe); every cut added since is one of these,
 * with its model name and default livery from the registry and one {@code RobeCutRenderer}.
 */
public class RobeCutItem extends WizardArmorItem {

    private final String assetName;
    private final Identifier defaultLivery;

    public RobeCutItem(Properties properties, String assetName, String defaultLivery) {
        super(properties, WizardArmorMaterials.STUDENT_ROBE, ArmorType.CHESTPLATE, Wear.CURIO);
        this.assetName = assetName;
        this.defaultLivery = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, defaultLivery);
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.RobeCutRenderer";
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
