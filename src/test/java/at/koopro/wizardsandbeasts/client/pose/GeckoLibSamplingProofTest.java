package at.koopro.wizardsandbeasts.client.pose;

import it.unimi.dsi.fastutil.objects.Reference2DoubleMap;
import it.unimi.dsi.fastutil.objects.Reference2DoubleMaps;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.animation.object.EasingType;
import software.bernie.geckolib.animation.state.ControllerState;
import software.bernie.geckolib.animation.state.EasingState;
import software.bernie.geckolib.cache.animation.Keyframe;
import software.bernie.geckolib.loading.math.MathValue;
import software.bernie.geckolib.loading.math.value.Constant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The proof gate for the keyframe pass: can a baked GeckoLib clip be sampled without a renderer?
 *
 * <p>This decides the shape of the whole pass, so it is settled before anything is built on it.
 * GeckoLib evaluates a keyframe through {@code MathValue.get(ControllerState)}, and
 * {@code ControllerState} carries a {@code GeoRenderState} — which the player has none of, because
 * the player is rendered by vanilla and never goes through a {@code GeoRenderer}. If that field
 * turns out to be load-bearing for ordinary keyframes, the pass cannot reuse GeckoLib's evaluator
 * and has to interpolate the baked data itself.
 *
 * <p>The answer is that it is not load-bearing for constant-valued keyframes, which is what
 * Blockbench exports unless the author writes a molang expression. That is the narrow claim these
 * tests pin, and the last test pins the boundary of it so nobody widens the claim by accident.
 */
class GeckoLibSamplingProofTest {

    private static final Reference2DoubleMap<software.bernie.geckolib.loading.math.value.Variable> NO_QUERIES =
            Reference2DoubleMaps.emptyMap();

    /**
     * A controller state with no renderer behind it.
     *
     * <p>Every field a keyframe evaluation does not read is null. If GeckoLib ever starts reading one
     * of them for constant keyframes, this blows up here rather than as an NPE inside a render pass.
     */
    private static ControllerState headless() {
        return new ControllerState(null, null, 0.0, 0, false, null, null, NO_QUERIES);
    }

    @Test
    void controllerStateCanBeBuiltWithoutARenderState() {
        assertDoesNotThrow(GeckoLibSamplingProofTest::headless,
                "ControllerState validates its render state — the pass cannot use GeckoLib's evaluator");
    }

    /** A constant is what a plain Blockbench keyframe bakes to. It must not consult the state. */
    @Test
    void constantKeyframeValuesEvaluateWithoutARenderState() {
        MathValue constant = new Constant(42.5);
        assertEquals(42.5, constant.get(headless()), 1e-9);
        assertFalse(constant.isMutable(), "a constant that reports itself mutable would be re-read per frame");
    }

    /**
     * The real question: does GeckoLib's own interpolator run headless?
     *
     * <p>Sampling by hand would mean reimplementing thirty easing curves and getting them subtly
     * different from the ones the clip was authored against.
     */
    @Test
    void linearEasingInterpolatesWithoutARenderState() {
        EasingState easing = new EasingState(EasingType.LINEAR, new MathValue[0], 0.5, 0.0, 90.0);
        assertEquals(45.0, EasingType.LINEAR.apply(easing, headless()), 1e-6);
    }

    /** Both ends, so a passing midpoint cannot be a coincidence of a stubbed-out return. */
    @Test
    void easingReachesBothEndpoints() {
        assertEquals(10.0, EasingType.LINEAR.apply(
                new EasingState(EasingType.LINEAR, new MathValue[0], 0.0, 10.0, 70.0), headless()), 1e-6);
        assertEquals(70.0, EasingType.LINEAR.apply(
                new EasingState(EasingType.LINEAR, new MathValue[0], 1.0, 10.0, 70.0), headless()), 1e-6);
    }

    /** A non-linear curve exercises {@code buildTransformer}, which linear can short-circuit past. */
    @Test
    void nonLinearEasingAlsoRunsHeadless() {
        double eased = EasingType.EASE_IN_OUT_SINE.apply(
                new EasingState(EasingType.EASE_IN_OUT_SINE, new MathValue[0], 0.5, 0.0, 100.0), headless());
        assertEquals(50.0, eased, 1e-6, "the midpoint of a symmetric ease is the midpoint");

        double quarter = EasingType.EASE_IN_OUT_SINE.apply(
                new EasingState(EasingType.EASE_IN_OUT_SINE, new MathValue[0], 0.25, 0.0, 100.0), headless());
        assertTrue(quarter < 25.0, "ease-in-out must be behind linear at a quarter, was " + quarter);
    }

    /** A whole keyframe, assembled the way the baked cache holds one, sampled end to end. */
    @Test
    void aBakedKeyframeSamplesEndToEnd() {
        Keyframe keyframe = new Keyframe(0.0, 10.0, new Constant(0.0), new Constant(180.0), EasingType.LINEAR);
        ControllerState state = headless();

        double from = keyframe.startValue().get(state);
        double to = keyframe.endValue().get(state);
        double atThreeTicks = keyframe.easingType().apply(
                new EasingState(keyframe.easingType(), keyframe.easingArgs(),
                        3.0 / keyframe.length(), from, to), state);

        assertEquals(54.0, atThreeTicks, 1e-6, "three ticks into a ten-tick 0->180 sweep");
        assertTrue(keyframe.getUsedVariables().isEmpty(),
                "a constant keyframe must declare no variables — that is what makes it safe to sample headless");
    }

    /**
     * The boundary. A keyframe whose value is a molang query does need the render state, and this
     * pins that so the claim above is not quietly widened into "GeckoLib works headless".
     *
     * <p>{@code getUsedVariables} is the cheap check for it, so the loader can reject such a clip at
     * load time with a clear message instead of throwing mid-render.
     */
    @Test
    void variableBackedKeyframesAreDetectableBeforeTheyAreEvaluated() {
        var variable = new software.bernie.geckolib.loading.math.value.Variable(
                "query.anim_time", (ControllerState ignored) -> 0.0);
        Keyframe keyframe = new Keyframe(0.0, 10.0, new Constant(0.0), variable, EasingType.LINEAR);

        assertFalse(keyframe.getUsedVariables().isEmpty(),
                "a molang-backed keyframe must be identifiable without evaluating it");
        assertTrue(variable.isMutable());
    }
}
