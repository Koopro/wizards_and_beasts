package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.model.PatronusStagModel;
import at.koopro.wizardsandbeasts.entity.spell.PatronusEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.animal.feline.CatModel;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.model.animal.rabbit.RabbitModel;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.renderer.entity.state.RabbitRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Patronus renderer. The drifting particle trail carries most of the look; on top of it we draw the
 * spell's <em>form</em> in ethereal silver-blue:
 * <ul>
 *   <li><b>Corporeal</b> (power ≥ 40): the resolved animal. Forms with a vanilla model
 *       ({@code cat}/{@code wolf}/{@code rabbit}/{@code parrot}) borrow it, tinted translucent
 *       silver; everything else falls back to the iconic {@link PatronusStagModel}.</li>
 *   <li><b>Non-corporeal</b> (power &lt; 40): a formless silver mist — no animal.</li>
 * </ul>
 */
public class PatronusRenderer extends EntityRenderer<PatronusEntity, PatronusRenderer.PatronusRenderState> {

    /** Flat silver texture; on a form model every texel reads near-white so the tint carries it. */
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/entity/form/placeholder.png");
    /** Icy translucent silver-blue. */
    private static final int TINT = 0x99B8E6FF;
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Standard vanilla entity-model vertical flip offset (origin sits at the top of the model). */
    private static final float MODEL_Y_OFFSET = -1.501f;

    private final PatronusStagModel stagModel = new PatronusStagModel();
    private @Nullable CatModel catModel;
    private @Nullable WolfModel wolfModel;
    private @Nullable RabbitModel rabbitModel;
    private @Nullable ParrotModel parrotModel;

    public PatronusRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public PatronusRenderState createRenderState() {
        return new PatronusRenderState();
    }

    @Override
    public void extractRenderState(PatronusEntity entity, PatronusRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.corporeal = entity.isCorporeal();
        state.formId = entity.getFormId();
    }

    @Override
    public void submit(PatronusRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);

        float age = state.ageInTicks;
        if (!state.corporeal) {
            renderMist(poseStack, collector, age);
            return;
        }

