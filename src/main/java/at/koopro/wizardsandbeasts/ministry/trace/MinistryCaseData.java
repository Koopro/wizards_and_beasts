package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;

/**
 * The Department of Magical Law Enforcement's files, saved with the world: incidents, reports still on their
 * way, each wizard's dossier, and the dark acts a wand examination could still find.
 *
 * <p>World data on the overworld rather than a player attachment, because nearly everything here happens to a
 * wizard who may be offline, dead or in another dimension at the time — a report arrives, a deadline passes —
 * and none of those may lose or duplicate it.
 *
 * <p>Bounded: an incident is kept only while a report, an open case or a wand examination still refers to it,
 * and a wizard's wand only remembers their last {@link #WAND_MEMORY} dark acts.
 */
@NullMarked
public final class MinistryCaseData extends SavedData {

    /** How many past dark acts a wand examination can find — Priori Incantato shows the last few spells. */
    public static final int WAND_MEMORY = 3;

    private static final Codec<Map<UUID, Dossier>> DOSSIERS = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Dossier.CODEC);
    private static final Codec<Map<UUID, List<Long>>> WAND_RESIDUE =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG.listOf());

    /** The Muggles and officials who saw one incident. A list of records because NBT map keys must be strings. */
    private record Witnesses(long incidentId, List<UUID> entities) {
        static final Codec<Witnesses> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("incident").forGetter(Witnesses::incidentId),
                UUIDUtil.CODEC.listOf().fieldOf("entities").forGetter(Witnesses::entities)
        ).apply(instance, Witnesses::new));
    }

    public static final SavedDataType<MinistryCaseData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_ministry_cases",
            MinistryCaseData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(data -> data.nextId),
                    Incident.CODEC.listOf().optionalFieldOf("incidents", List.of())
                            .forGetter(data -> List.copyOf(data.incidents.values())),
                    PendingReport.CODEC.listOf().optionalFieldOf("pending", List.of())
                            .forGetter(data -> List.copyOf(data.pending)),
                    DOSSIERS.optionalFieldOf("dossiers", Map.of()).forGetter(data -> Map.copyOf(data.dossiers)),
                    WAND_RESIDUE.optionalFieldOf("wand_residue", Map.of())
                            .forGetter(data -> Map.copyOf(data.residue)),
                    Evidence.CODEC.listOf().optionalFieldOf("evidence", List.of())
                            .forGetter(data -> data.evidence.values().stream().flatMap(List::stream).toList()),
                    Witnesses.CODEC.listOf().optionalFieldOf("witnesses", List.of())
                            .forGetter(data -> data.witnesses.entrySet().stream()
                                    .map(entry -> new Witnesses(entry.getKey(), entry.getValue())).toList()),
                    UnsolvedIncident.CODEC.listOf().optionalFieldOf("unsolved", List.of())
                            .forGetter(data -> List.copyOf(data.unsolved.values())),
                    NpcCase.CODEC.listOf().optionalFieldOf("npc_cases", List.of())
                            .forGetter(data -> List.copyOf(data.npcCases.values()))
            ).apply(instance, MinistryCaseData::new)));

    private long nextId;
    private final Map<Long, Incident> incidents = new LinkedHashMap<>();
    private final List<PendingReport> pending = new ArrayList<>();
    private final Map<UUID, Dossier> dossiers = new HashMap<>();
    private final Map<UUID, List<Long>> residue = new HashMap<>();
    private final Map<Long, List<Evidence>> evidence = new LinkedHashMap<>();
    private final Map<Long, List<UUID>> witnesses = new HashMap<>();
    private final Map<Long, UnsolvedIncident> unsolved = new LinkedHashMap<>();
    private final Map<Long, NpcCase> npcCases = new LinkedHashMap<>();

    public MinistryCaseData() {
        this(1L, List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of());
    }

    private MinistryCaseData(long nextId, List<Incident> incidents, List<PendingReport> pending,
                             Map<UUID, Dossier> dossiers, Map<UUID, List<Long>> residue, List<Evidence> evidence,
                             List<Witnesses> witnesses, List<UnsolvedIncident> unsolved, List<NpcCase> npcCases) {
        this.nextId = Math.max(1L, nextId);
        incidents.forEach(incident -> this.incidents.put(incident.id(), incident));
        this.pending.addAll(pending);
        this.dossiers.putAll(dossiers);
        residue.forEach((caster, ids) -> this.residue.put(caster, new ArrayList<>(ids)));
        evidence.forEach(item -> this.evidence.computeIfAbsent(item.incidentId(), id -> new ArrayList<>()).add(item));
        witnesses.forEach(entry -> this.witnesses.put(entry.incidentId(), List.copyOf(entry.entities())));
        unsolved.forEach(entry -> this.unsolved.put(entry.incidentId(), entry));
        npcCases.forEach(entry -> this.npcCases.put(entry.id(), entry));
        // A case opened before cases had numbers gets one now, so the board and the admin view can name it.
        this.dossiers.replaceAll((wizard, dossier) -> dossier.openCase()
                .filter(open -> open.caseId() == 0L)
                .map(open -> dossier.withOpenCase(Optional.of(open.withCaseId(this.nextId++))))
                .orElse(dossier));
    }

    public static MinistryCaseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // ── incidents ──

    public long allocateId() {
        setDirty();
        return nextId++;
    }

    public void putIncident(Incident incident) {
        incidents.put(incident.id(), incident);
        setDirty();
    }

    public @Nullable Incident incident(long id) {
        return incidents.get(id);
    }

    public int incidentCount() {
        return incidents.size();
    }

    // ── reports ──

    public void addReport(PendingReport report) {
        pending.add(report);
        setDirty();
    }

    /** Removes and returns every report due by {@code now} whose incident {@code filter} accepts, earliest first. */
    public List<PendingReport> takeDue(long now, LongPredicate filter) {
        List<PendingReport> due = new ArrayList<>();
        pending.removeIf(report -> {
            if (report.deliverAt() <= now && filter.test(report.incidentId())) {
                due.add(report);
                return true;
            }
            return false;
        });
        if (!due.isEmpty()) {
            due.sort((a, b) -> Long.compare(a.deliverAt(), b.deliverAt()));
            setDirty();
        }
        return due;
    }

    public List<PendingReport> pending() {
        return List.copyOf(pending);
    }

    public boolean inquiryPending(long incidentId) {
        return pending.stream().anyMatch(report ->
                report.incidentId() == incidentId && report.channel() == ReportChannel.INQUIRY);
    }

    // ── dossiers ──

    public Dossier dossier(UUID wizard) {
        return dossiers.getOrDefault(wizard, Dossier.EMPTY);
    }

    public void putDossier(UUID wizard, Dossier dossier) {
        if (dossier.equals(Dossier.EMPTY)) {
            if (dossiers.remove(wizard) != null) {
                setDirty();
            }
            return;
        }
        if (!dossier.equals(dossiers.put(wizard, dossier))) {
            setDirty();
        }
    }

    public Map<UUID, Dossier> dossiers() {
        return Map.copyOf(dossiers);
    }

    // ── wand residue ──

    /** Remembers a dark act for a later wand examination, forgetting the oldest beyond {@link #WAND_MEMORY}. */
    public void rememberDarkAct(UUID caster, long incidentId) {
        List<Long> ids = residue.computeIfAbsent(caster, key -> new ArrayList<>());
        ids.add(incidentId);
        while (ids.size() > WAND_MEMORY) {
            ids.removeFirst();
        }
        setDirty();
    }

    public List<Long> darkActs(UUID caster) {
        return List.copyOf(residue.getOrDefault(caster, List.of()));
    }

    public void forgetDarkActs(UUID caster) {
        if (residue.remove(caster) != null) {
            setDirty();
        }
    }

    // ── evidence ──

    /** Files one item of evidence. The same item twice is kept once. */
    public void addEvidence(Evidence item) {
        List<Evidence> list = evidence.computeIfAbsent(item.incidentId(), id -> new ArrayList<>());
        if (!list.contains(item)) {
            list.add(item);
            setDirty();
        }
    }

    public List<Evidence> evidence(long incidentId) {
        return List.copyOf(evidence.getOrDefault(incidentId, List.of()));
    }

    /** Whether {@code source} has already given evidence of {@code kind} on this incident. */
    public boolean hasEvidenceFrom(long incidentId, EvidenceKind kind, UUID source) {
        return evidence.getOrDefault(incidentId, List.of()).stream()
                .anyMatch(item -> item.kind() == kind && item.source().filter(source::equals).isPresent());
    }

    /** Whether any evidence of {@code kind} is on this incident. */
    public boolean hasEvidence(long incidentId, EvidenceKind kind) {
        return evidence.getOrDefault(incidentId, List.of()).stream().anyMatch(item -> item.kind() == kind);
    }

    public int strength(long incidentId) {
        return EvidenceRules.strength(evidence.getOrDefault(incidentId, List.of()));
    }

    // ── witnesses ──

    public void recordWitnesses(long incidentId, Collection<UUID> entities) {
        if (!entities.isEmpty()) {
            witnesses.put(incidentId, List.copyOf(entities));
            setDirty();
        }
    }

    /** The Muggles and officials who saw this incident, by entity id. */
    public List<UUID> witnessEntities(long incidentId) {
        return witnesses.getOrDefault(incidentId, List.of());
    }

    // ── the Auror board ──

    public void putUnsolved(UnsolvedIncident entry) {
        if (!entry.equals(unsolved.put(entry.incidentId(), entry))) {
            setDirty();
        }
    }

    public void removeUnsolved(long incidentId) {
        if (unsolved.remove(incidentId) != null) {
            setDirty();
        }
    }

    public @Nullable UnsolvedIncident unsolved(long incidentId) {
        return unsolved.get(incidentId);
    }

    public List<UnsolvedIncident> unsolved() {
        return List.copyOf(unsolved.values());
    }

    public void putNpcCase(NpcCase entry) {
        if (!entry.equals(npcCases.put(entry.id(), entry))) {
            setDirty();
        }
    }

    public void removeNpcCase(long id) {
        if (npcCases.remove(id) != null) {
            evidence.remove(id);
            setDirty();
        }
    }

    public @Nullable NpcCase npcCase(long id) {
        return npcCases.get(id);
    }

    public List<NpcCase> npcCases() {
        return List.copyOf(npcCases.values());
    }

    /** The open case with this number, and whose it is. */
    public Optional<Map.Entry<UUID, OpenCase>> openCase(long caseId) {
        for (Map.Entry<UUID, Dossier> entry : dossiers.entrySet()) {
            Optional<OpenCase> open = entry.getValue().openCase();
            if (open.isPresent() && open.get().caseId() == caseId) {
                return Optional.of(Map.entry(entry.getKey(), open.get()));
            }
        }
        return Optional.empty();
    }

    // ── housekeeping ──

    /** Drops every incident nothing refers to any more, and the evidence and witnesses that went with it. */
    public void collectGarbage() {
        Set<Long> live = new HashSet<>();
        pending.forEach(report -> live.add(report.incidentId()));
        residue.values().forEach(live::addAll);
        live.addAll(unsolved.keySet());
        dossiers.values().forEach(dossier -> dossier.openCase().ifPresent(open -> live.addAll(open.incidents())));
        boolean changed = incidents.keySet().removeIf(id -> !live.contains(id));
        changed |= witnesses.keySet().removeIf(id -> !live.contains(id));
        changed |= evidence.keySet().removeIf(id -> !live.contains(id) && !npcCases.containsKey(id));
        if (changed) {
            setDirty();
        }
    }
}
