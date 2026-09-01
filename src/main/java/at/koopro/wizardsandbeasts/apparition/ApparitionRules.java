package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.apparition.splinch.WindupDamageMode;
import org.jspecify.annotations.NullMarked;

/**
 * The tunable rules of Apparition, in one place.
 *
 * <p>Exactly one so far, and it is here rather than inline so that the answer to "where is this decided?"
 * has a single file to point at.
 *
 * <h2>Why this is not a config key</h2>
 *
 * <p>Apparition's three {@code Config} keys were deliberately deleted at {@code 0.1.0-alpha.1}, and the
 * surface that replaced them — per-module settings, world-owned and operator-editable — currently has no
 * registered schemas at all. Being its first user means shipping sync, command and screen paths that have
 * never run, and none of that can be verified without a live server. So the mode is chosen here, in code,
 * and {@link WindupDamageMode} is deliberately {@code StringRepresentable} with a codec so that registering
 * it as a module setting later is a registration rather than a rewrite.
 */
@NullMarked
public final class ApparitionRules {

    private static final WindupDamageMode WINDUP_DAMAGE_MODE = WindupDamageMode.HYBRID;

    private ApparitionRules() {}

    /** How being hit mid-wind-up is treated. The one line to change until this becomes a module setting. */
    public static WindupDamageMode windupDamageMode() {
        return WINDUP_DAMAGE_MODE;
    }
}
