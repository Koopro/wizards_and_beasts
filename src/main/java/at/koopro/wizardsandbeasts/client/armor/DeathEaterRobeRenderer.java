package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.DeathEaterRobeItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Death Eater robe — chest, legs and boots off one model.
 *
 * <pre>
 *   model      geckolib/models/armor/death_eater_robe.geo.json
 *   animation  geckolib/animations/armor/death_eater_robe.animation.json
 *   texture    textures/armor/death_eater_robe.png
 * </pre>
 *
 * <p>The only hooded set: the model carries {@code robeHood} and {@code robeHoodDown}, and
 * {@link HoodedArmorRenderer} shows whichever the worn stack asks for.
 */
public class DeathEaterRobeRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends HoodedArmorRenderer<DeathEaterRobeItem, R> {

    public static final String ASSET_NAME = "death_eater_robe";

    public DeathEaterRobeRenderer() {
        super(ASSET_NAME);
    }
}
