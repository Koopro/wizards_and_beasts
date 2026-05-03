package at.koopro.wizardsandbeasts.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Placeholder cube geometry for the centaur (QUADRUPED) form.
 * Human torso mounted on a horse body with four legs.
 */
public final class CentaurModel {

    public static final int COLOR = 0xFF886644; // tan

    private final ModelPart root;

    public CentaurModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        // Human upper body — mounted above horse body front
        rootDef.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.offset(0, -10, -6));
        rootDef.addOrReplaceChild("torso",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, 0, -2, 8, 12, 4),
                PartPose.offset(0, -10, -6));
        rootDef.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3, -2, -2, 4, 12, 4),
                PartPose.offset(-5, -8, -6));
        rootDef.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, -2, -2, 4, 12, 4),
                PartPose.offset(5, -8, -6));

        // Horse body
        rootDef.addOrReplaceChild("horse_body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5, -2, -8, 10, 8, 20),
                PartPose.ZERO);

        // Four legs
        rootDef.addOrReplaceChild("front_right_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, 0, -2, 4, 18, 4),
                PartPose.offset(-4, 6, -6));
        rootDef.addOrReplaceChild("front_left_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, 0, -2, 4, 18, 4),
                PartPose.offset(4, 6, -6));
        rootDef.addOrReplaceChild("back_right_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, 0, -2, 4, 18, 4),
                PartPose.offset(-4, 6, 8));
        rootDef.addOrReplaceChild("back_left_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, 0, -2, 4, 18, 4),
                PartPose.offset(4, 6, 8));

        rootDef.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, 0, 2, 2, 6),
                PartPose.offset(0, -1, 12));

        this.root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay) {
        root.render(poseStack, consumer, light, overlay, COLOR);
    }
}
