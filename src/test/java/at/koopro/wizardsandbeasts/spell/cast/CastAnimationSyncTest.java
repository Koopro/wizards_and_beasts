package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.CastType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The strike that follows an accepted release: every cast type gets a real, short animation (a zero length is the
 * wire's "off" and would erase the strike), a beam lets go quickest and a sweep lingers, and only the two beam types
 * hold the strike while the wand is held.
 */
class CastAnimationSyncTest {

    @Test
    void everyCastTypeStrikesForAShortPositiveTime() {
        for (CastType type : CastType.values()) {
            int ticks = CastAnimationSync.strikeTicks(type);
            assertTrue(ticks > 0 && ticks <= 20, type + " strikes for " + ticks + " ticks");
        }
        assertTrue(CastAnimationSync.strikeTicks(null) > 0, "a Java spell with no definition still strikes");
    }

    @Test
    void aBeamLetsGoQuickestAndASweepLingers() {
        assertTrue(CastAnimationSync.strikeTicks(CastType.BEAM_CHANNEL) < CastAnimationSync.strikeTicks(CastType.PROJECTILE));
        assertTrue(CastAnimationSync.strikeTicks(CastType.CONE) > CastAnimationSync.strikeTicks(CastType.PROJECTILE));
    }

    @Test
    void onlyBeamsHoldTheStrike() {
        for (CastType type : CastType.values()) {
            boolean beam = type == CastType.BEAM_CHANNEL || type == CastType.BEAM_LETHAL;
            assertTrue(CastAnimationSync.isBeam(type) == beam, type.name());
        }
        assertFalse(CastAnimationSync.isBeam(null));
    }

    @Test
    void aJavaSpellIsReadForItsCastTypeToo() {
        // The Killing Curse is a Java spell: read off JSON alone it had no type, so it held the wind-up while its
        // beam fired from behind the caster's head (spell showcase, 2026-11-12).
        var avada = at.koopro.wizardsandbeasts.spell.core.Spells.AVADA_KEDAVRA;
        assertTrue(CastAnimationSync.castType(avada.getId()) == CastType.BEAM_LETHAL);
        assertTrue(CastAnimationSync.isBeam(CastAnimationSync.castType(avada.getId())));
        assertTrue(CastAnimationSync.castType(null) == null);
    }
}
