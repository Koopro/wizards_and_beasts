package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.law.LawReputation;
import at.koopro.wizardsandbeasts.ministry.law.MinistryFines;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.post.MinistryPost;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
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
 * whatever channels saw it, each channel files the evidence it carries, the evidence puts a name to the incident
 * or does not, a named incident earns a response, an open case moves with time, Aurors look where the wizard was
 * last reported, and a hearing decides on the evidence.
 *
 * <p>Server-authoritative and deterministic. All state lives in {@link MinistryCaseData} and the player's
 * {@code PlayerMinistryRecord}; nothing is held in memory between ticks, so a logout, a death, a dimension
 * change or a restart changes nothing about where a case stands. Every time is an absolute game tick from the
 * overworld clock, which all dimensions share.
 *
 * <p>The rules are in {@link TraceRules}, {@link EvidenceRules}, {@link CaseRules}, {@link Wizengamot} and
 * {@link AppealRules}; this class only reads the world, stores the result and writes letters.
 */
@NullMarked
public final class MinistryTrace {

    private static final String CASE = "ministry.wizards_and_beasts.case.";
    private static final String HEARING = "ministry.wizards_and_beasts.hearing.";
    private static final String APPEAL = "ministry.wizards_and_beasts.appeal.";

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
        file(caster, spellId, SpellLawRegistry.lawFor(spellId, category));
    }

    /** Whether the law counts this spell among the Unforgivables, which are filed where they land. */
    public static boolean isUnforgivable(String spellId, @Nullable SpellCategory category) {
        return SpellLawRegistry.lawFor(spellId, category).legalClass() == LegalClass.UNFORGIVABLE;
    }

    /**
     * An Unforgivable Curse reached {@code victim}. The crime is using one "on a fellow human being" (<i>Goblet of
     * Fire</i> ch. 14), so on a person — a player, a villager or trader, an illager — it is filed as the Unforgivable
     * it is. On a creature it is filed as Dark magic: it still leaves wand residue, still weighs in any case, and
     * witnesses still report it, but it is not the life-sentence charge (documentation/CANON_AUDIT.md C-2).
     * {@code onSuccessfulCast} is no longer called for Unforgivables at release, so a curse that hits no one is not
     * an incident.
     */
    public static void onUnforgivableUse(ServerPlayer caster, String spellId, @Nullable SpellCategory category,
                                         net.minecraft.world.entity.LivingEntity victim) {
        SpellLaw law = SpellLawRegistry.lawFor(spellId, category);
        if (law.legalClass() == LegalClass.UNFORGIVABLE && !isPerson(victim)) {
            law = new SpellLaw(LegalClass.DARK, law.visibility(), java.util.Optional.empty());
        }
        file(caster, spellId, law);
    }

    /** A fellow human being, for the purposes of the Unforgivable law. */
    public static boolean isPerson(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player
                || entity instanceof net.minecraft.world.entity.npc.Npc
                || entity instanceof net.minecraft.world.entity.raid.Raider;
    }

    private static void file(ServerPlayer caster, String spellId, SpellLaw law) {
        if (!TraceService.isActive()) {
            return;
        }
        MinecraftServer server = caster.level().getServer();
        long now = now(server);
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
                scene.wizardWitnesses(), law.offence(), false, scene.licensedPractice());
        data.putIncident(incident);
        data.recordWitnesses(incident.id(), scene.witnessEntities());
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
        for (UnsolvedIncident unsolved : data.unsolved()) {
            Incident incident = data.incident(unsolved.incidentId());
            if (now >= unsolved.expiresAt() && (incident == null || subjects.test(incident.caster()))) {
                // The residue has faded and nobody solved it: the trail is cold for good.
                data.removeUnsolved(unsolved.incidentId());
            }
        }
        data.collectGarbage();
    }

    /**
     * A report arrives. Whatever it can testify to is filed as evidence; if the evidence on the incident now puts a
     * name to it, it is attributed. A report that names nobody may earn an inquiry; an inquiry that names nobody
     * leaves the incident on the Auror Office's board while the scene can still be read.
     */
    private static void deliver(MinecraftServer server, MinistryCaseData data, PendingReport report, long now) {
        Incident incident = data.incident(report.incidentId());
        if (incident == null) {
            return;
        }
        switch (report.channel()) {
            case TRACE -> {
                if (report.identifiesCaster()) {
                    data.addEvidence(Evidence.of(EvidenceKind.TRACE_READING, incident.id(), now));
                }
            }
            case OFFICIAL -> data.addEvidence(Evidence.of(EvidenceKind.OFFICIAL_REPORT, incident.id(), now));
            case MUGGLE_REPORT -> {
                // The Obliviators learn a place and a time, not a person.
            }
            case INQUIRY -> fileMuggleAccounts(data, incident, now);
        }
        if (incident.attributed()) {
            return;
        }
        if (EvidenceRules.identifies(data.evidence(incident.id()))) {
            attribute(server, data, incident.asAttributed(), now);
            return;
        }
        if (report.channel() != ReportChannel.INQUIRY) {
            if (TraceRules.inquiryWarranted(incident.legalClass(), incident.exposure())
                    && !data.inquiryPending(incident.id())) {
                data.addReport(new PendingReport(incident.id(), ReportChannel.INQUIRY, now + TraceRules.INQUIRY_TICKS,
                        TraceRules.inquiryIdentifies(incident.muggleWitnesses())));
            }
            return;
        }
        if (data.unsolved(incident.id()) == null) {
            data.putUnsolved(new UnsolvedIncident(incident.id(), incident.tick() + EvidenceRules.SCENE_FRESH_TICKS,
                    Optional.empty(), 0L));
        }
    }

    /**
     * The inquiry takes an account from every Muggle who saw it, before the Obliviators reach them. Each account
     * names the Muggle it came from, so a later interview of the same Muggle adds nothing new.
     */
    private static void fileMuggleAccounts(MinistryCaseData data, Incident incident, long now) {
        List<UUID> seen = data.witnessEntities(incident.id());
        for (int i = 0; i < incident.muggleWitnesses(); i++) {
            // Muggles are recorded first; an incident filed before witnesses were recorded gets stable stand-ins.
            UUID source = i < seen.size() ? seen.get(i)
                    : UUID.nameUUIDFromBytes(("muggle:" + incident.id() + ":" + i).getBytes(
                            java.nio.charset.StandardCharsets.UTF_8));
            data.addEvidence(Evidence.from(EvidenceKind.MUGGLE_ACCOUNT, incident.id(), source, "", now));
        }
    }

    /** The Ministry now knows who did it. */
    static void attribute(MinecraftServer server, MinistryCaseData data, Incident incident, long now) {
        data.putIncident(incident);
        UUID wizard = incident.caster();
        UnsolvedIncident wasOnBoard = data.unsolved(incident.id());
        data.removeUnsolved(incident.id());
        Dossier dossier = data.dossier(wizard);
        CaseRules.Decision decision = CaseRules.decide(incident.legalClass(), incident.exposure(),
                incident.underageBreach(), dossier.underageWarnings(), dossier.secrecyNotes());
        Optional<CaseStage> stageBefore = dossier.openCase().map(OpenCase::stage);
        Optional<OpenCase> open = CaseRules.fold(dossier.openCase(), decision.response(), incident.id(),
                incident.dimension(), incident.pos(), now);
        if (open.isPresent() && open.get().caseId() == 0L) {
            open = Optional.of(open.get().withCaseId(data.allocateId()));
        }
        if (open.isPresent() && wasOnBoard != null && wasOnBoard.aurorAt(now).isPresent()
                && open.get().aurorAt(now).isEmpty()) {
            // The Auror who put a name to it keeps it.
            open = Optional.of(open.get().withAuror(wasOnBoard.auror(), wasOnBoard.aurorUntil()));
        }
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
        if (wasOnBoard != null) {
            MinistryCasework.onIdentified(server, wasOnBoard, incident, now);
        }

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
            data.putDossier(wizard, dossier.withOpenCase(Optional.of(CaseRules.afterFruitlessSearch(open, now))));
            return;
        }
        online.level().playSound(null, online.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 0.8f, 1.0f);
        MinistryPost.send(online, Component.translatable(CASE + "arrested.subject"),
                Component.translatable(CASE + "arrested.body"));
        hold(server, data, online, Appearance.CAUGHT, Plea.CONTEST, now);
    }

    // ── the wizard's side ──

    /** What answering a summons did. */
    public enum Answer {
        HEARD,
        NOTHING_TO_ANSWER,
        STILL_UNDER_INVESTIGATION
    }

    /** How the wizard came before the court. */
    public enum Appearance {
        /** Answered the summons, or gave themselves up before any summons lapsed. */
        ANSWERED,
        /** Gave themselves up after letting a summons lapse. */
        SURRENDERED,
        /** Brought in by Aurors. */
        CAUGHT
    }

    /** {@link #answer} with the charges contested. */
    public static Answer answerSummons(ServerPlayer wizard) {
        return answer(wizard, Plea.CONTEST);
    }

    /**
     * A wizard answers their summons — or, with Aurors already looking, gives themselves up. Appearing before a
     * summons lapsed counts as answering; after it lapsed, as surrendering, which is still better than being
     * caught.
     */
    public static Answer answer(ServerPlayer wizard, Plea plea) {
        MinecraftServer server = wizard.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        Optional<OpenCase> open = data.dossier(wizard.getUUID()).openCase();
        if (open.isEmpty()) {
            return Answer.NOTHING_TO_ANSWER;
        }
        if (open.get().stage() == CaseStage.INVESTIGATING) {
            return Answer.STILL_UNDER_INVESTIGATION;
        }
        hold(server, data, wizard, open.get().failedToAppear() ? Appearance.SURRENDERED : Appearance.ANSWERED,
                plea, now(server));
        data.collectGarbage();
        return Answer.HEARD;
    }

    /**
     * Convenes the hearing on the evidence, applies the ruling through the existing penalty seams, keeps the
     * whole of it for an appeal, and closes the case.
     */
    static void hold(MinecraftServer server, MinistryCaseData data, ServerPlayer wizard, Appearance appearance,
                     Plea plea, long now) {
        UUID id = wizard.getUUID();
        Dossier dossier = data.dossier(id);
        OpenCase open = dossier.openCase().orElseThrow();
        List<Long> examined = data.darkActs(id);
        List<Wizengamot.Charge> charges = new ArrayList<>();
        for (long incidentId : open.incidents()) {
            Incident incident = data.incident(incidentId);
            if (incident != null) {
                if (examined.contains(incidentId)) {
                    // The wand bears it out: Priori Incantato at the hearing.
                    data.addEvidence(Evidence.of(EvidenceKind.WAND_EXAMINATION, incidentId, now));
                }
                // Attribution needs evidence since evidence existed, so an attributed incident with none on file was
                // proven under the old rules — a case opened before the update is not dismissed by it.
                int strength = data.evidence(incidentId).isEmpty() && incident.attributed()
                        ? EvidenceRules.MAX_STRENGTH : data.strength(incidentId);
                charges.add(Wizengamot.Charge.of(incident, false, strength, incident.licensedPractice()));
            }
        }
        for (long incidentId : examined) {
            Incident incident = data.incident(incidentId);
            if (incident != null && !open.incidents().contains(incidentId)) {
                charges.add(Wizengamot.Charge.of(incident, true, EvidenceRules.MAX_STRENGTH,
                        incident.licensedPractice()));
            }
        }
        Wizengamot.Hearing hearing = new Wizengamot.Hearing(charges, dossier.underageWarnings(),
                dossier.secrecyNotes(), appearance == Appearance.ANSWERED, plea, LawReputation.goodStanding(wizard), 0,
                appearance == Appearance.SURRENDERED);
        Wizengamot.Ruling ruling = Wizengamot.rule(hearing);

        TraceService.Filing filing = impose(wizard, ruling, now);
        long appealBy = plea == Plea.GUILTY || ruling.verdict() == Verdict.DISMISSED
                ? 0L : AppealRules.appealBy(now, Config.ministryAppealWindowDays);
        ClosedCase closed = new ClosedCase(open.caseId(), now, hearing, ruling, filing.notorietyAdded(),
                filing.fineKnuts(), appealBy, ClosedCase.Appeal.NONE);
        data.putDossier(id, dossier.closedWith(closed));
        data.forgetDarkActs(id);

        LawReputation.onRuling(wizard, hearing, ruling);
        MinistryCasework.onCaseClosed(server, open, wizard, ruling, now);

        MutableComponent body = Component.translatable(HEARING + "verdict." + ruling.verdict().getSerializedName()
                + ".body", duration(ruling.confiscationTicks())).copy();
        for (String reason : ruling.reasons()) {
            body.append(Component.literal("\n • ")).append(Component.translatable(HEARING + "reason." + reason));
        }
        if (!ruling.suspensions().isEmpty()) {
            body.append(Component.literal("\n")).append(Component.translatable(HEARING + "suspended",
                    licences(ruling.suspensions()), duration(ruling.suspensionTicks())));
        }
        if (appealBy > 0L) {
            body.append(Component.literal("\n")).append(Component.translatable(HEARING + "may_appeal",
                    duration(appealBy - now)));
        }
        MinistryPost.send(wizard, Component.translatable(HEARING + "verdict."
                + ruling.verdict().getSerializedName() + ".subject"), body);
        MinistryRecordSyncS2CPayload.syncToPlayer(wizard);
    }

    /** Applies a ruling's penalties: the filed offence and its fine, the wand, the suspensions. */
    private static TraceService.Filing impose(ServerPlayer wizard, Wizengamot.Ruling ruling, long now) {
        TraceService.Filing filing = ruling.filed()
                .map(offence -> TraceService.file(wizard, offence, ruling.finePercent()))
                .orElse(TraceService.Filing.NONE);
        if (ruling.confiscationTicks() > 0L) {
            long until = now + ruling.confiscationTicks();
            MinistryRecords.mutate(wizard, record ->
                    record.withWandConfiscatedUntil(Math.max(record.wandConfiscatedUntil(), until)));
        }
        for (LicenseType type : ruling.suspensions()) {
            long until = now + ruling.suspensionTicks();
            MinistryRecords.mutate(wizard, record -> record.withSuspension(type, until));
        }
        return filing;
    }

    /** What lodging an appeal did. */
    public enum AppealResult {
        NO_CASE,
        NOTHING_TO_APPEAL,
        PLEADED_GUILTY,
        ALREADY_APPEALED,
        WINDOW_CLOSED,
        CANNOT_PAY,
        UPHELD,
        REDUCED,
        OVERTURNED
    }

    /**
     * Appeals the wizard's last ruling to the full Wizengamot. The fee is refunded if the appeal succeeds; a
     * successful appeal takes back what the ruling cost — the conviction, the heat, the fine, the wand, the
     * suspensions — and imposes the lighter ruling in its place.
     */
    public static AppealResult appeal(ServerPlayer wizard) {
        MinecraftServer server = wizard.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        Dossier dossier = data.dossier(wizard.getUUID());
        if (dossier.lastCase().isEmpty()) {
            return AppealResult.NO_CASE;
        }
        ClosedCase closed = dossier.lastCase().get();
        long now = now(server);
        switch (AppealRules.eligibility(closed, now)) {
            case NO_CASE -> {
                return AppealResult.NO_CASE;
            }
            case NOTHING_TO_APPEAL -> {
                return AppealResult.NOTHING_TO_APPEAL;
            }
            case PLEADED_GUILTY -> {
                return AppealResult.PLEADED_GUILTY;
            }
            case ALREADY_APPEALED -> {
                return AppealResult.ALREADY_APPEALED;
            }
            case WINDOW_CLOSED -> {
                return AppealResult.WINDOW_CLOSED;
            }
            case ALLOWED -> {
            }
        }
        long fee = ModuleManager.isEnabled(Module.GRINGOTTS) ? Math.max(0, Config.ministryAppealFeeKnuts) : 0L;
        if (fee > 0L && !GringottsBilling.charge(wizard, MoneyFlow.LEGAL_FEE, fee)) {
            return AppealResult.CANNOT_PAY;
        }

        AppealRules.Review review = AppealRules.review(closed, LawReputation.goodStanding(wizard));
        Wizengamot.Ruling before = closed.ruling();
        ClosedCase decided;
        if (review.outcome() == ClosedCase.Appeal.UPHELD) {
            decided = closed.withAppeal(ClosedCase.Appeal.UPHELD, before);
        } else {
            undo(wizard, closed);
            TraceService.Filing filing = impose(wizard, review.standing(), closed.closedAt());
            decided = new ClosedCase(closed.caseId(), closed.closedAt(), closed.hearing(), review.standing(),
                    filing.notorietyAdded(), filing.fineKnuts(), closed.appealBy(), review.outcome());
            GringottsBilling.refund(wizard, MoneyFlow.LEGAL_FEE, fee);
        }
        data.putDossier(wizard.getUUID(), data.dossier(wizard.getUUID()).withLastCase(decided));
        LawReputation.onAppeal(wizard, review.outcome(), before, decided.ruling());

        String outcome = review.outcome().getSerializedName();
        MutableComponent body = Component.translatable(APPEAL + outcome + ".body").copy();
        for (String reason : decided.ruling().reasons()) {
            body.append(Component.literal("\n • ")).append(Component.translatable(HEARING + "reason." + reason));
        }
        MinistryPost.send(wizard, Component.translatable(APPEAL + outcome + ".subject"), body);
        MinistryRecordSyncS2CPayload.syncToPlayer(wizard);
        return switch (review.outcome()) {
            case OVERTURNED -> AppealResult.OVERTURNED;
            case REDUCED -> AppealResult.REDUCED;
            default -> AppealResult.UPHELD;
        };
    }

    /**
     * Takes back everything a ruling cost. The fine comes off what is still owed first; whatever had already been
     * collected goes back into the vault.
     */
    private static void undo(ServerPlayer wizard, ClosedCase closed) {
        Wizengamot.Ruling ruling = closed.ruling();
        ruling.filed().ifPresent(offence -> MinistryRecords.mutate(wizard,
                record -> record.withOffenceWithdrawn(offence, closed.notorietyAdded())));
        if (closed.fineKnuts() > 0L) {
            long owed = MinistryFines.owed(wizard);
            long forgiven = Math.min(owed, closed.fineKnuts());
            MinistryRecords.mutate(wizard, record -> record.withFineAdjusted(-forgiven));
            long collected = closed.fineKnuts() - forgiven;
            if (collected > 0L && ModuleManager.isEnabled(Module.GRINGOTTS)) {
                GringottsBilling.refund(wizard, MoneyFlow.MINISTRY_FINE, collected);
            }
        }
        if (ruling.confiscationTicks() > 0L) {
            long heldUntil = closed.closedAt() + ruling.confiscationTicks();
            MinistryRecords.mutate(wizard, record -> record.wandConfiscatedUntil() <= heldUntil
                    ? record.withWandConfiscatedUntil(0L) : record);
        }
        for (LicenseType type : ruling.suspensions()) {
            MinistryRecords.mutate(wizard, record -> record.withoutSuspension(type));
        }
    }

    /**
     * A player gives their account of an incident the server recorded them seeing. Their word is evidence — capped,
     * and never enough on its own — and it can tip an inquiry or an Auror's case into naming the caster.
     */
    public static CaseworkRules.Result testify(ServerPlayer witness, long incidentId) {
        MinecraftServer server = witness.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        Incident incident = data.incident(incidentId);
        if (incident == null) {
            return CaseworkRules.Result.NO_SUCH_CASE;
        }
        CaseworkRules.Result result = CaseworkRules.testify(witness.getUUID(), incident.caster(),
                incident.wizardWitnesses(),
                data.hasEvidenceFrom(incidentId, EvidenceKind.WIZARD_TESTIMONY, witness.getUUID()),
                LawReputation.credible(witness));
        if (!result.ok()) {
            return result;
        }
        long now = now(server);
        data.addEvidence(Evidence.from(EvidenceKind.WIZARD_TESTIMONY, incidentId, witness.getUUID(),
                witness.getGameProfile().name(), now));
        Incident current = data.incident(incidentId);
        if (current != null && !current.attributed() && EvidenceRules.identifies(data.evidence(incidentId))) {
            attribute(server, data, current.asAttributed(), now);
        }
        return result;
    }

    /** Incidents still on file that the server saw {@code witness} witness, and that they have not spoken to. */
    public static List<Incident> witnessed(ServerPlayer witness) {
        MinistryCaseData data = MinistryCaseData.get(witness.level().getServer());
        List<Incident> out = new ArrayList<>();
        for (long id : liveIncidentIds(data)) {
            Incident incident = data.incident(id);
            if (incident != null && incident.wizardWitnesses().contains(witness.getUUID())
                    && !incident.caster().equals(witness.getUUID())
                    && !data.hasEvidenceFrom(id, EvidenceKind.WIZARD_TESTIMONY, witness.getUUID())) {
                out.add(incident);
            }
        }
        return out;
    }

    private static List<Long> liveIncidentIds(MinistryCaseData data) {
        List<Long> ids = new ArrayList<>();
        data.pending().forEach(report -> ids.add(report.incidentId()));
        data.unsolved().forEach(entry -> ids.add(entry.incidentId()));
        data.dossiers().values().forEach(dossier -> dossier.openCase().ifPresent(open -> ids.addAll(open.incidents())));
        return ids.stream().distinct().toList();
    }

    // ── the black market ──

    /**
     * Whether a clerk could be paid to lose this wizard's file: a case still under investigation, with nothing
     * dark on it. Nothing can be bought once a summons has gone out, and no sum loses an Unforgivable.
     */
    public static boolean fileCanBeLost(MinecraftServer server, UUID wizard) {
        MinistryCaseData data = MinistryCaseData.get(server);
        Optional<OpenCase> open = data.dossier(wizard).openCase();
        if (open.isEmpty() || open.get().stage() != CaseStage.INVESTIGATING) {
            return false;
        }
        for (long id : open.get().incidents()) {
            Incident incident = data.incident(id);
            if (incident != null && incident.legalClass().leavesResidue()) {
                return false;
            }
        }
        return true;
    }

    /** The clerk loses the file. The case closes with no hearing; the counters it raised stay. */
    public static boolean loseFile(ServerPlayer wizard) {
        MinecraftServer server = wizard.level().getServer();
        if (!fileCanBeLost(server, wizard.getUUID())) {
            return false;
        }
        MinistryCaseData data = MinistryCaseData.get(server);
        data.putDossier(wizard.getUUID(), data.dossier(wizard.getUUID()).withOpenCase(Optional.empty()));
        data.collectGarbage();
        MinistryRecordSyncS2CPayload.syncToPlayer(wizard);
        return true;
    }

    /** A bribe was caught: the investigation is over and the summons goes out at once. */
    public static void escalateAfterBribe(ServerPlayer wizard) {
        MinecraftServer server = wizard.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        Dossier dossier = data.dossier(wizard.getUUID());
        Optional<OpenCase> open = dossier.openCase();
        if (open.isEmpty() || open.get().stage() != CaseStage.INVESTIGATING) {
            return;
        }
        long now = now(server);
        data.putDossier(wizard.getUUID(), dossier.withOpenCase(Optional.of(open.get().withStage(CaseStage.SUMMONED,
                now, now + CaseRules.SUMMONS_TICKS, 0L, open.get().failedToAppear())))
                .withNotice(new CaseNotice("summons", "", CaseRules.SUMMONS_TICKS)));
        postNotices(data, wizard, data.dossier(wizard.getUUID()), now);
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

    public static Component spellName(String spellId) {
        if (spellId.isEmpty()) {
            return Component.empty();
        }
        Spell spell = Spells.byId(spellId);
        return spell == null ? Component.literal(spellId) : Component.translatable(spell.getDisplayName());
    }

    private static Component licences(List<LicenseType> types) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                out.append(Component.literal(", "));
            }
            out.append(types.get(i).displayName());
        }
        return out;
    }

    /** A duration in in-game days, or hours when shorter than a day. */
    public static Component duration(long ticks) {
        if (ticks >= 24000L) {
            return Component.translatable(CASE + "duration.days", Math.round(ticks / 24000.0));
        }
        return Component.translatable(CASE + "duration.hours", Math.max(1L, Math.round(ticks / 1000.0)));
    }
}
