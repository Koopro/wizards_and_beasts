package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.map.MapLandmarkTags;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryArea;
import at.koopro.wizardsandbeasts.ministry.trace.CaseRules;
import at.koopro.wizardsandbeasts.ministry.trace.CaseStage;
import at.koopro.wizardsandbeasts.ministry.trace.Dossier;
import at.koopro.wizardsandbeasts.ministry.trace.Incident;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCaseData;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace;
import at.koopro.wizardsandbeasts.ministry.trace.PendingReport;
import at.koopro.wizardsandbeasts.ministry.trace.ReportChannel;
import at.koopro.wizardsandbeasts.ministry.trace.TraceRules;
import at.koopro.wizardsandbeasts.ministry.trace.Verdict;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.SpellNetworkGuards;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static at.koopro.wizardsandbeasts.gametest.WandCastLifecycleTests.beginCast;
import static at.koopro.wizardsandbeasts.gametest.WandCastLifecycleTests.releaseWand;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.spellData;

/**
 * The Ministry of Magic, run inside a live server: the Trace, witnesses, cases, Aurors and hearings.
 *
 * <p>Every scenario casts through the real wand path except where a spell cannot be cast by a test player in
 * the first place — the Unforgivables need the Dark Arts module, a target and a dark alignment — and there the
 * Ministry's own cast hook is called with the spell id, which is everything the Trace reads.
 *
 * <p>The Ministry module ships disabled, and switching it on is server-wide, so each scenario switches it on
 * only inside the step that needs it and back off before the step ends; no other scenario ever sees it on. The
 * clock is run ahead through {@link MinistryTrace#process(MinecraftServer, long, java.util.function.Predicate)}
 * for this scenario's wizards only.
 *
 * <p>Scenarios sit high above the grid and far apart: witnesses count out to 48 blocks, and a neighbour's
 * player or villager inside that range would be a witness this scenario never placed.
 */
public final class MinistryTraceTests {

    private static final String SPELL = "arresto_momentum";
    private static final String SPELL_PREREQUISITE = "wingardium_leviosa";

    private static final int CHILD_Y = 100;
    private static final int ALONE_Y = 150;
    private static final int HOGWARTS_Y = 200;
    private static final int MUGGLES_Y = 250;
    private static final int EXAMINATION_Y = 300;
    private static final int AURORS_Y = 350;

