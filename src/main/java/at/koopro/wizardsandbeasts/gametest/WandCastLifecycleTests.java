package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.network.spell.SpellCastC2SPayload;
import at.koopro.wizardsandbeasts.spell.cast.WandCastSessions;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.gameTime;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.spellData;

/**
 * The wand cast lifecycle, run inside a live dedicated server.
 *
 * <p>What the release architecture <em>decides</em> is already covered by {@code CastReleaseGateTest}, which
 * calls the gate directly. What no unit test can reach is whether a player pressing and letting go of a wand
 * ever arrives at that gate: {@code WandItem.use} opening a session through vanilla's
 * {@code ServerPlayerGameMode.useItem}, the item-use state surviving real server ticks, vanilla's own release
 * running before ours, and the session outliving all of it. These scenarios exercise that path and assert on
 * observable server state — cast counts, cooldowns, a real mob effect — never on the gate's return value.
 *
 * <p><b>Deliberately not mocked.</b> The caster is a real {@link ServerPlayer} in the server's player list,
 * the wand is a real bonded {@code WandItem} in its hand, the cast goes through the same
 * {@code gameMode.useItem} call the {@code ServerboundUseItemPacket} handler makes, and the release goes
 * through the same {@code SpellCastC2SPayload.completeWandCastRelease} the payload handler calls. The only
 * thing skipped is the netty round-trip of a packet that carries no fields.
 *
 * <p>The spell is {@code arresto_momentum}: {@code castType: self}, exactly one authored effect
 * ({@code slow_falling} for 220 ticks), no projectile, no target search, no {@code swap_active_spell}. It is
 * the most deterministic observable in the corpus — the effect is either on the caster or it is not.
 */
public final class WandCastLifecycleTests {

    private static final String SPELL_ID = "arresto_momentum";
    /** {@code arresto_momentum} declares a {@code knows} requirement on this. */
    private static final String PREREQUISITE_ID = "wingardium_leviosa";

