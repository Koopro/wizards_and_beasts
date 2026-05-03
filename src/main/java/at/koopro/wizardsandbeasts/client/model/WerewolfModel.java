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
 * Placeholder cube geometry for the werewolf (CUSTOM_BIPED) form.
 * Bipedal wolf silhouette — hunched torso, elongated head, tail.
 */
public final class WerewolfModel {

    public static final int COLOR = 0xFF884422; // brown

    private final ModelPart root;

    public WerewolfModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        rootDef.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5, 0, -3, 10, 14, 6),
                PartPose.ZERO);
        rootDef.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3, -8, -6, 6, 6, 8),
                PartPose.ZERO);
        rootDef.addOrReplaceChild("snout",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -5, -10, 3, 3, 4),
                PartPose.ZERO);
        rootDef.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3, -2, -2, 4, 14, 4),
                PartPose.offset(-7, 2, 0));
        rootDef.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, -2, -2, 4, 14, 4),
                PartPose.offset(7, 2, 0));
        rootDef.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, 0, -2, 4, 10, 4),
                PartPose.offset(-2.5f, 14, 0));
        rootDef.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, 0, -2, 4, 10, 4),
                PartPose.offset(2.5f, 14, 0));
        rootDef.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, 0, 2, 2, 8),
                PartPose.offset(0, 4, 3));

        this.root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay) {
        root.render(poseStack, consumer, light, overlay, COLOR);
    }
}
