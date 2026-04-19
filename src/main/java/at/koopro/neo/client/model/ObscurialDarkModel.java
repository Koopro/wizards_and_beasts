package at.koopro.neo.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Placeholder cube geometry for the obscurial (SHADOW) form.
 * Amorphous dark mass — overlapping cubes of different sizes.
 * Uses semi-transparent rendering.
 */
public final class ObscurialDarkModel {

    public static final int COLOR = 0xCC221133; // semi-transparent dark purple

    private final ModelPart root;

    public ObscurialDarkModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        // Large central mass
        rootDef.addOrReplaceChild("core",
                CubeListBuilder.create().texOffs(0, 0).addBox(-6, 0, -6, 12, 16, 12),
                PartPose.ZERO);
        // Upper protrusion (head-like)
        rootDef.addOrReplaceChild("top",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -10, -4, 8, 10, 8),
                PartPose.ZERO);
        // Side blobs
        rootDef.addOrReplaceChild("right_blob",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, 0, -3, 4, 8, 6),
                PartPose.offset(-8, 4, 0));
        rootDef.addOrReplaceChild("left_blob",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, 0, -3, 4, 8, 6),
                PartPose.offset(8, 4, 0));
        // Wide base spreading on the ground
        rootDef.addOrReplaceChild("base",
                CubeListBuilder.create().texOffs(0, 0).addBox(-8, 0, -8, 16, 4, 16),
                PartPose.offset(0, 16, 0));

        this.root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay) {
        root.render(poseStack, consumer, light, overlay, COLOR);
    }
}
