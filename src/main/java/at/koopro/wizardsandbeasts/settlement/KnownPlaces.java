package at.koopro.wizardsandbeasts.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * The settlements a player has reached, and whether they have been given a chart to one: theirs alone, kept across
 * deaths.
 *
 * @param sites   site keys, in the order found
 * @param charted a Wayfarer's Chart has been handed over
 */
@NullMarked
public record KnownPlaces(List<String> sites, boolean charted) {

    public static final KnownPlaces EMPTY = new KnownPlaces(List.of(), false);

    public static final Codec<KnownPlaces> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("sites", List.of()).forGetter(KnownPlaces::sites),
            Codec.BOOL.optionalFieldOf("charted", false).forGetter(KnownPlaces::charted)
    ).apply(i, KnownPlaces::new));

    public KnownPlaces {
        sites = List.copyOf(sites);
    }

    public boolean has(String site) {
        return sites.contains(site);
    }

    public KnownPlaces with(String site) {
        if (has(site)) {
            return this;
        }
        List<String> next = new ArrayList<>(sites);
        next.add(site);
        return new KnownPlaces(next, charted);
    }

    public KnownPlaces withChart() {
        return new KnownPlaces(sites, true);
    }
}
