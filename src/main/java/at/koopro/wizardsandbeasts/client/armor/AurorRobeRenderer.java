package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.AurorRobeItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Auror field robe — chest, legs and boots off one model.
 *
 * <pre>
 *   model      geckolib/models/armor/auror_robe.geo.json
 *   animation  geckolib/animations/armor/auror_robe.animation.json
 *   texture    textures/armor/auror_robe.png
 * </pre>
 */
public class AurorRobeRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends WizardArmorRenderer<AurorRobeItem, R> {

    public static final String ASSET_NAME = "auror_robe";

    public AurorRobeRenderer() {
        super(ASSET_NAME);
    }
}
