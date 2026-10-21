package at.koopro.wizardsandbeasts.treasure;

import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.location.PlayerLocationStanding;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerTrack;
import at.koopro.wizardsandbeasts.owl.career.track.CareerTracks;
import at.koopro.wizardsandbeasts.owl.career.track.ContractDefinition;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.ActiveContract;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.TrackRecord;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An expedition is not a separate thing: it is any contract in hand — at a location or in a career — whose work is
 * returning treasure from named sites. This reads which sites a player is on an expedition to right now.
 */
@NullMarked
public final class Expeditions {

    private Expeditions() {}

    /** The treasure sites named by {@code contract}'s objective, if it is treasure work. */
    public static List<Identifier> sitesOf(ContractDefinition contract) {
        if (contract.objective() instanceof ContractDefinition.Activity activity
                && activity.filter().activity() == ActivityType.TREASURE_RETURNED) {
            return activity.filter().species();
        }
        return List.of();
    }

    public static Set<Identifier> targets(ServerPlayer player) {
        Set<Identifier> sites = new HashSet<>();
        PlayerLocationStanding standing = player.getData(ModAttachments.LOCATION_STANDING.get());
        for (Map.Entry<Identifier, LocationDefinition> entry : Locations.all().entrySet()) {
            collect(entry.getValue().standing(), standing.at(entry.getKey().toString()), sites);
        }
        Profession profession = player.getData(ModAttachments.PROFESSION_DATA.get()).profession();
        CareerTrack career = CareerTracks.get(profession);
        if (profession != null && career != null) {
            collect(career, player.getData(ModAttachments.CAREER_PROGRESS.get())
                    .track(profession.name().toLowerCase(Locale.ROOT)), sites);
        }
        return sites;
    }

    private static void collect(CareerTrack track, TrackRecord record, Set<Identifier> into) {
        for (ActiveContract held : record.active()) {
            ContractDefinition contract = track.contract(held.id());
            if (contract != null) {
                into.addAll(sitesOf(contract));
            }
        }
    }
}
