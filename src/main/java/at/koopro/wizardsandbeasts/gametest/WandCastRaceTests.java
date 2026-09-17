package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.network.spell.BeamChannelS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellCastC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataDeltaS2CPayload;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.cast.WandCastSessions;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WandCastLifecycleTests.beginCast;
import static at.koopro.wizardsandbeasts.gametest.WandCastLifecycleTests.clearCooldowns;
import static at.koopro.wizardsandbeasts.gametest.WandCastLifecycleTests.describeSession;
import static at.koopro.wizardsandbeasts.gametest.WandCastLifecycleTests.readyCaster;
import static at.koopro.wizardsandbeasts.gametest.WandCastLifecycleTests.releaseWand;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.drainClientboundPayloads;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.gameTime;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.spellData;

/**
 * The wand release under hostile timing: presses and releases packed into one server tick, duplicates and
 * stragglers from an earlier hold, a release pair split across ticks, a use packet re-sent into a running
 * hold, the active spell changed under a hold, a release the server itself drives, and two players whose
 * releases must never reach each other.
 *
 * <p><b>What "latency" means here.</b> The server reads every packet of one connection in wire order — vanilla
 * packets and main-thread mod payloads share one FIFO queue ({@code PacketProcessor}) — so latency cannot
 * reorder a client's packets, only spread them over more server ticks. A client's vanilla RELEASE_USE_ITEM and
 * its {@code SpellCastC2SPayload} are adjacent on the wire, but the server may drain one before a tick and the
 * other after it. Those are the interleavings driven below; the reorderings that the ordered connection rules
 * out are driven too, as forged or duplicated packets, because a hostile client can send them.
 *
 * <p>Cooldowns are cleared between holds where a scenario needs two casts of one spell. They are a separate
 * gate, and these scenarios are about whether the session layer lets a genuine hold through.
 */
public final class WandCastRaceTests {

    /** A second castable self spell with no requirement: a switch target, and an effect that can be seen. */
    private static final String SECOND_SPELL_ID = "riddikulus";
    private static final String LEVIOSA_ID = "wingardium_leviosa";
    private static final String LEVIOSA_PREREQUISITE_ID = "lumos";
    private static final String AVADA_ID = "avada_kedavra";

    /** Caster's feet looking along +Z, and the target two blocks ahead — the Leviosa lane. */
    private static final Vec3 CASTER = new Vec3(1.5, 1.0, 0.5);
    private static final Vec3 TARGET = new Vec3(1.5, 1.0, 2.5);
    /** A second player standing beside the caster, off the caster's eye line. */
    private static final Vec3 OBSERVER = new Vec3(0.5, 1.0, 0.5);

    /** Comfortably past {@code WandBeamSpellHandlers.AVADA_MIN_CHARGE_TICKS} (24). */
    private static final int AVADA_HOLD_TICKS = 40;
    /** As in {@code LeviosaThrowTests}: the beam's reach ramps per tick, and the target is two blocks out. */
    private static final int CHANNEL_TICKS = 10;
    /** Server ticks that let the first chunk batch go out to both players. */
    private static final int TRACKING_SETTLE_TICKS = 4;
    /**
     * The two-caster scenario's own height. Game-test columns are six blocks apart and neighbouring scenarios'
     * area effects reach that far; the first runs saw effects on these players that neither of them cast.
     */
    private static final int DUO_Y = 37;