    private WandCastLifecycleTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("cast_and_release_casts_exactly_once", "wand cast: one release, one cast",
                WandCastLifecycleTests::castAndReleaseCastsExactlyOnce);
        tests.add("release_without_cast_does_nothing", "wand cast: release with no session",
                WandCastLifecycleTests::releaseWithoutCastDoesNothing);
        tests.add("duplicate_release_is_refused", "wand cast: duplicate release",
                WandCastLifecycleTests::duplicateReleaseIsRefused);
        tests.add("dead_caster_cannot_release", "wand cast: release after death",
                WandCastLifecycleTests::deadCasterCannotRelease);
        tests.add("interrupted_hold_cannot_release", "wand cast: interrupted hold cannot release",
                WandCastLifecycleTests::interruptedHoldCannotRelease);
        tests.add("respawn_then_stale_release_is_refused", "wand cast: release after respawn",
                WandCastLifecycleTests::respawnThenStaleReleaseIsRefused);
        tests.add("dimension_change_ends_hold", "wand cast: release after a dimension change",
                WandCastLifecycleTests::dimensionChangeEndsHold);
        tests.add("logout_clears_hold_and_reconnect_is_fresh", "wand cast: logout and reconnect",
                WandCastLifecycleTests::logoutClearsHoldAndReconnectIsFresh);
        tests.add("hotbar_swap_ends_hold", "wand cast: switching hotbar slot mid-hold",
                WandCastLifecycleTests::hotbarSwapEndsHold);
        tests.add("dropped_wand_ends_hold", "wand cast: dropping the wand mid-hold",
                WandCastLifecycleTests::droppedWandEndsHold);
    }

    // ── A: the cast completes exactly once ──────────────────────────────────────────────────────

    /**
     * Press, hold, let go — and exactly one cast comes out of it.
     *
     * <p>The hold spans real server ticks rather than being released on the tick it began, so the session has
     * to survive {@code onUseTick} to still be there when the release arrives.
     */
    private static void castAndReleaseCastsExactlyOnce(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper);
        String spellId = spellId();
        PlayerSpellData data = spellData(caster);

        check(helper, data.getCastCount(spellId) == 0,
                () -> "before the cast, cast count for " + spellId + " should be 0, was " + data.getCastCount(spellId));
        check(helper, !caster.hasEffect(MobEffects.SLOW_FALLING),
                () -> "before the cast the caster already has slow_falling; the test could not then tell a new "
                        + "application from a pre-existing one");

        helper.startSequence()
                .thenExecute(() -> {
                    beginCast(helper, caster);
                    check(helper, caster.isUsingItem(),
                            () -> "after gameMode.useItem the caster is not using an item, so WandItem.use never "
                                    + "reached startUsingItem (is Module.WANDS_AND_SPELLS enabled?)");
                    WandCastSessions.Session session = WandCastSessions.peek(caster);
                    check(helper, session != null,
                            () -> "WandItem.use opened no cast session; expected exactly one open session, found none");
                    check(helper, session != null && !session.releaseConsumed(),
                            () -> "the freshly opened session already has its release token spent: "
                                    + describeSession(caster));
                })
                // Three ticks of a live channel. onUseTick runs on each; the session must survive them.
                .thenIdle(3)
                .thenExecute(() -> {
                    check(helper, caster.isUsingItem(),
                            () -> "the caster stopped using the wand during the channel, at game tick "
                                    + gameTime(helper));
                    WandCastSessions.Session session = WandCastSessions.peek(caster);
                    check(helper, session != null && !session.releaseConsumed(),
                            () -> "the session did not survive three ticks of channel: " + describeSession(caster)
                                    + " at game tick " + gameTime(helper));
                })
                .thenExecute(() -> {
                    long tickAtRelease = gameTime(helper);
                    releaseWand(caster);

                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "one release must produce exactly one cast: expected cast count 1 for " + spellId
                                    + ", was " + data.getCastCount(spellId) + " (" + describeSession(caster) + ")");
                    check(helper, caster.hasEffect(MobEffects.SLOW_FALLING),
                            () -> "the spell's only authored effect (slow_falling) was never applied, so the release "
                                    + "was accepted but produced no cast (" + describeSession(caster) + ")");
                    check(helper, data.isOnCooldown(spellId, tickAtRelease),
                            () -> "no cooldown was stamped for " + spellId + " at game tick " + tickAtRelease
                                    + "; recorded expiry is " + data.getCooldownExpiry(spellId));

                    WandCastSessions.Session session = WandCastSessions.peek(caster);
                    check(helper, session != null && session.releaseConsumed(),
                            () -> "an accepted release did not spend the session's token: " + describeSession(caster));
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── B: a release with no cast behind it does nothing ────────────────────────────────────────

    /**
     * The release path is driven with no hold ever started. Nothing may happen.
     *
     * <p>The caster is otherwise <em>completely</em> valid — bonded wand in hand, spell known and selected,
     * wandkind heritage — so a refusal here can only be about the missing session. The scenario then performs
     * a real cast and release on the same player and asserts it works, which rules out the false pass where
     * the first release was refused for some unrelated reason and the assertions proved nothing.
     */
    private static void releaseWithoutCastDoesNothing(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper);
        String spellId = spellId();
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> {
                    check(helper, WandCastSessions.peek(caster) == null,
                            () -> "a session existed before any cast was begun: " + describeSession(caster));

                    releaseWand(caster);

                    check(helper, data.getCastCount(spellId) == 0,
                            () -> "a release with no session cast the spell: cast count for " + spellId + " is "
                                    + data.getCastCount(spellId) + ", expected 0");
                    check(helper, !caster.hasEffect(MobEffects.SLOW_FALLING),
                            () -> "a release with no session applied the spell's effect");
                    check(helper, !data.isOnCooldown(spellId, gameTime(helper)),
                            () -> "a release with no session stamped a cooldown for " + spellId + ", expiry "
                                    + data.getCooldownExpiry(spellId));
                    check(helper, WandCastSessions.peek(caster) == null,
                            () -> "a refused release created a session: " + describeSession(caster));
                })
                // Now prove the setup was good all along: the same player, casting properly, must succeed.
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(2)
                .thenExecute(() -> {
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "the control cast failed, so the earlier refusal cannot be attributed to the "
                                    + "missing session: cast count for " + spellId + " is " + data.getCastCount(spellId)
                                    + ", expected 1 (" + describeSession(caster) + ")");
                    check(helper, caster.hasEffect(MobEffects.SLOW_FALLING),
                            () -> "the control cast produced no slow_falling, so this caster was never able to cast "
                                    + "at all and the no-session assertions above proved nothing");
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── C: the second release of one hold is worth nothing ──────────────────────────────────────

    /**
     * Cast, release, release again. The duplicate must cost nothing and produce nothing.
     *
     * <p>The applied effect is <em>removed</em> between the two releases on purpose. Re-applying
     * {@code slow_falling} to a caster who already has it is invisible — same effect, same duration — so
     * without clearing it first a second cast would leave no trace. Cleared, a second cast would put it
     * straight back, and its absence afterwards is real evidence that nothing ran.
     */
    private static void duplicateReleaseIsRefused(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper);
        String spellId = spellId();
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(2)
                .thenExecute(() -> {
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "the first release should have cast once: cast count for " + spellId + " is "
                                    + data.getCastCount(spellId));
                    check(helper, caster.hasEffect(MobEffects.SLOW_FALLING),
                            () -> "the first release produced no slow_falling, so the duplicate assertions below "
                                    + "would prove nothing");
                })
                .thenExecute(() -> {
                    long expiryAfterFirst = data.getCooldownExpiry(spellId);
                    // Clear the effect so a second application would be plainly visible.
                    caster.removeEffect(MobEffects.SLOW_FALLING);
                    check(helper, !caster.hasEffect(MobEffects.SLOW_FALLING),
                            () -> "could not clear slow_falling before the duplicate release, so the test cannot "
                                    + "distinguish a second application from the first");

                    // The duplicate: the same empty payload arriving a second time for one hold.
                    SpellCastC2SPayload.completeWandCastRelease(caster);

                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "the duplicate release cast the spell again: cast count for " + spellId + " is "
                                    + data.getCastCount(spellId) + ", expected 1 (" + describeSession(caster) + ")");
                    check(helper, !caster.hasEffect(MobEffects.SLOW_FALLING),
                            () -> "the duplicate release re-applied slow_falling, so it resolved into a second cast");
                    check(helper, data.getCooldownExpiry(spellId) == expiryAfterFirst,
                            () -> "the duplicate release re-stamped the cooldown for " + spellId + ": expiry moved "
                                    + "from " + expiryAfterFirst + " to " + data.getCooldownExpiry(spellId));
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── D: a corpse cannot finish a cast ────────────────────────────────────────────────────────

    /**
     * Cast, die mid-hold, then release a tick later. Vanilla ends the item hold client-side as a player
     * dies and the release is sent regardless, so this is the packet a real dying player actually sends.
     *
     * <p>Two guarantees are asserted, and they are independent on purpose: death takes the session away in
     * the tick it happens, and the release that follows changes nothing. Either alone would be enough to
     * make the cast safe; the test would rather notice if one of them stopped being true.
     */
    private static void deadCasterCannotRelease(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper);
        String spellId = spellId();
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(2)
                .thenExecute(() -> {
                    check(helper, WandCastSessions.peek(caster) != null,
                            () -> "there was no session to invalidate — the cast never opened one");
                    caster.kill(helper.getLevel());
                    check(helper, !caster.isAlive(),
                            () -> "the caster survived Entity.kill(); health=" + caster.getHealth()
                                    + " removed=" + caster.isRemoved());
                    // Death must take the session with it. This is what makes the release below safe
                    // whatever the server does to the body afterwards.
                    check(helper, WandCastSessions.peek(caster) == null,
                            () -> "dying did not abort the cast session, so a release could still land on it: "
                                    + describeSession(caster));
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    check(helper, !caster.isAlive(),
                            () -> "the caster was alive again a tick after dying, so the release below would "
                                    + "not be testing a dead caster: health=" + caster.getHealth());

                    releaseWand(caster);

                    check(helper, data.getCastCount(spellId) == 0,
                            () -> "a dead caster completed a cast: cast count for " + spellId + " is "
                                    + data.getCastCount(spellId) + ", expected 0 (" + describeSession(caster) + ")");
                    check(helper, !caster.hasEffect(MobEffects.SLOW_FALLING),
                            () -> "a dead caster's release applied the spell's effect");
                    check(helper, !data.isOnCooldown(spellId, gameTime(helper)),
                            () -> "a dead caster's release stamped a cooldown for " + spellId + ", expiry "
                                    + data.getCooldownExpiry(spellId));
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── E: an interrupted hold cannot be converted into a late cast ────────────────────────────

    /**
     * A client packet is not proof of a release: the server must first have run vanilla's
     * {@code releaseUsing}. This covers a packet injected while the button is still down and the
     * shared {@code stopUsingItem} path used by slot swaps, dropping the wand, stuns, and other
     * server-side interruptions. The same player then casts normally, proving the abort did not
     * leave a stale guard that swallows a genuine new hold.
     */
    private static void interruptedHoldCannotRelease(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper);
        String spellId = spellId();
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(2)
                .thenExecute(() -> {
                    check(helper, WandCastSessions.peek(caster) != null && caster.isUsingItem(),
                            () -> "the initial hold did not open before testing its release guard: "
                                    + describeSession(caster));

                    // A forged early packet must not turn a still-held wand into a cast.
                    SpellCastC2SPayload.completeWandCastRelease(caster);
                    check(helper, data.getCastCount(spellId) == 0,
                            () -> "a client release before vanilla ended the hold cast " + spellId);
                    WandCastSessions.Session session = WandCastSessions.peek(caster);
                    check(helper, session != null && !session.releaseConsumed(),
                            () -> "an early client packet spent the release token: " + describeSession(caster));

                    // This is the common non-release exit used by swaps, disarms and forced controls.
                    caster.stopUsingItem();
                    check(helper, WandCastSessions.peek(caster) == null,
                            () -> "an interrupted hold retained a releasable session: " + describeSession(caster));

                    SpellCastC2SPayload.completeWandCastRelease(caster);
                    check(helper, data.getCastCount(spellId) == 0,
                            () -> "a late packet after interruption cast " + spellId);
                })
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(2)
                .thenExecute(() -> {
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "a genuine new hold was swallowed after interruption: cast count for "
                                    + spellId + " is " + data.getCastCount(spellId));
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── F: a respawned body inherits no hold ────────────────────────────────────────────────────

    /**
     * Die mid-hold, respawn, then let both bodies release. {@code PlayerList.respawn} builds a new
     * {@link ServerPlayer} under the same UUID, which is the key every session map uses — so this is the case
     * where a session that survived the death would be found again by the new body.
     *
     * <p><b>Everything after the respawn happens inside one tick.</b> The respawned body is a plain
     * {@code ServerPlayer}, and NeoForge's {@code ConfigSync} throws on the next server tick for a plain player
     * whose connection never went through configuration — which a mock connection never does. The body is
     * retired before that tick ends. Its genuine hold is therefore pressed and released in the same tick, which is
     * a legal hold (see {@code WandCastRaceTests}' rapid presses).
     */
    private static void respawnThenStaleReleaseIsRefused(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper);
        String spellId = spellId();

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(1)
                .thenExecute(() -> {
                    check(helper, WandCastSessions.peek(caster) != null,
                            () -> "the hold opened no session, so a respawn had nothing to invalidate");
                    caster.kill(helper.getLevel());
                    ServerPlayer fresh = helper.getLevel().getServer().getPlayerList()
                            .respawn(caster, false, Entity.RemovalReason.KILLED);
                    try {
                        check(helper, fresh != caster && fresh.getUUID().equals(caster.getUUID()),
                                () -> "respawn did not build a new body under the same id");
                        check(helper, WandCastSessions.peek(fresh) == null,
                                () -> "the respawned body found the old body's hold: " + describeSession(fresh));

                        // The corpse's own release, in client order, then the same packet read against the new body.
                        releaseWand(caster);
                        SpellCastC2SPayload.completeWandCastRelease(fresh);
                        check(helper, spellData(fresh).getCastCount(spellId) == 0,
                                () -> "a release after respawn cast " + spellId + ": count "
                                        + spellData(fresh).getCastCount(spellId));
                        check(helper, !fresh.hasEffect(MobEffects.SLOW_FALLING),
                                () -> "a release after respawn applied the spell's effect to the new body");

                        prepare(helper, fresh);
                        clearCooldowns(fresh);
                        beginCast(helper, fresh);
                        releaseWand(fresh);
                        check(helper, spellData(fresh).getCastCount(spellId) == 1,
                                () -> "the respawned body's first genuine hold was swallowed: count "
                                        + spellData(fresh).getCastCount(spellId) + " (" + describeSession(fresh) + ")");
                    } finally {
                        retire(helper, fresh);
                    }
                })
                .thenSucceed();
    }

    // ── G: a hold does not cross a dimension ────────────────────────────────────────────────────

    /**
     * Change dimension mid-hold, release in the new dimension, come back and cast again. Vanilla stops item use
     * on a dimension change; the release that follows must find nothing, and the next hold must be untouched.
     */
    private static void dimensionChangeEndsHold(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper);
        String spellId = spellId();
        PlayerSpellData data = spellData(caster);
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            helper.fail("the game-test server has no nether level, so a dimension change cannot be driven");
            return;
        }

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(1)
                .thenExecute(() -> {
                    check(helper, caster.isUsingItem() && WandCastSessions.peek(caster) != null,
                            () -> "the hold did not open before the dimension change: " + describeSession(caster));
                    caster.teleport(new TeleportTransition(nether, new Vec3(0.5, 90.0, 0.5), Vec3.ZERO, 0f, 0f,
                            TeleportTransition.DO_NOTHING));
                    check(helper, caster.level() == nether,
                            () -> "the teleport did not change dimension; caster is in " + caster.level().dimension());
                    check(helper, !caster.isUsingItem(),
                            () -> "the hold survived a dimension change");
                    check(helper, WandCastSessions.peek(caster) == null,
                            () -> "a dimension change left the hold's session open: " + describeSession(caster));

                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 0,
                            () -> "a release after a dimension change cast " + spellId);

                    caster.teleport(new TeleportTransition(helper.getLevel(),
                            Vec3.atBottomCenterOf(helper.absolutePos(BlockPos.ZERO)), Vec3.ZERO, 0f, 0f,
                            TeleportTransition.DO_NOTHING));
                    WizardTestSupport.parkAtOrigin(helper, caster);
                })
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(2)
                .thenExecute(() -> {
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "the first hold after coming back was swallowed: count "
                                    + data.getCastCount(spellId) + " (" + describeSession(caster) + ")");
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── H: logout takes the hold; a reconnect starts clean ──────────────────────────────────────

    /**
     * Log out mid-hold, let the departed connection's release arrive, reconnect under the same profile and let
     * a stale release arrive on the new connection. Neither may cast; the reconnected player's first hold must.
     */
    private static void logoutClearsHoldAndReconnectIsFresh(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper);
        String spellId = spellId();
        UUID id = caster.getUUID();
        String name = caster.getGameProfile().name();
        ServerPlayer[] reconnected = new ServerPlayer[1];
        int[] countBefore = new int[1];

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(1)
                .thenExecute(() -> {
                    check(helper, WandCastSessions.peek(caster) != null,
                            () -> "the hold opened no session before logout");
                    retire(helper, caster);
                    check(helper, WandCastSessions.peek(caster) == null,
                            () -> "logout left the hold's session behind: " + describeSession(caster));
                    releaseWand(caster);

                    ServerPlayer back = WizardTestSupport.placeMockPlayer(helper, name, id, GameType.CREATIVE);
                    reconnected[0] = back;
                    countBefore[0] = spellData(back).getCastCount(spellId);
                    check(helper, WandCastSessions.peek(back) == null,
                            () -> "the reconnected player inherited a hold: " + describeSession(back));
                    SpellCastC2SPayload.completeWandCastRelease(back);
                    check(helper, spellData(back).getCastCount(spellId) == countBefore[0],
                            () -> "a stale release after reconnect cast " + spellId);
                })
                .thenExecute(() -> {
                    prepare(helper, reconnected[0]);
                    beginCast(helper, reconnected[0]);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    ServerPlayer back = reconnected[0];
                    releaseWand(back);
                    check(helper, spellData(back).getCastCount(spellId) == countBefore[0] + 1,
                            () -> "the reconnected player's first hold was swallowed: count "
                                    + spellData(back).getCastCount(spellId) + ", expected " + (countBefore[0] + 1)
                                    + " (" + describeSession(back) + ")");
                })
                .thenExecute(() -> retire(helper, reconnected[0]))
                .thenSucceed();
    }

    // ── I: moving off the wand's slot ends the hold ─────────────────────────────────────────────

    /**
     * Switch to an empty hotbar slot mid-hold. Vanilla notices on the player's own tick (the mock player is ticked
     * by hand, see {@code LeviosaThrowTests}) and stops the use without a release.
     */
    private static void hotbarSwapEndsHold(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper);
        String spellId = spellId();
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(1)
                .thenExecute(() -> {
                    caster.getInventory().setSelectedSlot(1);
                    caster.doTick();
                    check(helper, !caster.isUsingItem(),
                            () -> "the hold survived switching to an empty slot");
                    check(helper, WandCastSessions.peek(caster) == null,
                            () -> "switching slot left the hold's session open: " + describeSession(caster));
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 0,
                            () -> "a release after a slot switch cast " + spellId);
                    caster.getInventory().setSelectedSlot(0);
                })
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(2)
                .thenExecute(() -> {
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "the hold after switching back was swallowed: count " + data.getCastCount(spellId));
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── J: dropping the wand ends the hold ──────────────────────────────────────────────────────

    /**
     * Drop the wand mid-hold. {@code ServerPlayer.drop} splits the used stack to nothing and only then stops
     * the use — and {@code stopUsingItem} skips {@code onStopUsing} for an empty stack, so the item's own
     * teardown never runs. The server's player tick is what must notice.
     */
    private static void droppedWandEndsHold(GameTestHelper helper) {
        ServerPlayer caster = readyCaster(helper);
        String spellId = spellId();
        PlayerSpellData data = spellData(caster);

        helper.startSequence()
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(1)
                .thenExecute(() -> {
                    caster.drop(false);
                    check(helper, caster.getMainHandItem().isEmpty(),
                            () -> "the wand was not dropped: hand holds " + caster.getMainHandItem());
                    caster.doTick();
                    check(helper, !caster.isUsingItem(), () -> "the hold survived dropping the wand");
                    check(helper, WandCastSessions.peek(caster) == null,
                            () -> "dropping the wand left the hold's session open: " + describeSession(caster));
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 0,
                            () -> "a release after dropping the wand cast " + spellId);
                    discardDroppedItems(helper, caster);
                    WizardTestSupport.giveBondedWand(caster);
                    WizardTestSupport.settleAllegiance(caster);
                })
                .thenExecute(() -> beginCast(helper, caster))
                .thenIdle(2)
                .thenExecute(() -> {
                    releaseWand(caster);
                    check(helper, data.getCastCount(spellId) == 1,
                            () -> "the hold with the picked-up wand was swallowed: count " + data.getCastCount(spellId)
                                    + " (" + describeSession(caster) + ")");
                })
                .thenExecute(() -> retire(helper, caster))
                .thenSucceed();
    }

    // ── shared setup ────────────────────────────────────────────────────────────────────────────

    /**
     * A caster for whom every gate ahead of the session check in {@code SpellCastService} is already
     * satisfied, so a refused release in these scenarios can only be the session or the caster's own state.
     */
    static ServerPlayer readyCaster(GameTestHelper helper) {
        return readyCaster(helper, "wandb-test-caster");
    }

    static ServerPlayer readyCaster(GameTestHelper helper, String name) {
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, name);
        prepare(helper, caster);
        return caster;
    }

    /** Makes an already-placed player a ready caster: parked, wandkind, bonded wand in hand, spell selected. */
    static void prepare(GameTestHelper helper, ServerPlayer caster) {
        WizardTestSupport.parkAtOrigin(helper, caster);
        WizardTestSupport.makeWandkind(caster);
        WizardTestSupport.giveBondedWand(caster);
        WizardTestSupport.settleAllegiance(caster);
        WizardTestSupport.learnAndSelect(helper, caster, SPELL_ID, PREREQUISITE_ID);
    }

    static String spellId() {
        var spell = at.koopro.wizardsandbeasts.spell.core.Spells.byId(SPELL_ID);
        return spell == null ? SPELL_ID : spell.getId();
    }

    /**
     * Begins the hold exactly the way the server does when it reads a {@code ServerboundUseItemPacket}. Going
     * through the game mode rather than calling {@code stack.use} keeps vanilla's own preconditions in the path.
     */
    static void beginCast(GameTestHelper helper, ServerPlayer caster) {
        caster.gameMode.useItem(caster, helper.getLevel(), caster.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    /** Lets the next cast of the test spell through the spell and global cooldowns, which are not under test. */
    static void clearCooldowns(ServerPlayer caster) {
        PlayerSpellData data = spellData(caster);
        data.setCooldown(spellId(), 0L);
        data.setGlobalCooldownEndTick(0L);
    }

    private static void discardDroppedItems(GameTestHelper helper, ServerPlayer caster) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, caster.getBoundingBox().inflate(4.0))
                .forEach(ItemEntity::discard);
    }

    /**
     * Lets go of the wand the way a real client does, in the order a real client does it.
     *
     * <p>{@code MultiPlayerGameMode.releaseUsingItem} sends the vanilla RELEASE_USE_ITEM packet <em>first</em>
     * and only then runs the local release that sends our payload, and the connection preserves that order. So
     * vanilla's release — which runs {@code WandItem.releaseUsing}, recording the hold duration and ending any
     * beam channel — lands before ours. Reproducing that order here is the interleaving no unit test reaches.
     */
    static void releaseWand(ServerPlayer caster) {
        caster.releaseUsingItem();
        SpellCastC2SPayload.completeWandCastRelease(caster);
    }

    /** Session state in one line, for failure messages. */
    static String describeSession(ServerPlayer caster) {
        WandCastSessions.Session session = WandCastSessions.peek(caster);
        if (session == null) {
            return "session=none";
        }
        return "session=#" + session.id() + " opened@" + session.startGameTick()
                + " releaseObserved=" + session.vanillaReleaseObserved()
                + " releaseConsumed=" + session.releaseConsumed()
                + " clashHold=" + session.clashHold()
                + " usingItem=" + caster.isUsingItem();
    }
}
