package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.WizardHatItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Pointed hat. Head slot only, so the model needs no bone but {@code armorHead}.
 *
 * <pre>
 *   model      geckolib/models/armor/wizard_hat.geo.json
 *   animation  geckolib/animations/armor/wizard_hat.animation.json
 *   texture    textures/armor/wizard_hat.png
 * </pre>
 */
public class WizardHatRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends WizardArmorRenderer<WizardHatItem, R> {

    public static final String ASSET_NAME = "wizard_hat";

    public WizardHatRenderer() {
        super(ASSET_NAME);
    }
}
