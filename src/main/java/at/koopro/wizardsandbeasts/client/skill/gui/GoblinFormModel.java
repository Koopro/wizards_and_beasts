package at.koopro.wizardsandbeasts.client.skill.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Placeholder cube geometry for the goblin (SMALL_HUMANOID) form.
 * Short body with an oversized head and prominent ears.
 */
public final class GoblinFormModel {

    public static final int COLOR = 0xFF559944; // green

    private final ModelPart root;

    public GoblinFormModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        // Offset body down so the small goblin stands on the ground
        rootDef.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3, 0, -2, 6, 8, 4),
                PartPose.offset(0, 8, 0));
        rootDef.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.offset(0, 8, 0));
        rootDef.addOrReplaceChild("right_ear",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, -4, -1, 2, 4, 2),
                PartPose.offset(-5, 4, 0));
        rootDef.addOrReplaceChild("left_ear",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1, -4, -1, 2, 4, 2),
                PartPose.offset(5, 4, 0));
        rootDef.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, -1, -1.5f, 2, 8, 3),
                PartPose.offset(-4, 9, 0));
        rootDef.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(0, 0).addBox(0, -1, -1.5f, 2, 8, 3),
                PartPose.offset(4, 9, 0));
        rootDef.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, 0, -1.5f, 3, 8, 3),
                PartPose.offset(-1.5f, 16, 0));
        rootDef.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, 0, -1.5f, 3, 8, 3),
                PartPose.offset(1.5f, 16, 0));

        this.root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay) {
        root.render(poseStack, consumer, light, overlay, COLOR);
    }
}