    private WandCastRaceTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("wand_race_rapid_presses_each_cast", "wand race: use-release-use-release in one tick",
                WandCastRaceTests::rapidPressesEachCast);
        tests.add("wand_race_duplicate_between_holds", "wand race: cast A, release A, cast B, duplicate A, release B",
                WandCastRaceTests::duplicateBetweenHoldsResolvesOnlyTheNewHold);
        tests.add("wand_race_release_pair_split_across_ticks", "wand latency: release pair spread over ticks",
                WandCastRaceTests::releasePairSplitAcrossTicks);
        tests.add("wand_race_lost_release_does_not_leak", "wand latency: an unanswered release and a new hold",
                WandCastRaceTests::lostReleaseDoesNotLeakIntoNextHold);
        tests.add("wand_race_resent_use_keeps_the_hold", "wand race: a use packet re-sent into a running hold",
                WandCastRaceTests::resentUseKeepsTheRunningHold);
        tests.add("wand_race_spell_switch_then_release", "wand race: switch spell and release in one tick",
                WandCastRaceTests::spellSwitchThenReleaseCastsNothing);
        tests.add("wand_race_spell_switch_ends_hold", "wand race: switching spell mid-hold ends the hold",
                WandCastRaceTests::spellSwitchEndsTheHold);
        tests.add("wand_race_server_driven_release_once", "wand race: Avada's server-driven release, then the client's",
                WandCastRaceTests::serverDrivenReleaseCastsOnce);
        tests.add("wand_duo_releases_stay_with_their_caster", "two casters: each release reaches only its own hold",
                WandCastRaceTests::releasesStayWithTheirCaster);
        tests.add("wand_duo_observer_sees_the_beam", "two casters: an observer is told of a beam, and of its end",
                WandCastRaceTests::observerSeesTheBeamStartAndEnd);
    }

    // ── A: two full presses read in one drain ──────────────────────────────────────────────────

    private static void rapidPressesEachCast(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper, "wandb-race-rapid");
        String spellId = WandCastLifecycleTests.spellId();
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> {
                    beginCast(helper, caster);
                    WandCastSessions.Session first = WandCastSessions.peek(caster);
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "the first press did not cast: count " + data.getCastCount(spellId));

                    clearCooldowns(caster);
                    beginCast(helper, caster);
                    WandCastSessions.Session second = WandCastSessions.peek(caster);
                    check(helper, second != null && second != first && !second.releaseConsumed(),
                            () -> "the second press did not open a hold of its own: " + describeSession(caster));
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 2,
                            () -> "the second press in the same tick was swallowed: count " + data.getCastCount(spellId));

                    SpellCastC2SPayload.completeWandCastRelease(caster);
                    check(helper, data.getCastCount(spellId) == 2,
                            () -> "a third release for two presses cast again: count " + data.getCastCount(spellId));
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── B: a straggler from hold A arrives inside hold B ───────────────────────────────────────

    /**
     * The sequence the audit brief names: cast A, release A, cast B, a duplicate of A's release, release B. The
     * duplicate lands on B's open hold before vanilla has released it, so it must neither cast nor spend B.
     */
    private static void duplicateBetweenHoldsResolvesOnlyTheNewHold(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper, "wandb-race-straggler");
        String spellId = WandCastLifecycleTests.spellId();
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(1)
                .thenExecute(() -> {
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 1, () -> "hold A did not cast");
                    clearCooldowns(caster);
                    beginCast(helper, caster);

                    SpellCastC2SPayload.completeWandCastRelease(caster);

                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "A's duplicate cast inside hold B: count " + data.getCastCount(spellId));
                    WandCastSessions.Session b = WandCastSessions.peek(caster);
                    check(helper, b != null && !b.releaseConsumed() && caster.isUsingItem(),
                            () -> "A's duplicate spent or ended hold B: " + describeSession(caster));
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 2,
                            () -> "hold B was swallowed by A's duplicate: count " + data.getCastCount(spellId));
                    SpellCastC2SPayload.completeWandCastRelease(caster);
                    check(helper, data.getCastCount(spellId) == 2,
                            () -> "a duplicate of B's release cast again: count " + data.getCastCount(spellId));
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── C: vanilla's release in one drain, the mod's packet ticks later ─────────────────────────

    /**
     * A long hold on a ticking player, the vanilla release, several full player ticks, and only then the mod's
     * release packet — then a late duplicate. The hold's reconciliation runs on every one of those ticks, and a
     * release vanilla has already confirmed must survive it: that pending state is what a high-latency client's
     * packet lands on.
     */
    private static void releasePairSplitAcrossTicks(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper, "wandb-race-latency");
        String spellId = WandCastLifecycleTests.spellId();
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenExecuteFor(20, caster::doTick)
                .thenExecute(() -> {
                    check(helper, caster.isUsingItem(), () -> "the hold ended on its own during 20 ticks");
                    caster.releaseUsingItem();
                })
                .thenExecuteFor(5, caster::doTick)
                .thenExecute(() -> {
                    WandCastSessions.Session pending = WandCastSessions.peek(caster);
                    check(helper, pending != null && pending.vanillaReleaseObserved() && !pending.releaseConsumed(),
                            () -> "the confirmed release did not survive five ticks waiting for its packet: "
                                    + describeSession(caster));
                    check(helper, data.getCastCount(spellId) == 0, () -> "the vanilla release alone cast the spell");

                    SpellCastC2SPayload.completeWandCastRelease(caster);
                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "the late release packet did not cast: count " + data.getCastCount(spellId)
                                    + " (" + describeSession(caster) + ")");
                })
                .thenExecuteFor(5, caster::doTick)
                .thenExecute(() -> {
                    SpellCastC2SPayload.completeWandCastRelease(caster);
                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "a duplicate arriving ten ticks late cast again: count " + data.getCastCount(spellId));
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── D: a release that never came, and the hold after it ─────────────────────────────────────

    /**
     * Vanilla releases hold A, A's packet never arrives, the player presses again. When A's packet finally shows up
     * it lands on hold B, which vanilla has not released: nothing may cast, and B must still be worth one cast.
     */
    private static void lostReleaseDoesNotLeakIntoNextHold(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper, "wandb-race-lost");
        String spellId = WandCastLifecycleTests.spellId();
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(1)
                .thenExecute(() -> {
                    caster.releaseUsingItem();
                    WandCastSessions.Session a = WandCastSessions.peek(caster);
                    beginCast(helper, caster);
                    WandCastSessions.Session b = WandCastSessions.peek(caster);
                    check(helper, b != null && b != a && !b.vanillaReleaseObserved(),
                            () -> "the new press did not replace the unanswered hold: " + describeSession(caster));

                    SpellCastC2SPayload.completeWandCastRelease(caster);
                    check(helper, data.getCastCount(spellId) == 0,
                            () -> "hold A's late packet cast inside hold B: count " + data.getCastCount(spellId));
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "hold B was swallowed: count " + data.getCastCount(spellId)
                                    + " (" + describeSession(caster) + ")");
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── E: a use packet re-sent into a hold the server is still running ────────────────────────

    /**
     * A use packet the server reads while it already has the wand in use starts nothing — vanilla's
     * {@code startUsingItem} is a no-op then — so it must not replace the running hold's session either.
     * Replacing it threw away what the session knew about the hold: here, that it is sustaining a spell clash,
     * whose release must cast nothing.
     */
    private static void resentUseKeepsTheRunningHold(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper, "wandb-race-resent");
        String spellId = WandCastLifecycleTests.spellId();
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(1)
                .thenExecute(() -> {
                    WandCastSessions.Session running = WandCastSessions.peek(caster);
                    beginCast(helper, caster);
                    check(helper, WandCastSessions.peek(caster) == running,
                            () -> "a re-sent use packet replaced the running hold's session: " + describeSession(caster));
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "the hold did not cast after a re-sent use packet: count " + data.getCastCount(spellId));
                    clearCooldowns(caster);
                })
                .thenExecute(() -> {
                    beginCast(helper, caster);
                    WandCastSessions.markClashHold(caster);
                    beginCast(helper, caster);
                    check(helper, WandCastSessions.isClashHold(caster),
                            () -> "a re-sent use packet turned a clash hold back into a castable one: "
                                    + describeSession(caster));
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "letting go of a clash hold cast after a re-sent use packet: count "
                                    + data.getCastCount(spellId));
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── F: the active spell changes under a hold ────────────────────────────────────────────────

    /**
     * Hold one spell, switch to another and let go in the same drain — the select packet and the release pair
     * back to back. The hold was charged for the first spell; neither may come out of it.
     */
    private static void spellSwitchThenReleaseCastsNothing(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper, "wandb-race-switch-release");
        String first = WandCastLifecycleTests.spellId();
        String second = loadSecondSpell(helper, caster);
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(2)
                .thenExecute(() -> {
                    data.setActiveSlot(1);
                    releaseWand(caster);
                    check(helper, data.getCastCount(second) == 0,
                            () -> "a hold charged for " + first + " cast " + second + " after a switch");
                    check(helper, data.getCastCount(first) == 0,
                            () -> "a release after switching away still cast " + first);
                    check(helper, !caster.hasEffect(MobEffects.RESISTANCE) && !caster.hasEffect(MobEffects.SLOW_FALLING),
                            () -> "a release after a spell switch applied an effect");
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    /**
     * Hold one spell and switch. The server ends that hold on its own tick, so the client stops it too and
     * presses afresh for the new spell — which must then cast normally.
     */
    private static void spellSwitchEndsTheHold(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper, "wandb-race-switch-hold");
        String second = loadSecondSpell(helper, caster);
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(1)
                .thenExecute(() -> {
                    data.setActiveSlot(1);
                    caster.doTick();
                    check(helper, !caster.isUsingItem(),
                            () -> "the hold survived switching the active spell: " + describeSession(caster));
                    check(helper, WandCastSessions.peek(caster) == null,
                            () -> "switching the active spell left the hold's session open: " + describeSession(caster));
                })
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(1)
                .thenExecute(() -> {
                    releaseWand(caster);
                    check(helper, data.getCastCount(second) == 1,
                            () -> "the fresh hold for " + second + " did not cast: count " + data.getCastCount(second)
                                    + " (" + describeSession(caster) + ")");
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── G: the server releases the hold itself ──────────────────────────────────────────────────

    /**
     * A real Killing Curse: hold on a target until the channel kills it. The kill releases the hold server-side and
     * spends the same token a client release would, so the client's own release for that hold is worth nothing —
     * and the next genuine hold still casts.
     */
    private static void serverDrivenReleaseCastsOnce(GameTestHelper helper) {
        // Cache-only on purpose (see leaseModule): ModuleStateService reloads datapacks under every running scenario.
        Runnable releaseDarkArts = WizardTestSupport.leaseModule(Module.DARK_ARTS);
        LeviosaThrowTests.clearTheLane(helper);
        ServerPlayer caster = laneCaster(helper, "wandb-race-avada", AVADA_ID, null, CASTER, TARGET);
        Villager target = helper.spawn(EntityType.VILLAGER, TARGET);
        target.setNoAi(true);
        String avada = canonical(AVADA_ID);
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenWaitUntil(() -> LeviosaThrowTests.checkLaneTicks(helper))
                .thenExecute(() -> beginCast(helper, caster))
                .thenExecuteFor(AVADA_HOLD_TICKS, caster::doTick)
                .thenExecute(() -> {
                    check(helper, !target.isAlive(),
                            () -> "the curse never killed its target in " + AVADA_HOLD_TICKS + " ticks; "
                                    + describeSession(caster) + " channel=" + at.koopro.wizardsandbeasts.spell.beam
                                    .WandBeamChannelLogic.activeChannelSpellId(caster));
                    check(helper, data.getCastCount(avada) == 1,
                            () -> "the kill should resolve exactly one cast: count " + data.getCastCount(avada));
                    check(helper, !caster.isUsingItem(), () -> "the server-driven release left the wand in use");
                    WandCastSessions.Session spent = WandCastSessions.peek(caster);
                    check(helper, spent != null && spent.releaseConsumed(),
                            () -> "the server-driven release did not spend the hold's token: " + describeSession(caster));

                    releaseWand(caster);
                    check(helper, data.getCastCount(avada) == 1,
                            () -> "the client's release after the kill cast again: count " + data.getCastCount(avada));
                })
                .thenExecute(() -> {
                    data.setCooldown(avada, 0L);
                    data.setGlobalCooldownEndTick(0L);
                    beginCast(helper, caster);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    releaseWand(caster);
                    check(helper, data.getCastCount(avada) == 2,
                            () -> "the first hold after a server-driven release was swallowed: count "
                                    + data.getCastCount(avada) + " (" + describeSession(caster) + ")");
                })
                .thenExecute(() -> {
                    releaseDarkArts.run();
                    retire(helper, caster);
                })
                .thenSucceed();
    }

    // ── H: two casters, one server ──────────────────────────────────────────────────────────────

    /**
     * A holds, B lets go of a button it never pressed; then A lets go. Reversed: B holds, A's release arrives
     * again. A release can only ever reach the hold of the connection it came in on — and what each client is
     * told about cooldowns is its own.
     */
    private static void releasesStayWithTheirCaster(GameTestHelper helper) {
        ServerPlayer a = readyCaster(helper, "wandb-duo-a");
        ServerPlayer b = readyCaster(helper, "wandb-duo-b");
        liftTo(helper, a, DUO_Y, 0);
        liftTo(helper, b, DUO_Y, 2);
        BlockPos duoMin = new BlockPos(0, DUO_Y, 0);
        BlockPos duoMax = new BlockPos(2, DUO_Y, 0);
        WizardTestSupport.forceChunks(helper, duoMin, duoMax);
        // Different spells, so every delta names its owner: A's are arresto_momentum, B's riddikulus.
        String spellA = WandCastLifecycleTests.spellId();
        String spellB = loadSecondSpell(helper, b);
        spellData(b).setActiveSlot(1);
        PlayerSpellData dataA = spellData(a);
        PlayerSpellData dataB = spellData(b);

        helper.startSequence()
                .thenWaitUntil(() -> WizardTestSupport.checkChunksTick(helper, duoMin, duoMax))
                .thenExecute(() -> {
                    drainClientboundPayloads(a);
                    drainClientboundPayloads(b);
                    beginCast(helper, a);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    int correctionsA = dataA.getSyncCorrections();
                    releaseWand(b);
                    check(helper, dataA.getCastCount(spellA) == 0 && dataB.getCastCount(spellB) == 0,
                            () -> "B's release without a hold cast for someone: A=" + dataA.getCastCount(spellA)
                                    + " B=" + dataB.getCastCount(spellB));
                    WandCastSessions.Session holdA = WandCastSessions.peek(a);
                    check(helper, holdA != null && !holdA.releaseConsumed() && a.isUsingItem(),
                            () -> "B's release touched A's hold: " + describeSession(a));

                    releaseWand(a);
                    long now = gameTime(helper);
                    check(helper, dataA.getCastCount(spellA) == 1 && dataB.getCastCount(spellB) == 0
                                    && dataB.getCastCount(spellA) == 0,
                            () -> "A's release did not resolve to A alone: A=" + dataA.getCastCount(spellA)
                                    + " B=" + dataB.getCastCount(spellB) + "/" + dataB.getCastCount(spellA));
                    check(helper, !dataB.isOnCooldown(spellA, now) && dataB.getGlobalCooldownEndTick() <= now,
                            () -> "A's cast put B on cooldown");
                    // Only A's own effect is asserted: Arresto Momentum is also a time-stop bubble that slows
                    // everything within four blocks but its caster, so what reaches B is decided by distance.
                    check(helper, a.hasEffect(MobEffects.SLOW_FALLING),
                            () -> "A's own cast left no effect on A: " + a.getActiveEffects());
                    check(helper, dataA.getSyncCorrections() == correctionsA,
                            () -> "A's ordinary release was counted as a sync correction");

                    List<CustomPacketPayload> toA = drainClientboundPayloads(a);
                    List<CustomPacketPayload> toB = drainClientboundPayloads(b);
                    check(helper, hasDelta(toA, spellA), () -> "A was never told of its own cast; A got " + describe(toA));
                    check(helper, !hasDelta(toB, spellA), () -> "B was sent A's spell data; B got " + describe(toB));
                })
                .thenExecute(() -> beginCast(helper, b))
                .thenIdle(1)
                .thenExecute(() -> {
                    releaseWand(a);
                    WandCastSessions.Session holdB = WandCastSessions.peek(b);
                    check(helper, holdB != null && !holdB.releaseConsumed() && b.isUsingItem(),
                            () -> "A's duplicate release touched B's hold: " + describeSession(b));
                    check(helper, dataA.getCastCount(spellA) == 1 && dataB.getCastCount(spellB) == 0,
                            () -> "A's duplicate cast for someone: A=" + dataA.getCastCount(spellA)
                                    + " B=" + dataB.getCastCount(spellB));

                    releaseWand(b);
                    check(helper, dataA.getCastCount(spellA) == 1 && dataB.getCastCount(spellB) == 1
                                    && dataA.getCastCount(spellB) == 0,
                            () -> "B's release did not resolve to B alone: A=" + dataA.getCastCount(spellA)
                                    + "/" + dataA.getCastCount(spellB) + " B=" + dataB.getCastCount(spellB));
                    List<CustomPacketPayload> toA = drainClientboundPayloads(a);
                    List<CustomPacketPayload> toB = drainClientboundPayloads(b);
                    check(helper, hasDelta(toB, spellB), () -> "B was never told of its own cast; B got " + describe(toB));
                    check(helper, !hasDelta(toA, spellB), () -> "A was sent B's spell data; A got " + describe(toA));
                })
                .thenExecute(() -> {
                    retire(helper, a);
                    retire(helper, b);
                })
                .thenSucceed();
    }

    /**
     * A channels Leviosa while B watches, then B channels while A watches. Each observer must be told the beam
     * started and, after the caster lets go, that it ended — in that order — while nothing about the cast itself
     * (cooldowns, counts, session) ever becomes the observer's.
     */
    private static void observerSeesTheBeamStartAndEnd(GameTestHelper helper) {
        LeviosaThrowTests.clearTheLane(helper);
        ServerPlayer a = laneCaster(helper, "wandb-watch-a", LEVIOSA_ID, LEVIOSA_PREREQUISITE_ID, CASTER, TARGET);
        ServerPlayer b = laneCaster(helper, "wandb-watch-b", LEVIOSA_ID, LEVIOSA_PREREQUISITE_ID, OBSERVER, TARGET);
        ArmorStand target = helper.spawn(EntityType.ARMOR_STAND, TARGET);
        String leviosa = canonical(LEVIOSA_ID);

        helper.startSequence()
                .thenWaitUntil(() -> LeviosaThrowTests.checkLaneTicks(helper))
                .thenIdle(TRACKING_SETTLE_TICKS)
                .thenExecute(() -> {
                    // A real client's movement packets run ServerChunkCache.move, which is where vanilla pairs a
                    // player with the entities in chunks it has been sent. A mock client sends none.
                    helper.getLevel().getChunkSource().move(a);
                    helper.getLevel().getChunkSource().move(b);
                })
                .thenExecute(() -> watchOneBeam(helper, a, b, target, leviosa, true))
                .thenExecuteFor(CHANNEL_TICKS, a::doTick)
                .thenExecute(() -> watchOneBeam(helper, a, b, target, leviosa, false))
                .thenExecute(() -> watchOneBeam(helper, b, a, target, leviosa, true))
                .thenExecuteFor(CHANNEL_TICKS, b::doTick)
                .thenExecute(() -> watchOneBeam(helper, b, a, target, leviosa, false))
                .thenExecute(() -> {
                    target.discard();
                    retire(helper, a);
                    retire(helper, b);
                })
                .thenSucceed();
    }

    /** First call begins the caster's hold; second asserts what the observer saw across it and lets go. */
    private static void watchOneBeam(GameTestHelper helper, ServerPlayer caster, ServerPlayer observer,
                                     ArmorStand target, String spellId, boolean begin) {
        PlayerSpellData watching = spellData(observer);
        if (begin) {
            drainClientboundPayloads(caster);
            drainClientboundPayloads(observer);
            beginCast(helper, caster);
            return;
        }
        check(helper, target.isNoGravity(),
                () -> caster.getScoreboardName() + "'s channel never lifted the target, so there was no beam to watch: "
                        + describeSession(caster) + " channel="
                        + at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic.activeChannelSpellId(caster)
                        + " eye=" + caster.getEyePosition() + " look=" + caster.getLookAngle()
                        + " target=" + target.position() + " targetRemoved=" + target.isRemoved()
                        + " activeSpell=" + spellData(caster).getActiveSpellId()
                        + " slowed=" + caster.hasEffect(MobEffects.SLOWNESS));
        List<CustomPacketPayload> during = drainClientboundPayloads(observer);
        check(helper, beamEvent(during, caster, true) >= 0,
                () -> observer.getScoreboardName() + " was never told " + caster.getScoreboardName()
                        + "'s beam started; got " + names(during));

        int countBefore = watching.getCastCount(spellId);
        // The observer may hold a released session of its own from an earlier round — that state is kept on
        // purpose (see CastReleaseGate: RELEASED). Watching must leave it exactly as it was.
        WandCastSessions.Session observersOwn = WandCastSessions.peek(observer);
        releaseWand(caster);
        List<CustomPacketPayload> after = drainClientboundPayloads(observer);
        int end = beamEvent(after, caster, false);
        check(helper, end >= 0,
                () -> observer.getScoreboardName() + " was never told " + caster.getScoreboardName()
                        + "'s beam ended; got " + names(after));
        int restart = beamEvent(after.subList(end, after.size()), caster, true);
        check(helper, restart < 0,
                () -> observer.getScoreboardName() + " was told the beam started again after it ended: " + names(after));
        check(helper, !hasDelta(after, spellId),
                () -> observer.getScoreboardName() + " was sent the caster's spell data: " + names(after));
        check(helper, spellData(caster).getCastCount(spellId) >= 1,
                () -> caster.getScoreboardName() + "'s release did not cast " + spellId);
        check(helper, watching.getCastCount(spellId) == countBefore && WandCastSessions.peek(observer) == observersOwn,
                () -> "watching a cast changed the observer's own cast state: " + describeSession(observer));
        check(helper, !target.isNoGravity(), () -> "letting go left the target floating");
        spellData(caster).setCooldown(spellId, 0L);
        spellData(caster).setGlobalCooldownEndTick(0L);
    }

    // ── shared ──────────────────────────────────────────────────────────────────────────────────

    /** Stands a player at this scenario's own height, {@code dx} blocks along x from the test origin. */
    private static void liftTo(GameTestHelper helper, ServerPlayer player, int y, int dx) {
        net.minecraft.core.BlockPos at = helper.absolutePos(new net.minecraft.core.BlockPos(dx, y, 0));
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.setNoGravity(true);
    }

    private static String loadSecondSpell(GameTestHelper helper, ServerPlayer caster) {
        Spell spell = Spells.byId(SECOND_SPELL_ID);
        if (spell == null) {
            helper.fail("test spell '" + SECOND_SPELL_ID + "' is not registered");
            return SECOND_SPELL_ID;
        }
        PlayerSpellData data = spellData(caster);
        data.learnSpell(spell.getId());
        data.setLoadoutSpell(1, spell.getId());
        return spell.getId();
    }

    private static String canonical(String spellId) {
        Spell spell = Spells.byId(spellId);
        return spell == null ? spellId : spell.getId();
    }

    /** A ready caster standing at {@code feet}, looking at {@code lookAt}'s mid-height, gravity off. */
    private static ServerPlayer laneCaster(GameTestHelper helper, String name, String spellId, String prerequisiteId,
                                           Vec3 feet, Vec3 lookAt) {
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, name);
        WizardTestSupport.makeWandkind(caster);
        WizardTestSupport.giveBondedWand(caster);
        WizardTestSupport.settleAllegiance(caster);
        WizardTestSupport.learnAndSelect(helper, caster, spellId, prerequisiteId);
        Vec3 from = helper.absoluteVec(feet);
        Vec3 to = helper.absoluteVec(lookAt);
        float yaw = (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0f;
        caster.snapTo(from.x, from.y, from.z, yaw, 0.0f);
        caster.setYHeadRot(yaw);
        caster.setNoGravity(true);
        return caster;
    }

    private static boolean hasDelta(List<CustomPacketPayload> payloads, String spellId) {
        return payloads.stream().anyMatch(p -> p instanceof SpellDataDeltaS2CPayload delta
                && delta.spellId().equals(spellId));
    }

    /** Index of the first beam start ({@code active}) or end for this caster, or -1. */
    private static int beamEvent(List<CustomPacketPayload> payloads, ServerPlayer caster, boolean active) {
        for (int i = 0; i < payloads.size(); i++) {
            if (payloads.get(i) instanceof BeamChannelS2CPayload beam
                    && beam.casterId() == caster.getId() && beam.active() == active) {
                return i;
            }
        }
        return -1;
    }

    /** Like {@link #names}, with each spell-data delta spelled out: spell, cast count, cooldown expiry. */
    private static String describe(List<CustomPacketPayload> payloads) {
        return payloads.stream().map(p -> p instanceof SpellDataDeltaS2CPayload d
                ? "delta(" + d.spellId() + " count=" + d.newCastCount() + " cd=" + d.cooldownExpiryTick() + ")"
                : p.type().id().getPath()).toList().toString();
    }

    private static String names(List<CustomPacketPayload> payloads) {
        return payloads.stream().map(p -> p.type().id().getPath()).toList().toString();
    }
}
