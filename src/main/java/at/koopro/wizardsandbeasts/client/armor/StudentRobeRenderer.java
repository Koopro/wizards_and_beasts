package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.StudentRobeItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Hogwarts school robe — chest, legs and boots, all three drawn from one model.
 *
 * <pre>
 *   model      geckolib/models/armor/student_robe.geo.json
 *   animation  geckolib/animations/armor/student_robe.animation.json
 *   texture    textures/armor/student_robe.png            (neutral, no house)
 * </pre>
 *
 * <p>House-coloured variants are meant to land as {@code textures/armor/student_robe_gryffindor.png}
 * and siblings. They cannot be wired the way the mask castings are: a mask's face is a property of
 * the item, so its path can be fixed at construction, whereas a robe's house is a property of the
 * <em>wearer</em>, and one item has to draw four ways. That needs the house carried into the render
 * state and {@code getTextureResource(GeoRenderState)} overridden on the model to read it back —
 * per-frame, not per-item. Left undone until the textures exist.
 */
public class StudentRobeRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends WizardArmorRenderer<StudentRobeItem, R> {

    public static final String ASSET_NAME = "student_robe";

    public StudentRobeRenderer() {
        super(ASSET_NAME);
    }
}
