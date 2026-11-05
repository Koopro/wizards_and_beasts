package at.koopro.wizardsandbeasts.item.armor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

/**
 * A worn cape or cloak in the Curios {@code back} slot (WARDROBE_RENAISSANCE Phase E1): purely
 * clothing, no stats, no charges — the invisibility cloaks are {@code CloakItem}s and are not this.
 *
 * <p>One class for every cape cut: the model name and default livery come in from the registry, and
 * one {@code CapeRenderer} draws them all. Its body bones hang off {@code armorBody}, so the piece
 * reports {@link ArmorType#CHESTPLATE} only to pick that bone group.
 */
public class CapeItem extends WizardArmorItem {

    private final String assetName;
    private final Identifier defaultLivery;

    public CapeItem(Properties properties, String assetName, String defaultLivery) {
        super(properties, WizardArmorMaterials.STUDENT_ROBE, ArmorType.CHESTPLATE, Wear.CURIO);
        this.assetName = assetName;
        this.defaultLivery = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, defaultLivery);
    }

    @Override
    protected String rendererClassName() {
        return "at.koopro.wizardsandbeasts.client.armor.CapeRenderer";
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
