package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.post.MinistryPost;
import at.koopro.wizardsandbeasts.network.ministry.MinistryRecordSyncS2CPayload;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The Trace and everything downstream of it: a cast becomes an incident, word of it reaches the Ministry by
 * whatever channels saw it, a named incident earns a response, an open case moves with time, Aurors look where
 * the wizard was last reported, and a hearing decides.
 *
 * <p>Server-authoritative and deterministic. All state lives in {@link MinistryCaseData} and the player's
 * {@code PlayerMinistryRecord}; nothing is held in memory between ticks, so a logout, a death, a dimension
 * change or a restart changes nothing about where a case stands. Every time is an absolute game tick from the
 * overworld clock, which all dimensions share.
 *
 * <p>The rules are in {@link TraceRules}, {@link CaseRules} and {@link Wizengamot}; this class only reads the
 * world, stores the result and writes letters.
 */
@NullMarked
public final class MinistryTrace {

    private static final String CASE = "ministry.wizards_and_beasts.case.";
    private static final String HEARING = "ministry.wizards_and_beasts.hearing.";

    private MinistryTrace() {}

    /** Game ticks in one year of a character's life. */
    public static long ticksPerYear() {
        return Math.max(1, Config.ministryDaysPerYear) * 24000L;
    }

    /** The one clock every Ministry time is measured on. */
    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ── the cast ──

    /**
     * Called once for every successful cast. Surveys the scene, and files an incident only when somebody will
     * hear of it, or when it is dark enough that a wand examination could find it later. An adult's legal
     * magic that nobody saw leaves nothing behind.
     */
    public static void onSuccessfulCast(ServerPlayer caster, String spellId, @Nullable SpellCategory category) {
        if (!TraceService.isActive()) {
            return;
        }
        MinecraftServer server = caster.level().getServer();
        long now = now(server);
        SpellLaw law = SpellLawRegistry.lawFor(spellId, category);
        CastScene scene = CastSurvey.survey(caster, law, now);
        Exposure exposure = TraceRules.exposure(scene, law);
        List<TraceRules.PlannedReport> reports = TraceRules.reports(scene, law, exposure);
        boolean darkAct = law.legalClass().leavesResidue();
        if (reports.isEmpty() && !darkAct) {
            return;
        }

        MinistryCaseData data = MinistryCaseData.get(server);
        Incident incident = new Incident(data.allocateId(), caster.getUUID(), caster.getGameProfile().name(),
                spellId, caster.level().dimension().identifier(), caster.blockPosition(), now, law.legalClass(),
                exposure, TraceRules.underageBreach(scene), scene.inDanger(), scene.muggleWitnesses(),
                scene.wizardWitnesses(), law.offence(), false);
        data.putIncident(incident);
        for (TraceRules.PlannedReport report : reports) {
            data.addReport(new PendingReport(incident.id(), report.channel(), now + report.delayTicks(),
                    report.identifiesCaster()));
        }
        if (darkAct) {
            data.rememberDarkAct(caster.getUUID(), incident.id());
        }
    }

    // ── time ──

    /**
     * Delivers every report that has arrived, moves every open case that time has moved, and hands out letters
     * to whoever is online to receive them. Called from the server tick; callable directly with any {@code now}.
     */
    public static void process(MinecraftServer server, long now) {
        process(server, now, wizard -> true);
    }

    /**
     * {@link #process}, limited to the incidents and cases of the wizards {@code subjects} accepts. A scenario
     * that runs the clock ahead must not also run it ahead for every other scenario on the same server.
     */
    public static void process(MinecraftServer server, long now, Predicate<UUID> subjects) {
        MinistryCaseData data = MinistryCaseData.get(server);
        for (PendingReport report : data.takeDue(now, incidentId -> {
            Incident incident = data.incident(incidentId);
            return incident == null || subjects.test(incident.caster());
        })) {
            deliver(server, data, report, now);
        }
        for (Map.Entry<UUID, Dossier> entry : data.dossiers().entrySet()) {
            if (subjects.test(entry.getKey())) {
                advance(server, data, entry.getKey(), entry.getValue(), now);
            }
        }
        for (Map.Entry<UUID, Dossier> entry : data.dossiers().entrySet()) {
            if (!subjects.test(entry.getKey())) {
                continue;
            }
            ServerPlayer wizard = server.getPlayerList().getPlayer(entry.getKey());
            if (wizard != null && !entry.getValue().undelivered().isEmpty()) {
                postNotices(data, wizard, entry.getValue(), now);
            }
        }
        data.collectGarbage();
    }

    private static void deliver(MinecraftServer server, MinistryCaseData data, PendingReport report, long now) {
        Incident incident = data.incident(report.incidentId());
        if (incident == null) {
            return;
        }
        if (report.identifiesCaster()) {
            if (!incident.attributed()) {
                attribute(server, data, incident.asAttributed(), now);
            }
            return;
        }
        if (report.channel() != ReportChannel.INQUIRY && !incident.attributed()
                && TraceRules.inquiryWarranted(incident.legalClass(), incident.exposure())
                && !data.inquiryPending(incident.id())) {
            data.addReport(new PendingReport(incident.id(), ReportChannel.INQUIRY, now + TraceRules.INQUIRY_TICKS,
                    TraceRules.inquiryIdentifies(incident.muggleWitnesses())));
        }
        // An inquiry that names nobody lets the trail go cold; garbage collection forgets the incident.
    }

