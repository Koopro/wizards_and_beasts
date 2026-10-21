package at.koopro.wizardsandbeasts.travel;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What a player has done travelling, kept on the player ({@code ModAttachments.TRAVEL_RECORD}): best time-trial
 * times, parcels delivered, country surveyed from the air, treasure sites spotted, and whether their Floo address
 * book has been seeded from the network as it stood before discovery existed.
 *
 * <p>Immutable; every change returns a copy, so a caller cannot half-update it. The survey and sighting sets are
 * capped — they exist to say "new or not", and a player who has overflown {@link #MAX_CELLS} cells has stopped
 * needing that answer to be exact.
 *
 * @param bestTrialTicks    fastest finish per course (location id), in ticks
 * @param trialsFinished    time trials flown through every ring
 * @param parcelsDelivered  courier parcels delivered in time
 * @param expressDeliveries of those, how many inside the express window
 * @param surveyedCells     survey cells overflown high ({@code AerialSurvey.cellKey})
 * @param sightedSites      treasure sites spotted from the air, by structure start chunk
 * @param flooSeeded        whether the Floo discovery migration has run for this player
 * @param activeParcel      the one courier parcel this player has in flight, if any
 * @param parcelDeadline    game tick that parcel lapses at; meaningless with no parcel
 */
@NullMarked
public record TravelRecord(Map<String, Integer> bestTrialTicks, int trialsFinished, int parcelsDelivered,
                           int expressDeliveries, Set<Long> surveyedCells, Set<Long> sightedSites,
                           boolean flooSeeded, Optional<UUID> activeParcel, long parcelDeadline) {

    /** Survey cells kept before new ones stop counting as new. */
    public static final int MAX_CELLS = 8192;
    /** Sighted treasure sites kept. */
    public static final int MAX_SITES = 1024;

    public static final TravelRecord EMPTY = new TravelRecord(Map.of(), 0, 0, 0, Set.of(), Set.of(), false,
            Optional.empty(), 0L);

    public static final Codec<TravelRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("best_trial_ticks", Map.of())
                    .forGetter(TravelRecord::bestTrialTicks),
            Codec.INT.optionalFieldOf("trials_finished", 0).forGetter(TravelRecord::trialsFinished),
            Codec.INT.optionalFieldOf("parcels_delivered", 0).forGetter(TravelRecord::parcelsDelivered),
            Codec.INT.optionalFieldOf("express_deliveries", 0).forGetter(TravelRecord::expressDeliveries),
            Codec.LONG.listOf().<Set<Long>>xmap(HashSet::new, List::copyOf).optionalFieldOf("surveyed_cells", Set.of())
                    .forGetter(TravelRecord::surveyedCells),
            Codec.LONG.listOf().<Set<Long>>xmap(HashSet::new, List::copyOf).optionalFieldOf("sighted_sites", Set.of())
                    .forGetter(TravelRecord::sightedSites),
            Codec.BOOL.optionalFieldOf("floo_seeded", false).forGetter(TravelRecord::flooSeeded),
            UUIDUtil.CODEC.optionalFieldOf("active_parcel").forGetter(TravelRecord::activeParcel),
            Codec.LONG.optionalFieldOf("parcel_deadline", 0L).forGetter(TravelRecord::parcelDeadline)
    ).apply(i, TravelRecord::new));

    public TravelRecord {
        bestTrialTicks = Map.copyOf(bestTrialTicks);
        surveyedCells = Set.copyOf(surveyedCells);
        sightedSites = Set.copyOf(sightedSites);
    }

    /** The best time on {@code course}, or -1 when it has never been finished. */
    public int bestTicks(String course) {
        return bestTrialTicks.getOrDefault(course, -1);
    }

    /** A finished trial; keeps the faster of the old best and {@code ticks}. */
    public TravelRecord withTrialFinished(String course, int ticks) {
        Map<String, Integer> best = new HashMap<>(bestTrialTicks);
        best.merge(course, ticks, Math::min);
        return new TravelRecord(best, trialsFinished + 1, parcelsDelivered, expressDeliveries, surveyedCells,
                sightedSites, flooSeeded, activeParcel, parcelDeadline);
    }

    public TravelRecord withParcelDelivered(boolean express) {
        return new TravelRecord(bestTrialTicks, trialsFinished, parcelsDelivered + 1,
                expressDeliveries + (express ? 1 : 0), surveyedCells, sightedSites, flooSeeded, Optional.empty(), 0L);
    }

    /** Whether {@code cell} would be new — false once the set is full, so a full set records nothing more. */
    public boolean isNewCell(long cell) {
        return surveyedCells.size() < MAX_CELLS && !surveyedCells.contains(cell);
    }

    public TravelRecord withCell(long cell) {
        if (!isNewCell(cell)) {
            return this;
        }
        Set<Long> cells = new HashSet<>(surveyedCells);
        cells.add(cell);
        return new TravelRecord(bestTrialTicks, trialsFinished, parcelsDelivered, expressDeliveries, cells,
                sightedSites, flooSeeded, activeParcel, parcelDeadline);
    }

    public boolean isNewSite(long site) {
        return sightedSites.size() < MAX_SITES && !sightedSites.contains(site);
    }

    public TravelRecord withSite(long site) {
        if (!isNewSite(site)) {
            return this;
        }
        Set<Long> sites = new HashSet<>(sightedSites);
        sites.add(site);
        return new TravelRecord(bestTrialTicks, trialsFinished, parcelsDelivered, expressDeliveries, surveyedCells,
                sites, flooSeeded, activeParcel, parcelDeadline);
    }

    public TravelRecord withFlooSeeded() {
        return new TravelRecord(bestTrialTicks, trialsFinished, parcelsDelivered, expressDeliveries, surveyedCells,
                sightedSites, true, activeParcel, parcelDeadline);
    }

    /** Whether a parcel is in flight at {@code now} (an expired one no longer holds the slot). */
    public boolean hasParcelInFlight(long now) {
        return activeParcel.isPresent() && now < parcelDeadline;
    }

    public TravelRecord withParcelInFlight(UUID parcel, long deadline) {
        return new TravelRecord(bestTrialTicks, trialsFinished, parcelsDelivered, expressDeliveries, surveyedCells,
                sightedSites, flooSeeded, Optional.of(parcel), deadline);
    }

    public TravelRecord withoutParcel() {
        return new TravelRecord(bestTrialTicks, trialsFinished, parcelsDelivered, expressDeliveries, surveyedCells,
                sightedSites, flooSeeded, Optional.empty(), 0L);
    }
}
