package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.item.trinket.MinistryLicenseScrollItem;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.data.MinistryRank;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicences;
import at.koopro.wizardsandbeasts.ministry.trace.CaseRules;
import at.koopro.wizardsandbeasts.ministry.trace.CaseStage;
import at.koopro.wizardsandbeasts.ministry.trace.CaseworkRules;
import at.koopro.wizardsandbeasts.ministry.trace.ClosedCase;
import at.koopro.wizardsandbeasts.ministry.trace.Dossier;
import at.koopro.wizardsandbeasts.ministry.trace.Evidence;
import at.koopro.wizardsandbeasts.ministry.trace.EvidenceKind;
import at.koopro.wizardsandbeasts.ministry.trace.Exposure;
import at.koopro.wizardsandbeasts.ministry.trace.Incident;
import at.koopro.wizardsandbeasts.ministry.trace.LegalClass;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCaseData;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCasework;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace;
import at.koopro.wizardsandbeasts.ministry.trace.NpcCase;
import at.koopro.wizardsandbeasts.ministry.trace.OpenCase;
import at.koopro.wizardsandbeasts.ministry.trace.Plea;
import at.koopro.wizardsandbeasts.ministry.trace.TraceRules;
import at.koopro.wizardsandbeasts.ministry.trace.UnsolvedIncident;
import at.koopro.wizardsandbeasts.ministry.trace.Verdict;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * Ministry law as a game, run on a live server: testimony that cannot frame anyone, an Auror who solves an
 * incident the inquiry could not, apprehension, the plea and the appeal, licence suspensions, a bribe, and a posted
 * case whose culprit is taken alive or lost.
 *
 * <p>As in {@link MinistryTraceTests}, the Ministry module is switched on only inside the step that needs it. The
 * two scenarios that read "who saw it" run in batches of their own ({@code addAlone}): a neighbour's villager inside
 * the witness radius would be a witness the scenario never placed.
 */
public final class MinistryCaseworkTests {

    private static final int Y = 140;
    private static final String DARK_SPELL = "sectumsempra";

