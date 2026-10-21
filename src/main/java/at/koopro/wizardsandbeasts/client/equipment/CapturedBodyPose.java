package at.koopro.wizardsandbeasts.client.equipment;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.jspecify.annotations.NullMarked;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoArmorRenderer.ArmorSegment;

/**
 * The wearer's limb transforms, copied off the parent model at the moment a worn piece is submitted.
 *
 * <p><b>Why a copy.</b> In 1.21.11 a GeckoLib render pass computes its bone transforms lazily, when
 * the frame's submissions are flushed — long after the player renderer has posed its shared
 * {@code PlayerModel} for the next player on screen. GeckoLib's own armour path survives this by
 * re-running {@code setupAnim} at flush time from a full per-slot copy of the render state. A Curios
 * piece is drawn from {@code CuriosLayer}, where the parent model has <em>just</em> been posed for
 * this wearer ({@code LivingEntityRenderer#submit} calls {@code setupAnim} immediately before its
 * layers), so six part transforms are copied here instead — 36 floats, not a second render-state
 * extraction.
 *
 * <p>The parent is the very model vanilla armour is fitted to (a {@code PlayerModel} of the wearer's
 * own slim/classic type — see {@code AvatarRenderer}'s armour layer), so a robe body lines up with the
 * arms and torso exactly as the chestplate it replaces would, including the half-pixel lower shoulder
 * pivot of slim arms.
 */
@NullMarked
public record CapturedBodyPose(float[] values) {

    /** Carried on the piece's GeckoLib render state; its presence switches bone placement to it. */
    public static final DataTicket<CapturedBodyPose> TICKET =
            DataTicket.create("wizards_and_beasts_captured_body_pose", CapturedBodyPose.class);

    private static final int STRIDE = 6;

    public static CapturedBodyPose capture(HumanoidModel<?> model) {
        float[] values = new float[STRIDE * 6];
        write(values, 0, model.head);
        write(values, 1, model.body);
        write(values, 2, model.leftArm);
        write(values, 3, model.rightArm);
        write(values, 4, model.leftLeg);
        write(values, 5, model.rightLeg);
        return new CapturedBodyPose(values);
    }

    private static void write(float[] values, int part, ModelPart source) {
        int o = part * STRIDE;
        values[o] = source.x;
        values[o + 1] = source.y;
        values[o + 2] = source.z;
        values[o + 3] = source.xRot;
        values[o + 4] = source.yRot;
        values[o + 5] = source.zRot;
    }

    /** Index of the vanilla part a segment follows — feet ride the leg parts, as in GeckoLib. */
    private static int part(ArmorSegment segment) {
        return switch (segment) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEFT_ARM -> 2;
            case RIGHT_ARM -> 3;
            case LEFT_LEG, LEFT_FOOT -> 4;
            case RIGHT_LEG, RIGHT_FOOT -> 5;
        };
    }

    public float x(ArmorSegment segment) { return values[part(segment) * STRIDE]; }

    public float y(ArmorSegment segment) { return values[part(segment) * STRIDE + 1]; }

    public float z(ArmorSegment segment) { return values[part(segment) * STRIDE + 2]; }

    public float xRot(ArmorSegment segment) { return values[part(segment) * STRIDE + 3]; }

    public float yRot(ArmorSegment segment) { return values[part(segment) * STRIDE + 4]; }

    public float zRot(ArmorSegment segment) { return values[part(segment) * STRIDE + 5]; }
}
