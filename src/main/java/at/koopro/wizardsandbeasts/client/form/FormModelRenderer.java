package at.koopro.wizardsandbeasts.client.form;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.model.ObscurialDarkModel;
import at.koopro.wizardsandbeasts.client.model.WerewolfModel;
import at.koopro.wizardsandbeasts.client.model.CentaurModel;
import at.koopro.wizardsandbeasts.client.model.PatronusStagModel;
import at.koopro.wizardsandbeasts.client.model.MerfolkSwimModel;
import at.koopro.wizardsandbeasts.client.form.model.BatFormModel;
import at.koopro.wizardsandbeasts.client.skill.gui.GoblinFormModel;
import at.koopro.wizardsandbeasts.form.ModelType;
import at.koopro.wizardsandbeasts.form.RenderFlag;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.animal.feline.CatModel;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.model.animal.rabbit.RabbitModel;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.monster.silverfish.SilverfishModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.renderer.entity.state.RabbitRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.UUID;

/**
 * Dispatches rendering of custom (non-HUMANOID) player form models.
 * <p>
 * Animagus cat/dog forms borrow the vanilla {@link CatModel}/{@link WolfModel} —
 * including their walk, sit and idle animations — by populating a vanilla render
 * state from the player's own {@link LivingEntityRenderState} each frame. All other
 * forms fall back to placeholder cube geometry keyed on {@link ModelType}.
 */
public final class FormModelRenderer {

    private static final Identifier PLACEHOLDER_TEXTURE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/entity/form/placeholder.png");

    private static final Identifier CAT_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/cat/tabby.png");
    private static final Identifier WOLF_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/wolf/wolf.png");
    private static final Identifier RABBIT_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/rabbit/brown.png");
    private static final Identifier PARROT_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/parrot/parrot_grey.png");
    private static final Identifier SILVERFISH_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/silverfish.png");

    /** Solid white tint — no recolour applied to the borrowed model. */
    private static final int NO_TINT = -1;

    /** Standard vanilla entity-model vertical offset (origin sits at the top of the model). */
    private static final float MODEL_Y_OFFSET = -1.501f;

