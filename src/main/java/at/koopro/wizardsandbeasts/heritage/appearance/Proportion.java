package at.koopro.wizardsandbeasts.heritage.appearance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

/**
 * Mechanism A — the player keeps their own skin and their own model, at different proportions.
 *
 * <p>Canon treats part-giants, goblins and vampires as differently-proportioned people rather than
 * different creatures: Hagrid and Madame Maxime are large people, Flitwick is a small one. So this
 * arm is scale and offsets, and deliberately nothing else — the moment it swapped the texture it
 * would stop being a proportion pass and start being {@link FormAppearance}.
 *
 * <p><b>Visual only.</b> This arm writes no attribute and touches no hitbox. The shipped
 * {@code SizeSystemAPI} already applies {@code Attributes.SCALE}, reach, step height and knockback
 * resistance per form, and that behaviour is left exactly as it is — see the Phase 0 addendum §A3 in
 * {@code documentation/AGENT_PROMPT_HERITAGE_FORMS.md}. The prohibition binds new code.
 *
 * <p><b>Bone names are strings, not an enum.</b> The obvious key type is
 * {@code client.pose.PlayerModelPart}, which imports {@code net.minecraft.client.model} and cannot
 * be loaded on a dedicated server. Resolution from name to part happens client-side, which also
 * makes the key set the natural place to state a rig's asset-swap contract.
 *
 * @param scale       uniform visual scale multiplier; 1.0 leaves the model alone
 * @param boneOffsets per-bone translation, keyed by bone name, in model sixteenths — the units a
 *                    {@code ModelPart} uses, <em>not</em> the blocks the pose stack uses. An unknown
 *                    bone name is ignored at resolve time rather than failing the datapack, because a
 *                    name that is meaningful to a future rig should not break the current one.
 */
public record Proportion(float scale, Map<String, Offset> boneOffsets) implements AppearanceMechanism {

    /** A translation in model sixteenths. */
    public record Offset(float x, float y, float z) {
        public static final Codec<Offset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("x", 0.0f).forGetter(Offset::x),
                Codec.FLOAT.optionalFieldOf("y", 0.0f).forGetter(Offset::y),
                Codec.FLOAT.optionalFieldOf("z", 0.0f).forGetter(Offset::z)
        ).apply(instance, Offset::new));

        public boolean isIdentity() {
            return x == 0.0f && y == 0.0f && z == 0.0f;
        }
    }

    /**
     * Scale is bounded rather than free. A player at 20× is not a design choice anyone made on
     * purpose; it is a typo, and it renders as a wall of texture that fills the screen for everyone
     * nearby. The upper bound is comfortably above the largest shipped profile
     * ({@code giant_full}, 3.5×).
     */
    private static final float MIN_SCALE = 0.1f;
    private static final float MAX_SCALE = 8.0f;

    public static final MapCodec<Proportion> CODEC = RecordCodecBuilder.<Proportion>mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("scale", 1.0f).forGetter(Proportion::scale),
            Codec.unboundedMap(Codec.STRING, Offset.CODEC)
                    .optionalFieldOf("boneOffsets", Map.of()).forGetter(Proportion::boneOffsets)
    ).apply(instance, Proportion::new)).validate(Proportion::validate);

    @Override
    public Type type() {
        return Type.PROPORTION;
    }

    /** True when this would change nothing, so a resolver can skip it entirely. */
    public boolean isIdentity() {
        return scale == 1.0f && boneOffsets.values().stream().allMatch(Offset::isIdentity);
    }

    private static DataResult<Proportion> validate(Proportion proportion) {
        if (!(proportion.scale >= MIN_SCALE) || !(proportion.scale <= MAX_SCALE)) {
            return DataResult.error(() -> "proportion scale " + proportion.scale
                    + " is outside [" + MIN_SCALE + ", " + MAX_SCALE + "]");
        }
        for (String bone : proportion.boneOffsets.keySet()) {
            if (bone.isBlank()) {
                return DataResult.error(() -> "proportion boneOffsets contains a blank bone name");
            }
        }
        return DataResult.success(proportion);
    }
}
