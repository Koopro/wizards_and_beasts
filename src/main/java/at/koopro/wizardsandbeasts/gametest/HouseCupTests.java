package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.house.House;
import at.koopro.wizardsandbeasts.house.HouseCup;
import at.koopro.wizardsandbeasts.house.HousePoints;
import at.koopro.wizardsandbeasts.prophet.DailyProphet;
import at.koopro.wizardsandbeasts.prophet.ProphetNews;
import net.minecraft.gametest.framework.GameTestHelper;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/** D1 (systems audit 2026-11-25): the House Cup settles on its cycle boundary, awards the leader, and resets. */
public final class HouseCupTests {

    private HouseCupTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("house_cup_settles_on_the_cycle_boundary",
                "house cup: nothing before the boundary; at it the leader wins, every house resets; a tie wins nothing",
                HouseCupTests::settlesOnTheBoundary);
    }

    private static void settlesOnTheBoundary(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        HousePoints points = HousePoints.get(server);
        int days = HouseCup.rules().cycleDays();
        check(helper, days == 7, () -> "the shipped House Cup cycle is " + days + " days, not a week");

        // Start from a cycle of our own past anything already settled, so other scenarios' points do not leak in.
        HouseCup.settle(server, DailyProphet.day(server));
        long base = Math.max(HouseCup.cycleOf(DailyProphet.day(server)), points.settledCycle()) + 1;
        HouseCup.settle(server, base * days);
        check(helper, points.settledCycle() == base, () -> "setup: cycle " + base + " was not settled");

        int cupsBefore = points.cupsWon(House.GRYFFINDOR);
        points.add(House.GRYFFINDOR, 30);
        points.add(House.SLYTHERIN, 20);

        House early = HouseCup.settle(server, base * days + days - 1);
        check(helper, early == null && points.of(House.GRYFFINDOR) == 30,
                () -> "the Cup was settled a day before the cycle ended");

        House winner = HouseCup.settle(server, (base + 1) * days);
        check(helper, winner == House.GRYFFINDOR, () -> "the leader did not win the Cup: " + winner);
        check(helper, points.cupsWon(House.GRYFFINDOR) == cupsBefore + 1, () -> "the Cup was not counted");
        for (House house : House.values()) {
            check(helper, points.of(house) == 0, () -> house + " kept points into the new cycle");
        }
        boolean printed = ProphetNews.get(server).since(0).stream()
                .anyMatch(s -> s.key().equals("prophet.wizards_and_beasts.story.house_cup"));
        check(helper, printed, () -> "the Prophet did not print the House Cup");

        points.add(House.RAVENCLAW, 5);
        points.add(House.HUFFLEPUFF, 5);
        House tie = HouseCup.settle(server, (base + 2) * days);
        check(helper, tie == null,
                () -> "a tie at the top awarded the Cup to " + tie);
        check(helper, points.of(House.RAVENCLAW) == 0, () -> "a tied cycle did not reset");
        helper.succeed();
    }
}
