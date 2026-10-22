package at.koopro.wizardsandbeasts.wand.refine;

import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Action;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Bench;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Material;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Status;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandRefinementTest {

    private static final Bench PLAIN = new Bench(1.0f, Set.of());
    private static final Bench FURNISHED = new Bench(4.5f, Set.of());
    private static final Bench WITH_LECTERN = new Bench(4.5f, Set.of(WandRefinement.LECTERN));
    private static final Bench WITH_CAULDRON = new Bench(4.5f, Set.of(WandRefinement.CAULDRON));

    private static Status check(Action a, Bench b, boolean grain, Material m, float integrity, WandFlexibility flex) {
        return WandRefinement.check(a, b, true, grain, m, integrity, flex);
    }

    @Test
    void tiersUnlockLooksThenPolishThenRefit() {
        assertEquals(Status.READY, check(Action.HANDLE, PLAIN, false, Material.OTHER_LOG, 1f, WandFlexibility.PLIANT));
        assertEquals(Status.TOO_PLAIN, check(Action.POLISH, PLAIN, false, Material.HONEYCOMB, 0.5f, WandFlexibility.PLIANT));
        assertEquals(Status.READY, check(Action.POLISH, FURNISHED, false, Material.HONEYCOMB, 0.5f, WandFlexibility.PLIANT));
        assertEquals(Status.TOO_PLAIN, check(Action.STIFFER, new Bench(3.9f, Set.of()), true, Material.OWN_WOOD_LOG, 1f,
                WandFlexibility.PLIANT));
    }

    @Test
    void refitNeedsTheGrainOrALectern() {
        assertEquals(Status.NEEDS_GRAIN, check(Action.STIFFER, FURNISHED, false, Material.OWN_WOOD_LOG, 1f, WandFlexibility.PLIANT));
        assertEquals(Status.READY, check(Action.STIFFER, FURNISHED, true, Material.OWN_WOOD_LOG, 1f, WandFlexibility.PLIANT));
        assertEquals(Status.READY, check(Action.SUPPLER, WITH_LECTERN, false, Material.OWN_WOOD_LOG, 1f, WandFlexibility.PLIANT));
    }

    @Test
    void eachActionIsPaidWithItsOwnMaterial() {
        assertEquals(Status.NO_MATERIAL, check(Action.POLISH, FURNISHED, true, Material.NONE, 0.5f, WandFlexibility.PLIANT));
        assertEquals(Status.WRONG_MATERIAL, check(Action.POLISH, FURNISHED, true, Material.OTHER_LOG, 0.5f, WandFlexibility.PLIANT));
        assertEquals(Status.WRONG_MATERIAL, check(Action.STIFFER, FURNISHED, true, Material.OTHER_LOG, 1f, WandFlexibility.PLIANT));
        assertEquals(Status.READY, check(Action.TIP, FURNISHED, true, Material.OWN_WOOD_LOG, 1f, WandFlexibility.PLIANT));
        assertEquals(Status.WRONG_MATERIAL, check(Action.ORNAMENT, FURNISHED, true, Material.HONEYCOMB, 1f, WandFlexibility.PLIANT));
    }

    @Test
    void polishNeverMendsASnappedWandAndDoesNothingToAWholeOne() {
        assertEquals(Status.SNAPPED, check(Action.POLISH, FURNISHED, true, Material.HONEYCOMB, 0.10f, WandFlexibility.PLIANT));
        assertEquals(Status.NOTHING_TO_DO, check(Action.POLISH, FURNISHED, true, Material.HONEYCOMB, 1.0f, WandFlexibility.PLIANT));
        assertEquals(0.25f, WandRefinement.polishAmount(FURNISHED));
        assertEquals(0.40f, WandRefinement.polishAmount(WITH_CAULDRON));
    }

    @Test
    void refitMovesOneStepAndStopsAtTheEnds() {
        assertEquals(Optional.of(WandFlexibility.RIGID), WandRefinement.refit(WandFlexibility.PLIANT, Action.STIFFER));
        assertEquals(Optional.of(WandFlexibility.SUPPLE), WandRefinement.refit(WandFlexibility.PLIANT, Action.SUPPLER));
        assertEquals(Optional.empty(), WandRefinement.refit(WandFlexibility.UNYIELDING, Action.STIFFER));
        assertEquals(Optional.empty(), WandRefinement.refit(WandFlexibility.SPRINGY, Action.SUPPLER));
        assertEquals(Status.NOTHING_TO_DO, check(Action.SUPPLER, FURNISHED, true, Material.OWN_WOOD_LOG, 1f, WandFlexibility.SPRINGY));
    }

    @Test
    void looksCycleThroughTheModulesAndAnOrnamentCanBeTakenOff() {
        Identifier a = Identifier.parse("w:a");
        Identifier b = Identifier.parse("w:b");
        List<Identifier> choices = List.of(b, a);
        assertEquals(Optional.of(b), WandRefinement.nextModule(choices, a, false));
        assertEquals(Optional.of(a), WandRefinement.nextModule(choices, b, false), "a required slot wraps");
        assertEquals(Optional.empty(), WandRefinement.nextModule(choices, b, true), "an ornament comes off after the last");
        assertEquals(Optional.of(a), WandRefinement.nextModule(choices, null, true), "and goes back on at the first");
    }

    @Test
    void noWandNoWork() {
        assertEquals(Status.NO_WAND, WandRefinement.check(Action.POLISH, FURNISHED, false, true, Material.HONEYCOMB, 0.5f, null));
        assertTrue(Action.STIFFER.refit() && Action.HANDLE.look() && !Action.POLISH.look());
        assertFalse(WandRefinement.pays(Action.POLISH, Material.OWN_WOOD_LOG));
    }
}
