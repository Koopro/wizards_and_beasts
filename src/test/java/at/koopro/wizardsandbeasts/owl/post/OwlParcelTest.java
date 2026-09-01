package at.koopro.wizardsandbeasts.owl.post;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The scheduling half of the owl post: timing, turn-around and addressing.
 *
 * <p>{@link OwlParcel} holds the only logic in the delivery loop that can be reasoned about without
 * a server — when a parcel has arrived, who it is currently flying to, and what turning it around
 * does. The rest of {@link OwlPostService} is inventory and player lookup, which needs a live world
 * and belongs to the in-game pass.
 */
class OwlParcelTest {

    private static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID RECIPIENT = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @BeforeAll
    static void bootstrapMinecraft() {
        // ItemStack.CODEC and Items both reach BuiltInRegistries.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static OwlParcel outbound(long arrivalTick) {
        return new OwlParcel(UUID.randomUUID(), SENDER, RECIPIENT,
                new ItemStack(Items.DIAMOND, 3), arrivalTick, false);
    }

    // -- timing ----------------------------------------------------------------------------------

    @Test
    void aParcelHasNotArrivedBeforeItsTime() {
        OwlParcel parcel = outbound(1000L);
        assertFalse(parcel.hasArrived(0L));
        assertFalse(parcel.hasArrived(999L));
        assertTrue(parcel.hasArrived(1000L));
        assertTrue(parcel.hasArrived(5000L));
    }

    /**
     * Arrival is absolute game time, not a countdown.
     *
     * <p>The distinction is the whole reason it survives a restart: a relative timer would pause
     * while the server is off, so a parcel sent on Friday would still have two minutes left on
     * Monday. An absolute tick that is already in the past simply delivers on the next sweep.
     */
    @Test
    void aParcelWhoseTimeHasLongPassedDeliversImmediately() {
        assertTrue(outbound(10L).hasArrived(9_999_999L));
    }

    // -- addressing ------------------------------------------------------------------------------

    @Test
    void anOutboundParcelFliesToTheRecipient() {
        assertEquals(RECIPIENT, outbound(100L).currentTarget());
    }

    @Test
    void aReturningParcelFliesToTheSender() {
        OwlParcel returned = outbound(100L).turnedAround(100L, 2400L);
        assertEquals(SENDER, returned.currentTarget());
        assertTrue(returned.returning());
    }

    // -- turn-around -----------------------------------------------------------------------------

    @Test
    void turningAroundRetimesTheFlightRatherThanArrivingInstantly() {
        // A full inventory should read as "the owl is bringing it back", not as an instant bounce.
        OwlParcel returned = outbound(100L).turnedAround(100L, 2400L);
        assertEquals(2500L, returned.arrivalTick());
        assertFalse(returned.hasArrived(2499L));
        assertTrue(returned.hasArrived(2500L));
    }

    @Test
    void turningAroundKeepsIdentityAndPayload() {
        OwlParcel original = outbound(100L);
        OwlParcel returned = original.turnedAround(100L, 2400L);
        assertEquals(original.id(), returned.id(), "the queue replaces by id; a new id would duplicate it");
        assertEquals(original.sender(), returned.sender());
        assertEquals(original.recipient(), returned.recipient());
        assertTrue(ItemStack.isSameItemSameComponents(original.payload(), returned.payload()));
        assertEquals(original.payload().getCount(), returned.payload().getCount());
    }

    @Test
    void aParcelAlreadyReturningStaysAddressedToTheSender() {
        // Guards the loop the service relies on: a returning parcel that failed again must not
        // flip back to the recipient and ping-pong forever.
        OwlParcel returned = outbound(100L).turnedAround(100L, 2400L);
        OwlParcel again = returned.turnedAround(2500L, 2400L);
        assertEquals(SENDER, again.currentTarget());
        assertTrue(again.returning());
    }

    // -- service contract ------------------------------------------------------------------------

    @Test
    void everySendResultCarriesItsOwnMessage() {
        // Each failure state has to say something specific; a shared "could not send" would be the
        // silent-failure this loop exists to avoid.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (OwlPostService.SendResult result : OwlPostService.SendResult.values()) {
            var contents = result.message().getContents();
            assertInstanceOf(net.minecraft.network.chat.contents.TranslatableContents.class, contents,
                    result + " must carry a translation key, not a literal");
            String key = ((net.minecraft.network.chat.contents.TranslatableContents) contents).getKey();
            assertTrue(seen.add(key), result + " shares its message with another outcome: " + key);
        }
        assertEquals(OwlPostService.SendResult.values().length, seen.size());
    }

    @Test
    void onlySentCountsAsSuccess() {
        for (OwlPostService.SendResult result : OwlPostService.SendResult.values()) {
            assertEquals(result == OwlPostService.SendResult.SENT, result.ok(), result.toString());
        }
    }

    @Test
    void theFlightIsLongEnoughToBeAJourneyAndShortEnoughToTest() {
        assertTrue(OwlPostService.FLIGHT_TICKS >= 600L, "an owl must not be a teleporter");
        assertTrue(OwlPostService.FLIGHT_TICKS <= 24000L, "a parcel must arrive within a Minecraft day");
        assertTrue(OwlPostService.SWEEP_INTERVAL_TICKS <= 20,
                "a parcel must never be more than a second late");
    }
}