    /** The Ministry now knows who did it. */
    private static void attribute(MinecraftServer server, MinistryCaseData data, Incident incident, long now) {
        data.putIncident(incident);
        UUID wizard = incident.caster();
        Dossier dossier = data.dossier(wizard);
        CaseRules.Decision decision = CaseRules.decide(incident.legalClass(), incident.exposure(),
                incident.underageBreach(), dossier.underageWarnings(), dossier.secrecyNotes());
        Optional<CaseStage> stageBefore = dossier.openCase().map(OpenCase::stage);
        Optional<OpenCase> open = CaseRules.fold(dossier.openCase(), decision.response(), incident.id(),
                incident.dimension(), incident.pos(), now);
        Dossier next = dossier.withCounts(decision.underageWarnings(), decision.secrecyNotes()).withOpenCase(open);

        Optional<CaseStage> stageAfter = open.map(OpenCase::stage);
        CaseNotice notice;
        if (stageAfter.isPresent() && !stageAfter.equals(stageBefore)) {
            notice = switch (stageAfter.get()) {
                case INVESTIGATING -> new CaseNotice("investigation", incident.spellId(), 0L);
                case SUMMONED -> new CaseNotice("summons", incident.spellId(), CaseRules.SUMMONS_TICKS);
                case AURORS_ASSIGNED -> new CaseNotice("aurors", incident.spellId(), 0L);
            };
        } else if (stageAfter.isPresent()) {
            notice = new CaseNotice("added_to_case", incident.spellId(), 0L);
        } else if (decision.response() == MinistryResponse.WARNING) {
            notice = new CaseNotice(incident.underageBreach() ? "warning_underage" : "warning_secrecy",
                    incident.spellId(), 0L);
        } else {
            notice = new CaseNotice("recorded", incident.spellId(), 0L);
        }
        data.putDossier(wizard, next.withNotice(notice));

        ServerPlayer online = server.getPlayerList().getPlayer(wizard);
        if (online != null) {
            postNotices(data, online, data.dossier(wizard), now);
        }
    }

    private static void advance(MinecraftServer server, MinistryCaseData data, UUID wizard, Dossier dossier,
                                long now) {
        if (dossier.openCase().isEmpty()) {
            return;
        }
        OpenCase open = dossier.openCase().get();
        ServerPlayer online = server.getPlayerList().getPlayer(wizard);
        // A summons is only issued to someone who can receive it: the deadline starts when the letter lands.
        if (open.stage() == CaseStage.INVESTIGATING && online == null) {
            return;
        }
        CaseRules.Advance step = CaseRules.advance(open, now);
        switch (step.step()) {
            case NOTHING -> {
            }
            case SUMMONS_ISSUED -> data.putDossier(wizard, dossier.withOpenCase(Optional.of(step.openCase()))
                    .withNotice(new CaseNotice("summons", "", CaseRules.SUMMONS_TICKS)));
            case FAILED_TO_APPEAR -> data.putDossier(wizard, dossier.withOpenCase(Optional.of(step.openCase()))
                    .withNotice(new CaseNotice("failed_to_appear", "", 0L)));
            case SEARCH_DUE -> search(server, data, wizard, dossier, open, online, now);
        }
    }

    /** The Aurors go to where the wizard was last reported. They find them only if they are still there. */
    private static void search(MinecraftServer server, MinistryCaseData data, UUID wizard, Dossier dossier,
                               OpenCase open, @Nullable ServerPlayer online, long now) {
        boolean found = online != null && CaseRules.aurorsFind(
                online.isAlive(),
                online.level().dimension().identifier().equals(open.lastKnownDimension()),
                online.blockPosition().distSqr(open.lastKnownPos()));
        if (!found) {
            data.putDossier(wizard, dossier.withOpenCase(
                    Optional.of(open.afterFruitlessSearch(CaseRules.nextSearchAfter(now)))));
            return;
        }
        online.level().playSound(null, online.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 0.8f, 1.0f);
        MinistryPost.send(online, Component.translatable(CASE + "arrested.subject"),
                Component.translatable(CASE + "arrested.body"));
        hold(server, data, online, false, now);
    }

    // ── the wizard's side ──

    /** What answering a summons did. */
    public enum Answer {
        HEARD,
        NOTHING_TO_ANSWER,
        STILL_UNDER_INVESTIGATION
    }

