package at.koopro.wizardsandbeasts.spell.beam;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which caster currently "owns" a beam-channel target so two players channelling on the same
 * entity cannot fight over its state. The first caster to affect a target claims it; a second caster is
 * contested and applies nothing until the first releases.
 *
 * <p>A claim is released only by its holder ({@link Map#remove(Object, Object)} is a no-op otherwise), so
 * one caster's end-of-channel cleanup can never strip effects or restore gravity on a target another
 * caster is still holding. All claim sites have a matching release on the beam-session lifecycle
 * (target change, target loss, and {@code endChannel}, which fires on release, death, logout, respawn and
 * dimension change), so entries do not leak.
 */
final class BeamTargetClaims {

    private static final Map<UUID, UUID> CLAIMS = new ConcurrentHashMap<>();

    private BeamTargetClaims() {}

    /**
     * Claims {@code target} for {@code caster}. Returns {@code true} if the target is now held by this
     * caster — either freshly claimed or already theirs — and {@code false} if another caster holds it.
     */
    static boolean claim(UUID target, UUID caster) {
        UUID holder = CLAIMS.putIfAbsent(target, caster);
        return holder == null || holder.equals(caster);
    }

    /** Releases the claim on {@code target}, but only if {@code caster} is the current holder. */
    static void release(UUID target, UUID caster) {
        CLAIMS.remove(target, caster);
    }
}
