package at.koopro.wizardsandbeasts.wand.refine;

import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Action;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Hands;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Bench;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Material;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Status;
import at.koopro.wizardsandbeasts.wand.bench.BenchTool;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandRefinementTest {

    private static final Bench PLAIN = Bench.BARE;
    /** Chisel and knife, no varnish pot. */
    private static final Bench FURNISHED = new Bench(BenchTool.CHISEL.bit() | BenchTool.KNIFE.bit());
    private static final Bench WITH_VARNISH = new Bench(BenchTool.VARNISH.bit());

    private static Status check(Action a, Bench b, boolean grain, Material m, float integrity, WandFlexibility flex) {
        return WandRefinement.check(a, b, true, grain, m, integrity, flex);
    }

    @Test
    void aBareBenchPolishesAndChangesLooksButReFitsOnlyWithTheChisel() {
        assertEquals(Status.READY, check(Action.HANDLE, PLAIN, false, Material.OTHER_LOG, 1f, WandFlexibility.PLIANT));
        assertEquals(Status.READY, check(Action.POLISH, PLAIN, false, Material.HONEYCOMB, 0.5f, WandFlexibility.PLIANT));
        assertEquals(Status.NEEDS_TOOL, check(Action.STIFFER, WITH_VARNISH, true, Material.OWN_WOOD_LOG, 1f,
                WandFlexibility.PLIANT));
        assertEquals(BenchTool.CHISEL, Action.SUPPLER.requiredTool());
        assertEquals(null, Action.POLISH.requiredTool());
    }

    @Test
    void refitNeedsTheGrainAndNothingStandsInForIt() {
        assertEquals(Status.NEEDS_GRAIN, check(Action.STIFFER, FURNISHED, false, Material.OWN_WOOD_LOG, 1f, WandFlexibility.PLIANT));
        assertEquals(Status.READY, check(Action.STIFFER, FURNISHED, true, Material.OWN_WOOD_LOG, 1f, WandFlexibility.PLIANT));
        assertEquals(Status.NEEDS_GRAIN, check(Action.SUPPLER, new Bench(31), false, Material.OWN_WOOD_LOG, 1f,
                WandFlexibility.PLIANT), "every tool on the bench is still not knowing the grain");
    }

    @Test
    void eachActionIsPaidWithItsOwnMaterial() {
        assertEquals(Status.NO_MATERIAL, check(Action.POLISH, FURNISHED, true, Material.NONE, 0.5f, WandFlexibility.PLIANT));
        assertEquals(Status.WRONG_MATERIAL, check(Action.POLISH, FURNISHED, true, Material.OTHER_LOG, 0.5f, WandFlexibility.PLIANT));
        assertEquals(Status.WRONG_MATERIAL, check(Action.STIFFER, FURNISHED, true, Material.OTHER_LOG, 1f, WandFlexibility.PLIANT));
        assertEquals(Status.READY, check(Action.HANDLE, FURNISHED, true, Material.OWN_WOOD_LOG, 1f, WandFlexibility.PLIANT));
        assertEquals(Status.WRONG_MATERIAL, check(Action.ORNAMENT, FURNISHED, true, Material.HONEYCOMB, 1f, WandFlexibility.PLIANT));
    }

    @Test
    void polishNeverMendsASnappedWandAndDoesNothingToAWholeOne() {
        assertEquals(Status.SNAPPED, check(Action.POLISH, FURNISHED, true, Material.HONEYCOMB, 0.10f, WandFlexibility.PLIANT));
        assertEquals(Status.NOTHING_TO_DO, check(Action.POLISH, FURNISHED, true, Material.HONEYCOMB, 1.0f, WandFlexibility.PLIANT));
        assertEquals(0.25f, WandRefinement.polishAmount(FURNISHED));
        assertEquals(0.40f, WandRefinement.polishAmount(WITH_VARNISH));
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

    @Test
    void anotherWizardsWandIsAWandmakersWork() {
        Hands stranger = new Hands(true, false, 0f);
        Hands wandmaker = new Hands(true, true, 0f);
        assertEquals(Status.NOT_YOURS, WandRefinement.check(Action.HANDLE, FURNISHED, true, true, Material.OTHER_LOG,
                1f, WandFlexibility.PLIANT, stranger));
        assertEquals(Status.READY, WandRefinement.check(Action.HANDLE, FURNISHED, true, true, Material.OTHER_LOG,
                1f, WandFlexibility.PLIANT, wandmaker));
        assertEquals(Status.READY, WandRefinement.check(Action.POLISH, FURNISHED, true, true, Material.HONEYCOMB,
                0.5f, WandFlexibility.PLIANT, Hands.OWN));
    }

    @Test
    void aWandmakersPolishCleansesASoundButDarkenedWand() {
        assertEquals(Status.NOTHING_TO_DO, WandRefinement.check(Action.POLISH, FURNISHED, true, true,
                Material.HONEYCOMB, 1f, WandFlexibility.PLIANT, new Hands(false, false, 0.6f)));
        assertEquals(Status.READY, WandRefinement.check(Action.POLISH, FURNISHED, true, true,
                Material.HONEYCOMB, 1f, WandFlexibility.PLIANT, new Hands(false, true, 0.6f)));
        assertEquals(Status.NOTHING_TO_DO, WandRefinement.check(Action.POLISH, FURNISHED, true, true,
                Material.HONEYCOMB, 1f, WandFlexibility.PLIANT, new Hands(false, true, 0f)));
    }
}
