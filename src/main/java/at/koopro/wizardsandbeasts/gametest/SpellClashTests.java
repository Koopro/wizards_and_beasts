package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.spell.SpellClashEntity;
import at.koopro.wizardsandbeasts.entity.spell.SpellProjectileEntity;
import at.koopro.wizardsandbeasts.network.spell.SpellCastC2SPayload;
import at.koopro.wizardsandbeasts.spell.clash.SpellClashRules;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Two bolts fired at each other lock into a clash, and the lock is won by holding the wand.
 *
 * <p>Both casters fire through {@code /wandb magic spell cast} in the same tick, dispatched from the
 * server console as {@code /execute as <caster> at @s run …} — the way the command is meant to be used —
 * so the command is covered along with the clash. From the console on purpose: the cast subcommand is
 * admin-only, and with {@code adminUuids} configured (it is, by default) an operator permission level is
 * not enough for a player, while the console always qualifies.
 *
 * <p><b>Four and a half steps apart, on purpose.</b> A bolt moves its whole step at once: a novice's
 * Stupefy flies 1.8 &times; 0.82 = 1.476 blocks a tick. With the casters 6.64 blocks apart, every gap there
 * ever is between the two bolts — after either one moves, in any tick — is at least 0.74 blocks, well
 * outside the 0.4 clash radius. A check on where the bolts are sees them pass straight through each
 * other; only a check along both paths catches the meeting.
 *
 * <p><b>Fired empty-handed, on purpose.</b> A wand brings a misfire roll with it — its wood, flexibility and
 * allegiance can all add fizzle — and a test that fails one run in some dozens teaches nothing. With no wand
 * every misfire source is zero and the cast is otherwise the real one. A caster who needs to hold the lock is
 * handed a wand after the bolts are away.
 *
 * <p><b>Up in the air, each scenario at its own height, on purpose.</b> Neighbouring tests sit five or six
 * blocks away, closer than a lane is long, and each is caged in barriers. Rows are seven blocks apart, which
 * would put one lane's far caster 0.36 blocks from the next lane's near one — close enough for their bolts
 * to lock with each other. Three blocks of height between lanes keeps every scenario's bolts its own.
 *
 * <p><b>Every chunk under a lane is force-loaded.</b> Vanilla force-loads only the chunks under a test's
 * structure — a single one for {@code minecraft:empty} — and a bolt in any other chunk is simply not
 * ticked: it hangs in the air where it crossed the boundary. See {@link Duel#loadTheLane}.
 */
public final class SpellClashTests {

    private static final String SPELL_ID = "stupefy";
    /** See the class comment: four and a half novice Stupefy steps. */
    private static final double LANE_LENGTH = 6.64;

    private static final Lane LOCK_LANE = new Lane(8);
    private static final Lane HELD_LANE = new Lane(11);
    private static final Lane BREAK_LANE = new Lane(14);
    private static final Lane RELEASE_LANE = new Lane(17);

    /** The bolts meet on the second or third tick; the lock then waits out its grace. */
    private static final int SETTLE_TICKS = 6;
    /** Even casters meet in the middle; this is room for one bolt setting off a tick's step ahead. */
    private static final double MIDPOINT_TOLERANCE = 1.0;
    /** Past the grace, long enough for a winner's bolt to cross from the joint to the loser. */
    private static final int LANDING_TICKS = 10;
    /** Ticks a held lock is watched before a caster lets go of it. */
    private static final int HELD_TICKS = 5;

    private SpellClashTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("spell_clash_opposed_bolts_lock", "spell clash: two bolts fired at each other lock",
                SpellClashTests::opposedBoltsLock);
        tests.add("spell_clash_held_side_wins", "spell clash: the caster still holding wins",
                SpellClashTests::heldSideWins);
        tests.add("spell_clash_both_let_go_breaks", "spell clash: nobody holding breaks the lock",
                SpellClashTests::bothLetGoBreaks);
        tests.add("spell_clash_hold_release_casts_nothing", "spell clash: letting go of a lock casts nothing",
                SpellClashTests::holdReleaseCastsNothing);
    }

    // ── A: the bolts lock ───────────────────────────────────────────────────────────────────────

    private static void opposedBoltsLock(GameTestHelper helper) {
        Duel duel = Duel.set(helper, LOCK_LANE, "wandb-clash");

        helper.startSequence()
                .thenWaitUntil(duel::checkLaneTicks)
                .thenExecute(duel::fire)
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    List<SpellClashEntity> clashes = duel.clashes();
                    List<SpellProjectileEntity> left = duel.bolts();
                    check(helper, clashes.size() == 1,
                            () -> "expected exactly one clash, found " + clashes.size() + "; " + duel.describe());
                    check(helper, left.isEmpty(), () -> "the clash left bolts flying: " + duel.describe());
                    double midZ = (helper.absoluteVec(LOCK_LANE.casterA()).z
                            + helper.absoluteVec(LOCK_LANE.casterB()).z) / 2.0;
                    Vec3 at = clashes.getFirst().position();
                    check(helper, Math.abs(at.z - midZ) <= MIDPOINT_TOLERANCE,
                            () -> "the clash locked at z=" + at.z + ", expected within " + MIDPOINT_TOLERANCE
                                    + " of the midpoint z=" + midZ);
                })
                .thenExecute(duel::end)
                .thenSucceed();
    }

    // ── B: the one holding wins ─────────────────────────────────────────────────────────────────

    private static void heldSideWins(GameTestHelper helper) {
        Duel duel = Duel.set(helper, HELD_LANE, "wandb-held");

        helper.startSequence()
                .thenWaitUntil(duel::checkLaneTicks)
                .thenExecute(duel::fire)
                .thenWaitUntil(duel::checkLocked)
                .thenExecute(() -> duel.hold(duel.a()))
                .thenIdle(SpellClashRules.HOLD_GRACE_TICKS / 2)
                .thenExecute(() -> {
                    check(helper, duel.clashes().size() == 1,
                            () -> "the lock was decided inside the grace, before B had to pick it up: " + duel.describe());
                    check(helper, !stupefied(duel.a()) && !stupefied(duel.b()),
                            () -> "somebody was hit inside the grace: " + duel.describe());
                })
                .thenWaitUntil(() -> check(helper, stupefied(duel.b()),
                        () -> "B never picked the lock up and was never hit by A's spell: " + duel.describe()))
                .thenExecute(() -> {
                    check(helper, !stupefied(duel.a()), () -> "the winner was hit too: " + duel.describe());
                    check(helper, duel.clashes().isEmpty(), () -> "the lock outlived its result: " + duel.describe());
                })
                .thenExecute(duel::end)
                .thenSucceed();
    }

    // ── C: nobody holding breaks it ─────────────────────────────────────────────────────────────

    private static void bothLetGoBreaks(GameTestHelper helper) {
        Duel duel = Duel.set(helper, BREAK_LANE, "wandb-break");

        helper.startSequence()
                .thenWaitUntil(duel::checkLaneTicks)
                .thenExecute(duel::fire)
                .thenWaitUntil(duel::checkLocked)
                .thenIdle(SpellClashRules.HOLD_GRACE_TICKS + LANDING_TICKS)
                .thenExecute(() -> {
                    check(helper, duel.clashes().isEmpty(),
                            () -> "nobody held the lock and it is still there after the grace: " + duel.describe());
                    check(helper, duel.bolts().isEmpty(),
                            () -> "a lock nobody won fired a bolt: " + duel.describe());
                    check(helper, !stupefied(duel.a()) && !stupefied(duel.b()),
                            () -> "a lock nobody won hit somebody: " + duel.describe());
                })
                .thenExecute(duel::end)
                .thenSucceed();
    }

    // ── D: the hold feeds the lock, and letting go casts nothing ────────────────────────────────

    private static void holdReleaseCastsNothing(GameTestHelper helper) {
        Duel duel = Duel.set(helper, RELEASE_LANE, "wandb-release");
        // A release outside a lock would cast this, so a bolt from A afterwards means the gate let it through.
        WizardTestSupport.learnAndSelect(helper, duel.a(), SPELL_ID, null);

        helper.startSequence()
                .thenWaitUntil(duel::checkLaneTicks)
                .thenExecute(() -> {
                    // By name, as a person would type it: a players() argument refuses a UUID, which vanilla
                    // parses as a selector that may match non-players.
                    List<String> said = duel.console("wandb magic spell clash hold " + duel.b().getScoreboardName() + " true");
                    check(helper, said.stream().anyMatch(line -> line.startsWith("Pinned")),
                            () -> "pinning B's hold said " + said);
                    duel.fire();
                })
                .thenWaitUntil(duel::checkLocked)
                .thenExecute(() -> duel.hold(duel.a()))
                .thenIdle(HELD_TICKS)
                .thenExecute(() -> {
                    check(helper, duel.clashes().size() == 1,
                            () -> "the lock broke while A held and B was pinned: " + duel.describe());

                    // The client's own order: the vanilla release, then the mod's release packet.
                    duel.a().releaseUsingItem();
                    SpellCastC2SPayload.completeWandCastRelease(duel.a());

                    List<SpellProjectileEntity> fromA = duel.bolts().stream()
                            .filter(bolt -> duel.a().getUUID().equals(bolt.getCasterUuid()))
                            .toList();
                    check(helper, fromA.isEmpty(),
                            () -> "letting go of the lock cast " + fromA.size() + " bolt(s) from A: " + duel.describe());
                })
                .thenWaitUntil(() -> check(helper, stupefied(duel.a()),
                        () -> "A let go of the lock and was never hit by B's spell: " + duel.describe()))
                .thenExecute(() -> {
                    check(helper, !stupefied(duel.b()), () -> "the pinned winner was hit too: " + duel.describe());
                    duel.console("wandb magic spell clash hold " + duel.b().getScoreboardName() + " false");
                })
                .thenExecute(duel::end)
                .thenSucceed();
    }

    // ── shared setup ────────────────────────────────────────────────────────────────────────────

    private static boolean stupefied(ServerPlayer player) {
        return player.hasEffect(ModEffects.STUPEFY);
    }

    /** One scenario's firing line: A looks along +Z at B, B looks back along -Z at A. */
    private record Lane(int y) {
        Vec3 casterA() {
            return new Vec3(0.5, y, 0.5);
        }

        Vec3 casterB() {
            return new Vec3(0.5, y, 0.5 + LANE_LENGTH);
        }
    }

    /** Two casters on a lane, and everything a scenario does with them. */
    private record Duel(GameTestHelper helper, Lane lane, ServerPlayer a, ServerPlayer b, AABB box) {

        static Duel set(GameTestHelper helper, Lane lane, String name) {
            clearTheLane(helper, lane);
            loadTheLane(helper, lane);
            ServerPlayer a = readyCaster(helper, name + "-a", lane.casterA(), 0.0f);
            ServerPlayer b = readyCaster(helper, name + "-b", lane.casterB(), 180.0f);
            AABB box = new AABB(helper.absoluteVec(lane.casterA()), helper.absoluteVec(lane.casterB())).inflate(2.0);
            return new Duel(helper, lane, a, b, box);
        }

        /** Air along the whole flight path, in case anything was ever built up here. */
        private static void clearTheLane(GameTestHelper helper, Lane lane) {
            for (int z = 0; z <= 7; z++) {
                for (int y = lane.y(); y <= lane.y() + 2; y++) {
                    helper.setBlock(new BlockPos(0, y, z), Blocks.AIR);
                }
            }
        }

        /**
         * Force-loads every chunk between the two casters. The lane is 6.64 blocks long and a test lands at
         * an arbitrary spot inside its chunk, so it crosses a chunk boundary more often than not. The runner
         * releases every forced chunk when the batch ends, exactly as it does the test's own.
         */
        private static void loadTheLane(GameTestHelper helper, Lane lane) {
            ServerLevel level = helper.getLevel();
            ChunkPos.rangeClosed(
                            new ChunkPos(BlockPos.containing(helper.absoluteVec(lane.casterA()))),
                            new ChunkPos(BlockPos.containing(helper.absoluteVec(lane.casterB()))))
                    .forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, true));
        }

        private static ServerPlayer readyCaster(GameTestHelper helper, String name, Vec3 at, float yaw) {
            ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, name);
            WizardTestSupport.makeWandkind(caster);
            Vec3 feet = helper.absoluteVec(at);
            caster.snapTo(feet.x, feet.y, feet.z, yaw, 0.0f);
            caster.setYHeadRot(yaw);
            caster.setNoGravity(true);
            return caster;
        }

        /** A forced chunk starts ticking entities once its ticket propagates, which is not immediate. */
        void checkLaneTicks() {
            ServerLevel level = helper.getLevel();
            check(helper, level.isPositionEntityTicking(BlockPos.containing(helper.absoluteVec(lane.casterA())))
                            && level.isPositionEntityTicking(BlockPos.containing(helper.absoluteVec(lane.casterB()))),
                    () -> "the chunks under the lane never started ticking entities");
        }

        void checkLocked() {
            check(helper, clashes().size() == 1, () -> "the bolts never locked: " + describe());
        }

        /** Both casters fire in the same tick, through the cast command, from the console. */
        void fire() {
            List<String> saidA = castAs(a);
            List<String> saidB = castAs(b);
            int fired = bolts().size();
            check(helper, fired == 2,
                    () -> "the cast command fired " + fired + " bolts, expected one from each caster;"
                            + " A at " + a.position() + " said " + saidA
                            + ", B at " + b.position() + " said " + saidB
                            + ", bolts within 32 blocks: " + describe(bolts(box.inflate(32.0))));
        }

        private List<String> castAs(ServerPlayer caster) {
            return console("execute as " + caster.getStringUUID() + " at @s run wandb magic spell cast " + SPELL_ID);
        }

        /** Hands the caster a bonded wand and holds it, through the server's own entry for a use-item packet. */
        void hold(ServerPlayer caster) {
            ItemStack wand = WizardTestSupport.giveBondedWand(caster);
            caster.gameMode.useItem(caster, helper.getLevel(), wand, InteractionHand.MAIN_HAND);
            check(helper, caster.isUsingItem(), () -> "holding the wand did not start a use: " + describe());
        }

        /**
         * Runs a command from the server console.
         *
         * @return everything the command said back
         */
        List<String> console(String command) {
            List<String> said = new ArrayList<>();
            CommandSource listener = new CommandSource() {
                @Override
                public void sendSystemMessage(Component message) {
                    said.add(message.getString());
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            };
            CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withSource(listener);
            helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
            return said;
        }

        List<SpellProjectileEntity> bolts() {
            return bolts(box);
        }

        private List<SpellProjectileEntity> bolts(AABB area) {
            return helper.getLevel().getEntitiesOfClass(SpellProjectileEntity.class, area);
        }

        List<SpellClashEntity> clashes() {
            return helper.getLevel().getEntitiesOfClass(SpellClashEntity.class, box);
        }

        /** Takes the casters out of the player list and any lock out of the world. */
        void end() {
            clashes().forEach(SpellClashEntity::discard);
            WizardTestSupport.retire(helper, a);
            WizardTestSupport.retire(helper, b);
        }

        String describe() {
            StringBuilder out = new StringBuilder();
            out.append("A usingItem=").append(a.isUsingItem()).append(" stupefied=").append(stupefied(a))
                    .append(" | B usingItem=").append(b.isUsingItem()).append(" stupefied=").append(stupefied(b))
                    .append(" | clashes:");
            for (SpellClashEntity clash : clashes()) {
                out.append(" [at ").append(clash.position())
                        .append(" holdingA=").append(clash.isHoldingA())
                        .append(" holdingB=").append(clash.isHoldingB()).append(']');
            }
            return out.append(" | bolts: ").append(describe(bolts())).toString();
        }

        private static String describe(List<SpellProjectileEntity> bolts) {
            StringBuilder out = new StringBuilder("[");
            for (SpellProjectileEntity bolt : bolts) {
                out.append(' ').append(bolt.getSpellId()).append(" at ").append(bolt.position())
                        .append(" moving ").append(bolt.getDeltaMovement());
            }
            return out.append(" ]").toString();
        }
    }
}
