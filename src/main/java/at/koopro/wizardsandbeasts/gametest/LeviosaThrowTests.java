package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.spellData;

/**
 * Wingardium Leviosa throws on the attack key, and only on the attack key.
 *
 * <p>Every end of a hold used to fling the target along the caster's aim, because the one teardown all
 * of them share did it — so letting go of the button shot the target forward and nothing could simply be
 * put down. These drive a real hold on a real target and assert on the target: its gravity and the
 * velocity it was left with, measured along the caster's aim. The throw is 1.6 blocks a tick along the
 * aim; a settled hold leaves almost none.
 *
 * <p><b>The mock player is ticked by hand.</b> A real player's item use advances in
 * {@code ServerPlayer.doTick}, which only {@code ServerGamePacketListenerImpl.tickPlayer} calls, and only
 * for connections {@code ServerConnectionListener} accepted. The mock connection is not one of them, so
 * without calling {@code doTick} here {@code WandItem.onUseTick} never fires and there is no channel at
 * all — every assertion below would be about a spell that never ran.
 *
 * <p>The target is an armor stand because it has no AI to walk it off the crosshair. It also cannot move
 * while it has no gravity ({@code ArmorStand.travel} is skipped without physics), which is why nothing
 * here asserts on position.
 */
public final class LeviosaThrowTests {

    private static final String SPELL_ID = "wingardium_leviosa";
    /** {@code wingardium_leviosa} declares a {@code knows} requirement on this. */
    private static final String PREREQUISITE_ID = "lumos";

    /** Caster's feet, looking along +Z; the target stands two blocks ahead, in the eye line. */
    private static final Vec3 CASTER = new Vec3(1.5, 1.0, 0.5);
    private static final Vec3 TARGET = new Vec3(1.5, 1.0, 2.5);

    /** Long enough for the spring to settle; the lift itself lands on the first channel tick. */
    private static final int HOLD_TICKS = 10;
    /** Channel ticks after a throw in which the thrown target must stay down. */
    private static final int RELIFT_WATCH_TICKS = 5;
    /** Well under the 1.6 a throw imparts along the aim, well over what a settled hold leaves. */
    private static final double THROWN_ALONG_AIM_MIN = 1.2;
    private static final double DROPPED_ALONG_AIM_MAX = 0.5;

