package at.koopro.wizardsandbeasts.brew.effect;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * When a component runs.
 *
 * <p>The brewing equivalent of {@code EffectCadence}, and deliberately much smaller: a spell has a
 * channel with per-tick phases, a potion has two moments worth naming.
 *
 * <p>Defaulting to {@link #ON_DRINK} is what lets every brew written before phases existed parse and
 * behave identically — an authored component with no {@code phase} field is a thing that happens when
 * somebody drinks it, which is what all of them were.
 */
public enum BrewEffectPhase implements StringRepresentable {

    /** The normal case: somebody drank it. */
    ON_DRINK("on_drink"),

    /**
     * The moment the cauldron finishes, before anybody bottles anything.
     *
     * <p>For brews whose interesting behaviour is in the making rather than the drinking — a potion
     * that gasses the room, one that marks the brewer, one that reacts with what is around the pot.
     * The subject is the <em>brewer</em>, not a drinker, and there is no potency to scale by.
     */
    ON_BREW_COMPLETE("on_brew_complete");

    public static final Codec<BrewEffectPhase> CODEC = StringRepresentable.fromValues(BrewEffectPhase::values);

    private final String serializedName;

    BrewEffectPhase(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
