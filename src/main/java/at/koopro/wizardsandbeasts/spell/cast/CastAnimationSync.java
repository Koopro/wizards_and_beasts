package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload;
import at.koopro.wizardsandbeasts.spell.core.CastType;
import at.koopro.wizardsandbeasts.spell.core.JsonSpell;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.def.SpellDefinition;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Tells the caster and everyone watching what the casting arm is doing, at the server's own cast moments.
 *
 * <p>Owner decision 2026-11-04 ({@code WAND_CAST_POSE_SCHEMA} §4): the phases come from the cast session, not from a
 * fixed clip. A wand cast is a hold and a release ({@link CastReleaseGate}), so:
 *
 * <ul>
 *   <li><b>hold begins</b> → the wind-up, held for as long as the wand is held (a beam, which channels from its first
 *       tick, holds the strike instead);</li>
 *   <li><b>release accepted and the spell cast</b> → the strike and the settle, timed from that moment, so the strike
 *       lands on the frame the spell leaves;</li>
 *   <li><b>the hold ends without a cast</b> (refused, rejected, interrupted) → off.</li>
 * </ul>
 *
 * <p>All three ride {@link SpellCastAnimationS2CPayload} unchanged. A spell's own {@code castTiming} still overrides the
 * strike's timing; otherwise its cast type picks one.
 */
@NullMarked
public final class CastAnimationSync {

    private CastAnimationSync() {}

    /** Any positive length: a held cast never advances, and zero is the wire's "off". */
    private static final int HELD_TICKS = 1;
    /** Held phases, as {@link SpellCastAnimationS2CPayload#holdPhase()} numbers them. */
    private static final int HOLD_WINDUP = 0;
    private static final int HOLD_STRIKE = 1;
    /** Fraction of the strike-and-settle the strike itself takes. */
    private static final float STRIKE_END = 0.3f;

    /** The hold has begun: gather, or point and channel for a beam. */
    public static void holdStarted(ServerPlayer caster, @Nullable String spellId) {
        boolean beam = isBeam(castType(spellId));
        send(caster, spellId, HELD_TICKS, 0.35f, 0.6f, beam ? HOLD_STRIKE : HOLD_WINDUP);
    }

    /** The release was accepted and the spell cast: strike now, then settle. */
    public static void released(ServerPlayer caster, @Nullable String spellId) {
        SpellDefinition def = definition(spellId);
        if (def != null && def.castTiming().isPresent()) {
            SpellDefinition.CastTiming t = def.castTiming().get();
            send(caster, spellId, t.ticks(), t.windupEnd(), t.releaseEnd(), SpellCastAnimationS2CPayload.NO_HOLD);
            return;
        }
        // The wind-up was the hold, so the timed part starts at the strike.
        send(caster, spellId, strikeTicks(castType(spellId)), 0f, STRIKE_END, SpellCastAnimationS2CPayload.NO_HOLD);
    }

    /** The hold ended without a cast. */
    public static void ended(ServerPlayer caster) {
        send(caster, null, 0, 0f, 0f, SpellCastAnimationS2CPayload.NO_HOLD);
    }

    /** Ticks of strike plus settle: a beam lets go quickly, a sweep (cone) lingers. */
    static int strikeTicks(@Nullable CastType type) {
        if (type == null) {
            return 10;
        }
        return switch (type) {
            case BEAM_CHANNEL, BEAM_LETHAL -> 8;
            case CONE -> 12;
            case PROJECTILE, TARGETED, SELF -> 10;
        };
    }

    static boolean isBeam(@Nullable CastType type) {
        return type == CastType.BEAM_CHANNEL || type == CastType.BEAM_LETHAL;
    }

    /**
     * Any spell's cast type, Java spells included. Reading it off the JSON definition alone left the Killing Curse
     * (a Java beam) holding the wind-up, wand behind the head, while its beam already fired (seen 2026-11-12).
     */
    static @Nullable CastType castType(@Nullable String spellId) {
        Spell spell = spellId == null ? null : Spells.byId(spellId);
        return spell == null || spell.getProperties() == null ? null : spell.getProperties().getCastType();
    }

    private static @Nullable SpellDefinition definition(@Nullable String spellId) {
        Spell spell = spellId == null ? null : Spells.byId(spellId);
        return spell instanceof JsonSpell json ? json.definition() : null;
    }

    private static void send(ServerPlayer caster, @Nullable String spellId, int ticks, float windupEnd,
                             float releaseEnd, int holdPhase) {
        SpellCastAnimationS2CPayload.send(caster, new SpellCastAnimationS2CPayload(
                caster.getId(), spellId == null ? "" : spellId, ticks, windupEnd, releaseEnd, holdPhase));
    }
}