    private MinistryCaseworkTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.addAlone("ministry_casework_players_alone_cannot_name_anyone",
                "ministry: any number of eyewitness players cannot attribute an incident; bystanders cannot testify",
                MinistryCaseworkTests::playersAloneCannotNameAnyone);
        tests.addAlone("ministry_casework_auror_solves_and_apprehends",
                "ministry: an Auror examines the scene of an unsolved incident, names the caster and takes them in",
                MinistryCaseworkTests::anAurorSolvesAndApprehends);
        tests.add("ministry_casework_plea_and_appeal",
                "ministry: a thin conviction is overturned on appeal and given back; a plea cannot be appealed",
                MinistryCaseworkTests::pleaAndAppeal);
        tests.add("ministry_casework_suspension_and_bribe",
                "ministry: a suspended licence refuses whatever proof is carried; only an early, clean file is lost",
                MinistryCaseworkTests::suspensionAndBribe);
        tests.add("ministry_casework_posted_case",
                "ministry: a posted culprit is found by scene and trail, taken alive, or lost when killed",
                MinistryCaseworkTests::postedCase);
    }

    // ── testimony ──

    private static void playersAloneCannotNameAnyone(GameTestHelper helper) {
        stage(helper);
        ServerPlayer caster = wizard(helper, "wandb-case-caster", 2, 2);
        ServerPlayer w1 = wizard(helper, "wandb-case-w1", 5, 2);
        ServerPlayer w2 = wizard(helper, "wandb-case-w2", 2, 5);
        ServerPlayer w3 = wizard(helper, "wandb-case-w3", 5, 5);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(), max()))
                .thenExecute(() -> {
                    withMinistry(() -> MinistryTrace.onSuccessfulCast(caster, DARK_SPELL, SpellCategory.DARK_ARTS));
                    Incident incident = latestBy(helper, caster);
                    check(helper, incident != null, () -> "an unseen dark act was not kept for a wand examination");
                    check(helper, incident.wizardWitnesses().containsAll(List.of(w1.getUUID(), w2.getUUID(),
                            w3.getUUID())), () -> "the eyewitnesses were not recorded: " + incident.wizardWitnesses());

                    // A player the server did not see there cannot accuse anyone: placed after the cast.
                    ServerPlayer bystander = wizard(helper, "wandb-case-bystander", 4, 3);
                    try {
                        CaseworkRules.Result notThere = withMinistry(() -> MinistryTrace.testify(bystander,
                                incident.id()));
                        check(helper, notThere == CaseworkRules.Result.NOT_A_WITNESS,
                                () -> "a player who was not there testified: " + notThere);
                    } finally {
                        retire(helper, bystander);
                    }
                    CaseworkRules.Result self = withMinistry(() -> MinistryTrace.testify(caster, incident.id()));
                    check(helper, self == CaseworkRules.Result.OWN_CASE, () -> "the caster testified: " + self);

                    for (ServerPlayer witness : List.of(w1, w2, w3)) {
                        CaseworkRules.Result given = withMinistry(() -> MinistryTrace.testify(witness, incident.id()));
                        check(helper, given.ok(), () -> "a recorded eyewitness could not testify: " + given);
                    }
                    CaseworkRules.Result again = withMinistry(() -> MinistryTrace.testify(w1, incident.id()));
                    check(helper, again == CaseworkRules.Result.ALREADY_GIVEN, () -> "a witness spoke twice: " + again);

                    Incident after = data(helper).incident(incident.id());
                    check(helper, after != null && !after.attributed(),
                            () -> "three players' word alone named the caster");
                    check(helper, data(helper).dossier(caster.getUUID()).equals(Dossier.EMPTY),
                            () -> "players alone opened a case: " + data(helper).dossier(caster.getUUID()));
                })
                .thenExecute(() -> {
                    retire(helper, caster);
                    retire(helper, w1);
                    retire(helper, w2);
                    retire(helper, w3);
                })
                .thenSucceed();
    }

    // ── an Auror's case ──

    private static void anAurorSolvesAndApprehends(GameTestHelper helper) {
        stage(helper);
        ServerPlayer caster = wizard(helper, "wandb-case-dark", 2, 2);
        // Far enough that they are not a witness to what they will investigate.
        ServerPlayer auror = wizard(helper, "wandb-case-auror", 2, 7);
        ServerPlayer clerk = wizard(helper, "wandb-case-clerk", 4, 7);
        Villager muggle = helper.spawn(EntityType.VILLAGER, new BlockPos(4, Y, 2));
        muggle.setNoAi(true);
        muggle.setInvulnerable(true);
        auror.snapTo(auror.getX() + 60.0, auror.getY(), auror.getZ(), 0.0f, 0.0f);
        clerk.snapTo(clerk.getX() + 60.0, clerk.getY(), clerk.getZ(), 0.0f, 0.0f);
        MinistryRecords.mutate(auror, record -> record.withRank(MinistryRank.AUROR));
        long[] incidentId = new long[1];

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(), max()))
                .thenExecute(() -> {
                    withMinistry(() -> MinistryTrace.onSuccessfulCast(caster, DARK_SPELL, SpellCategory.DARK_ARTS));
                    Incident incident = latestBy(helper, caster);
                    check(helper, incident != null && incident.exposure() == Exposure.EXTREME,
                            () -> "dark magic in front of a Muggle was not an extreme breach: " + incident);
                    incidentId[0] = incident.id();
                    process(helper, TraceRules.MUGGLE_REPORT_DELAY_TICKS, caster);
                    process(helper, TraceRules.MUGGLE_REPORT_DELAY_TICKS + TraceRules.INQUIRY_TICKS, caster);
                    UnsolvedIncident board = data(helper).unsolved(incident.id());
                    check(helper, board != null, () -> "an inquiry one Muggle could not settle was not left to the Aurors");
                    check(helper, data(helper).dossier(caster.getUUID()).openCase().isEmpty(),
                            () -> "one Muggle's account named the caster");
                })
                .thenExecute(() -> {
                    long id = incidentId[0];
                    CaseworkRules.Result clerkTakes = withMinistry(() -> MinistryCasework.take(clerk, id));
                    check(helper, clerkTakes == CaseworkRules.Result.NOT_AUROR,
                            () -> "a wizard with no post took a case: " + clerkTakes);
                    MinistryRecords.mutate(caster, record -> record.withRank(MinistryRank.AUROR));
                    CaseworkRules.Result ownCase = withMinistry(() -> MinistryCasework.take(caster, id));
                    MinistryRecords.mutate(caster, record -> record.withRank(MinistryRank.NONE));
                    check(helper, ownCase == CaseworkRules.Result.OWN_CASE,
                            () -> "an Auror took the case against themselves: " + ownCase);
                    CaseworkRules.Result taken = withMinistry(() -> MinistryCasework.take(auror, id));
                    check(helper, taken.ok(), () -> "the Auror could not take the case: " + taken);

                    int fromAfar = withMinistry(() -> MinistryCasework.examine(auror));
                    check(helper, fromAfar == 0, () -> "a scene was examined from 60 blocks away");

                    auror.snapTo(caster.getX() + 1.0, caster.getY(), caster.getZ() + 2.0, 0.0f, 0.0f);
                    CaseworkRules.Result requestioned = withMinistry(() -> MinistryCasework.interview(auror, muggle));
                    check(helper, requestioned == CaseworkRules.Result.ALREADY_GIVEN,
                            () -> "a Muggle who already gave an account was counted again: " + requestioned);
                    int filed = withMinistry(() -> MinistryCasework.examine(auror));
                    check(helper, filed == 1, () -> "examining the scene filed " + filed + " items");
                    Optional<OpenCase> open = data(helper).dossier(caster.getUUID()).openCase();
                    check(helper, open.isPresent() && open.get().stage() == CaseStage.AURORS_ASSIGNED,
                            () -> "the scene and the Muggle did not name the caster: " + open);
                    check(helper, open.get().aurorAt(MinistryTrace.now(helper.getLevel().getServer()))
                                    .filter(auror.getUUID()::equals).isPresent(),
                            () -> "the Auror who named the caster does not hold the case");
                    check(helper, open.get().caseId() > 0L, () -> "a new case has no number");
                })
                .thenExecute(() -> {
                    CaseworkRules.Result standing = withMinistry(() -> MinistryCasework.apprehend(auror, caster));
                    check(helper, standing == CaseworkRules.Result.NOT_DOWNED,
                            () -> "a suspect on their feet was taken: " + standing);
                    caster.setHealth(caster.getMaxHealth() * 0.2f);
                    CaseworkRules.Result taken = withMinistry(() -> MinistryCasework.apprehend(auror, caster));
                    check(helper, taken.ok(), () -> "a downed suspect could not be taken: " + taken);
                    Dossier after = data(helper).dossier(caster.getUUID());
                    check(helper, after.openCase().isEmpty() && after.lastCase().isPresent(),
                            () -> "no hearing followed the apprehension: " + after);
                    ClosedCase closed = after.lastCase().get();
                    check(helper, closed.ruling().verdict() == Verdict.AZKABAN_REFERRAL
                                    && closed.ruling().reasons().contains("failed_to_appear"),
                            () -> "a dark act in front of a Muggle, caught: " + closed.ruling());
                    check(helper, MinistryRecords.get(caster).conduct() < 0,
                            () -> "being caught did not count against conduct");
                })
                .thenExecute(() -> {
                    retire(helper, caster);
                    retire(helper, auror);
                    retire(helper, clerk);
                })
                .thenSucceed();
    }

    // ── the court ──

    private static void pleaAndAppeal(GameTestHelper helper) {
        ServerPlayer contested = wizard(helper, "wandb-case-contest", 1, 1);
        ServerPlayer pleaded = wizard(helper, "wandb-case-plead", 3, 1);
        try {
            // Two Muggle accounts: exactly the bar for dark magic, and short of the appeal court's.
            summon(helper, contested, LegalClass.DARK, 2);
            MinistryTrace.Answer heard = withMinistry(() -> MinistryTrace.answer(contested, Plea.CONTEST));
            check(helper, heard == MinistryTrace.Answer.HEARD, () -> "the summons could not be answered: " + heard);
            ClosedCase closed = data(helper).dossier(contested.getUUID()).lastCase().orElseThrow();
            check(helper, closed.ruling().verdict() == Verdict.WAND_CONFISCATION && closed.appealBy() > 0L,
                    () -> "a contested dark conviction: " + closed);
            check(helper, withMinistry(() -> MinistryTrace.wandConfiscated(contested)), () -> "the wand was not held");
            check(helper, MinistryLicences.suspended(contested, LicenseType.APPARITION),
                    () -> "no Apparition suspension came with the wand");

            MinistryTrace.AppealResult appeal = withMinistry(() -> withAppealFee(0, () -> MinistryTrace.appeal(contested)));
            check(helper, appeal == MinistryTrace.AppealResult.OVERTURNED, () -> "the appeal: " + appeal);
            check(helper, !withMinistry(() -> MinistryTrace.wandConfiscated(contested)),
                    () -> "an overturned ruling kept the wand");
            check(helper, !MinistryLicences.suspended(contested, LicenseType.APPARITION),
                    () -> "an overturned ruling kept the suspension");
            check(helper, data(helper).dossier(contested.getUUID()).lastCase().orElseThrow().appeal()
                    == ClosedCase.Appeal.OVERTURNED, () -> "the file does not show the appeal");
            MinistryTrace.AppealResult twice = withMinistry(() -> withAppealFee(0, () -> MinistryTrace.appeal(contested)));
            check(helper, twice == MinistryTrace.AppealResult.ALREADY_APPEALED, () -> "appealed twice: " + twice);

            // A plea convicts on evidence that would not, and gives up the appeal.
            summon(helper, pleaded, LegalClass.DARK, 0);
            withMinistry(() -> MinistryTrace.answer(pleaded, Plea.GUILTY));
            ClosedCase plea = data(helper).dossier(pleaded.getUUID()).lastCase().orElseThrow();
            check(helper, plea.ruling().verdict() == Verdict.WAND_CONFISCATION
                            && plea.ruling().reasons().contains("pleaded_guilty")
                            && plea.ruling().confiscationTicks() < 72000L,
                    () -> "a guilty plea: " + plea.ruling());
            MinistryTrace.AppealResult refused = withMinistry(() -> withAppealFee(0, () -> MinistryTrace.appeal(pleaded)));
            check(helper, refused == MinistryTrace.AppealResult.PLEADED_GUILTY, () -> "a plea was appealed: " + refused);
            helper.succeed();
        } finally {
            retire(helper, contested);
            retire(helper, pleaded);
        }
    }

    // ── licences and the black market ──

    private static void suspensionAndBribe(GameTestHelper helper) {
        ServerPlayer holder = wizard(helper, "wandb-case-licensed", 1, 1);
        ServerPlayer early = wizard(helper, "wandb-case-early", 3, 1);
        ServerPlayer dark = wizard(helper, "wandb-case-darkfile", 1, 3);
        try {
            holder.getInventory().add(MinistryLicenseScrollItem.issue(
                    new ItemStack(TrinketItemRegistry.MINISTRY_LICENSE_SCROLL.get()), LicenseType.BROOM, holder.getUUID()));
            check(helper, MinistryLicences.has(holder, LicenseType.BROOM), () -> "the issued licence was not valid");
            PlayerAbilityHelper.setApparitionLicensed(holder, true);
            long now = MinistryTrace.now(helper.getLevel().getServer());
            MinistryRecords.mutate(holder, record -> record.withSuspension(LicenseType.BROOM, now + 24000L)
                    .withSuspension(LicenseType.APPARITION, now + 24000L));
            check(helper, !MinistryLicences.has(holder, LicenseType.BROOM), () -> "a suspended licence still admitted");
            check(helper, !ApparitionServerLogic.isLicensed(holder), () -> "the Apparition flag outran its suspension");
            MinistryRecords.mutate(holder, record -> record.withoutSuspension(LicenseType.BROOM));
            check(helper, MinistryLicences.has(holder, LicenseType.BROOM), () -> "a lifted suspension still refused");

            MinecraftServer server = helper.getLevel().getServer();
            investigate(helper, early, LegalClass.RESTRICTED);
            investigate(helper, dark, LegalClass.DARK);
            check(helper, MinistryTrace.fileCanBeLost(server, early.getUUID()), () -> "an early, clean file is not for sale");
            check(helper, !MinistryTrace.fileCanBeLost(server, dark.getUUID()), () -> "a dark file was for sale");
            check(helper, MinistryTrace.loseFile(early) && data(helper).dossier(early.getUUID()).openCase().isEmpty(),
                    () -> "the bought file was not lost");
            investigate(helper, early, LegalClass.RESTRICTED);
            withMinistry(() -> MinistryTrace.escalateAfterBribe(early));
            check(helper, data(helper).dossier(early.getUUID()).openCase().map(OpenCase::stage).orElse(null)
                    == CaseStage.SUMMONED, () -> "a caught bribe did not bring the summons forward");
            helper.succeed();
        } finally {
            retire(helper, holder);
            retire(helper, early);
            retire(helper, dark);
        }
    }

    // ── a posted case ──

    private static void postedCase(GameTestHelper helper) {
        stage(helper);
        ServerPlayer auror = wizard(helper, "wandb-case-posted", 2, 2);
        MinistryRecords.mutate(auror, record -> record.withRank(MinistryRank.AUROR));
        Identifier template = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "jinxed_villagers");
        BlockPos scene = helper.absolutePos(new BlockPos(2, Y, 2));
        BlockPos hideout = helper.absolutePos(new BlockPos(22, Y, 2));
        long[] ids = new long[2];

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(), max()))
                .thenExecute(() -> {
                    NpcCase posted = MinistryCasework.post(auror, template, scene, hideout);
                    check(helper, posted != null, () -> "the jinxed_villagers template did not load");
                    ids[0] = posted.id();
                    found(helper, auror, posted.id(), scene, hideout);
                    Entity culprit = culprit(helper, posted.id());
                    check(helper, culprit instanceof LivingEntity, () -> "the culprit was not at the hideout");
                    boolean taken = withMinistry(() -> MinistryCasework.onCulpritHurt((LivingEntity) culprit, 1000.0f,
                            auror));
                    check(helper, taken && data(helper).npcCase(posted.id()) == null && culprit.isRemoved(),
                            () -> "the culprit was not taken alive by the Auror's blow");
                })
                .thenExecute(() -> {
                    NpcCase second = MinistryCasework.post(auror, template, scene, hideout);
                    ids[1] = second.id();
                    found(helper, auror, second.id(), scene, hideout);
                    Entity culprit = culprit(helper, second.id());
                    check(helper, culprit instanceof LivingEntity, () -> "the second culprit was not at the hideout");
                    withMinistry(() -> {
                        culprit.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(),
                                Float.MAX_VALUE);
                    });
                    check(helper, data(helper).npcCase(second.id()) == null,
                            () -> "a dead culprit's case stayed on the board");
                })
                .thenExecute(() -> retire(helper, auror))
                .thenSucceed();
    }

    /** Takes the posted case, examines the scene and the hideout, and lets the tick place the culprit. */
    private static void found(GameTestHelper helper, ServerPlayer auror, long id, BlockPos scene, BlockPos hideout) {
        CaseworkRules.Result taken = withMinistry(() -> MinistryCasework.take(auror, id));
        check(helper, taken.ok(), () -> "the posted case could not be taken: " + taken);
        auror.snapTo(scene.getX() + 0.5, scene.getY(), scene.getZ() + 0.5, 0.0f, 0.0f);
        int atScene = withMinistry(() -> MinistryCasework.examine(auror));
        check(helper, atScene == 1, () -> "the scene gave " + atScene);
        check(helper, !data(helper).npcCase(id).identified(), () -> "the scene alone identified the culprit");
        auror.snapTo(hideout.getX() + 0.5, hideout.getY(), hideout.getZ() + 3.5, 0.0f, 0.0f);
        int atHideout = withMinistry(() -> MinistryCasework.examine(auror));
        check(helper, atHideout == 1 && data(helper).npcCase(id).identified(),
                () -> "the trail did not identify the culprit: " + data(helper).npcCase(id));
        withMinistry(() -> MinistryCasework.tick(helper.getLevel().getServer(),
                MinistryTrace.now(helper.getLevel().getServer())));
    }

    private static Entity culprit(GameTestHelper helper, long id) {
        NpcCase posted = data(helper).npcCase(id);
        return posted == null ? null : posted.culprit().map(helper.getLevel()::getEntity).orElse(null);
    }

    // ── cases built by hand ──

    /** Opens a case at the summons stage on one incident carrying {@code muggleAccounts} accounts. */
    private static void summon(GameTestHelper helper, ServerPlayer wizard, LegalClass legalClass, int muggleAccounts) {
        open(helper, wizard, legalClass, muggleAccounts, CaseStage.SUMMONED);
    }

    private static void investigate(GameTestHelper helper, ServerPlayer wizard, LegalClass legalClass) {
        open(helper, wizard, legalClass, 2, CaseStage.INVESTIGATING);
    }

    private static void open(GameTestHelper helper, ServerPlayer wizard, LegalClass legalClass, int muggleAccounts,
                             CaseStage stage) {
        MinistryCaseData data = data(helper);
        long now = MinistryTrace.now(helper.getLevel().getServer());
        Incident incident = new Incident(data.allocateId(), wizard.getUUID(), wizard.getGameProfile().name(),
                DARK_SPELL, helper.getLevel().dimension().identifier(), wizard.blockPosition(), now, legalClass,
                Exposure.LOW, false, false, muggleAccounts, List.of(), Optional.empty(), true);
        data.putIncident(incident);
        for (int i = 0; i < muggleAccounts; i++) {
            data.addEvidence(Evidence.from(EvidenceKind.MUGGLE_ACCOUNT, incident.id(), UUID.randomUUID(), "", now));
        }
        OpenCase open = new OpenCase(stage, List.of(incident.id()), now,
                stage == CaseStage.SUMMONED ? now + CaseRules.SUMMONS_TICKS : 0L, incident.dimension(), incident.pos(),
                0L, 0, false).withCaseId(data.allocateId());
        data.putDossier(wizard.getUUID(), data.dossier(wizard.getUUID()).withOpenCase(Optional.of(open)));
    }

    // ── helpers ──

    private static ServerPlayer wizard(GameTestHelper helper, String name, int x, int z) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, UUID.randomUUID(), GameType.CREATIVE);
        BlockPos at = helper.absolutePos(new BlockPos(x, Y, z));
        player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
        player.setNoGravity(true);
        WizardTestSupport.makeWandkind(player);
        return player;
    }

    private static void stage(GameTestHelper helper) {
        WizardTestSupport.forceChunks(helper, min(), max());
        for (int x = -1; x <= 26; x++) {
            for (int z = -1; z <= 9; z++) {
                helper.setBlock(new BlockPos(x, Y - 1, z), Blocks.STONE);
                for (int dy = 0; dy <= 2; dy++) {
                    helper.setBlock(new BlockPos(x, Y + dy, z), Blocks.AIR);
                }
            }
        }
    }

    private static BlockPos min() {
        return new BlockPos(-1, Y - 1, -1);
    }

    private static BlockPos max() {
        return new BlockPos(70, Y + 2, 9);
    }

    private static Incident latestBy(GameTestHelper helper, ServerPlayer caster) {
        MinistryCaseData data = data(helper);
        Incident latest = null;
        for (long id : data.darkActs(caster.getUUID())) {
            Incident incident = data.incident(id);
            if (incident != null && (latest == null || incident.id() > latest.id())) {
                latest = incident;
            }
        }
        return latest;
    }

    private static void process(GameTestHelper helper, long ticksAhead, ServerPlayer subject) {
        MinecraftServer server = helper.getLevel().getServer();
        withMinistry(() -> MinistryTrace.process(server, MinistryTrace.now(server) + ticksAhead,
                subject.getUUID()::equals));
    }

    private static MinistryCaseData data(GameTestHelper helper) {
        return MinistryCaseData.get(helper.getLevel().getServer());
    }

    private static <T> T withAppealFee(int knuts, Supplier<T> action) {
        int was = Config.ministryAppealFeeKnuts;
        Config.ministryAppealFeeKnuts = knuts;
        try {
            return action.get();
        } finally {
            Config.ministryAppealFeeKnuts = was;
        }
    }

    private static void withMinistry(Runnable action) {
        withMinistry(() -> {
            action.run();
            return null;
        });
    }

    private static <T> T withMinistry(Supplier<T> action) {
        boolean wasOn = ModuleManager.isEnabled(Module.MINISTRY);
        ModuleManager.overrideCached(Module.MINISTRY, ModuleState.ENABLED);
        try {
            return action.get();
        } finally {
            if (!wasOn) {
                ModuleManager.overrideCached(Module.MINISTRY, ModuleState.DISABLED);
            }
        }
    }
}
