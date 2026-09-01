package at.koopro.wizardsandbeasts.apparition.splinch;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import com.mojang.serialization.Codec;

/**
 * What being hit mid-wind-up does to an Apparition.
 *
 * <p>Three answers to one question — can a wizard Apparate out of a fight? — and the question is a balance
 * decision rather than a fact about the world, which is why it is a mode and not a constant.
 *
 * <p>{@link StringRepresentable} and codec-backed on purpose: this is the shape a module setting takes, so
 * exposing it to operators later is a registration rather than a rewrite. See
 * {@link at.koopro.wizardsandbeasts.apparition.ApparitionRules} for where the choice is made today.
 */
@NullMarked
public enum WindupDamageMode implements StringRepresentable {

    /**
     * The default. Being hit never cancels the jump and never leaves it clean: one hit floors the outcome to
     * at least a minor tear, an anchored hold or a second hit floors it to major, and carrying somebody
     * floors it to catastrophic. A wizard under fire can still get out, and will pay for it.
     */
    HYBRID("hybrid"),

    /**
     * Any hit during the wind-up costs the journey.
     *
     * <p>Expressed as {@link SplinchTier#CATASTROPHIC}, because that is the ladder's only rung that does not
     * arrive — so this mode is not merely a cancellation, it is the most expensive outcome in the game. A
     * gentler "the attempt simply stops, at no cost" would need a rung that does not exist. Offered for
     * servers that want Apparition unusable in combat and are content with that price.
     */
    CANCEL("cancel"),

    /**
     * The pre-hybrid behaviour: damage inflates the miss through the ordinary ladder and floors nothing, so
     * a single hit on a well-timed release lands on the minor rung and a perfectly composed anchored jump
     * could still be finished under fire. Kept because it is what shipped, and removing an option nobody
     * asked to lose is not a kindness.
     */
    LENIENT("lenient");

    public static final Codec<WindupDamageMode> CODEC =
            StringRepresentable.fromEnum(WindupDamageMode::values);

    private final String serializedName;

    WindupDamageMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
