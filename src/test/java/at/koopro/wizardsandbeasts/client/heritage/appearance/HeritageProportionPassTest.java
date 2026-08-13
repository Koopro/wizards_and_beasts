package at.koopro.wizardsandbeasts.client.heritage.appearance;

import at.koopro.wizardsandbeasts.client.pose.PartPoseData;
import at.koopro.wizardsandbeasts.client.pose.PlayerModelPart;
import at.koopro.wizardsandbeasts.client.pose.PoseBand;
import at.koopro.wizardsandbeasts.client.pose.PoseBuilder;
import at.koopro.wizardsandbeasts.client.pose.PoseOpType;
import at.koopro.wizardsandbeasts.client.pose.PoseTarget;
import at.koopro.wizardsandbeasts.heritage.appearance.Proportion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What Mechanism A emits.
 *
 * <p>Exercises the op emission directly rather than through {@code pose(builder, context)}, because a
 * {@code PoseContext} needs a live {@code PlayerModel} and an {@code AvatarRenderState}. The pass
 * splits the two for exactly this reason — {@code pose} reads the render state and delegates here.
 */
class HeritageProportionPassTest {

    private static PoseBuilder poseWith(Proportion proportion) {
        PoseBuilder builder = new PoseBuilder();
        HeritageProportionPass.apply(builder, proportion);
        return builder;
    }

    private static float valueOf(PartPoseData data, PoseTarget target) {
        for (PartPoseData.Op op : data.ops()) {
            if (op.target() == target) {
                return op.value();
            }
        }
        throw new AssertionError("no op for " + target);
    }

    @Test
    void scaleGoesOnTheVirtualBodyPart_notOnIndividualLimbs() {
        PoseBuilder builder = poseWith(new Proportion(1.6f, Map.of()));

        assertEquals(Map.of(PlayerModelPart.BODY, builder.parts().get(PlayerModelPart.BODY)).keySet(),
                builder.parts().keySet(),
                "scale addresses the whole avatar; scaling limbs individually pulls the model apart");

        PartPoseData body = builder.parts().get(PlayerModelPart.BODY);
        assertNotNull(body);
        assertEquals(1.6f, valueOf(body, PoseTarget.X_SCALE));
        assertEquals(1.6f, valueOf(body, PoseTarget.Y_SCALE));
        assertEquals(1.6f, valueOf(body, PoseTarget.Z_SCALE));
    }

    @Test
    void scaleIsSetNotAdded() {
        PoseBuilder builder = poseWith(new Proportion(3.5f, Map.of()));
        for (PartPoseData.Op op : builder.parts().get(PlayerModelPart.BODY).ops()) {
            assertSame(PoseOpType.SET, op.type(),
                    "ADD on a scale target is multiplicative around 1.0, which would make 3.5 mean 4.5x");
        }
    }

    @Test
    void boneOffsetsLandOnTheirParts() {
        PoseBuilder builder = poseWith(new Proportion(1.0f, Map.of(
                "head", new Proportion.Offset(0f, -0.5f, 0f),
                "right_arm", new Proportion.Offset(0.25f, 0f, 0.1f))));

        PartPoseData head = builder.parts().get(PlayerModelPart.HEAD);
        assertNotNull(head, "head offset should reach the HEAD part");
        assertEquals(-0.5f, valueOf(head, PoseTarget.Y));

        PartPoseData arm = builder.parts().get(PlayerModelPart.RIGHT_ARM);
        assertNotNull(arm);
        assertEquals(0.25f, valueOf(arm, PoseTarget.X));
        assertEquals(0.1f, valueOf(arm, PoseTarget.Z));
    }

    /** {@code body} is the torso. Offering the virtual whole-avatar part here would mix units. */
    @Test
    void bodyResolvesToTheTorsoNotTheWholeAvatar() {
        PoseBuilder builder = poseWith(new Proportion(1.0f,
                Map.of("body", new Proportion.Offset(0f, 1f, 0f))));

        assertTrue(builder.parts().containsKey(PlayerModelPart.CHEST));
        assertFalse(builder.parts().containsKey(PlayerModelPart.BODY),
                "a bone offset must never address the pose stack; its units are blocks, not sixteenths");
    }

