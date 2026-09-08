package at.koopro.wizardsandbeasts.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The chocolate frog itself — a moulded confection, not a live amphibian.
 *
 * <p>Model space here is the usual entity convention: y counts <em>downward</em>, so the more
 * negative a coordinate the higher it sits, and the renderer applies the {@code scale(-1, -1, 1)}
 * flip that makes that true. The root pivot is on the ground between the frog's feet, which is what
 * lets the renderer squash and stretch it about its feet rather than about its middle.
 *
 * <p>Proportions are deliberately confectionery: the eyes are 2x2x2 on a 5-wide head and the
 * haunches are nearly as deep as the body. A chocolatier moulds the frog people picture, and a
 * frog people picture is mostly eyes and legs.
 *
 * <p>The {@code texOffs} below are the box-UV nets {@code tools/chocolate_frog.py} paints, in the
 * same order. Move a box and the atlas has to move with it.
 */
public final class ChocolateFrogModel {

    /** How far the back legs kick out at the top of a hop, radians. */
    private static final float BACK_LEG_KICK = 1.05f;
    /** How far the front legs tuck under mid-air, radians. */
    private static final float FRONT_LEG_TUCK = -0.85f;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart throat;
    private final ModelPart frontLeft;
    private final ModelPart frontRight;
    private final ModelPart backLeft;
    private final ModelPart backRight;

    public ChocolateFrogModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        // Barrel: wider than it is tall, and longer than it is wide.
        rootDef.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.5f, -6.0f, -4.0f, 5, 3, 7),
                PartPose.ZERO);

        // Head hinges at the shoulders so it can dip on landing.
        PartDefinition headDef = rootDef.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 12).addBox(-2.5f, -2.0f, -3.0f, 5, 3, 3),
                PartPose.offset(0.0f, -4.5f, -4.0f));
        // Two separate bulges rather than one brow: a moulded frog's eyes stand proud of the skull.
        headDef.addOrReplaceChild("eye_left",
                CubeListBuilder.create().texOffs(18, 12).addBox(0.6f, -3.5f, -2.5f, 2, 2, 2),
                PartPose.ZERO);
        headDef.addOrReplaceChild("eye_right",
                CubeListBuilder.create().texOffs(18, 12).addBox(-2.6f, -3.5f, -2.5f, 2, 2, 2),
                PartPose.ZERO);
        // The throat pouch is the only part that moves while the frog is sitting still.
        headDef.addOrReplaceChild("throat",
                CubeListBuilder.create().texOffs(0, 20).addBox(-1.5f, 0.0f, -2.5f, 3, 1, 2),
                PartPose.offset(0.0f, 0.6f, 0.0f));

        rootDef.addOrReplaceChild("front_left",
                CubeListBuilder.create().texOffs(12, 20).addBox(-0.5f, 0.0f, -0.5f, 1, 3, 1),
                PartPose.offset(2.0f, -3.2f, -3.0f));
        rootDef.addOrReplaceChild("front_right",
                CubeListBuilder.create().texOffs(12, 20).addBox(-0.5f, 0.0f, -0.5f, 1, 3, 1),
                PartPose.offset(-2.0f, -3.2f, -3.0f));
        // Haunches: folded under the frog at rest, thrown straight back at the top of a hop.
        rootDef.addOrReplaceChild("back_left",
                CubeListBuilder.create().texOffs(18, 20).addBox(-1.0f, 0.0f, -1.0f, 2, 3, 3),
                PartPose.offset(2.2f, -3.4f, 1.5f));
        rootDef.addOrReplaceChild("back_right",
                CubeListBuilder.create().texOffs(18, 20).addBox(-1.0f, 0.0f, -1.0f, 2, 3, 3),
                PartPose.offset(-2.2f, -3.4f, 1.5f));

        // Baked once here rather than through a ModelLayerLocation: nothing else needs this
        // geometry, and the escaped frog is the only thing that draws it.
        this.root = LayerDefinition.create(mesh, 32, 32).bakeRoot();
        this.head = root.getChild("head");
        this.throat = head.getChild("throat");
        this.frontLeft = root.getChild("front_left");
        this.frontRight = root.getChild("front_right");
        this.backLeft = root.getChild("back_left");
        this.backRight = root.getChild("back_right");
    }

    /**
     * Poses the frog for one frame.
     *
     * @param airborne  0 while sitting, 1 at the top of a hop — the renderer's smoothed
     *                  off-the-ground signal, not a raw {@code onGround} flag, so the legs do not
     *                  snap between poses on a bumpy landing
     * @param ageTicks  entity age plus partial tick, for the idle throat pulse
     */
    public void setup(float airborne, float ageTicks) {
        float a = Mth.clamp(airborne, 0.0f, 1.0f);
        backLeft.xRot = BACK_LEG_KICK * a;
        backRight.xRot = BACK_LEG_KICK * a;
        frontLeft.xRot = FRONT_LEG_TUCK * a;
        frontRight.xRot = FRONT_LEG_TUCK * a;
        // Head lifts into the jump and dips as the frog settles.
        head.xRot = -0.35f * a;
        // Throat only pulses on the ground; mid-air the frog is holding its breath, so to speak.
        throat.yScale = 1.0f + (1.0f - a) * 0.30f * (1.0f + Mth.sin(ageTicks * 0.35f)) * 0.5f;
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int colour) {
        root.render(poseStack, consumer, light, overlay, colour);
    }
}