    private MinistryTraceTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("ministry_trace_child_is_warned_then_heard",
                "ministry: an underage wizard is warned, then summoned, then loses the wand",
                MinistryTraceTests::aChildIsWarnedThenHeard);
        tests.add("ministry_trace_nobody_saw_it",
                "ministry: an adult alone, or a child among adults, is known to nobody",
                MinistryTraceTests::nobodySawIt);
        tests.add("ministry_trace_hogwarts_lets_children_learn",
                "ministry: underage magic at Hogwarts is not a breach",
                MinistryTraceTests::hogwartsLetsChildrenLearn);
        tests.add("ministry_trace_muggles_see_magic",
                "ministry: one Muggle is tidied up; a crowd opens an inquiry that names the caster",
                MinistryTraceTests::mugglesSeeMagic);
        tests.add("ministry_trace_wand_examination_finds_an_unforgivable",
                "ministry: an unseen Unforgivable is found when a later hearing examines the wand",
                MinistryTraceTests::aWandExaminationFindsAnUnforgivable);
        tests.add("ministry_trace_aurors_follow_the_last_report",
                "ministry: Aurors miss the dead, the departed and the logged out, then arrest where reported",
                MinistryTraceTests::aurorsFollowTheLastReport);
    }

    // ── underage, wilderness, repeated violations ─────────────────────────────────────────────────

    private static void aChildIsWarnedThenHeard(GameTestHelper helper) {
        stage(helper, CHILD_Y, Blocks.STONE);
        ServerPlayer child = wizard(helper, "wandb-trace-child", CHILD_Y, 2, 2, 13);
        String spell = canonical();

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(CHILD_Y), max(CHILD_Y)))
                .thenExecute(() -> beginCast(helper, child))
                .thenIdle(1)
                .thenExecute(() -> {
                    withMinistry(() -> releaseWand(child));
                    check(helper, spellData(child).getCastCount(spell) == 1, () -> "the child's spell never cast");
                    List<PendingReport> reports = reportsAbout(helper, child);
                    check(helper, reports.size() == 1 && reports.getFirst().channel() == ReportChannel.TRACE
                                    && reports.getFirst().identifiesCaster(),
                            () -> "the Trace should report a lone child by name, and only the Trace: " + reports);
                    check(helper, dossier(helper, child).equals(Dossier.EMPTY),
                            () -> "the Ministry knew before any report reached it: " + dossier(helper, child));

                    process(helper, TraceRules.TRACE_DELAY_TICKS, child);
                    Dossier warned = dossier(helper, child);
                    check(helper, warned.underageWarnings() == 1 && warned.openCase().isEmpty(),
                            () -> "a first offence should be a warning letter and nothing more: " + warned);
                    check(helper, warned.undelivered().isEmpty(),
                            () -> "the warning was not delivered to a wizard who is online: " + warned);
                })
                .thenExecute(() -> {
                    clearCooldowns(child);
                    beginCast(helper, child);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    withMinistry(() -> releaseWand(child));
                    check(helper, spellData(child).getCastCount(spell) == 2, () -> "the second spell never cast");
                    process(helper, TraceRules.TRACE_DELAY_TICKS, child);
                    Dossier summoned = dossier(helper, child);
                    check(helper, stage(summoned) == CaseStage.SUMMONED,
                            () -> "a second offence after a warning should mean a hearing: " + summoned);

                    MinistryTrace.Answer answer = withMinistry(() -> MinistryTrace.answerSummons(child));
                    Dossier heard = dossier(helper, child);
                    check(helper, answer == MinistryTrace.Answer.HEARD, () -> "the summons could not be answered");
                    check(helper, heard.openCase().isEmpty()
                                    && heard.lastVerdict().orElse(null) == Verdict.WAND_CONFISCATION,
                            () -> "underage magic after a warning should cost the wand for a while: " + heard);
                    check(helper, MinistryRecords.get(child).offenceCount(MagicalOffence.UNDERAGE_MAGIC) == 1,
                            () -> "the caution was not filed: " + MinistryRecords.get(child));
                    String refusal = withMinistry(() -> SpellNetworkGuards.wandRefusal(child));
                    check(helper, SpellRejectCodes.SUFFIX_WAND_CONFISCATED.equals(refusal),
                            () -> "a confiscated wand still answered its wizard: refusal " + refusal);
                })
                .thenExecute(() -> retire(helper, child))
                .thenSucceed();
    }

    // ── adult, no witness ─────────────────────────────────────────────────────────────────────────

    private static void nobodySawIt(GameTestHelper helper) {
        stage(helper, ALONE_Y, Blocks.STONE);
        ServerPlayer adult = wizard(helper, "wandb-trace-adult", ALONE_Y, 1, 2, -1);
        ServerPlayer child = wizard(helper, "wandb-trace-with-parent", ALONE_Y, 3, 2, 12);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(ALONE_Y), max(ALONE_Y)))
                .thenExecute(() -> beginCast(helper, adult))
                .thenIdle(1)
                .thenExecute(() -> {
                    withMinistry(() -> releaseWand(adult));
                    check(helper, spellData(adult).getCastCount(canonical()) == 1, () -> "the adult's spell never cast");
                    check(helper, reportsAbout(helper, adult).isEmpty(),
                            () -> "an adult's legal magic in an empty field was reported: " + reportsAbout(helper, adult));
                    process(helper, CaseRules.SUMMONS_TICKS, adult);
                    check(helper, dossier(helper, adult).equals(Dossier.EMPTY),
                            () -> "the Ministry opened a file on magic nobody saw: " + dossier(helper, adult));
                })
                .thenExecute(() -> beginCast(helper, child))
                .thenIdle(1)
                .thenExecute(() -> {
                    withMinistry(() -> releaseWand(child));
                    check(helper, spellData(child).getCastCount(canonical()) == 1, () -> "the child's spell never cast");
                    List<PendingReport> reports = reportsAbout(helper, child);
                    check(helper, reports.size() == 1 && !reports.getFirst().identifiesCaster(),
                            () -> "with an adult wizard beside them the Trace cannot tell who cast: " + reports);
                    process(helper, CaseRules.SUMMONS_TICKS, child);
                    check(helper, dossier(helper, child).equals(Dossier.EMPTY),
                            () -> "a child was warned for magic the adult beside them could have done: "
                                    + dossier(helper, child));
                })
                .thenExecute(() -> {
                    retire(helper, adult);
                    retire(helper, child);
                })
                .thenSucceed();
    }

    // ── Hogwarts ──────────────────────────────────────────────────────────────────────────────────

    private static void hogwartsLetsChildrenLearn(GameTestHelper helper) {
        Block hogwartsStone = BuiltInRegistries.BLOCK.getValue(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hogwarts_stone"));
        stage(helper, HOGWARTS_Y, hogwartsStone);
        ServerPlayer student = wizard(helper, "wandb-trace-student", HOGWARTS_Y, 2, 2, 12);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(HOGWARTS_Y), max(HOGWARTS_Y)))
                .thenExecute(() -> {
                    check(helper, MinistryArea.isInside(helper.getLevel(), student.blockPosition(),
                                    MapLandmarkTags.HOGWARTS),
                            () -> "the fixture is not Hogwarts, so this scenario would prove nothing");
                    beginCast(helper, student);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    withMinistry(() -> releaseWand(student));
                    check(helper, spellData(student).getCastCount(canonical()) == 1,
                            () -> "the student's spell never cast");
                    check(helper, reportsAbout(helper, student).isEmpty(),
                            () -> "magic at school was reported: " + reportsAbout(helper, student));
                    process(helper, CaseRules.SUMMONS_TICKS, student);
                    check(helper, dossier(helper, student).equals(Dossier.EMPTY),
                            () -> "a student was written to for magic at school: " + dossier(helper, student));
                })
                .thenExecute(() -> retire(helper, student))
                .thenSucceed();
    }

    // ── Muggle witnesses ──────────────────────────────────────────────────────────────────────────

    private static void mugglesSeeMagic(GameTestHelper helper) {
        stage(helper, MUGGLES_Y, Blocks.STONE);
        ServerPlayer adult = wizard(helper, "wandb-trace-seen", MUGGLES_Y, 2, 1, -1);
        List<Villager> muggles = new ArrayList<>();
        muggles.add(muggle(helper, MUGGLES_Y, 2, 5));

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(MUGGLES_Y), max(MUGGLES_Y)))
                .thenExecute(() -> beginCast(helper, adult))
                .thenIdle(1)
                .thenExecute(() -> {
                    withMinistry(() -> releaseWand(adult));
                    List<PendingReport> reports = reportsAbout(helper, adult);
                    check(helper, reports.size() == 1 && reports.getFirst().channel() == ReportChannel.MUGGLE_REPORT
                                    && !reports.getFirst().identifiesCaster(),
                            () -> "one Muggle should produce one report without a name: " + reports);
                    Incident seen = incident(helper, reports.getFirst());
                    check(helper, seen != null && seen.muggleWitnesses() == 1,
                            () -> "the witness was not recorded: " + seen);

                    process(helper, TraceRules.MUGGLE_REPORT_DELAY_TICKS, adult);
                    check(helper, reportsAbout(helper, adult).isEmpty(),
                            () -> "a single sighting should be tidied up, not inquired into: " + reportsAbout(helper, adult));
                    check(helper, dossier(helper, adult).equals(Dossier.EMPTY),
                            () -> "a light seen by one Muggle put a name on file: " + dossier(helper, adult));

                    muggles.add(muggle(helper, MUGGLES_Y, 0, 5));
                    muggles.add(muggle(helper, MUGGLES_Y, 4, 5));
                    muggles.add(muggle(helper, MUGGLES_Y, 1, 6));
                    clearCooldowns(adult);
                    beginCast(helper, adult);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    withMinistry(() -> releaseWand(adult));
                    List<PendingReport> reports = reportsAbout(helper, adult);
                    check(helper, reports.size() == 1 && incident(helper, reports.getFirst()) != null
                                    && incident(helper, reports.getFirst()).muggleWitnesses() == 4,
                            () -> "a crowd of four should have been recorded: " + reports);

                    process(helper, TraceRules.MUGGLE_REPORT_DELAY_TICKS, adult);
                    List<PendingReport> inquiry = reportsAbout(helper, adult);
                    check(helper, inquiry.size() == 1 && inquiry.getFirst().channel() == ReportChannel.INQUIRY
                                    && inquiry.getFirst().identifiesCaster(),
                            () -> "a severe breach should open an inquiry that the crowd can name: " + inquiry);
                    check(helper, dossier(helper, adult).equals(Dossier.EMPTY),
                            () -> "the name was known before the inquiry finished: " + dossier(helper, adult));

                    process(helper, TraceRules.MUGGLE_REPORT_DELAY_TICKS + TraceRules.INQUIRY_TICKS, adult);
                    Dossier named = dossier(helper, adult);
                    check(helper, stage(named) == CaseStage.INVESTIGATING && named.secrecyNotes() == 1,
                            () -> "the inquiry should put the breach to the caster and open an investigation: " + named);
                })
                .thenExecute(() -> {
                    muggles.forEach(Villager::discard);
                    retire(helper, adult);
                })
                .thenSucceed();
    }

    // ── an Unforgivable nobody saw ────────────────────────────────────────────────────────────────

    private static void aWandExaminationFindsAnUnforgivable(GameTestHelper helper) {
        stage(helper, EXAMINATION_Y, Blocks.STONE);
        ServerPlayer adult = wizard(helper, "wandb-trace-examined", EXAMINATION_Y, 2, 1, -1);
        List<Villager> muggles = new ArrayList<>();

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(EXAMINATION_Y), max(EXAMINATION_Y)))
                .thenExecute(() -> {
                    withMinistry(() -> MinistryTrace.onSuccessfulCast(adult, "crucio", SpellCategory.DARK_ARTS));
                    MinistryCaseData data = data(helper);
                    check(helper, reportsAbout(helper, adult).isEmpty(),
                            () -> "an Unforgivable nobody saw was reported: " + reportsAbout(helper, adult));
                    check(helper, data.darkActs(adult.getUUID()).size() == 1,
                            () -> "the wand does not remember the curse: " + data.darkActs(adult.getUUID()));
                    process(helper, CaseRules.SUMMONS_TICKS, adult);
                    check(helper, dossier(helper, adult).equals(Dossier.EMPTY),
                            () -> "the Ministry acted on a curse nobody reported: " + dossier(helper, adult));

                    muggles.add(muggle(helper, EXAMINATION_Y, 0, 5));
                    muggles.add(muggle(helper, EXAMINATION_Y, 2, 5));
                    muggles.add(muggle(helper, EXAMINATION_Y, 4, 5));
                    muggles.add(muggle(helper, EXAMINATION_Y, 1, 6));
                    beginCast(helper, adult);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    withMinistry(() -> releaseWand(adult));
                    check(helper, spellData(adult).getCastCount(canonical()) == 1, () -> "the spell never cast");
                    long inquiryDone = TraceRules.MUGGLE_REPORT_DELAY_TICKS + TraceRules.INQUIRY_TICKS;
                    process(helper, TraceRules.MUGGLE_REPORT_DELAY_TICKS, adult);
                    process(helper, inquiryDone, adult);
                    check(helper, stage(dossier(helper, adult)) == CaseStage.INVESTIGATING,
                            () -> "the breach should be under investigation: " + dossier(helper, adult));
                    process(helper, inquiryDone + CaseRules.INVESTIGATION_TICKS, adult);
                    check(helper, stage(dossier(helper, adult)) == CaseStage.SUMMONED,
                            () -> "the investigation should end in a summons: " + dossier(helper, adult));

                    withMinistry(() -> MinistryTrace.answerSummons(adult));
                    Dossier heard = dossier(helper, adult);
                    check(helper, heard.lastVerdict().orElse(null) == Verdict.AZKABAN_REFERRAL,
                            () -> "examining the wand should have found the Cruciatus Curse: " + heard);
                    check(helper, MinistryRecords.get(adult).offenceCount(MagicalOffence.CRUCIO) == 1,
                            () -> "the curse was not filed: " + MinistryRecords.get(adult));
                    check(helper, data(helper).darkActs(adult.getUUID()).isEmpty(),
                            () -> "an examined wand still holds the same evidence for the next hearing");
                })
                .thenExecute(() -> {
                    muggles.forEach(Villager::discard);
                    retire(helper, adult);
                })
                .thenSucceed();
    }

    // ── Aurors: death, dimension change, logout, reconnect ────────────────────────────────────────

    private static void aurorsFollowTheLastReport(GameTestHelper helper) {
        stage(helper, AURORS_Y, Blocks.STONE);
        UUID id = UUID.randomUUID();
        String name = "wandb-trace-sought";
        ServerPlayer[] wanted = {wizard(helper, name, id, AURORS_Y, 2, 1, -1)};
        List<Villager> muggles = new ArrayList<>();
        muggles.add(muggle(helper, AURORS_Y, 1, 5));
        muggles.add(muggle(helper, AURORS_Y, 3, 5));
        BlockPos reported = helper.absolutePos(new BlockPos(2, AURORS_Y, 1));
        long[] clock = {0L};

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, min(AURORS_Y), max(AURORS_Y)))
                .thenExecute(() -> {
                    withMinistry(() -> MinistryTrace.onSuccessfulCast(wanted[0], "crucio", SpellCategory.DARK_ARTS));
                    muggles.forEach(Villager::discard);
                    clock[0] = TraceRules.MUGGLE_REPORT_DELAY_TICKS;
                    process(helper, clock[0], wanted[0]);
                    clock[0] += TraceRules.INQUIRY_TICKS;
                    process(helper, clock[0], wanted[0]);
                    Dossier sought = dossier(helper, wanted[0]);
                    check(helper, stage(sought) == CaseStage.AURORS_ASSIGNED
                                    && sought.openCase().get().lastKnownPos().equals(reported),
                            () -> "a curse two Muggles saw should send Aurors to where it happened: " + sought);

                    // Dead: the Aurors find nobody to arrest.
                    wanted[0].setHealth(0.0f);
                    clock[0] = searchTime(helper, wanted[0]);
                    process(helper, clock[0], wanted[0]);
                    wanted[0].setHealth(wanted[0].getMaxHealth());
                    check(helper, searches(helper, wanted[0]) == 1 && stage(dossier(helper, wanted[0]))
                                    == CaseStage.AURORS_ASSIGNED,
                            () -> "a dead wizard was arrested, or the case was lost: " + dossier(helper, wanted[0]));

                    // Gone to the Nether: not where the report put them.
                    ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
                    check(helper, nether != null, () -> "the game-test server has no Nether");
                    wanted[0].teleport(new TeleportTransition(nether, new Vec3(reported.getX() + 0.5, 100,
                            reported.getZ() + 0.5), Vec3.ZERO, 0.0f, 0.0f, Set.of(), entity -> {}));
                    check(helper, wanted[0].level() == nether, () -> "the wizard never reached the Nether");
                    clock[0] = searchTime(helper, wanted[0]);
                    process(helper, clock[0], wanted[0]);
                    check(helper, searches(helper, wanted[0]) == 2,
                            () -> "Aurors found a wizard in another dimension: " + dossier(helper, wanted[0]));
                    wanted[0].teleport(new TeleportTransition(helper.getLevel(), Vec3.atBottomCenterOf(reported),
                            Vec3.ZERO, 0.0f, 0.0f, Set.of(), entity -> {}));
                    wanted[0].setNoGravity(true);
                })
                .thenExecute(() -> {
                    // Logged out: the case waits in world data.
                    retire(helper, wanted[0]);
                    clock[0] = searchTimeFor(helper, id);
                    process(helper, clock[0], id);
                    Dossier waiting = data(helper).dossier(id);
                    check(helper, stage(waiting) == CaseStage.AURORS_ASSIGNED
                                    && waiting.openCase().get().searches() == 3,
                            () -> "a logout lost or settled the case: " + waiting);
                })
                .thenExecute(() -> {
                    // Back online, back where the report put them.
                    wanted[0] = WizardTestSupport.placeMockPlayer(helper, name, id, GameType.CREATIVE);
                    wanted[0].snapTo(reported.getX() + 0.5, reported.getY(), reported.getZ() + 0.5, 0.0f, 0.0f);
                    wanted[0].setNoGravity(true);
                    clock[0] = searchTime(helper, wanted[0]);
                    process(helper, clock[0], wanted[0]);
                    Dossier tried = dossier(helper, wanted[0]);
                    check(helper, tried.openCase().isEmpty()
                                    && tried.lastVerdict().orElse(null) == Verdict.AZKABAN_REFERRAL,
                            () -> "Aurors should arrest a wizard standing where they were reported: " + tried);
                    check(helper, MinistryRecords.get(wanted[0]).offenceCount(MagicalOffence.CRUCIO) == 1,
                            () -> "the conviction was not filed on the reconnected record: " + MinistryRecords.get(wanted[0]));
                    Boolean held = withMinistry(() -> MinistryTrace.wandConfiscated(wanted[0]));
                    check(helper, held, () -> "a wizard referred to Azkaban kept the wand");
                })
                .thenExecute(() -> retire(helper, wanted[0]))
                .thenSucceed();
    }

    // ── shared ────────────────────────────────────────────────────────────────────────────────────

    private static BlockPos min(int y) {
        return new BlockPos(-1, y - 1, -1);
    }

    private static BlockPos max(int y) {
        return new BlockPos(5, y + 2, 7);
    }

    /** A floor of {@code floor} with air above, chunks forced. Seven by nine, so four Muggles fit in front. */
    private static void stage(GameTestHelper helper, int y, Block floor) {
        WizardTestSupport.forceChunks(helper, min(y), max(y));
        for (int x = -1; x <= 5; x++) {
            for (int z = -1; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, y - 1, z), floor);
                for (int dy = 0; dy <= 2; dy++) {
                    helper.setBlock(new BlockPos(x, y + dy, z), Blocks.AIR);
                }
            }
        }
    }

    /**
     * A wandkind wizard holding a bonded wand with the test spell selected.
     *
     * @param age years old, or -1 for no birth record (of age)
     */
    private static ServerPlayer wizard(GameTestHelper helper, String name, int y, int x, int z, int age) {
        return wizard(helper, name, UUID.randomUUID(), y, x, z, age);
    }

    private static ServerPlayer wizard(GameTestHelper helper, String name, UUID id, int y, int x, int z, int age) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, id, GameType.CREATIVE);
        BlockPos at = helper.absolutePos(new BlockPos(x, y, z));
        player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
        player.setNoGravity(true);
        WizardTestSupport.makeWandkind(player);
        WizardTestSupport.learnAndSelect(helper, player, SPELL, SPELL_PREREQUISITE);
        WizardTestSupport.giveBondedWand(player);
        WizardTestSupport.settleAllegiance(player);
        if (age >= 0) {
            MinistryTrace.setAge(player, age);
        }
        return player;
    }

    private static Villager muggle(GameTestHelper helper, int y, int x, int z) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(x, y, z));
        villager.setNoAi(true);
        villager.setInvulnerable(true);
        return villager;
    }

    /** Runs {@code action} with the Ministry module on, and puts it back however the action ends. */
    private static void withMinistry(Runnable action) {
        withMinistry(() -> {
            action.run();
            return null;
        });
    }

    @SuppressWarnings("deprecation") // The cache-only setter is the point: nothing is persisted or broadcast.
    private static <T> T withMinistry(Supplier<T> action) {
        boolean wasOn = ModuleManager.isEnabled(Module.MINISTRY);
        ModuleManager.setState(Module.MINISTRY, ModuleManager.State.ENABLED);
        try {
            return action.get();
        } finally {
            if (!wasOn) {
                ModuleManager.setState(Module.MINISTRY, ModuleManager.State.DISABLED);
            }
        }
    }

    private static void process(GameTestHelper helper, long ticksAhead, ServerPlayer subject) {
        process(helper, ticksAhead, subject.getUUID());
    }

    private static void process(GameTestHelper helper, long ticksAhead, UUID subject) {
        MinecraftServer server = helper.getLevel().getServer();
        withMinistry(() -> MinistryTrace.process(server, MinistryTrace.now(server) + ticksAhead, subject::equals));
    }

    private static MinistryCaseData data(GameTestHelper helper) {
        return MinistryCaseData.get(helper.getLevel().getServer());
    }

    private static Dossier dossier(GameTestHelper helper, ServerPlayer wizard) {
        return data(helper).dossier(wizard.getUUID());
    }

    private static CaseStage stage(Dossier dossier) {
        return dossier.openCase().map(open -> open.stage()).orElse(null);
    }

    private static int searches(GameTestHelper helper, ServerPlayer wizard) {
        return dossier(helper, wizard).openCase().map(open -> open.searches()).orElse(-1);
    }

    /** Ticks ahead of now at which the Aurors next look for this wizard. */
    private static long searchTime(GameTestHelper helper, ServerPlayer wizard) {
        return searchTimeFor(helper, wizard.getUUID());
    }

    private static long searchTimeFor(GameTestHelper helper, UUID wizard) {
        long nextSearch = data(helper).dossier(wizard).openCase().map(open -> open.nextSearch()).orElse(0L);
        return nextSearch - MinistryTrace.now(helper.getLevel().getServer());
    }

    private static List<PendingReport> reportsAbout(GameTestHelper helper, ServerPlayer wizard) {
        MinistryCaseData data = data(helper);
        return data.pending().stream().filter(report -> {
            Incident incident = data.incident(report.incidentId());
            return incident != null && incident.caster().equals(wizard.getUUID());
        }).toList();
    }

    private static Incident incident(GameTestHelper helper, PendingReport report) {
        return data(helper).incident(report.incidentId());
    }

    private static void clearCooldowns(ServerPlayer caster) {
        spellData(caster).setCooldown(canonical(), 0L);
        spellData(caster).setGlobalCooldownEndTick(0L);
    }

    private static String canonical() {
        Spell spell = Spells.byId(SPELL);
        return spell == null ? SPELL : spell.getId();
    }
}
