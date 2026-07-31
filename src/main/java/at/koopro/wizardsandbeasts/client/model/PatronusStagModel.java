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

        // Slim deer barrel — narrower and a touch deeper than a cow, tapering toward the haunch.
        rootDef.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.5f, -3, -6, 5, 6, 12)
                        .addBox(-2, -2.5f, 6, 4, 5, 2), // haunch cap
                PartPose.ZERO);
        // Chest into a forward-leaning neck.
        rootDef.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -9, -2, 3, 9, 3),
                PartPose.offsetAndRotation(0, -2, -6, -0.7f, 0, 0));
        // Tapered head with a snout.
        rootDef.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.5f, -3, -4, 3, 3, 4)
                        .addBox(-1, -2, -7, 2, 2, 3),  // muzzle
                PartPose.offsetAndRotation(0, -10, -8, -0.15f, 0, 0));
        // Ears, swept back off the crown.
        rootDef.addOrReplaceChild("ears",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3, -13.5f, -6.5f, 1, 2, 1)
                        .addBox(2, -13.5f, -6.5f, 1, 2, 1),
                PartPose.ZERO);
        // Branched antlers — a main beam sweeping up-and-back with two tines each side.
        rootDef.addOrReplaceChild("antler_left",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(1.5f, -15, -6, 1, 5, 1)   // beam
                        .addBox(1.5f, -15, -4, 1, 1, 3)   // rear fork
                        .addBox(3.5f, -14, -6, 3, 1, 1)   // outer tine
                        .addBox(3.5f, -16, -6, 1, 2, 1),  // upper prong
                PartPose.ZERO);
        rootDef.addOrReplaceChild("antler_right",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.5f, -15, -6, 1, 5, 1)
                        .addBox(-2.5f, -15, -4, 1, 1, 3)
                        .addBox(-6.5f, -14, -6, 3, 1, 1)
                        .addBox(-4.5f, -16, -6, 1, 2, 1),
                PartPose.ZERO);
        // Long, thin deer legs.
        rootDef.addOrReplaceChild("front_left_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.5f, 0, -0.5f, 1.5f, 13, 1.5f),
                PartPose.offset(1.6f, 3, -4.5f));
        rootDef.addOrReplaceChild("front_right_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, -0.5f, 1.5f, 13, 1.5f),
                PartPose.offset(-1.6f, 3, -4.5f));
        rootDef.addOrReplaceChild("back_left_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-0.5f, 0, -0.5f, 1.5f, 13, 1.5f),
                PartPose.offset(1.6f, 3, 5));
        rootDef.addOrReplaceChild("back_right_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, -0.5f, 1.5f, 13, 1.5f),
                PartPose.offset(-1.6f, 3, 5));
        // Short upturned tail.
        rootDef.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, -1, 0, 2, 3, 1),
                PartPose.offsetAndRotation(0, -2.5f, 7.5f, 0.4f, 0, 0));

        this.root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        root.render(poseStack, consumer, light, overlay, color);
    }
}
