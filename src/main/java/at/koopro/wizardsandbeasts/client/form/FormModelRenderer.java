package at.koopro.wizardsandbeasts.client.form;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.model.WerewolfModel;
import at.koopro.wizardsandbeasts.client.model.CentaurModel;
import at.koopro.wizardsandbeasts.client.model.MerfolkSwimModel;
import at.koopro.wizardsandbeasts.client.form.model.BatFormModel;
import at.koopro.wizardsandbeasts.client.skill.gui.GoblinFormModel;
import at.koopro.wizardsandbeasts.form.ModelType;
import at.koopro.wizardsandbeasts.form.RenderFlag;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Dispatches rendering of custom (non-HUMANOID) player form models.
 * Each {@link ModelType} maps to a placeholder model with simple cube geometry.
 */
public final class FormModelRenderer {

    private static final Identifier PLACEHOLDER_TEXTURE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/entity/form/placeholder.png");

    private static WerewolfModel werewolfModel;
    private static CentaurModel centaurModel;
    private static GoblinFormModel goblinModel;
    private static BatFormModel batModel;
    private static MerfolkSwimModel merfolkModel;

    private FormModelRenderer() {}

    /**
     * Renders a custom form model for a player whose default render was cancelled.
     *
     * @param poseStack    the current pose stack (already scaled for form size)
     * @param bufferSource the buffer source for obtaining vertex consumers
     * @param packedLight  packed light level
     * @param formData     the form render data (model type, texture, flags)
     */
    public static void render(PoseStack poseStack,
                               MultiBufferSource bufferSource,
                               int packedLight,
                               FormRenderStateModifier.FormRenderData formData) {
        Identifier texture = formData.texturePath() != null ? formData.texturePath() : PLACEHOLDER_TEXTURE;

        boolean translucent = formData.renderFlags().contains(RenderFlag.TRANSLUCENT);
        RenderType renderType = translucent
                ? RenderTypes.entityTranslucent(texture)
                : RenderTypes.entitySolid(texture);

        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        int overlay = OverlayTexture.NO_OVERLAY;

        switch (formData.modelType()) {
            case CUSTOM_BIPED -> getWerewolfModel().render(poseStack, consumer, packedLight, overlay);
            case QUADRUPED -> getCentaurModel().render(poseStack, consumer, packedLight, overlay);
            case SMALL_HUMANOID -> getGoblinModel().render(poseStack, consumer, packedLight, overlay);
            case FLYING -> getBatModel().render(poseStack, consumer, packedLight, overlay);
            case SWIMMING -> getMerfolkModel().render(poseStack, consumer, packedLight, overlay);
            case SHADOW -> {
                // Obscurial dark form is represented by particles and overlays, not solid geometry.
            }
            default -> {} // HUMANOID handled by vanilla — should never reach here
        }
    }

    /**
     * Renders a custom form model using the new SubmitNodeCollector pipeline.
     * Called from {@link at.koopro.wizardsandbeasts.mixin.client.LivingEntityRendererMixin}
     * where the PoseStack already has form scale applied.
     */
    public static void renderToCollector(PoseStack poseStack, SubmitNodeCollector collector,
                                          FormRenderStateModifier.FormRenderData formData) {
        Identifier texture = formData.texturePath() != null ? formData.texturePath() : PLACEHOLDER_TEXTURE;

        boolean translucent = formData.renderFlags().contains(RenderFlag.TRANSLUCENT);
        RenderType renderType = translucent
                ? RenderTypes.entityTranslucent(texture)
                : RenderTypes.entitySolid(texture);

        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            // Reconstruct a PoseStack from the composed Pose for ModelPart.render()
            PoseStack tempStack = new PoseStack();
            tempStack.last().pose().set(pose.pose());
            tempStack.last().normal().set(pose.normal());

            int light = 0xF000F0; // full brightness for placeholder models
            int overlay = OverlayTexture.NO_OVERLAY;

            switch (formData.modelType()) {
                case CUSTOM_BIPED -> getWerewolfModel().render(tempStack, consumer, light, overlay);
                case QUADRUPED -> getCentaurModel().render(tempStack, consumer, light, overlay);
                case SMALL_HUMANOID -> getGoblinModel().render(tempStack, consumer, light, overlay);
                case FLYING -> getBatModel().render(tempStack, consumer, light, overlay);
                case SWIMMING -> getMerfolkModel().render(tempStack, consumer, light, overlay);
                case SHADOW -> {
                    // Obscurial dark form uses particle cloud rendering only.
                }
                default -> {}
            }
        });
    }

    private static WerewolfModel getWerewolfModel() {
        if (werewolfModel == null) werewolfModel = new WerewolfModel();
        return werewolfModel;
    }

    private static CentaurModel getCentaurModel() {
        if (centaurModel == null) centaurModel = new CentaurModel();
        return centaurModel;
    }

    private static GoblinFormModel getGoblinModel() {
        if (goblinModel == null) goblinModel = new GoblinFormModel();
        return goblinModel;
    }

    private static BatFormModel getBatModel() {
        if (batModel == null) batModel = new BatFormModel();
        return batModel;
    }

    private static MerfolkSwimModel getMerfolkModel() {
        if (merfolkModel == null) merfolkModel = new MerfolkSwimModel();
        return merfolkModel;
    }

}