    /**
     * A wizard answers their summons — or, with Aurors already looking, gives themselves up. A voluntary
     * appearance counts at the hearing unless the summons deadline had already passed.
     */
    public static Answer answerSummons(ServerPlayer wizard) {
        MinecraftServer server = wizard.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        Optional<OpenCase> open = data.dossier(wizard.getUUID()).openCase();
        if (open.isEmpty()) {
            return Answer.NOTHING_TO_ANSWER;
        }
        if (open.get().stage() == CaseStage.INVESTIGATING) {
            return Answer.STILL_UNDER_INVESTIGATION;
        }
        hold(server, data, wizard, !open.get().failedToAppear(), now(server));
        data.collectGarbage();
        return Answer.HEARD;
    }

    /** Convenes the hearing, applies the ruling through the existing penalty seams, and closes the case. */
    private static void hold(MinecraftServer server, MinistryCaseData data, ServerPlayer wizard,
                             boolean appearedVoluntarily, long now) {
        UUID id = wizard.getUUID();
        Dossier dossier = data.dossier(id);
        OpenCase open = dossier.openCase().orElseThrow();
        List<Wizengamot.Charge> charges = new ArrayList<>();
        for (long incidentId : open.incidents()) {
            Incident incident = data.incident(incidentId);
            if (incident != null) {
                charges.add(Wizengamot.Charge.of(incident, false));
            }
        }
        for (long incidentId : data.darkActs(id)) {
            Incident incident = data.incident(incidentId);
            if (incident != null && !open.incidents().contains(incidentId)) {
                charges.add(Wizengamot.Charge.of(incident, true));
            }
        }
        Wizengamot.Ruling ruling = Wizengamot.rule(new Wizengamot.Hearing(charges, dossier.underageWarnings(),
                dossier.secrecyNotes(), appearedVoluntarily));

        data.putDossier(id, dossier.closedWith(ruling.verdict()));
        data.forgetDarkActs(id);

        ruling.filed().ifPresent(offence -> TraceService.report(wizard, offence));
        if (ruling.confiscationTicks() > 0L) {
            long until = now + ruling.confiscationTicks();
            MinistryRecords.mutate(wizard, record ->
                    record.withWandConfiscatedUntil(Math.max(record.wandConfiscatedUntil(), until)));
        }

        MutableComponent body = Component.translatable(HEARING + "verdict." + ruling.verdict().getSerializedName()
                + ".body", duration(ruling.confiscationTicks())).copy();
        for (String reason : ruling.reasons()) {
            body.append(Component.literal("\n • ")).append(Component.translatable(HEARING + "reason." + reason));
        }
        MinistryPost.send(wizard, Component.translatable(HEARING + "verdict."
                + ruling.verdict().getSerializedName() + ".subject"), body);
        MinistryRecordSyncS2CPayload.syncToPlayer(wizard);
    }

    private static void postNotices(MinistryCaseData data, ServerPlayer wizard, Dossier dossier, long now) {
        Dossier next = dossier.withoutNotices();
        for (CaseNotice notice : dossier.undelivered()) {
            if (notice.kind().equals("summons") && next.openCase().isPresent()
                    && next.openCase().get().stage() == CaseStage.SUMMONED) {
                // The deadline runs from the day the letter is read, not from a day it sat waiting.
                OpenCase open = next.openCase().get();
                next = next.withOpenCase(Optional.of(open.withStage(CaseStage.SUMMONED, open.stageSince(),
                        now + CaseRules.SUMMONS_TICKS, 0L, open.failedToAppear())));
            }
            MinistryPost.send(wizard,
                    Component.translatable(CASE + notice.kind() + ".subject"),
                    Component.translatable(CASE + notice.kind() + ".body", spellName(notice.spellId()),
                            duration(notice.ticks())));
        }
        data.putDossier(wizard.getUUID(), next);
        MinistryRecordSyncS2CPayload.syncToPlayer(wizard);
    }

    // ── records ──

    /** Sets an age as of now, or clears it with {@link WizardingAge#UNSET}. */
    public static void setAge(ServerPlayer wizard, int years) {
        long now = now(wizard.level().getServer());
        MinistryRecords.mutate(wizard, record -> record.withAge(years, now));
    }

    public static boolean isUnderage(ServerPlayer wizard) {
        return WizardingAge.isUnderage(MinistryRecords.get(wizard).ageAt(now(wizard.level().getServer()),
                ticksPerYear()));
    }

    /** Whether the Ministry is holding this wizard's wand right now. */
    public static boolean wandConfiscated(ServerPlayer wizard) {
        return TraceService.isActive()
                && MinistryRecords.get(wizard).wandConfiscatedAt(now(wizard.level().getServer()));
    }

    private static Component spellName(String spellId) {
        if (spellId.isEmpty()) {
            return Component.empty();
        }
        Spell spell = Spells.byId(spellId);
        return spell == null ? Component.literal(spellId) : Component.translatable(spell.getDisplayName());
    }

    /** A duration in in-game days, or hours when shorter than a day. */
    public static Component duration(long ticks) {
        if (ticks >= 24000L) {
            return Component.translatable(CASE + "duration.days", Math.round(ticks / 24000.0));
        }
        return Component.translatable(CASE + "duration.hours", Math.max(1L, Math.round(ticks / 1000.0)));
    }
}
