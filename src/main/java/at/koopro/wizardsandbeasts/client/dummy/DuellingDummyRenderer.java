package at.koopro.wizardsandbeasts.client.dummy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.dummy.DuellingDummyEntity;

/**
 * Draws a duelling dummy on vanilla's own player rig.
 *
 * <p>{@link ModelLayers#PLAYER} and {@link ModelLayers#PLAYER_ARMOR} rather than a bespoke mesh.
 * The whole reason the dummy exists is to answer "what would this do to a wizard", so it should be
 * a wizard's shape and wear a wizard's armour on the same bones - and going through the vanilla
 * layers means a robe rendered on the dummy is rendered by exactly the code that renders it on a
 * player, with no second set of offsets to keep in step.
 *
 * <p>Layers mirror {@code ArmorStandRenderer}'s, minus the wings: armour, held items, and the
 * custom head layer that makes the decoy mode legible - a player head on the dummy renders as that
 * player's face, which is what tells you at a glance what the dummy is set to do.
 */
@NullMarked
public class DuellingDummyRenderer
        extends LivingEntityRenderer<DuellingDummyEntity, DuellingDummyRenderer.DummyRenderState,
                HumanoidModel<DuellingDummyRenderer.DummyRenderState>> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/entity/duelling_dummy.png");

    /** Damage that swings the dummy as far as it will go. Above this the lean stops growing. */
    private static final float FULL_SWING_DAMAGE = 12.0f;
    /** How far the dummy leans away from a full-strength blow, in degrees. */
    private static final float MAX_SWING_DEGREES = 22.0f;
    /** Oscillations over one hurt animation. Three reads as a rattle rather than a single nod. */
    private static final float SWING_CYCLES = 3.0f;

    public DuellingDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
                context.getEquipmentRenderer()));
        this.addLayer(new ItemInHandLayer<>(this));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
    }

    @Override
    public Identifier getTextureLocation(DummyRenderState state) {
        return TEXTURE;
    }

    @Override
    public DummyRenderState createRenderState() {
        return new DummyRenderState();
    }

    @Override
    public void extractRenderState(DuellingDummyEntity dummy, DummyRenderState state, float partialTick) {
        super.extractRenderState(dummy, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(dummy, state, partialTick, this.itemModelResolver);
        // The swing is a pure function of "how hard, how long ago", and both halves are already
        // here: hurtTime is client-side vanilla state, and the damage is the one synced field.
        float remaining = dummy.hurtTime - partialTick;
        state.swing = remaining <= 0.0f || dummy.hurtDuration <= 0
                ? 0.0f
                : Mth.clamp(remaining / dummy.hurtDuration, 0.0f, 1.0f);
        state.swingStrength = Mth.clamp(dummy.lastHitDamage() / FULL_SWING_DAMAGE, 0.0f, 1.0f);
    }

    @Override
    protected void setupRotations(DummyRenderState state, PoseStack poseStack, float bodyRot, float scale) {
        super.setupRotations(state, poseStack, bodyRot, scale);
        if (state.swing <= 0.0f || state.swingStrength <= 0.0f) {
            return;
        }
        // Decaying oscillation: amplitude falls with the remaining hurt time, so the dummy settles
        // rather than stopping dead at the bottom of a swing.
        float angle = Mth.sin(state.swing * Mth.PI * SWING_CYCLES) * state.swing
                * state.swingStrength * MAX_SWING_DEGREES;
        // Pivoted at the feet, not the middle: a post in the ground rocks about where it is planted.
        poseStack.translate(0.0f, 1.5f, 0.0f);
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));
        poseStack.translate(0.0f, -1.5f, 0.0f);
    }

    @Override
    protected boolean shouldShowName(DuellingDummyEntity dummy, double distanceSq) {
        // Always, when named. A practice hall with three dummies in it needs to say which is which,
        // and unlike a mob there is no reason to hide the label until you look straight at it.
        return dummy.hasCustomName();
    }

    /** Adds the two numbers the wobble needs to the ordinary humanoid state. */
    public static class DummyRenderState extends HumanoidRenderState {
        /** 1 immediately after a hit, falling to 0 as the hurt animation runs out. */
        public float swing;
        /** How hard the last blow was, normalised. */
        public float swingStrength;
    }
}
