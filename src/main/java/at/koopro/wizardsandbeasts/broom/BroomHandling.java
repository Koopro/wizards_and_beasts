package at.koopro.wizardsandbeasts.broom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;

/**
 * How a broom behaves that {@code maxSpeed} and {@code turnSpeed} cannot express: how true it holds
 * a line, how much momentum it keeps, how it acts under boost, and how badly it takes a hit.
 *
 * <p>Every field falls back to {@link #profile()}'s value, and the profile falls back to
 * {@link HandlingProfile#BALANCED}, which reproduces the constants these numbers replaced. A
 * definition that authors nothing here flies as it always did.
 *
 * <p>The one thing that is genuinely new at the default is {@link #yawDrift()} — see
 * {@link HandlingProfile} for why that half-degree is deliberate.
 */
public record BroomHandling(
        HandlingProfile profile,
        float yawDrift,
        float momentumRetention,
        float wobbleAtBoost,
        float boostFovPunch,
        float crashDamageMultiplier,
        int minorImpactDurabilityLoss,
        int severeImpactDurabilityLoss) {

    /** What a broom authoring no handling keys at all gets. */
    public static final BroomHandling DEFAULT = of(HandlingProfile.BALANCED);

    /**
     * The momentum-retention value that reproduces {@code BroomTuning}'s old drag constants exactly.
     * See {@link #coastDrag()} for the mapping this anchors.
     */
    public static final float NEUTRAL_MOMENTUM = 0.90f;

    private static final Codec<HandlingProfile> PROFILE_CODEC = Codec.STRING.comapFlatMap(
            id -> HandlingProfile.byId(id)
                    .map(com.mojang.serialization.DataResult::success)
                    .orElseGet(() -> com.mojang.serialization.DataResult.error(
                            () -> "Unknown handlingProfile '" + id + "' (school, balanced, racing, tank)")),
            HandlingProfile::profileId);

    /** Every field taken from a profile, which is what an unauthored broom resolves to. */
    public static BroomHandling of(HandlingProfile profile) {
        return new BroomHandling(profile, profile.yawDrift(), profile.momentumRetention(),
                profile.wobbleAtBoost(), profile.boostFovPunch(), profile.crashDamageMultiplier(),
                profile.minorImpactDurabilityLoss(), profile.severeImpactDurabilityLoss());
    }

    static BroomHandling decode(BroomFields<?> fields) {
        HandlingProfile profile =
                fields.optional("handlingProfile", PROFILE_CODEC, HandlingProfile.BALANCED);
        return new BroomHandling(
                profile,
                fields.rangedFloat("yawDrift", 0.0f, 0.5f, profile.yawDrift()),
                fields.rangedFloat("momentumRetention", 0.5f, 1.0f, profile.momentumRetention()),
                fields.rangedFloat("wobbleAtBoost", 0.0f, 1.0f, profile.wobbleAtBoost()),
                fields.rangedFloat("boostFovPunch", 0.0f, 0.5f, profile.boostFovPunch()),
                fields.rangedFloat("crashDamageMultiplier", 0.0f, 4.0f, profile.crashDamageMultiplier()),
                fields.rangedInt("minorImpactDurabilityLoss", 0, 32, profile.minorImpactDurabilityLoss()),
                fields.rangedInt("severeImpactDurabilityLoss", 0, 64, profile.severeImpactDurabilityLoss()));
    }

    /**
     * Per-tick horizontal drag while coasting, derived from {@link #momentumRetention()}.
     *
     * <p>{@code momentumRetention} is authored on a readable 0.5–1.0 scale, but a per-tick drag
     * multiplier lives in a narrow band just under 1 — 0.9 per tick would strip 88% of a broom's
     * speed in a second. The mapping is anchored so {@link #NEUTRAL_MOMENTUM} reproduces the old
     * {@code COAST_DRAG} of 0.990 exactly, and the ends of the range stay meaningful: 0.86 coasts
     * down to 76% of its speed over a second where 0.97 keeps 94%.
     */
    public float coastDrag() {
        return 1.0f - (1.0f - momentumRetention) * 0.10f;
    }

    /** As {@link #coastDrag()}, for a tick the rider is holding an input. Anchors to 0.995. */
    public float inputDrag() {
        return 1.0f - (1.0f - momentumRetention) * 0.05f;
    }

    /**
     * Durability lost by a moderate impact — the band between minor and severe.
     *
     * <p>Interpolated rather than authored. The impact scale has three bands and the brief names two
     * values; inventing a third key would make every datapack author choose a number that only ever
     * sits between the other two.
     */
    public int moderateImpactDurabilityLoss() {
        return Math.round((minorImpactDurabilityLoss + severeImpactDurabilityLoss) / 2.0f);
    }

    <T> void encode(RecordBuilder<T> builder, DynamicOps<T> ops) {
        BroomHandling fromProfile = of(profile);
        if (profile != HandlingProfile.BALANCED) {
            builder.add("handlingProfile", PROFILE_CODEC.encodeStart(ops, profile).result().orElseThrow());
        }
        // Only what the profile does not already say, so re-encoding a broom that named a profile
        // and nothing else gives back exactly that one key.
        if (yawDrift != fromProfile.yawDrift()) {
            builder.add("yawDrift", ops.createFloat(yawDrift));
        }
        if (momentumRetention != fromProfile.momentumRetention()) {
            builder.add("momentumRetention", ops.createFloat(momentumRetention));
        }
        if (wobbleAtBoost != fromProfile.wobbleAtBoost()) {
            builder.add("wobbleAtBoost", ops.createFloat(wobbleAtBoost));
        }
        if (boostFovPunch != fromProfile.boostFovPunch()) {
            builder.add("boostFovPunch", ops.createFloat(boostFovPunch));
        }
        if (crashDamageMultiplier != fromProfile.crashDamageMultiplier()) {
            builder.add("crashDamageMultiplier", ops.createFloat(crashDamageMultiplier));
        }
        if (minorImpactDurabilityLoss != fromProfile.minorImpactDurabilityLoss()) {
            builder.add("minorImpactDurabilityLoss", ops.createInt(minorImpactDurabilityLoss));
        }
        if (severeImpactDurabilityLoss != fromProfile.severeImpactDurabilityLoss()) {
            builder.add("severeImpactDurabilityLoss", ops.createInt(severeImpactDurabilityLoss));
        }
    }
}
