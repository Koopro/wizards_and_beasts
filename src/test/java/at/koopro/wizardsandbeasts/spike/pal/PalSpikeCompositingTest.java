package at.koopro.wizardsandbeasts.spike.pal;

import com.google.gson.JsonObject;
import com.zigythebird.playeranim.accessors.IBoneUpdater;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.HumanoidAnimationController;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import com.zigythebird.playeranimcore.molang.MolangLoader;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPIKE ONLY -- evidence for AGENT_PROMPT_PAL_SPIKE. Not a feature. Do not merge.
 *
 * <p>Everything here is measured, not read off documentation. The pieces of PAL exercised below
 * are the ones that decide adoption: what its stack does to a {@code ModelPart} that already
 * carries someone else's value (Q4), whether a procedural {@code IAnimation} is a real extension
 * point (Q9), whether weights can be driven continuously (Q10), and what its loader does to the
 * numbers in a GeckoLib-format clip (Q6).
 *
 * <p>No {@code Minecraft.getInstance()} anywhere: every call path used here is pure data, which is
 * itself a finding — PAL's blend core is testable headless, the same property the existing pose
 * tests rely on.
 */
class PalSpikeCompositingTest {

    private static final float SENTINEL = 0.4242f;
    private static final float PAL_VALUE = -1.25f;