        Model vanilla = buildVanillaModel(pathOf(state.formId));
        if (vanilla != null) {
            renderVanillaForm(poseStack, collector, age, vanilla);
        } else {
            renderStag(poseStack, collector, age);
        }
    }

    /** The classic silvery stag — the wizardkind form and the fallback for any form without a model. */
    private void renderStag(PoseStack poseStack, SubmitNodeCollector collector, float age) {
        poseStack.pushPose();
        // Entity model space is inverted; lift so the legs rest near the entity origin.
        poseStack.translate(0.0, 1.4, 0.0);
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 1.5f));
        poseStack.translate(0.0, (float) Math.sin(age * 0.12f) * 0.04f, 0.0);

        RenderType renderType = RenderTypes.entityTranslucent(TEXTURE);
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            PoseStack temp = new PoseStack();
            temp.last().pose().set(pose.pose());
            temp.last().normal().set(pose.normal());
            stagModel.render(temp, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, TINT);
        });
        poseStack.popPose();
    }

    /** Renders a borrowed vanilla beast model in silver-blue, applying the vanilla Y-down flip. */
    private void renderVanillaForm(PoseStack poseStack, SubmitNodeCollector collector, float age, Model model) {
        poseStack.pushPose();
        poseStack.translate(0.0, 1.4, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 1.5f));
        poseStack.translate(0.0, (float) Math.sin(age * 0.12f) * 0.04f, 0.0);

        RenderType renderType = RenderTypes.entityTranslucent(TEXTURE);
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            PoseStack temp = new PoseStack();
            temp.last().pose().set(pose.pose());
            temp.last().normal().set(pose.normal());
            temp.mulPose(Axis.YP.rotationDegrees(180.0f));
            temp.scale(-1.0f, -1.0f, 1.0f);
            temp.translate(0.0f, MODEL_Y_OFFSET, 0.0f);
            model.renderToBuffer(temp, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, TINT);
        });
        poseStack.popPose();
    }

    /** A non-corporeal Patronus: a soft, pulsing silver wisp built from additive crossed billboards. */
    private void renderMist(PoseStack poseStack, SubmitNodeCollector collector, float age) {
        poseStack.pushPose();
        poseStack.translate(0.0, 1.2, 0.0);
        float pulse = 0.42f + 0.06f * (float) Math.sin(age * 0.15f);
        float alpha = 0.22f;
        // Vertical stack of jittered puffs → a rising wisp rather than a rigid ball.
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, consumer) -> {
            Matrix4f m = pose.pose();
            for (int i = 0; i < 4; i++) {
                float t = i / 3.0f;
                float oy = (t - 0.5f) * 0.6f;
                float wobble = (float) Math.sin(age * 0.1f + i) * 0.12f;
                float size = pulse * (1.0f - t * 0.4f);
                float a = alpha * (1.0f - t * 0.5f);
                billboardStar(m, consumer, wobble, oy, wobble * 0.5f, size, a);
            }
        });
        poseStack.popPose();
    }

    /** Two perpendicular quads (XY and ZY planes) — a cheap puff visible from most angles. */
    private static void billboardStar(Matrix4f m, VertexConsumer c, float ox, float oy, float oz,
                                       float s, float a) {
        float r = 0.72f, g = 0.85f, b = 1.0f;
        // XY plane
        c.addVertex(m, ox - s, oy - s, oz).setColor(r, g, b, a);
        c.addVertex(m, ox - s, oy + s, oz).setColor(r, g, b, a);
        c.addVertex(m, ox + s, oy + s, oz).setColor(r, g, b, a);
        c.addVertex(m, ox + s, oy - s, oz).setColor(r, g, b, a);
        // ZY plane
        c.addVertex(m, ox, oy - s, oz - s).setColor(r, g, b, a);
        c.addVertex(m, ox, oy + s, oz - s).setColor(r, g, b, a);
        c.addVertex(m, ox, oy + s, oz + s).setColor(r, g, b, a);
        c.addVertex(m, ox, oy - s, oz + s).setColor(r, g, b, a);
    }

    /** Builds (and animates for a calm standing idle) the vanilla model for a form, or null if none. */
    private @Nullable Model buildVanillaModel(String path) {
        switch (path) {
            case "cat" -> {
                CatModel model = catModel();
                model.resetPose();
                model.setupAnim(idleCat());
                return model;
            }
            case "wolf" -> {
                WolfModel model = wolfModel();
                model.resetPose();
                model.setupAnim(idleWolf());
                return model;
            }
            case "rabbit" -> {
                RabbitModel model = rabbitModel();
                model.resetPose();
                model.setupAnim(idleRabbit());
                return model;
            }
            case "parrot" -> {
                ParrotModel model = parrotModel();
                model.resetPose();
                model.setupAnim(idleParrot());
                return model;
            }
            default -> {
                return null;
            }
        }
    }

    private static CatRenderState idleCat() {
        CatRenderState s = new CatRenderState();
        s.ageScale = 1.0f;
        return s;
    }

    private static WolfRenderState idleWolf() {
        WolfRenderState s = new WolfRenderState();
        s.ageScale = 1.0f;
        return s;
    }

    private static RabbitRenderState idleRabbit() {
        RabbitRenderState s = new RabbitRenderState();
        s.ageScale = 1.0f;
        return s;
    }

    private static ParrotRenderState idleParrot() {
        ParrotRenderState s = new ParrotRenderState();
        s.ageScale = 1.0f;
        s.pose = ParrotModel.Pose.STANDING;
        return s;
    }

    /** Strips the namespace from a form id: {@code minecraft:polar_bear} → {@code polar_bear}. */
    private static String pathOf(String formId) {
        if (formId == null) return "";
        int colon = formId.lastIndexOf(':');
        return colon >= 0 ? formId.substring(colon + 1) : formId;
    }

    private CatModel catModel() {
        if (catModel == null) {
            catModel = new CatModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.CAT));
        }
        return catModel;
    }

    private WolfModel wolfModel() {
        if (wolfModel == null) {
            wolfModel = new WolfModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.WOLF));
        }
        return wolfModel;
    }

    private RabbitModel rabbitModel() {
        if (rabbitModel == null) {
            rabbitModel = new RabbitModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.RABBIT));
        }
        return rabbitModel;
    }

    private ParrotModel parrotModel() {
        if (parrotModel == null) {
            parrotModel = new ParrotModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PARROT));
        }
        return parrotModel;
    }

    /** Render state carrying the synced corporeality + form so other players see the right shape. */
    public static class PatronusRenderState extends EntityRenderState {
        public boolean corporeal;
        public String formId = "";
    }
}
