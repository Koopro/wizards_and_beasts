package at.koopro.wizardsandbeasts.travel;

import at.koopro.wizardsandbeasts.travel.aerial.AerialSurvey;
import at.koopro.wizardsandbeasts.travel.courier.CourierParcel;
import at.koopro.wizardsandbeasts.travel.courier.CourierService;
import at.koopro.wizardsandbeasts.travel.courier.CourierService.Outcome;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Courier parcels can be delivered once, by their courier, in time and far enough away; the survey grid is sound. */
class CourierAndSurveyTest {

    private static final UUID COURIER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID THIEF = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static CourierParcel parcel(UUID id) {
        return new CourierParcel(id, COURIER, Identifier.parse("wizards_and_beasts:broom_shop"),
                Identifier.parse("wizards_and_beasts:apothecary"), BlockPos.ZERO, 0L,
                CourierService.EXPRESS_TICKS, CourierService.DEADLINE_TICKS);
    }

    @Test
    void onlyTheCourierWithTheParcelInFlightDeliversIt() {
        UUID id = UUID.randomUUID();
        CourierParcel parcel = parcel(id);
        TravelRecord inFlight = TravelRecord.EMPTY.withParcelInFlight(id, parcel.deadline());
        double far = CourierService.MIN_DISTANCE + 1;

        assertEquals(Outcome.DELIVERED, CourierService.judge(parcel, COURIER, inFlight, 100L, far));
        assertEquals(Outcome.NOT_YOURS, CourierService.judge(parcel, THIEF, inFlight, 100L, far),
                "a parcel handed to someone else");
        TravelRecord newer = TravelRecord.EMPTY.withParcelInFlight(UUID.randomUUID(), parcel.deadline());
        assertEquals(Outcome.NOT_IN_FLIGHT, CourierService.judge(parcel, COURIER, newer, 100L, far),
                "a stashed parcel cashed alongside a newer one");
        assertEquals(Outcome.NOT_IN_FLIGHT, CourierService.judge(parcel, COURIER, inFlight.withParcelDelivered(false),
                100L, far), "a copy of a parcel already delivered");
        assertEquals(Outcome.LAPSED, CourierService.judge(parcel, COURIER, inFlight, parcel.deadline(), far));
        assertEquals(Outcome.TOO_CLOSE, CourierService.judge(parcel, COURIER, inFlight, 100L,
                CourierService.MIN_DISTANCE - 1), "two keepers next door to each other");
    }

    @Test
    void oneParcelInFlightAndTheSlotFreesAtTheDeadline() {
        UUID id = UUID.randomUUID();
        TravelRecord record = TravelRecord.EMPTY.withParcelInFlight(id, 500L);
        assertTrue(record.hasParcelInFlight(499L));
        assertFalse(record.hasParcelInFlight(500L), "a lost parcel stops holding the slot when it lapses");
        TravelRecord delivered = record.withParcelDelivered(true);
        assertFalse(delivered.hasParcelInFlight(0L));
        assertEquals(1, delivered.parcelsDelivered());
        assertEquals(1, delivered.expressDeliveries());
    }

    @Test
    void postageGrowsWithDistanceWithinBounds() {
        assertEquals(CourierService.MIN_POSTAGE, CourierService.postage(0, false));
        assertEquals(CourierService.MAX_POSTAGE, CourierService.postage(1_000_000, false));
        assertTrue(CourierService.postage(400, false) > CourierService.postage(200, false));
        assertTrue(CourierService.postage(400, true) > CourierService.postage(400, false));
        // Express is reachable and the deadline is generous: a day to deliver, eight minutes for the bonus.
        assertTrue(CourierService.EXPRESS_TICKS < CourierService.DEADLINE_TICKS);
    }

    @Test
    void theParcelAndTheRecordSurviveSaving() {
        CourierParcel parcel = parcel(UUID.randomUUID());
        assertEquals(parcel, CourierParcel.CODEC.parse(JsonOps.INSTANCE,
                CourierParcel.CODEC.encodeStart(JsonOps.INSTANCE, parcel).getOrThrow()).getOrThrow());
        TravelRecord record = TravelRecord.EMPTY.withTrialFinished("a", 400).withCell(7L).withSite(9L)
                .withFlooSeeded().withParcelInFlight(UUID.randomUUID(), 77L);
        assertEquals(record, TravelRecord.CODEC.parse(JsonOps.INSTANCE,
                TravelRecord.CODEC.encodeStart(JsonOps.INSTANCE, record).getOrThrow()).getOrThrow());
    }

    @Test
    void surveyCellsAre64BlocksAndKeepWorldsApart() {
        assertEquals(AerialSurvey.cellKey(1, 0, 0), AerialSurvey.cellKey(1, 63, 63));
        assertNotEquals(AerialSurvey.cellKey(1, 0, 0), AerialSurvey.cellKey(1, 64, 0));
        assertNotEquals(AerialSurvey.cellKey(1, -1, 0), AerialSurvey.cellKey(1, 0, 0));
        assertNotEquals(AerialSurvey.cellKey(1, 0, 0), AerialSurvey.cellKey(2, 0, 0), "two worlds, one cell");
        TravelRecord record = TravelRecord.EMPTY.withCell(5L);
        assertFalse(record.isNewCell(5L));
        assertTrue(record.isNewCell(6L));
        assertEquals(Set.of(5L), record.withCell(5L).surveyedCells());
        assertTrue(AerialSurvey.SURVEY_ALTITUDE > AerialSurvey.SIGHTING_ALTITUDE);
    }
}
