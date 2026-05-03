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
 * Placeholder cube geometry for the merfolk (SWIMMING) form.
 * Humanoid upper body with a fish tail replacing legs.
 */
public final class MerfolkSwimModel {

    public static final int COLOR = 0xFF2288AA; // teal

    private final ModelPart root;

    public MerfolkSwimModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        rootDef.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, 0, -2, 8, 12, 4),
                PartPose.ZERO);
        rootDef.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.ZERO);
        rootDef.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3, -2, -2, 4, 12, 4),
                PartPose.offset(-5, 2, 0));
        rootDef.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, -2, -2, 4, 12, 4),
                PartPose.offset(5, 2, 0));
        // Fish tail — tapers from torso
        rootDef.addOrReplaceChild("tail_upper",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3, 0, -2, 6, 8, 4),
                PartPose.offset(0, 12, 0));
        rootDef.addOrReplaceChild("tail_lower",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, 0, -2, 4, 4, 4),
                PartPose.offset(0, 20, 0));
        // Wide tail fin
        rootDef.addOrReplaceChild("tail_fin",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, 0, -1, 8, 2, 2),
                PartPose.offset(0, 24, 0));

        this.root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay) {
        root.render(poseStack, consumer, light, overlay, COLOR);
    }
}
