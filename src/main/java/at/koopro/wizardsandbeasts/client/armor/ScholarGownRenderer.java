package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.ScholarGownItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** Scholar's Gown — {@code geckolib/models/armor/scholar_gown.geo.json}, sheets from {@code tools/wardrobe_model.py}. */
public class ScholarGownRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends WizardArmorRenderer<ScholarGownItem, R> {

    public static final String ASSET_NAME = "scholar_gown";

    public ScholarGownRenderer() {
        super(ASSET_NAME);
    }
}