    private LeviosaThrowTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("leviosa_release_drops_without_throwing", "leviosa: letting go drops the target",
                LeviosaThrowTests::releaseDropsWithoutThrowing);
        tests.add("leviosa_attack_throws_and_does_not_relift", "leviosa: attack key throws, once",
                LeviosaThrowTests::attackThrowsAndDoesNotRelift);
        tests.add("leviosa_dropped_wand_ends_the_channel", "leviosa: dropping the wand drops the target",
                LeviosaThrowTests::droppedWandEndsTheChannel);
        tests.add("leviosa_silenced_caster_cannot_channel", "leviosa: a langlocked hold lifts nothing",
                LeviosaThrowTests::silencedCasterCannotChannel);
        tests.add("leviosa_interrupt_is_not_undone", "leviosa: an interrupted channel stays down while silenced",
                LeviosaThrowTests::interruptIsNotUndoneNextTick);
    }

    // ── A: letting go is a drop ─────────────────────────────────────────────────────────────────

    private static void releaseDropsWithoutThrowing(GameTestHelper helper) {
        clearTheLane(helper);
        ServerPlayer caster = readyCaster(helper, "wandb-leviosa-dropper");
        ArmorStand target = helper.spawn(EntityType.ARMOR_STAND, TARGET);

        helper.startSequence()
                .thenWaitUntil(() -> checkLaneTicks(helper))
                .thenExecute(() -> beginCast(helper, caster))
                .thenExecuteFor(HOLD_TICKS, caster::doTick)
                .thenExecute(() -> {
                    checkLifted(helper, caster, target);

                    caster.releaseUsingItem();

                    check(helper, !target.isNoGravity(),
                            () -> "letting go left the target floating: " + describe(caster, target));
                    double along = alongAim(caster, target);
                    check(helper, along < DROPPED_ALONG_AIM_MAX,
                            () -> "letting go sent the target along the caster's aim at " + along
                                    + " blocks/tick, expected under " + DROPPED_ALONG_AIM_MAX
                                    + " — releasing is throwing again: " + describe(caster, target));
                })
                .thenExecute(() -> {
                    target.discard();
                    retire(helper, caster);
                })
                .thenSucceed();
    }

    // ── B: the attack key throws, and the hold does not catch it again ──────────────────────────

    private static void attackThrowsAndDoesNotRelift(GameTestHelper helper) {
        clearTheLane(helper);
        ServerPlayer caster = readyCaster(helper, "wandb-leviosa-thrower");
        ArmorStand target = helper.spawn(EntityType.ARMOR_STAND, TARGET);

        helper.startSequence()
                .thenWaitUntil(() -> checkLaneTicks(helper))
                .thenExecute(() -> {
                    beginCast(helper, caster);
                    // No channel tick has run, so there is no session: a throw here must be a no-op.
                    check(helper, !WandBeamChannelLogic.throwLeviosaTarget(caster),
                            () -> "a throw before the hold had lifted anything reported a throw: "
                                    + describe(caster, target));
                })
                .thenExecuteFor(HOLD_TICKS, caster::doTick)
                .thenExecute(() -> {
                    checkLifted(helper, caster, target);

                    boolean thrown = WandBeamChannelLogic.throwLeviosaTarget(caster);

                    check(helper, thrown,
                            () -> "the hold was lifting a target and the throw found nothing to throw: "
                                    + describe(caster, target));
                    check(helper, !target.isNoGravity(),
                            () -> "the thrown target kept no-gravity: " + describe(caster, target));
                    double along = alongAim(caster, target);
                    check(helper, along >= THROWN_ALONG_AIM_MIN,
                            () -> "the thrown target moves along the caster's aim at " + along
                                    + " blocks/tick, expected at least " + THROWN_ALONG_AIM_MIN + ": "
                                    + describe(caster, target));
                })
                .thenExecuteFor(RELIFT_WATCH_TICKS, caster::doTick)
                .thenExecute(() -> {
                    check(helper, caster.isUsingItem(),
                            () -> "the hold ended after the throw, so nothing below watched a live channel: "
                                    + describe(caster, target));
                    check(helper, !target.isNoGravity(),
                            () -> "the hold lifted the thrown target straight back up within "
                                    + RELIFT_WATCH_TICKS + " ticks: " + describe(caster, target));
                    check(helper, !WandBeamChannelLogic.throwLeviosaTarget(caster),
                            () -> "a second throw in the same hold threw again: " + describe(caster, target));
                    caster.releaseUsingItem();
                })
                .thenExecute(() -> {
                    target.discard();
                    retire(helper, caster);
                })
                .thenSucceed();
    }

    // ── C: dropping the wand ends the channel ───────────────────────────────────────────────────

    /**
     * Drop the wand while it lifts something. {@code ServerPlayer.drop} empties the used stack before stopping the
     * use, and vanilla skips {@code onStopUsing} for an empty stack — so without the server's own reconciliation
     * the channel outlived the hold: the target hung in the air and the beam never ended for anyone watching.
     */
    private static void droppedWandEndsTheChannel(GameTestHelper helper) {
        clearTheLane(helper);
        ServerPlayer caster = readyCaster(helper, "wandb-leviosa-dropper-wand");
        ArmorStand target = helper.spawn(EntityType.ARMOR_STAND, TARGET);

        helper.startSequence()
                .thenWaitUntil(() -> checkLaneTicks(helper))
                .thenExecute(() -> beginCast(helper, caster))
                .thenExecuteFor(HOLD_TICKS, caster::doTick)
                .thenExecute(() -> {
                    checkLifted(helper, caster, target);
                    caster.drop(false);
                })
                .thenExecuteFor(2, caster::doTick)
                .thenExecute(() -> {
                    check(helper, WandBeamChannelLogic.activeChannelSpellId(caster) == null,
                            () -> "the channel outlived the dropped wand: " + describe(caster, target));
                    check(helper, !target.isNoGravity(),
                            () -> "the target kept floating after the wand was dropped: " + describe(caster, target));
                })
                .thenExecute(() -> {
                    target.discard();
                    helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            caster.getBoundingBox().inflate(4.0)).forEach(net.minecraft.world.entity.Entity::discard);
                    retire(helper, caster);
                })
                .thenSucceed();
    }

    // ── D: a silenced caster's hold drives no channel ───────────────────────────────────────────

    /**
     * Langlock refuses a release; it must refuse the channel under that release too, or a silenced caster holds
     * the beam for as long as they like and never meets the refusal that would have cost them a cooldown.
     */
    private static void silencedCasterCannotChannel(GameTestHelper helper) {
        clearTheLane(helper);
        ServerPlayer caster = readyCaster(helper, "wandb-leviosa-silenced");
        ArmorStand target = helper.spawn(EntityType.ARMOR_STAND, TARGET);

        helper.startSequence()
                .thenWaitUntil(() -> checkLaneTicks(helper))
                .thenExecute(() -> {
                    caster.addEffect(new MobEffectInstance(ModEffects.LANGLOCK, 200, 0));
                    beginCast(helper, caster);
                })
                .thenExecuteFor(HOLD_TICKS, caster::doTick)
                .thenExecute(() -> {
                    check(helper, caster.isUsingItem(), () -> "the hold ended on its own: " + describe(caster, target));
                    check(helper, !target.isNoGravity() && WandBeamChannelLogic.activeChannelSpellId(caster) == null,
                            () -> "a langlocked caster channelled Leviosa: " + describe(caster, target));
                    caster.removeEffect(ModEffects.LANGLOCK);
                })
                .thenExecuteFor(HOLD_TICKS, caster::doTick)
                .thenExecute(() -> {
                    // The control: the same hold, unsilenced, lifts — so the refusal above was the langlock.
                    checkLifted(helper, caster, target);
                    caster.releaseUsingItem();
                })
                .thenExecute(() -> {
                    target.discard();
                    retire(helper, caster);
                })
                .thenSucceed();
    }

    // ── E: an interrupt holds while the langlock does ───────────────────────────────────────────

    /**
     * Finite's interrupt (and the {@code interrupt_cast} effect component) end the target's channel and langlock
     * them. The hold itself stays down on the client, so the very next tick drove the channel again and the
     * interrupt lasted one tick. Driven here exactly as {@code SpellCastTargetedHandler.interruptCasting} does it.
     */
    private static void interruptIsNotUndoneNextTick(GameTestHelper helper) {
        clearTheLane(helper);
        ServerPlayer caster = readyCaster(helper, "wandb-leviosa-interrupted");
        ArmorStand target = helper.spawn(EntityType.ARMOR_STAND, TARGET);

        helper.startSequence()
                .thenWaitUntil(() -> checkLaneTicks(helper))
                .thenExecute(() -> beginCast(helper, caster))
                .thenExecuteFor(HOLD_TICKS, caster::doTick)
                .thenExecute(() -> {
                    checkLifted(helper, caster, target);
                    WandBeamChannelLogic.endChannel(caster);
                    caster.addEffect(new MobEffectInstance(ModEffects.LANGLOCK, 30, 0, false, true, true));
                    check(helper, !target.isNoGravity(),
                            () -> "the interrupt did not put the target down: " + describe(caster, target));
                })
                .thenExecuteFor(5, caster::doTick)
                .thenExecute(() -> {
                    check(helper, !target.isNoGravity() && WandBeamChannelLogic.activeChannelSpellId(caster) == null,
                            () -> "the interrupted channel picked the target straight back up while langlocked: "
                                    + describe(caster, target));
                    caster.releaseUsingItem();
                })
                .thenExecute(() -> {
                    target.discard();
                    retire(helper, caster);
                })
                .thenSucceed();
    }

    // ── shared setup ────────────────────────────────────────────────────────────────────────────

    /**
     * Stone underfoot and air from the caster to past the target.
     *
     * <p>The empty structure is walled in barriers, which are colliders: left in place, the eye line would
     * stop on one before it reached the target, and the first channel tick — which is allowed to lift a
     * block when it finds no entity — would try to pull a barrier out of the wall instead.
     */
    static void clearTheLane(GameTestHelper helper) {
        // The lane spans up to five blocks from the origin, which can cross a chunk boundary. See forceChunks.
        WizardTestSupport.forceChunks(helper, LANE_MIN, LANE_MAX);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    /** The lane's corners, test-relative: stone at y 0, air above, three wide and five long. */
    static final BlockPos LANE_MIN = new BlockPos(0, 0, 0);
    static final BlockPos LANE_MAX = new BlockPos(2, 4, 4);

    /** The first step of every lane scenario: {@link #clearTheLane} forced the chunks; wait until they tick. */
    static void checkLaneTicks(GameTestHelper helper) {
        WizardTestSupport.checkChunksTick(helper, LANE_MIN, LANE_MAX);
    }

    private static ServerPlayer readyCaster(GameTestHelper helper, String name) {
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, name);
        WizardTestSupport.makeWandkind(caster);
        WizardTestSupport.giveBondedWand(caster);
        WizardTestSupport.learnAndSelect(helper, caster, SPELL_ID, PREREQUISITE_ID);
        Vec3 feet = helper.absoluteVec(CASTER);
        // Yaw 0 looks along +Z, straight at the target.
        caster.snapTo(feet.x, feet.y, feet.z, 0.0f, 0.0f);
        caster.setYHeadRot(0.0f);
        caster.setNoGravity(true);
        return caster;
    }

    /** The server's own entry for a use-item packet, so vanilla's preconditions stay in the path. */
    private static void beginCast(GameTestHelper helper, ServerPlayer caster) {
        caster.gameMode.useItem(caster, helper.getLevel(), caster.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    private static void checkLifted(GameTestHelper helper, ServerPlayer caster, ArmorStand target) {
        check(helper, caster.isUsingItem(),
                () -> "the caster is not holding the wand after " + HOLD_TICKS + " ticks: " + describe(caster, target));
        check(helper, target.isNoGravity(),
                () -> "the hold never lifted the target, so nothing about releasing or throwing it can be "
                        + "measured: " + describe(caster, target));
    }

    private static double alongAim(ServerPlayer caster, ArmorStand target) {
        return target.getDeltaMovement().dot(caster.getLookAngle());
    }

    private static String describe(ServerPlayer caster, ArmorStand target) {
        return "usingItem=" + caster.isUsingItem()
                + " useItem=" + caster.getUseItem()
                + " activeSpell=" + spellData(caster).getActiveSpellId()
                + " aim=" + caster.getLookAngle()
                + " eye=" + caster.getEyePosition()
                + " | target at " + target.position()
                + " noGravity=" + target.isNoGravity()
                + " velocity=" + target.getDeltaMovement()
                + " removed=" + target.isRemoved();
    }
}
