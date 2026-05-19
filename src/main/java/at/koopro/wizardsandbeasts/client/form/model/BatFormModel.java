package at.koopro.wizardsandbeasts.client.form.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Placeholder cube geometry for the bat/vampire bat (FLYING) form.
 * Tiny body with large flat wings.
 */
public final class BatFormModel {

    public static final int COLOR = 0xFF444455; // dark grey

    private final ModelPart root;

    public BatFormModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        // Small body positioned near ground level
        rootDef.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(0, 16, 0));
        rootDef.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, -4, -2, 4, 4, 4),
                PartPose.offset(0, 16, 0));
        rootDef.addOrReplaceChild("right_ear",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, -3, 0, 2, 3, 1),
                PartPose.offset(-2, 12, -1));
        rootDef.addOrReplaceChild("left_ear",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, -3, 0, 2, 3, 1),
                PartPose.offset(2, 12, -1));
        // Large wings extending from body sides
        rootDef.addOrReplaceChild("right_wing",
                CubeListBuilder.create().texOffs(0, 0).addBox(-12, -2, -1, 12, 8, 1),
                PartPose.offset(-2, 16, 0));
        rootDef.addOrReplaceChild("left_wing",
                CubeListBuilder.create().texOffs(0, 0).addBox(0, -2, -1, 12, 8, 1),
                PartPose.offset(2, 16, 0));
        // Tiny legs
        rootDef.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, -1, 2, 2, 2),
                PartPose.offset(-1, 22, 0));
        rootDef.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, -1, 2, 2, 2),
                PartPose.offset(1, 22, 0));

        this.root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay) {
        root.render(poseStack, consumer, light, overlay, COLOR);
    }
}
