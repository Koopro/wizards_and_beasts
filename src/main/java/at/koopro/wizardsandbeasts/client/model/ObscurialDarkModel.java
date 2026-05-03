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
 * Smoky placeholder geometry for obscurial (SHADOW) form.
 * Built from layered wisps so it reads as a drifting cloud silhouette.
 */
public final class ObscurialDarkModel {

    public static final int COLOR = 0xAA1A0E26; // translucent deep violet

    private final ModelPart root;

    public ObscurialDarkModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        rootDef.addOrReplaceChild("core_wisp",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 18, 8),
                PartPose.ZERO);

        rootDef.addOrReplaceChild("upper_mist",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5, -14, -5, 10, 8, 10),
                PartPose.offset(0, 0, 0));
        rootDef.addOrReplaceChild("middle_swirl",
                CubeListBuilder.create().texOffs(0, 0).addBox(-7, -2, -7, 14, 8, 14),
                PartPose.offset(0, 3, 0));
        rootDef.addOrReplaceChild("lower_haze",
                CubeListBuilder.create().texOffs(0, 0).addBox(-8, 0, -8, 16, 6, 16),
                PartPose.offset(0, 10, 0));

        rootDef.addOrReplaceChild("right_trail",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, -1, -5, 4, 12, 10),
                PartPose.offset(-7, 4, 0));
        rootDef.addOrReplaceChild("left_trail",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, -1, -5, 4, 12, 10),
                PartPose.offset(7, 4, 0));

        this.root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay) {
        root.render(poseStack, consumer, light, overlay, COLOR);
    }
}
