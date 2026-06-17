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
 * Placeholder cube geometry for the corporeal Patronus — an ethereal stag, the most iconic form.
 * Stands in until real per-form Patronus geometry is authored. Tinted translucent by the renderer.
 */
public final class PatronusStagModel {

    private final ModelPart root;

    public PatronusStagModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        // Barrel body
        rootDef.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3, -3, -7, 6, 7, 14),
                PartPose.ZERO);
        // Neck rising to the front
        rootDef.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, -9, -2, 4, 8, 4),
                PartPose.offsetAndRotation(0, -2, -7, -0.6f, 0, 0));
        // Head
        rootDef.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, -3, -5, 4, 4, 6),
                PartPose.offset(0, -10, -8));
        // Antlers — a few thin prongs each side
        rootDef.addOrReplaceChild("antler_left",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(2, -7, -7, 1, 6, 1)
                        .addBox(2, -7, -5, 4, 1, 1)
                        .addBox(2, -9, -3, 1, 4, 1),
                PartPose.ZERO);
        rootDef.addOrReplaceChild("antler_right",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3, -7, -7, 1, 6, 1)
                        .addBox(-6, -7, -5, 4, 1, 1)
                        .addBox(-3, -9, -3, 1, 4, 1),
                PartPose.ZERO);
        // Legs
        rootDef.addOrReplaceChild("front_left_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, -1, 2, 11, 2),
                PartPose.offset(2, 4, -5));
        rootDef.addOrReplaceChild("front_right_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, -1, 2, 11, 2),
                PartPose.offset(-2, 4, -5));
        rootDef.addOrReplaceChild("back_left_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, -1, 2, 11, 2),
                PartPose.offset(2, 4, 5));
        rootDef.addOrReplaceChild("back_right_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, -1, 2, 11, 2),
                PartPose.offset(-2, 4, 5));
        // Tail
        rootDef.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, -1, 0, 2, 3, 2),
                PartPose.offset(0, -2, 7));

        this.root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        root.render(poseStack, consumer, light, overlay, color);
    }
}