    private static PlayerModel bakeModel() {
        ModelPart root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64)
                .bakeRoot();
        return new PlayerModel(root, false);
    }

    /**
     * Q3 precondition, and a negative result worth recording: PAL's mixins do <b>not</b> apply in
     * this project's FML unit-test harness.
     *
     * <p>PAL arrives as an ordinary {@code implementation} dependency, so its classes are on the
     * classpath and compile against; but FML in the unit-test environment loads only the tested mod,
     * so {@code player_animation_library.mixins.json} is never registered and {@code PlayerModel}
     * comes out untransformed. Consequence for this spike: the mixin ordering question cannot be
     * settled from a unit test, only from a launched client. Consequence for the project: any future
     * test that wants to assert on composited output has the same ceiling.
     */
    @Test
    void palMixinsDoNotApplyInTheUnitTestHarness() {
        PlayerModel model = bakeModel();
        assertTrue(IBoneUpdater.class.getName().startsWith("com.zigythebird"),
                "PAL is not even on the test classpath");
        assertFalse(model instanceof IBoneUpdater,
                "PAL's PlayerModelMixin now applies under FML unit tests -- this finding is stale, "
                        + "re-check the ordering question from a test rather than a client run");
        assertFalse(new AvatarRenderState() instanceof com.zigythebird.playeranim.accessors.IAvatarAnimationState,
                "PAL's AvatarRenderStateMixin now applies under FML unit tests -- finding is stale");
    }

    /**
     * Q4, first half: what PAL does to a value already sitting on the part.
     *
     * <p>{@code updatePart} seeds its bone from the live {@code ModelPart} and then hands it down
     * the stack, so whatever posed the part first is the stack's <em>input</em>, not a competitor.
     * On an axis a layer writes, the layer wins outright.
     */
    @Test
    void palReplacesTheAxesItAddresses() {
        PlayerModel model = bakeModel();
        model.rightArm.xRot = SENTINEL;

        PalSpikeProceduralAnimation animation = new PalSpikeProceduralAnimation();
        animation.armPitch = PAL_VALUE;

        AvatarAnimManager manager = new AvatarAnimManager(null);
        manager.addAnimLayer(0, animation);
        manager.updatePart(model.rightArm, seed(model.rightArm, "right_arm"));

        assertEquals(PAL_VALUE, model.rightArm.xRot, 1.0e-6f,
                "PAL did not overwrite an axis its layer addressed");
    }

    /** Q4, second half: an axis no layer addresses keeps the value it arrived with. */
    @Test
    void palPreservesAxesNoLayerAddresses() {
        PlayerModel model = bakeModel();
        model.rightArm.yRot = SENTINEL;
        model.leftLeg.xRot = SENTINEL;

        PalSpikeProceduralAnimation animation = new PalSpikeProceduralAnimation();
        animation.armPitch = PAL_VALUE;

        AvatarAnimManager manager = new AvatarAnimManager(null);
        manager.addAnimLayer(0, animation);
        manager.updatePart(model.rightArm, seed(model.rightArm, "right_arm"));
        manager.updatePart(model.leftLeg, seed(model.leftLeg, "left_leg"));

        assertEquals(SENTINEL, model.rightArm.yRot, 1.0e-6f,
                "PAL clobbered an axis its layer never touched");
        assertEquals(SENTINEL, model.leftLeg.xRot, 1.0e-6f,
                "PAL clobbered a bone its layer never touched");
    }

    /** Q9: a pure-Java layer is a first-class stack member -- it ticks, it is set up, it transforms. */
    @Test
    void proceduralLayerIsDrivenByTheStack() {
        PalSpikeProceduralAnimation animation = new PalSpikeProceduralAnimation();
        AvatarAnimManager manager = new AvatarAnimManager(null);
        manager.addAnimLayer(0, animation);

        AnimationData data = new AnimationData(0.5f, 0.25f, false);
        manager.tick(data);
        manager.setupAnim(data);

        assertEquals(1, animation.tickCount, "the stack never ticked the procedural layer");
        assertNotNull(animation.lastSetupData, "the stack never called setupAnim on the procedural layer");
        assertEquals(0.25f, animation.lastSetupData.getPartialTick(), 1.0e-6f);
    }

    /** Q5: the whole-avatar channel is a bone named {@code body}, addressable like any other. */
    @Test
    void wholeBodyPitchIsAnOrdinaryBone() {
        PalSpikeProceduralAnimation animation = new PalSpikeProceduralAnimation();
        animation.bodyPitch = 0.75f;

        AvatarAnimManager manager = new AvatarAnimManager(null);
        manager.addAnimLayer(0, animation);

        PlayerAnimBone body = manager.get3DTransform(new PlayerAnimBone("body"));
        assertEquals(0.75f, body.getRotX(), 1.0e-6f,
                "PAL's body bone did not carry a whole-avatar pitch");
    }

    /** Q10: weights drive a continuous, order-independent three-way blend. */
    @Test
    void threeWayWeightedBlendIsContinuous() {
        PalSpikeProceduralAnimation hover = new PalSpikeProceduralAnimation();
        PalSpikeProceduralAnimation glide = new PalSpikeProceduralAnimation();
        PalSpikeProceduralAnimation propelled = new PalSpikeProceduralAnimation();
        hover.bodyPitch = 0f;
        glide.bodyPitch = 0.5f;
        propelled.bodyPitch = 1.5f;

        PalSpikeWeightedBlend blend = new PalSpikeWeightedBlend(List.of(hover, glide, propelled));

        for (int step = 0; step <= 10; step++) {
            float t = step / 10f;
            blend.weights[0] = 1f - t;
            blend.weights[1] = t;
            blend.weights[2] = 0f;
            float sampled = blend.get3DTransform(new PlayerAnimBone("body")).getRotX();
            assertEquals(0.5f * t, sampled, 1.0e-5f, "blend was not linear in the weight at t=" + t);
        }

        blend.weights[0] = 0.2f;
        blend.weights[1] = 0.3f;
        blend.weights[2] = 0.5f;
        float first = blend.get3DTransform(new PlayerAnimBone("body")).getRotX();
        float second = blend.get3DTransform(new PlayerAnimBone("body")).getRotX();
        assertEquals(first, second, 0f, "the blend was not stable between two evaluations of one state");
        assertEquals(0.2f * 0f + 0.3f * 0.5f + 0.5f * 1.5f, first, 1.0e-5f);
    }

    /**
     * Q6: PAL parses the project's GeckoLib animation format directly, and what it does to the
     * numbers on the way in.
     */
    @Test
    void geckoLibClipLoadsAndKeepsItsSign() throws IOException {
        Path clip = Path.of("src/main/resources/assets/wizards_and_beasts/player_animations/spike_pal_probe.json");
        assertTrue(Files.exists(clip), "spike clip missing at " + clip.toAbsolutePath());

        JsonObject json = com.zigythebird.playeranimcore.PlayerAnimLib.GSON
                .fromJson(Files.readString(clip), JsonObject.class);
        Map<String, Animation> loaded = UniversalAnimLoader.loadAnimations(json);

        assertTrue(loaded.containsKey("spike_pal_probe"),
                "PAL did not load a GeckoLib-format animation; got " + loaded.keySet());
        Animation animation = loaded.get("spike_pal_probe");
        // FORMAT_KEY is written only for the player-animator format; a GeckoLib-format clip leaves it
        // absent, which is itself how PAL distinguishes the two.
        assertNotEquals(com.zigythebird.playeranimcore.enums.AnimationFormat.PLAYER_ANIMATOR,
                animation.data().<com.zigythebird.playeranimcore.enums.AnimationFormat>get(
                        com.zigythebird.playeranimcore.animation.ExtraAnimationData.FORMAT_KEY).orElse(null),
                "PAL read the GeckoLib-format clip as player-animator format");

        HumanoidAnimationController controller = new HumanoidAnimationController(
                (ctrl, state, setter) -> setter.setAnimation(RawAnimation.begin().thenLoop(animation)),
                MolangLoader::createNewEngine);

        AnimationData data = new AnimationData(0f, 0f, false);
        controller.tick(data);
        controller.setupAnim(data);

        PlayerAnimBone arm = controller.get3DTransform(new PlayerAnimBone("right_arm"));
        assertEquals((float) Math.toRadians(-90), arm.getRotX(), 1.0e-4f,
                "PAL changed the sign or the unit of a GeckoLib rotation keyframe");
    }

    /**
     * Q11: a mod-registered Molang query, read live out of a keyframe.
     *
     * <p>The probe clip's {@code left_arm} rotation is the expression
     * {@code query.spike_propulsion * 90}. Nothing in PAL knows that name; it is registered here
     * through {@code MolangEvent}, which PAL fires while building each controller's engine and
     * before it freezes the binding. Two different values of the gameplay signal are sampled to
     * prove the query is read every frame rather than folded away at load.
     */
    @Test
    void modRegisteredMolangQueryIsReadFromAClip() throws IOException {
        float[] propulsion = {0f};
        com.zigythebird.playeranimcore.event.MolangEvent.MOLANG_EVENT.register(
                (ctrl, engine, binding) -> MolangLoader.setDoubleQuery(
                        binding, "spike_propulsion", c -> propulsion[0]));

        Animation animation = loadProbeClip().get("spike_pal_probe");
        HumanoidAnimationController controller = new HumanoidAnimationController(
                (ctrl, state, setter) -> setter.setAnimation(RawAnimation.begin().thenLoop(animation)),
                MolangLoader::createNewEngine);

        propulsion[0] = 1f;
        AnimationData data = new AnimationData(0f, 0f, false);
        controller.tick(data);
        controller.setupAnim(data);
        float atFull = controller.get3DTransform(new PlayerAnimBone("left_arm")).getRotX();

        propulsion[0] = 0.5f;
        controller.setupAnim(new AnimationData(0f, 0f, false));
        float atHalf = controller.get3DTransform(new PlayerAnimBone("left_arm")).getRotX();

        assertEquals((float) Math.toRadians(90), atFull, 1.0e-4f,
                "a mod-registered Molang query did not reach the clip");
        assertEquals((float) Math.toRadians(45), atHalf, 1.0e-4f,
                "the Molang query was evaluated once and cached rather than read per frame");
    }

    private static Map<String, Animation> loadProbeClip() throws IOException {
        Path clip = Path.of("src/main/resources/assets/wizards_and_beasts/player_animations/spike_pal_probe.json");
        assertTrue(Files.exists(clip), "spike clip missing at " + clip.toAbsolutePath());
        JsonObject json = com.zigythebird.playeranimcore.PlayerAnimLib.GSON
                .fromJson(Files.readString(clip), JsonObject.class);
        return UniversalAnimLoader.loadAnimations(json);
    }

    private static PlayerAnimBone seed(ModelPart part, String name) {
        PlayerAnimBone bone = new PlayerAnimBone(name);
        com.zigythebird.playeranim.util.RenderUtil.copyVanillaPart(part, bone);
        return bone;
    }

    @SuppressWarnings("unused")
    private static PlayState unusedPlayStateImport() {
        return PlayState.CONTINUE;
    }

    @SuppressWarnings("unused")
    private static IAnimation unusedIAnimationImport() {
        return null;
    }
}