    private static WerewolfModel werewolfModel;
    private static ObscurialDarkModel darkModel;
    private static CentaurModel centaurModel;
    private static PatronusStagModel stagModel;
    private static GoblinFormModel goblinModel;
    private static BatFormModel batModel;
    private static MerfolkSwimModel merfolkModel;
    private static CatModel catModel;
    private static WolfModel wolfModel;
    private static RabbitModel rabbitModel;
    private static ParrotModel parrotModel;
    private static SilverfishModel silverfishModel;

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
            case STAG -> getStagModel().render(poseStack, consumer, packedLight, overlay, NO_TINT);
            case SMALL_HUMANOID -> getGoblinModel().render(poseStack, consumer, packedLight, overlay);
            case FLYING -> getBatModel().render(poseStack, consumer, packedLight, overlay);
            case SWIMMING -> getMerfolkModel().render(poseStack, consumer, packedLight, overlay);
            case SHADOW -> getDarkModel().render(poseStack, consumer, packedLight, overlay, clientTime());
            default -> {} // HUMANOID handled by vanilla — should never reach here
        }
    }

    /**
     * Renders a custom form model using the new SubmitNodeCollector pipeline.
     * Called from {@link at.koopro.wizardsandbeasts.mixin.client.LivingEntityRendererMixin}
     * where the PoseStack already has form scale applied.
     *
     * @param src the player's own render state, used to drive vanilla beast-model animation
     */
    public static void renderToCollector(PoseStack poseStack, SubmitNodeCollector collector,
                                          FormRenderStateModifier.FormRenderData formData,
                                          LivingEntityRenderState src) {
        // Animagus forms borrow a real vanilla entity model (animation + proportions + texture).
        switch (formData.formId()) {
            case "animagus_cat" -> {
                renderVanilla(poseStack, collector, CAT_TEXTURE, src, NO_TINT, () -> {
                    CatModel model = getCatModel();
                    model.resetPose();
                    model.setupAnim(buildCatState(formData, src));
                    return model;
                });
                return;
            }
            case "animagus_dog" -> {
                UUID uuid = formData.playerUUID();
                int wetTint = wetTint(WetShakeTracker.wetShade(uuid));
                renderVanilla(poseStack, collector, WOLF_TEXTURE, src, wetTint, () -> {
                    WolfModel model = getWolfModel();
                    model.resetPose();
                    model.setupAnim(buildWolfState(formData, src));
                    return model;
                });
                return;
            }
            case "animagus_hare" -> {
                renderVanilla(poseStack, collector, RABBIT_TEXTURE, src, NO_TINT, () -> {
                    RabbitModel model = getRabbitModel();
                    model.resetPose();
                    model.setupAnim(buildRabbitState(src));
                    return model;
                });
                return;
            }
            case "animagus_hawk" -> {
                renderVanilla(poseStack, collector, PARROT_TEXTURE, src, NO_TINT, () -> {
                    ParrotModel model = getParrotModel();
                    model.resetPose();
                    model.setupAnim(buildParrotState(formData, src));
                    return model;
                });
                return;
            }
            case "animagus_beetle" -> {
                renderVanilla(poseStack, collector, SILVERFISH_TEXTURE, src, NO_TINT, () -> {
                    SilverfishModel model = getSilverfishModel();
                    model.resetPose();
                    model.setupAnim(src); // reads only ageInTicks from the (living) render state
                    return model;
                });
                return;
            }
            default -> { /* stag has no vanilla analog — fall through to placeholder geometry */ }
        }

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
                case STAG -> getStagModel().render(tempStack, consumer, light, overlay, NO_TINT);
                case SMALL_HUMANOID -> getGoblinModel().render(tempStack, consumer, light, overlay);
                case FLYING -> getBatModel().render(tempStack, consumer, light, overlay);
                case SWIMMING -> getMerfolkModel().render(tempStack, consumer, light, overlay);
                // The obscurial's smoke churns off the player's own age, so two transformed
                // players next to each other are not in lockstep.
                case SHADOW -> getDarkModel().render(tempStack, consumer, light, overlay,
                        src != null ? src.ageInTicks : clientTime());
                default -> {}
            }
        });
    }

    /**
     * Renders a vanilla {@link net.minecraft.client.model.Model} in entity space. The model is
     * authored Y-down with its origin at the top, so we mirror the body-yaw rotation and the
     * {@code scale(-1,-1,1)} / {@link #MODEL_Y_OFFSET} flip that the vanilla renderer would apply —
     * the form-scale already sits on the incoming PoseStack from the mixin.
     */
    private static void renderVanilla(PoseStack poseStack, SubmitNodeCollector collector,
                                       Identifier texture, LivingEntityRenderState src, int color,
                                       java.util.function.Supplier<net.minecraft.client.model.Model> animated) {
        RenderType renderType = RenderTypes.entityCutoutNoCull(texture);
        float bodyRot = src != null ? src.bodyRot : 0.0f;
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            PoseStack tempStack = new PoseStack();
            tempStack.last().pose().set(pose.pose());
            tempStack.last().normal().set(pose.normal());

            tempStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyRot));
            tempStack.scale(-1.0f, -1.0f, 1.0f);
            tempStack.translate(0.0f, MODEL_Y_OFFSET, 0.0f);

            net.minecraft.client.model.Model model = animated.get();
            model.renderToBuffer(tempStack, consumer, 0xF000F0, OverlayTexture.NO_OVERLAY, color);
        });
    }

    /** Packs a wet-coat shade ({@code 1.0} dry .. darker) into an opaque ARGB tint for the wolf model. */
    private static int wetTint(float shade) {
        int v = Math.round(255.0f * Mth.clamp(shade, 0.0f, 1.0f));
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    /** Ever-advancing client animation clock (game time + partial tick) for idle flap/wiggle. */
    private static float animTime() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0.0f;
        return (float) mc.level.getGameTime()
                + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    private static WolfRenderState buildWolfState(FormRenderStateModifier.FormRenderData formData,
                                                  LivingEntityRenderState src) {
        WolfRenderState s = new WolfRenderState();
        boolean sitting = isSitPose(formData, src);
        s.ageScale = 1.0f;
        s.walkAnimationPos = src != null ? src.walkAnimationPos : 0.0f;
        s.walkAnimationSpeed = sitting ? 0.0f : clampSwing(src);
        s.yRot = netHeadYaw(src);
        s.xRot = src != null ? src.xRot : 0.0f;
        s.isSitting = sitting;
        // Wet-dog shake: a non-zero shakeAnim makes the model body-roll wiggle (and dry off).
        s.shakeAnim = WetShakeTracker.shakeAnim(formData.playerUUID());
        return s;
    }

    private static RabbitRenderState buildRabbitState(LivingEntityRenderState src) {
        RabbitRenderState s = new RabbitRenderState();
        s.ageScale = 1.0f;
        s.walkAnimationPos = src != null ? src.walkAnimationPos : 0.0f;
        s.walkAnimationSpeed = clampSwing(src);
        s.yRot = netHeadYaw(src);
        s.xRot = src != null ? src.xRot : 0.0f;
        // Hop crouch peaks mid-stride while moving; resting rabbits stay flat.
        s.jumpCompletion = Math.min(1.0f, s.walkAnimationSpeed) * Math.abs(Mth.sin(s.walkAnimationPos * 0.5f));
        return s;
    }

    private static ParrotRenderState buildParrotState(FormRenderStateModifier.FormRenderData formData,
                                                      LivingEntityRenderState src) {
        ParrotRenderState s = new ParrotRenderState();
        AbstractClientPlayer player = livePlayer(formData);
        s.ageScale = 1.0f;
        s.walkAnimationPos = src != null ? src.walkAnimationPos : 0.0f;
        s.walkAnimationSpeed = clampSwing(src);
        s.yRot = netHeadYaw(src);
        s.xRot = src != null ? src.xRot : 0.0f;
        boolean airborne = player != null && !player.onGround();
        s.pose = airborne ? ParrotModel.Pose.FLYING : ParrotModel.Pose.STANDING;
        s.flapAngle = Mth.cos(animTime() * 0.3f); // gentle idle bob; FLYING pose spreads the wings
        return s;
    }

    private static CatRenderState buildCatState(FormRenderStateModifier.FormRenderData formData,
                                                LivingEntityRenderState src) {
        CatRenderState s = new CatRenderState();
        boolean sitting = isSitPose(formData, src);
        AbstractClientPlayer player = livePlayer(formData);
        s.ageScale = 1.0f;
        s.walkAnimationPos = src != null ? src.walkAnimationPos : 0.0f;
        s.walkAnimationSpeed = sitting ? 0.0f : clampSwing(src);
        s.yRot = netHeadYaw(src);
        s.xRot = src != null ? src.xRot : 0.0f;
        s.isSitting = sitting;
        s.isSprinting = player != null && player.isSprinting();
        s.isCrouching = false; // sneak is mapped to the sit pose for beast forms
        return s;
    }

    /**
     * Net head yaw in degrees. {@link LivingEntityRenderState#yRot} is already the head yaw with
     * the body yaw subtracted (see LivingEntityRenderer#extractRenderState), so it is used as-is —
     * subtracting bodyRot again would double-count and mis-aim the head.
     */
    private static float netHeadYaw(LivingEntityRenderState src) {
        return src != null ? src.yRot : 0.0f;
    }

    private static float clampSwing(LivingEntityRenderState src) {
        return src != null ? Math.min(1.0f, src.walkAnimationSpeed) : 0.0f;
    }

    /** Beast sits when the player sneaks while standing still; sneak-walking keeps the walk cycle. */
    private static boolean isSitPose(FormRenderStateModifier.FormRenderData formData, LivingEntityRenderState src) {
        AbstractClientPlayer player = livePlayer(formData);
        boolean sneaking = player != null && player.isShiftKeyDown();
        return sneaking && clampSwing(src) <= 0.05f;
    }

    private static @Nullable AbstractClientPlayer livePlayer(FormRenderStateModifier.FormRenderData formData) {
        if (Minecraft.getInstance().level == null) return null;
        return Minecraft.getInstance().level.getPlayerByUUID(formData.playerUUID()) instanceof AbstractClientPlayer p
                ? p : null;
    }

    private static WerewolfModel getWerewolfModel() {
        if (werewolfModel == null) werewolfModel = new WerewolfModel();
        return werewolfModel;
    }

    private static ObscurialDarkModel getDarkModel() {
        if (darkModel == null) darkModel = new ObscurialDarkModel();
        return darkModel;
    }

    /** Fallback animation clock for call sites with no render state to read {@code ageInTicks} from. */
    private static float clientTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0.0f : (float) (mc.level.getGameTime() % 100000L);
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

    /**
     * The stag shares the Patronus's geometry — it is the same animal, and the alternative was
     * the centaur body. Its cubes all sit at texOffs(0,0), so the texture is read as one hide
     * rather than a per-part layout.
     */
    private static PatronusStagModel getStagModel() {
        if (stagModel == null) stagModel = new PatronusStagModel();
        return stagModel;
    }

    private static MerfolkSwimModel getMerfolkModel() {
        if (merfolkModel == null) merfolkModel = new MerfolkSwimModel();
        return merfolkModel;
    }

    private static CatModel getCatModel() {
        if (catModel == null) {
            catModel = new CatModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.CAT));
        }
        return catModel;
    }

    private static WolfModel getWolfModel() {
        if (wolfModel == null) {
            wolfModel = new WolfModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.WOLF));
        }
        return wolfModel;
    }

    private static RabbitModel getRabbitModel() {
        if (rabbitModel == null) {
            rabbitModel = new RabbitModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.RABBIT));
        }
        return rabbitModel;
    }

    private static ParrotModel getParrotModel() {
        if (parrotModel == null) {
            parrotModel = new ParrotModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PARROT));
        }
        return parrotModel;
    }

    private static SilverfishModel getSilverfishModel() {
        if (silverfishModel == null) {
            silverfishModel = new SilverfishModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SILVERFISH));
        }
        return silverfishModel;
    }

}