    @Test
    void offsetsAreAddedSoVanillaAnimationSurvives() {
        PoseBuilder builder = poseWith(new Proportion(1.0f,
                Map.of("left_leg", new Proportion.Offset(0.3f, 0f, 0f))));

        for (PartPoseData.Op op : builder.parts().get(PlayerModelPart.LEFT_LEG).ops()) {
            assertSame(PoseOpType.ADD, op.type(),
                    "SET on a translation would freeze the part and discard the walk cycle");
        }
    }

    @Test
    void unknownBoneNames_areIgnoredRatherThanFatal() {
        HeritageProportionPass.clearUnknownBoneLog();
        PoseBuilder builder = poseWith(new Proportion(1.0f, Map.of(
                "tail", new Proportion.Offset(0f, 1f, 0f),
                "head", new Proportion.Offset(0f, -0.5f, 0f))));

        assertTrue(builder.parts().containsKey(PlayerModelPart.HEAD),
                "a name meant for a future rig must not stop the rest of the entry drawing");
        assertEquals(1, builder.parts().size());
    }

    @Test
    void boneNamesAreCaseInsensitive() {
        PoseBuilder builder = poseWith(new Proportion(1.0f,
                Map.of("Right_Arm", new Proportion.Offset(1f, 0f, 0f))));
        assertTrue(builder.parts().containsKey(PlayerModelPart.RIGHT_ARM));
    }

    @Test
    void anIdentityProportion_emitsNothing() {
        assertTrue(poseWith(new Proportion(1.0f, Map.of())).isEmpty());
        assertTrue(poseWith(new Proportion(1.0f,
                Map.of("head", new Proportion.Offset(0f, 0f, 0f)))).isEmpty(),
                "an all-zero offset is not a reason to touch the model");
    }

    @Test
    void aNullProportion_emitsNothing() {
        PoseBuilder builder = new PoseBuilder();
        HeritageProportionPass.apply(builder, null);
        assertTrue(builder.isEmpty());
    }

    @Test
    void theTextureIsUnreachableFromHere() {
        // Mechanism A preserves the player's own skin. The pass has no texture op to emit, so this is
        // enforced by construction rather than by discipline — assert the vocabulary stays that way.
        for (PoseTarget target : PoseTarget.values()) {
            assertTrue(List.of(PoseTarget.Group.TRANSLATE, PoseTarget.Group.TRANSLATE_POST,
                            PoseTarget.Group.ROTATE, PoseTarget.Group.SCALE).contains(target.group()),
                    "a pose op that could address anything but transform would break skin preservation");
        }
    }

    @Test
    void thePassSitsInTheTransformBand() {
        assertTrue(PoseBand.TRANSFORM.contains(HeritageProportionPass.PRIORITY),
                "proportion is the most structural thing that can happen to a body; it resolves last");
    }

    // ── residual scale ──

    @Test
    void withNoActiveForm_theDeclaredScaleIsUsedVerbatim() {
        assertEquals(1.6f, HeritageAppearanceRenderState.residualScale(1.6f, 1.0f));
    }

    @Test
    void anActiveFormScale_isDividedBackOutSoTheProductIsTheDeclaredScale() {
        float declared = 1.6f;
        float formScale = 1.6f;
        float residual = HeritageAppearanceRenderState.residualScale(declared, formScale);
        assertEquals(declared, residual * formScale, 1e-5f,
                "the datapack entry wins; it must not multiply with the form's own scale");
    }

    @Test
    void aCorruptFormScale_isIgnoredRatherThanDividedBy() {
        assertEquals(2.0f, HeritageAppearanceRenderState.residualScale(2.0f, 0.0f));
        assertEquals(2.0f, HeritageAppearanceRenderState.residualScale(2.0f, -1.0f));
    }
}
