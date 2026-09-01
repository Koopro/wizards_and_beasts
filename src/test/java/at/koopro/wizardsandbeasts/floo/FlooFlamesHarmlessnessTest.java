package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.block.floo.FlooFlamesBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one promise this feature cannot be allowed to break: standing in Floo flames must not burn.
 *
 * <p>The guarantee is structural rather than behavioural — the flames are harmless because nothing in
 * the game believes they are fire, not because a list of damage paths has been overridden. That is
 * exactly what makes it worth pinning here: what has to hold is that the class never acquires a fire
 * block in its ancestry, since {@link BaseFireBlock#entityInside} is where ignition and {@code inFire}
 * damage come from, and a subclass inherits them whether or not anyone remembers to look.
 *
 * <p>No block is instantiated. {@code Block}'s constructor claims an intrusive holder in the block
 * registry, which {@code Bootstrap.bootStrap} freezes — and other suites in this source set do call
 * it, so a test that built its own block would pass or fail depending on which class the runner
 * happened to reach first. Everything below therefore reads the type and its property constants,
 * which need no registry at all. Shapes, lighting and the step-in offer need a world and belong to
 * the in-game pass.
 */
class FlooFlamesHarmlessnessTest {

    // ── the safety design ──

    @Test
    void isNotAFireBlockAnywhereInItsAncestry() {
        assertFalse(BaseFireBlock.class.isAssignableFrom(FlooFlamesBlock.class),
                "Floo flames must not inherit BaseFireBlock.entityInside — that is where FIRE_IGNITE "
                        + "and inFire damage come from, and no override list is ever provably complete.");
        assertEquals(Block.class, FlooFlamesBlock.class.getSuperclass(),
                "a plain Block is the guarantee; a different parent is a new set of inherited "
                        + "behaviour to re-audit.");
    }

    // ── the charge ladder ──

    @Test
    void chargesCoverExactlyOneThroughMax() {
        assertEquals(FlooFlamesBlock.MAX_CHARGES, FlooFlamesBlock.CHARGES.getPossibleValues().size());
        assertTrue(FlooFlamesBlock.CHARGES.getPossibleValues().contains(1));
        assertTrue(FlooFlamesBlock.CHARGES.getPossibleValues().contains(FlooFlamesBlock.MAX_CHARGES));
        assertFalse(FlooFlamesBlock.CHARGES.getPossibleValues().contains(0),
                "zero charges is not a state the fire sits in — it is extinguished instead, which is "
                        + "what stops a spent hearth glowing over nothing");
    }

    @Test
    void aPinchOfPowderBuysMoreThanOneHop() {
        // Charges only earn their existence if a lit hearth outlasts a single departure; at one, the
        // whole mechanic collapses back into "powder lights a fire, travel puts it out".
        assertTrue(FlooFlamesBlock.MAX_CHARGES > 1);
    }

    // ── the link back to the hearth ──

    @Test
    void facingIsHorizontalOnlySoTheHearthIsAlwaysReachable() {
        // FlooFlamePlacement falls back to north for a vertical direction. That guard is there for
        // callers passing a hit face; it must never have to cover a value read back off this block,
        // or flames could remember a hearth above or below them and orphan themselves.
        for (Direction facing : FlooFlamesBlock.FACING.getPossibleValues()) {
            assertTrue(facing.getAxis().isHorizontal(), facing + " is not a horizontal facing");
        }
        assertEquals(4, FlooFlamesBlock.FACING.getPossibleValues().size());
    }

    // ── the burn-down clock ──

    @Test
    void aFullFireOutlivesTheHearthsOwnLitTimeout() {
        // FlooFireplaceBlockEntity holds its lit timer at full while flames burn, so the flames are
        // the clock and the hearth must never go dark underneath a fire that is still burning.
        //
        // The comparison is against the WHOLE fire, not one charge. It used to be one charge, which
        // was the stricter claim and stopped being true the moment the hearth timeout moved to ninety
        // seconds: a single sixty-second charge is shorter than that, and nothing is wrong with it,
        // because the timer is held at full for as long as any charge remains. What must hold is that
        // a freshly lit fire outlasts the fallback clock.
        assertTrue(FlooFlamesBlock.MAX_CHARGES * FlooFlamesBlock.TICKS_PER_CHARGE
                        >= FlooFireplaceBlockEntity.litTimeoutTicks(),
                "a full fire must not burn out before the hearth's fallback timer");
    }
}
