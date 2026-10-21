package at.koopro.wizardsandbeasts.ministry.trace;

import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every case the Ministry holds, as an operator needs to see it: whose, at what stage, who is working it, and —
 * the point of the page — every item of evidence with where it came from, against the bar it has to clear. Built
 * once for the admin Cases page and for {@code /wandb admin ministry cases}, so both say the same thing.
 *
 * <p>Provenance is what lets an operator answer "was this case manufactured?": every item names its channel and
 * its source, and a source is always something the server observed.
 */
@NullMarked
public final class CaseReport {

    private CaseReport() {}

    /**
     * One case, flattened for the wire.
     *
     * @param kind     {@code case}, {@code unsolved} or {@code posted}
     * @param subject  the named wizard, or empty
     * @param stage    the case stage, {@code cold} appended when cold; empty for board-only entries
     * @param strength the strongest incident's evidence
     * @param bar      the conviction standard for that incident's legal class
     * @param holder   the Auror holding it, by name, or empty
     * @param evidence one line of provenance: {@code kind(source), …}
     */
    public record CaseRow(long id, String kind, String subject, String spell, String legalClass, String stage,
                          int strength, int bar, String holder, String where, String evidence) {}

    public record Snapshot(List<CaseRow> rows, int incidents, int pending, int dossiers) {

        public Snapshot {
            rows = List.copyOf(rows);
        }
    }

    public static Snapshot build(MinecraftServer server) {
        MinistryCaseData data = MinistryCaseData.get(server);
        long now = MinistryTrace.now(server);
        List<CaseRow> rows = new ArrayList<>();
        Map<UUID, Dossier> dossiers = data.dossiers();
        for (MinistryCasework.Entry entry : MinistryCasework.board(data, now)) {
            List<Long> incidents = switch (entry.kind()) {
                case CASE -> data.openCase(entry.id()).map(open -> open.getValue().incidents()).orElse(List.of());
                case UNSOLVED, POSTED -> List.of(entry.id());
            };
            StringBuilder provenance = new StringBuilder();
            int bar = EvidenceRules.standardFor(entry.legalClass());
            for (long id : incidents) {
                for (Evidence item : data.evidence(id)) {
                    if (!provenance.isEmpty()) {
                        provenance.append(", ");
                    }
                    provenance.append(item.kind().getSerializedName());
                    if (!item.sourceLabel().isEmpty()) {
                        provenance.append('(').append(item.sourceLabel()).append(')');
                    } else if (item.source().isPresent()) {
                        provenance.append('(').append(item.source().get().toString(), 0, 8).append(')');
                    }
                }
            }
            String stage = entry.stage() == null ? "" : entry.stage().getSerializedName();
            if (entry.kind() == MinistryCasework.Kind.CASE) {
                boolean cold = data.openCase(entry.id()).map(open -> open.getValue().cold()).orElse(false);
                stage = cold ? stage + " (cold)" : stage;
            }
            String holder = entry.holder().map(id -> {
                var online = server.getPlayerList().getPlayer(id);
                return online == null ? id.toString().substring(0, 8) : online.getGameProfile().name();
            }).orElse("");
            rows.add(new CaseRow(entry.id(), entry.kind().name().toLowerCase(java.util.Locale.ROOT),
                    entry.subject().orElse(""), entry.spellId(), entry.legalClass().getSerializedName(), stage,
                    entry.strength(), bar, holder, entry.dimension().getPath() + " " + entry.where().getX() + ","
                            + entry.where().getY() + "," + entry.where().getZ(),
                    provenance.toString()));
        }
        return new Snapshot(rows, data.incidentCount(), data.pending().size(), dossiers.size());
    }
}
