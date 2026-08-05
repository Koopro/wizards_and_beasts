package at.koopro.wizardsandbeasts.animagus;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.NullMarked;

/**
 * A power an Animagus beast form confers.
 * <p>
 * Deliberately <em>not</em> a home for the no-hands constraint (no wand, no casting, no inventory,
 * no block interaction, no item use). That is a hard property of being transformed, applied by the
 * transform entry point for every form, and expressing it here would imply it were optional.
 * <p>
 * Aquatic movement is not represented. It is deferred rather than stubbed, so that a datapack
 * naming it fails loudly instead of silently loading a capability nothing implements.
 */
@NullMarked
public enum AnimagusCapability {

    /** Ascend by walking into a climbable surface. */
    CLIMB,

    /**
     * Marker only — the behaviour falls out of the small hitbox rather than any code path. Carried
     * so content authors can state the intent and gating code can query it.
     */
    GAP_SQUEEZE,

    /** Reduced fall damage via the vanilla {@code safe_fall_distance} attribute, not custom fall handling. */
    SAFE_LANDING,

    NIGHT_VISION,

    /** Hostile mobs deprioritise the player as a target. */
    LOW_PROFILE,

    /** Glide-and-flap flight. Requires a {@code flight} block on the form definition. */
    FLIGHT,

    /** Reveals nearby living entities through blocks, to the transformed player alone. */
    SCENT_TRACK,

    /** Extends entity-highlight range and clears distance fog. Confers no combat or interaction benefit. */
    KEEN_SIGHT;

    public static final Codec<AnimagusCapability> CODEC =
            Codec.STRING.xmap(AnimagusCapability::valueOf, AnimagusCapability::name);
}
