package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the Department of Magical Law Enforcement keeps on one wizard between cases: warnings already given,
 * breaches already noted, the case open now, letters waiting, and the last ruling.
 *
 * <p>Held in world saved data rather than on the player, because a report can arrive, and a deadline pass,
 * while the wizard is offline.
 */
@NullMarked
public record Dossier(
        int underageWarnings,
        int secrecyNotes,
        Optional<OpenCase> openCase,
        List<CaseNotice> undelivered,
        Optional<Verdict> lastVerdict) {

    public static final Dossier EMPTY = new Dossier(0, 0, Optional.empty(), List.of(), Optional.empty());

    public static final Codec<Dossier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("underage_warnings", 0).forGetter(Dossier::underageWarnings),
            Codec.INT.optionalFieldOf("secrecy_notes", 0).forGetter(Dossier::secrecyNotes),
            OpenCase.CODEC.optionalFieldOf("open_case").forGetter(Dossier::openCase),
            CaseNotice.CODEC.listOf().optionalFieldOf("undelivered", List.of()).forGetter(Dossier::undelivered),
            Verdict.CODEC.optionalFieldOf("last_verdict").forGetter(Dossier::lastVerdict)
    ).apply(instance, Dossier::new));

    public Dossier {
        underageWarnings = Math.max(0, underageWarnings);
        secrecyNotes = Math.max(0, secrecyNotes);
        undelivered = List.copyOf(undelivered);
    }

    public Dossier withOpenCase(Optional<OpenCase> value) {
        return new Dossier(underageWarnings, secrecyNotes, value, undelivered, lastVerdict);
    }

    public Dossier withCounts(int underage, int secrecy) {
        return new Dossier(underage, secrecy, openCase, undelivered, lastVerdict);
    }

    public Dossier withNotice(CaseNotice notice) {
        List<CaseNotice> next = new ArrayList<>(undelivered);
        next.add(notice);
        return new Dossier(underageWarnings, secrecyNotes, openCase, next, lastVerdict);
    }

    public Dossier withoutNotices() {
        return new Dossier(underageWarnings, secrecyNotes, openCase, List.of(), lastVerdict);
    }

    /** Closes the open case with a ruling. */
    public Dossier closedWith(Verdict verdict) {
        return new Dossier(underageWarnings, secrecyNotes, Optional.empty(), undelivered, Optional.of(verdict));
    }
}
