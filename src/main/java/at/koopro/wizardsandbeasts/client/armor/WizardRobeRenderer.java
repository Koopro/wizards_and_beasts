package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.WizardRobeItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** Everyday Robe — {@code geckolib/models/armor/wizard_robe.geo.json}, sheets from {@code tools/wardrobe_model.py}. */
public class WizardRobeRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends WizardArmorRenderer<WizardRobeItem, R> {

    public static final String ASSET_NAME = "wizard_robe";

    public WizardRobeRenderer() {
        super(ASSET_NAME);
    }
}
