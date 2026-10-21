package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.data.MinistryRank;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.post.MinistryPost;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.standing.StandingService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Auror Office: the board of cases, and what a player Auror may do on one.
 *
 * <p>Three kinds of work share the board. An <b>open case</b> against a player the Ministry has already named; an
 * <b>unsolved incident</b> the Department's inquiry could not put a name to; and a <b>posted case</b> against a
 * non-player culprit ({@link NpcCase}), so an Auror has work on a quiet server. Each is worked the same way:
 * take it, examine the scene (here, or by casting Revelio), question the witnesses, and — once the evidence puts
 * a name or a face to it — bring the suspect in.
 *
 * <p>Every check is in {@link CaseworkRules}; every piece of evidence is something the server observed. An Auror
 * decides where to look, never what is found, and nothing here can open a case against anyone.
 */
@NullMarked
public final class MinistryCasework {

    /** The career subject recorded for work on a case against a player. */
    public static final Identifier PLAYER_CASE = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
            "player_case");
    /** Professional standing for a culprit brought in alive. */
    public static final float APPREHENSION_CREDIT = 2.0f;
    /** Professional standing lost when the culprit of a held case dies instead of being taken. */
    public static final float SUSPECT_KILLED_PENALTY = 3.0f;

    private static final String KEY = "ministry.wizards_and_beasts.casework.";

    /** Culprit entities currently on a posted case, so the damage hook can skip every other entity cheaply. */
    private static final Set<UUID> CULPRITS = ConcurrentHashMap.newKeySet();
    private static long lastPosting = Long.MIN_VALUE / 2;

    private MinistryCasework() {}

    // ── who ──

    /** Whether this player works cases: player Aurors on, and an Auror by profession or by Ministry post. */
    public static boolean isAuror(ServerPlayer player) {
        if (!Config.ministryPlayerAurors || !TraceService.isActive()) {
            return false;
        }
        return player.getData(ModAttachments.PROFESSION_DATA.get()).profession() == Profession.AUROR
                || MinistryRecords.get(player).rank().atLeast(MinistryRank.AUROR);
    }

    /**
     * How senior an Auror is, for which posted cases they are trusted with: their Auror-track rank, or, for an
     * Auror by appointment, one step per Ministry rank from Auror up.
     */
    public static int seniority(ServerPlayer player) {
        int byCareer = 0;
        if (player.getData(ModAttachments.PROFESSION_DATA.get()).profession() == Profession.AUROR) {
            byCareer = player.getData(ModAttachments.CAREER_PROGRESS.get()).track("auror").rank();
        }
        MinistryRank post = MinistryRecords.get(player).rank();
        int byPost = post.atLeast(MinistryRank.AUROR) ? post.ordinal() - MinistryRank.AUROR.ordinal() + 1 : 0;
        return Math.max(byCareer, byPost);
    }

    // ── the board ──

    /** What a board entry is. */
    public enum Kind {
        CASE,
        UNSOLVED,
        POSTED
    }

    /**
     * One line on the board.
     *
     * @param subject the named suspect, for an open case; empty otherwise
     * @param where   where it happened — or, for an open case, where the suspect was last reported
     * @param holder  the Auror holding it, if any
     */
    public record Entry(long id, Kind kind, String spellId, LegalClass legalClass, Optional<String> subject,
                        Identifier dimension, BlockPos where, Optional<UUID> holder, int strength,
                        @Nullable CaseStage stage) {}

    /** Every case on the board at {@code now}, posting a new case first if the board is short. */
    public static List<Entry> board(ServerPlayer viewer) {
        MinecraftServer server = viewer.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        long now = MinistryTrace.now(server);
        if (isAuror(viewer)) {
            postIfShort(server, data, viewer, now);
        }
        return board(data, now);
    }

    /** The board as it stands, without posting anything. */
    public static List<Entry> board(MinistryCaseData data, long now) {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<UUID, Dossier> dossier : data.dossiers().entrySet()) {
            Optional<OpenCase> open = dossier.getValue().openCase();
            if (open.isEmpty()) {
                continue;
            }
            OpenCase found = open.get();
            Incident first = found.incidents().isEmpty() ? null : data.incident(found.incidents().getLast());
            String name = first == null ? "" : first.casterName();
            int strength = found.incidents().stream().mapToInt(data::strength).max().orElse(0);
            entries.add(new Entry(found.caseId(), Kind.CASE, first == null ? "" : first.spellId(),
                    first == null ? LegalClass.UNRESTRICTED : first.legalClass(), Optional.of(name),
                    found.lastKnownDimension(), found.lastKnownPos(), found.aurorAt(now), strength, found.stage()));
        }
        for (UnsolvedIncident unsolved : data.unsolved()) {
            Incident incident = data.incident(unsolved.incidentId());
            if (incident != null) {
                entries.add(new Entry(incident.id(), Kind.UNSOLVED, incident.spellId(), incident.legalClass(),
                        Optional.empty(), incident.dimension(), incident.pos(), unsolved.aurorAt(now),
                        data.strength(incident.id()), null));
            }
        }
        for (NpcCase posted : data.npcCases()) {
            entries.add(new Entry(posted.id(), Kind.POSTED, posted.spellId(), posted.legalClass(), Optional.empty(),
                    posted.dimension(), posted.scene(), posted.aurorAt(now), data.strength(posted.id()), null));
        }
        return entries;
    }

    /** Takes a case off the board. */
    public static CaseworkRules.Result take(ServerPlayer auror, long id) {
        if (!Config.ministryPlayerAurors) {
            return CaseworkRules.Result.DISABLED;
        }
        MinecraftServer server = auror.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        long now = MinistryTrace.now(server);
        UUID me = auror.getUUID();
        int held = heldCount(data, me, now);

        Optional<Map.Entry<UUID, OpenCase>> open = data.openCase(id);
        if (open.isPresent()) {
            OpenCase found = open.get().getValue();
            CaseworkRules.Result result = CaseworkRules.take(me, isAuror(auror), Optional.of(open.get().getKey()),
                    witnessesOf(data, found.incidents()), found.aurorAt(now), held);
            if (result.ok()) {
                UUID wizard = open.get().getKey();
                data.putDossier(wizard, data.dossier(wizard).withOpenCase(Optional.of(
                        found.withAuror(Optional.of(me), now + CaseworkRules.HOLD_TICKS))));
            }
            return result;
        }
        UnsolvedIncident unsolved = data.unsolved(id);
        if (unsolved != null) {
            Incident incident = data.incident(id);
            if (incident == null) {
                return CaseworkRules.Result.NO_SUCH_CASE;
            }
            CaseworkRules.Result result = CaseworkRules.take(me, isAuror(auror), Optional.of(incident.caster()),
                    incident.wizardWitnesses(), unsolved.aurorAt(now), held);
            if (result.ok()) {
                data.putUnsolved(unsolved.withAuror(Optional.of(me), now + CaseworkRules.HOLD_TICKS));
            }
            return result;
        }
        NpcCase posted = data.npcCase(id);
        if (posted != null) {
            NpcCaseTemplates.Template template = NpcCaseTemplates.get(posted.template());
            if (template != null && template.minRank() > seniority(auror) && isAuror(auror)) {
                return CaseworkRules.Result.NOT_AUROR;
            }
            CaseworkRules.Result result = CaseworkRules.take(me, isAuror(auror), Optional.empty(), List.of(),
                    posted.aurorAt(now), held);
            if (result.ok()) {
                data.putNpcCase(posted.withAuror(Optional.of(me), now + CaseworkRules.HOLD_TICKS));
            }
            return result;
        }
        return CaseworkRules.Result.NO_SUCH_CASE;
    }

    /** Hands a held case back to the board. */
    public static CaseworkRules.Result drop(ServerPlayer auror, long id) {
        MinecraftServer server = auror.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        long now = MinistryTrace.now(server);
        UUID me = auror.getUUID();
        Optional<Map.Entry<UUID, OpenCase>> open = data.openCase(id);
        if (open.isPresent() && open.get().getValue().aurorAt(now).filter(me::equals).isPresent()) {
            UUID wizard = open.get().getKey();
            data.putDossier(wizard, data.dossier(wizard).withOpenCase(Optional.of(
                    open.get().getValue().withAuror(Optional.empty(), 0L))));
            return CaseworkRules.Result.OK;
        }
        UnsolvedIncident unsolved = data.unsolved(id);
        if (unsolved != null && unsolved.aurorAt(now).filter(me::equals).isPresent()) {
            data.putUnsolved(unsolved.withAuror(Optional.empty(), 0L));
            return CaseworkRules.Result.OK;
        }
        NpcCase posted = data.npcCase(id);
        if (posted != null && posted.aurorAt(now).filter(me::equals).isPresent()) {
            discardCulprit(server, posted);
            data.putNpcCase(posted.withAuror(Optional.empty(), 0L).withCulprit(Optional.empty()));
            return CaseworkRules.Result.OK;
        }
        return CaseworkRules.Result.NOT_HOLDER;
    }

    private static int heldCount(MinistryCaseData data, UUID auror, long now) {
        int held = 0;
        for (Entry entry : board(data, now)) {
            if (entry.holder().filter(auror::equals).isPresent()) {
                held++;
            }
        }
        return held;
    }

    private static List<UUID> witnessesOf(MinistryCaseData data, List<Long> incidents) {
        List<UUID> witnesses = new ArrayList<>();
        for (long id : incidents) {
            Incident incident = data.incident(id);
            if (incident != null) {
                witnesses.addAll(incident.wizardWitnesses());
            }
        }
        return witnesses;
    }

    /** The incidents of every case the Auror holds, with the board id they belong to. */
    private static List<Incident> heldIncidents(MinistryCaseData data, UUID auror, long now) {
        List<Incident> out = new ArrayList<>();
        for (Dossier dossier : data.dossiers().values()) {
            dossier.openCase().filter(open -> open.aurorAt(now).filter(auror::equals).isPresent())
                    .ifPresent(open -> open.incidents().forEach(id -> {
                        Incident incident = data.incident(id);
                        if (incident != null) {
                            out.add(incident);
                        }
                    }));
        }
        for (UnsolvedIncident unsolved : data.unsolved()) {
            Incident incident = data.incident(unsolved.incidentId());
            if (incident != null && unsolved.aurorAt(now).filter(auror::equals).isPresent()) {
                out.add(incident);
            }
        }
        return out;
    }

    // ── the work ──

    /**
     * Examines the scene of every held case within reach of where the Auror stands. Called by the
     * {@code casework examine} command and by Revelio.
     *
     * @return the evidence filed; 0 when there was nothing here to examine
     */
    public static int examine(ServerPlayer auror) {
        if (!isAuror(auror)) {
            return 0;
        }
        MinecraftServer server = auror.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        long now = MinistryTrace.now(server);
        Identifier here = auror.level().dimension().identifier();
        int filed = 0;
        for (Incident incident : heldIncidents(data, auror.getUUID(), now)) {
            CaseworkRules.Result result = CaseworkRules.examine(true, incident.dimension().equals(here),
                    auror.blockPosition().distSqr(incident.pos()), incident.tick(), now,
                    data.hasEvidence(incident.id(), EvidenceKind.SCENE_RESIDUE));
            if (!result.ok()) {
                if (result == CaseworkRules.Result.STALE_SCENE && within(auror, incident.pos(),
                        CaseworkRules.EXAMINE_RADIUS)) {
                    PlayerFeedback.actionBar(auror, Component.translatable(KEY + "stale"));
                }
                continue;
            }
            data.addEvidence(Evidence.from(EvidenceKind.SCENE_RESIDUE, incident.id(), auror.getUUID(),
                    auror.getGameProfile().name(), now));
            credit(auror, ActivityType.EVIDENCE_GATHERED, PLAYER_CASE);
            PlayerFeedback.toast(auror, NoticeKind.SUCCESS, Component.translatable(KEY + "residue.title"),
                    Component.translatable(KEY + "residue.body", MinistryTrace.spellName(incident.spellId())));
            filed++;
            identifyIfProven(server, data, incident.id(), now);
        }
        for (NpcCase posted : data.npcCases()) {
            if (posted.aurorAt(now).filter(auror.getUUID()::equals).isEmpty() || !posted.sceneSettled()
                    || !posted.dimension().equals(here)) {
                continue;
            }
            filed += examinePosted(server, data, auror, posted, now);
        }
        return filed;
    }

    private static int examinePosted(MinecraftServer server, MinistryCaseData data, ServerPlayer auror,
                                     NpcCase posted, long now) {
        double reach = CaseworkRules.EXAMINE_RADIUS;
        boolean sceneDone = data.hasEvidence(posted.id(), EvidenceKind.SCENE_RESIDUE);
        if (!sceneDone && within(auror, posted.scene(), reach)) {
            data.addEvidence(Evidence.from(EvidenceKind.SCENE_RESIDUE, posted.id(), auror.getUUID(),
                    auror.getGameProfile().name(), now));
            credit(auror, ActivityType.EVIDENCE_GATHERED, posted.template());
            BlockPos to = posted.hideout();
            PlayerFeedback.toast(auror, NoticeKind.SUCCESS, Component.translatable(KEY + "residue.title"),
                    Component.translatable(KEY + "trail_leads", bearing(posted.scene(), to),
                            Component.translatable(KEY + "distance." + NpcCaseRules.distanceBand(
                                    Math.sqrt(posted.scene().distSqr(to))))));
            identifyPostedIfProven(server, data, posted.id(), auror, now);
            return 1;
        }
        if (sceneDone && !data.hasEvidence(posted.id(), EvidenceKind.RESIDUE_TRAIL)
                && within(auror, posted.hideout(), reach)) {
            data.addEvidence(Evidence.from(EvidenceKind.RESIDUE_TRAIL, posted.id(), auror.getUUID(),
                    auror.getGameProfile().name(), now));
            credit(auror, ActivityType.EVIDENCE_GATHERED, posted.template());
            PlayerFeedback.toast(auror, NoticeKind.SUCCESS, Component.translatable(KEY + "trail.title"),
                    Component.translatable(KEY + "trail.body"));
            identifyPostedIfProven(server, data, posted.id(), auror, now);
            return 1;
        }
        return 0;
    }

    /**
     * Questions {@code witness} for whichever held case they saw.
     *
     * @return the outcome; {@link CaseworkRules.Result#NOT_A_WITNESS} when they saw nothing the Auror is working
     */
    public static CaseworkRules.Result interview(ServerPlayer auror, LivingEntity witness) {
        if (!isAuror(auror)) {
            return CaseworkRules.Result.NOT_AUROR;
        }
        MinecraftServer server = auror.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        long now = MinistryTrace.now(server);
        double distance = auror.distanceToSqr(witness);
        CaseworkRules.Result last = CaseworkRules.Result.NOT_A_WITNESS;
        for (Incident incident : heldIncidents(data, auror.getUUID(), now)) {
            if (!data.witnessEntities(incident.id()).contains(witness.getUUID())) {
                continue;
            }
            boolean given = data.hasEvidenceFrom(incident.id(), EvidenceKind.WITNESS_INTERVIEW, witness.getUUID())
                    || data.hasEvidenceFrom(incident.id(), EvidenceKind.MUGGLE_ACCOUNT, witness.getUUID());
            last = CaseworkRules.interview(true, true, witness.isAlive(), distance, given);
            if (last.ok()) {
                fileInterview(auror, witness, incident.id(), PLAYER_CASE, now, data);
                identifyIfProven(server, data, incident.id(), now);
                return last;
            }
        }
        if (witness instanceof Villager) {
            for (NpcCase posted : data.npcCases()) {
                if (posted.aurorAt(now).filter(auror.getUUID()::equals).isEmpty() || !posted.sceneSettled()
                        || !within(witness, posted.scene(), NpcCaseRules.WITNESS_RADIUS)) {
                    continue;
                }
                last = CaseworkRules.interview(true, true, witness.isAlive(), distance,
                        data.hasEvidenceFrom(posted.id(), EvidenceKind.WITNESS_INTERVIEW, witness.getUUID()));
                if (last.ok()) {
                    fileInterview(auror, witness, posted.id(), posted.template(), now, data);
                    PlayerFeedback.actionBar(auror, Component.translatable(KEY + "witness_points",
                            bearing(witness.blockPosition(), posted.hideout())));
                    identifyPostedIfProven(server, data, posted.id(), auror, now);
                    return last;
                }
            }
        }
        return last;
    }

    private static void fileInterview(ServerPlayer auror, LivingEntity witness, long id, Identifier subject, long now,
                                      MinistryCaseData data) {
        data.addEvidence(Evidence.from(EvidenceKind.WITNESS_INTERVIEW, id, witness.getUUID(),
                witness.getName().getString(), now));
        credit(auror, ActivityType.WITNESS_INTERVIEWED, subject);
        credit(auror, ActivityType.EVIDENCE_GATHERED, subject);
        PlayerFeedback.toast(auror, NoticeKind.SUCCESS, Component.translatable(KEY + "interview.title"),
                Component.translatable(KEY + "interview.body", witness.getName()));
    }

    /** Questions every witness within reach. */
    public static int interviewNearby(ServerPlayer auror) {
        int done = 0;
        for (LivingEntity entity : auror.level().getEntitiesOfClass(LivingEntity.class,
                auror.getBoundingBox().inflate(CaseworkRules.INTERVIEW_RADIUS), entity -> entity != auror)) {
            if (interview(auror, entity).ok()) {
                done++;
            }
        }
        return done;
    }

    private static void identifyIfProven(MinecraftServer server, MinistryCaseData data, long incidentId, long now) {
        Incident incident = data.incident(incidentId);
        if (incident != null && !incident.attributed() && EvidenceRules.identifies(data.evidence(incidentId))) {
            MinistryTrace.attribute(server, data, incident.asAttributed(), now);
        }
    }

    /** Called when an incident on the board was attributed: the Auror who held it is credited. */
    static void onIdentified(MinecraftServer server, UnsolvedIncident wasOnBoard, Incident incident, long now) {
        wasOnBoard.aurorAt(now).map(server.getPlayerList()::getPlayer).ifPresent(auror -> {
            credit(auror, ActivityType.SUSPECT_IDENTIFIED, PLAYER_CASE);
            PlayerFeedback.toast(auror, NoticeKind.UNLOCK, Component.translatable(KEY + "identified.title"),
                    Component.translatable(KEY + "identified.body", incident.casterName()));
        });
    }

    /**
     * Brings in a named suspect. Only on a case the Auror holds, at the Aurors' stage, with the suspect close,
     * alive, and downed. A suspect who would rather give themselves up answers their summons instead.
     */
    public static CaseworkRules.Result apprehend(ServerPlayer auror, ServerPlayer suspect) {
        if (!isAuror(auror)) {
            return CaseworkRules.Result.NOT_AUROR;
        }
        MinecraftServer server = auror.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        long now = MinistryTrace.now(server);
        if (suspect.getUUID().equals(auror.getUUID())) {
            return CaseworkRules.Result.OWN_CASE;
        }
        Optional<OpenCase> open = data.dossier(suspect.getUUID()).openCase();
        if (open.isEmpty()) {
            return CaseworkRules.Result.NO_SUCH_CASE;
        }
        CaseworkRules.Result result = CaseworkRules.apprehend(
                open.get().aurorAt(now).filter(auror.getUUID()::equals).isPresent(), open.get().stage(),
                suspect.isAlive(), suspect.level() == auror.level(), auror.distanceToSqr(suspect), downed(suspect),
                false);
        if (!result.ok()) {
            return result;
        }
        credit(auror, ActivityType.SUSPECT_APPREHENDED, PLAYER_CASE);
        suspect.level().playSound(null, suspect.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 0.8f, 1.0f);
        MinistryPost.send(suspect, Component.translatable("ministry.wizards_and_beasts.case.arrested.subject"),
                Component.translatable(KEY + "apprehended_by", auror.getDisplayName()));
        MinistryTrace.hold(server, data, suspect, MinistryTrace.Appearance.CAUGHT, Plea.CONTEST, now);
        data.collectGarbage();
        return result;
    }

    /** Whether an entity is down: badly hurt, or stunned or petrified. */
    public static boolean downed(LivingEntity entity) {
        return CaseworkRules.downed(entity.getHealth(), entity.getMaxHealth(),
                entity.hasEffect(ModEffects.STUPEFY) || entity.hasEffect(ModEffects.PETRIFICUS_TOTALUS));
    }

    /** A case against a player closed by a hearing: the Auror holding it is credited, whatever the verdict. */
    static void onCaseClosed(MinecraftServer server, OpenCase open, ServerPlayer wizard, Wizengamot.Ruling ruling,
                             long now) {
        open.aurorAt(now).filter(holder -> !holder.equals(wizard.getUUID()))
                .map(server.getPlayerList()::getPlayer)
                .ifPresent(auror -> {
                    credit(auror, ActivityType.CASE_CLOSED, PLAYER_CASE);
                    PlayerFeedback.toast(auror, NoticeKind.SUCCESS, Component.translatable(KEY + "closed.title"),
                            Component.translatable(KEY + "closed.body", wizard.getDisplayName(),
                                    Component.translatable("ministry.wizards_and_beasts.hearing.verdict."
                                            + ruling.verdict().getSerializedName() + ".subject")));
                });
    }

    // ── posted cases ──

    private static void postIfShort(MinecraftServer server, MinistryCaseData data, ServerPlayer viewer, long now) {
        // The interval is measured only forwards: a world with an earlier clock (another save) posts at once.
        if (!Config.ministryNpcCases || data.npcCases().size() >= NpcCaseRules.BOARD_SIZE
                || (now >= lastPosting && now - lastPosting < NpcCaseRules.POSTING_INTERVAL_TICKS)) {
            return;
        }
        ServerLevel overworld = server.overworld();
        RandomSource random = overworld.getRandom();
        Map.Entry<Identifier, NpcCaseTemplates.Template> drawn = NpcCaseTemplates.draw(random, seniority(viewer));
        if (drawn == null) {
            return;
        }
        lastPosting = now;
        BlockPos origin = viewer.level().dimension() == Level.OVERWORLD ? viewer.blockPosition()
                : overworld.getRespawnData().pos();
        double angle = random.nextDouble() * Math.PI * 2.0;
        int distance = NpcCaseRules.POST_MIN + random.nextInt(NpcCaseRules.POST_MAX - NpcCaseRules.POST_MIN + 1);
        BlockPos guess = origin.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
        BlockPos village = overworld.findNearestMapStructure(StructureTags.VILLAGE, guess, 16, false);
        BlockPos scene = village != null && village.distSqr(origin) <= 1000.0 * 1000.0
                ? village.offset(random.nextInt(25) - 12, 0, random.nextInt(25) - 12) : guess;
        scene = new BlockPos(scene.getX(), overworld.getSeaLevel(), scene.getZ());
        BlockPos hideout = NpcCaseRules.hideout(scene, random.nextDouble() * Math.PI * 2.0,
                NpcCaseRules.HIDEOUT_MIN + random.nextInt(NpcCaseRules.HIDEOUT_MAX - NpcCaseRules.HIDEOUT_MIN + 1));
        NpcCaseTemplates.Template template = drawn.getValue();
        data.putNpcCase(new NpcCase(data.allocateId(), drawn.getKey(), template.spell(), template.legalClass(),
                Level.OVERWORLD.identifier(), scene, false, hideout, now, now + NpcCaseRules.POSTING_TICKS,
                Optional.empty(), 0L, Optional.empty(), false));
    }

    /** Posts a case now, for an operator or a test; ignores the posting interval. */
    public static @Nullable NpcCase post(ServerPlayer near, Identifier templateId, BlockPos scene, BlockPos hideout) {
        NpcCaseTemplates.Template template = NpcCaseTemplates.get(templateId);
        if (template == null) {
            return null;
        }
        MinecraftServer server = near.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        long now = MinistryTrace.now(server);
        NpcCase posted = new NpcCase(data.allocateId(), templateId, template.spell(), template.legalClass(),
                near.level().dimension().identifier(), scene, true, hideout, now, now + NpcCaseRules.POSTING_TICKS,
                Optional.empty(), 0L, Optional.empty(), false);
        data.putNpcCase(posted);
        return posted;
    }

    private static void identifyPostedIfProven(MinecraftServer server, MinistryCaseData data, long id,
                                               ServerPlayer auror, long now) {
        NpcCase posted = data.npcCase(id);
        if (posted == null || posted.identified() || !EvidenceRules.identifies(data.evidence(id))) {
            return;
        }
        data.putNpcCase(posted.asIdentified());
        credit(auror, ActivityType.SUSPECT_IDENTIFIED, posted.template());
        PlayerFeedback.toast(auror, NoticeKind.UNLOCK, Component.translatable(KEY + "identified.title"),
                Component.translatable(KEY + "identified_posted.body",
                        bearing(auror.blockPosition(), posted.hideout())));
        tickPosted(server, data, data.npcCase(id), now);
    }

    /** Moves every posted case: settles scenes, places culprits, lets lapsed holds and stale postings go. */
    public static void tick(MinecraftServer server, long now) {
        MinistryCaseData data = MinistryCaseData.get(server);
        Set<UUID> culprits = new java.util.HashSet<>();
        for (NpcCase posted : data.npcCases()) {
            NpcCase current = tickPosted(server, data, posted, now);
            if (current != null) {
                current.culprit().ifPresent(culprits::add);
            }
        }
        CULPRITS.retainAll(culprits);
        CULPRITS.addAll(culprits);
    }

    private static @Nullable NpcCase tickPosted(MinecraftServer server, MinistryCaseData data,
                                                @Nullable NpcCase posted, long now) {
        if (posted == null) {
            return null;
        }
        if (posted.auror().isPresent() && posted.aurorAt(now).isEmpty()) {
            // The hold lapsed: the case goes back on the board and the culprit back into hiding.
            discardCulprit(server, posted);
            posted = posted.withAuror(Optional.empty(), 0L).withCulprit(Optional.empty());
            data.putNpcCase(posted);
        }
        if (posted.auror().isEmpty()) {
            if (now >= posted.expiresAt()) {
                discardCulprit(server, posted);
                data.removeNpcCase(posted.id());
                return null;
            }
            return posted;
        }
        ServerPlayer holder = server.getPlayerList().getPlayer(posted.auror().get());
        ServerLevel level = server.overworld();
        if (holder == null || holder.level() != level) {
            return posted;
        }
        if (!posted.sceneSettled()) {
            if (within2d(holder, posted.scene(), NpcCaseRules.SETTLE_REACH) && level.isLoaded(posted.scene())
                    && level.isLoaded(posted.hideout())) {
                posted = posted.withScene(ground(level, posted.scene()), ground(level, posted.hideout()));
                data.putNpcCase(posted);
            }
            return posted;
        }
        if (!data.hasEvidence(posted.id(), EvidenceKind.SCENE_RESIDUE) && within(holder, posted.scene(), 48.0)) {
            BlockPos scene = posted.scene();
            level.sendParticles(ParticleTypes.WITCH, scene.getX() + 0.5, scene.getY() + 0.6, scene.getZ() + 0.5,
                    6, 0.6, 0.4, 0.6, 0.01);
        }
        if (!posted.identified()) {
            return posted;
        }
        Entity culprit = posted.culprit().map(level::getEntity).orElse(null);
        if (culprit == null && within(holder, posted.hideout(), NpcCaseRules.SPAWN_REACH)
                && level.isLoaded(posted.hideout())) {
            culprit = spawnCulprit(level, posted);
            posted = posted.withCulprit(culprit == null ? Optional.empty() : Optional.of(culprit.getUUID()));
            data.putNpcCase(posted);
        }
        if (culprit instanceof LivingEntity living && living.isAlive() && downed(living)
                && holder.distanceToSqr(living) <= CaseworkRules.APPREHEND_RADIUS * CaseworkRules.APPREHEND_RADIUS) {
            // Stunned within reach: the Auror simply takes them.
            closePosted(server, data, posted, holder, true);
            return null;
        }
        return posted;
    }

    private static @Nullable Entity spawnCulprit(ServerLevel level, NpcCase posted) {
        NpcCaseTemplates.Template template = NpcCaseTemplates.get(posted.template());
        if (template == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(template.culprit()).orElse(null);
        Entity entity = type == null ? null : type.create(level, EntitySpawnReason.EVENT);
        if (entity == null) {
            return null;
        }
        BlockPos at = ground(level, posted.hideout());
        entity.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0f, 0.0f);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
            mob.setPersistenceRequired();
        }
        entity.setCustomName(Component.translatable(KEY + "suspect_name"));
        entity.setGlowingTag(true);
        if (!level.addFreshEntity(entity)) {
            return null;
        }
        CULPRITS.add(entity.getUUID());
        return entity;
    }

    /**
     * A culprit took damage. From the Auror holding the case, a blow that would bring them down instead brings them
     * in: they are taken alive.
     *
     * @return whether the damage should be cancelled
     */
    public static boolean onCulpritHurt(LivingEntity culprit, float amount, @Nullable Entity attacker) {
        if (!CULPRITS.contains(culprit.getUUID()) || !(attacker instanceof ServerPlayer auror)) {
            return false;
        }
        MinecraftServer server = auror.level().getServer();
        MinistryCaseData data = MinistryCaseData.get(server);
        long now = MinistryTrace.now(server);
        NpcCase posted = postedFor(data, culprit.getUUID());
        if (posted == null || posted.aurorAt(now).filter(auror.getUUID()::equals).isEmpty()) {
            return false;
        }
        if (culprit.getHealth() - amount > culprit.getMaxHealth() * CaseworkRules.DOWNED_FRACTION) {
            return false;
        }
        closePosted(server, data, posted, auror, true);
        return true;
    }

    /** A culprit died before anyone took them alive. */
    public static void onCulpritDied(LivingEntity culprit) {
        if (!CULPRITS.remove(culprit.getUUID())) {
            return;
        }
        MinecraftServer server = culprit.level().getServer();
        if (server == null) {
            return;
        }
        MinistryCaseData data = MinistryCaseData.get(server);
        NpcCase posted = postedFor(data, culprit.getUUID());
        if (posted == null) {
            return;
        }
        ServerPlayer holder = posted.aurorAt(MinistryTrace.now(server))
                .map(server.getPlayerList()::getPlayer).orElse(null);
        closePosted(server, data, posted, holder, false);
    }

    private static @Nullable NpcCase postedFor(MinistryCaseData data, UUID culprit) {
        for (NpcCase posted : data.npcCases()) {
            if (posted.culprit().filter(culprit::equals).isPresent()) {
                return posted;
            }
        }
        return null;
    }

    /** Closes a posted case: the culprit taken alive and the bounty paid, or dead and the case failed. */
    private static void closePosted(MinecraftServer server, MinistryCaseData data, NpcCase posted,
                                    @Nullable ServerPlayer auror, boolean alive) {
        if (alive) {
            discardCulprit(server, posted);
        }
        posted.culprit().ifPresent(CULPRITS::remove);
        data.removeNpcCase(posted.id());
        if (auror == null) {
            return;
        }
        if (!alive) {
            StandingService.adjust(auror, StandingAxis.PROFESSION, -SUSPECT_KILLED_PENALTY);
            MinistryPost.send(auror, Component.translatable(KEY + "killed.subject"),
                    Component.translatable(KEY + "killed.body"));
            return;
        }
        NpcCaseTemplates.Template template = NpcCaseTemplates.get(posted.template());
        long bounty = template == null ? 0L : template.bountyKnuts();
        boolean paid = bounty > 0L && ModuleManager.isEnabled(Module.GRINGOTTS)
                && GringottsBilling.credit(auror, MoneyFlow.MINISTRY_BOUNTY, bounty);
        credit(auror, ActivityType.SUSPECT_APPREHENDED, posted.template());
        credit(auror, ActivityType.CASE_CLOSED, posted.template());
        StandingService.adjust(auror, StandingAxis.PROFESSION, APPREHENSION_CREDIT);
        auror.level().playSound(null, auror.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 0.8f, 1.2f);
        MinistryPost.send(auror, Component.translatable(KEY + "taken.subject"), paid
                ? Component.translatable(KEY + "taken.body_paid", CurrencyHelper.formatFromKnuts(bounty))
                : Component.translatable(KEY + "taken.body"));
    }

    private static void discardCulprit(MinecraftServer server, NpcCase posted) {
        posted.culprit().ifPresent(id -> {
            Entity entity = server.overworld().getEntity(id);
            if (entity != null) {
                server.overworld().sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 1.0,
                        entity.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
                entity.discard();
            }
            CULPRITS.remove(id);
        });
    }

    // ── helpers ──

    private static void credit(ServerPlayer auror, ActivityType type, Identifier subject) {
        CareerProgression.record(auror, type, subject);
    }

    private static boolean within(Entity entity, BlockPos pos, double radius) {
        return entity.blockPosition().distSqr(pos) <= radius * radius;
    }

    private static boolean within2d(Entity entity, BlockPos pos, double radius) {
        double dx = entity.getX() - (pos.getX() + 0.5);
        double dz = entity.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dz * dz <= radius * radius;
    }

    private static BlockPos ground(ServerLevel level, BlockPos pos) {
        return new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(),
                pos.getZ()), pos.getZ());
    }

    /** A compass point from {@code from} to {@code to}, as a component. */
    public static Component bearing(BlockPos from, BlockPos to) {
        return Component.translatable(KEY + "bearing." + NpcCaseRules.bearing(to.getX() - from.getX(),
                to.getZ() - from.getZ()));
    }
}
