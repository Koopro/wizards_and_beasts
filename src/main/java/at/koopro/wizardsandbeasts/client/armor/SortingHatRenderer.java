package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.artefact.gryffindor.SortingHatItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * The Sorting Hat, worn. Head slot only, like the wizard's hat.
 *
 * <pre>
 *   model      geckolib/models/armor/sorting_hat.geo.json
 *   animation  geckolib/animations/armor/sorting_hat.animation.json
 *   texture    textures/armor/sorting_hat.png
 * </pre>
 */
public class SortingHatRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends WizardArmorRenderer<SortingHatItem, R> {

    public static final String ASSET_NAME = "sorting_hat";

    public SortingHatRenderer() {
        super(ASSET_NAME);
    }
}
