package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.entity.spell.SpellClashEntity;
import at.koopro.wizardsandbeasts.entity.spell.SpellProjectileEntity;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * Two bolts fired at each other lock into a clash instead of flying through each other.
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
 * <p><b>Empty-handed, on purpose.</b> A wand brings a misfire roll with it — a phoenix feather core has a
 * fizzle chance, and wand allegiance adds more — and a test that fails one run in some dozens teaches
 * nothing. With no wand every misfire source is zero and the cast is otherwise the real one.
 *
 * <p><b>Up in the air, on purpose.</b> Neighbouring tests sit five or six blocks away, closer than this
 * lane is long, and each is caged in barriers. At {@link #LANE_Y} the bolts fly over all of it.
 *
 * <p><b>Every chunk under the lane is force-loaded.</b> Vanilla force-loads only the chunks under a test's
 * structure — a single one for {@code minecraft:empty} — and a bolt in any other chunk is simply not
 * ticked: it hangs in the air where it crossed the boundary. See {@link #loadTheLane}.
 */
public final class SpellClashTests {

    private static final String SPELL_ID = "stupefy";

    private static final int LANE_Y = 8;
    /** A's feet, looking along +Z at B. */
    private static final Vec3 CASTER_A = new Vec3(0.5, LANE_Y, 0.5);
    /** B's feet, 6.64 blocks down the lane, looking back along -Z at A. See the class comment. */
    private static final Vec3 CASTER_B = new Vec3(0.5, LANE_Y, 0.5 + 6.64);

    /** The bolts meet on the second or third tick; a clash then holds for at least thirty. */
    private static final int SETTLE_TICKS = 6;
    /** Even casters meet in the middle; this is room for one bolt setting off a tick's step ahead. */
    private static final double MIDPOINT_TOLERANCE = 1.0;

    private SpellClashTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("spell_clash_opposed_bolts_lock", "spell clash: two bolts fired at each other lock",
                SpellClashTests::opposedBoltsLock);
    }

    private static void opposedBoltsLock(GameTestHelper helper) {
        clearTheLane(helper);
        ServerPlayer casterA = readyCaster(helper, "wandb-clash-a", CASTER_A, 0.0f);
        ServerPlayer casterB = readyCaster(helper, "wandb-clash-b", CASTER_B, 180.0f);
        AABB lane = new AABB(helper.absoluteVec(CASTER_A), helper.absoluteVec(CASTER_B)).inflate(2.0);
        loadTheLane(helper);

        helper.startSequence()
                .thenWaitUntil(() -> check(helper, laneTicks(helper),
                        () -> "the chunks under the lane never started ticking entities"))
                .thenExecute(() -> {
                    List<String> saidA = cast(helper, casterA);
                    List<String> saidB = cast(helper, casterB);
                    int fired = bolts(helper, lane).size();
                    check(helper, fired == 2,
                            () -> "the cast command fired " + fired + " bolts, expected one from each caster;"
                                    + " A at " + casterA.position() + " said " + saidA
                                    + ", B at " + casterB.position() + " said " + saidB
                                    + ", bolts within 32 blocks: "
                                    + describe(bolts(helper, lane.inflate(32.0))));
                })
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    List<SpellClashEntity> clashes = clashes(helper, lane);
                    List<SpellProjectileEntity> left = bolts(helper, lane);
                    check(helper, clashes.size() == 1,
                            () -> "expected exactly one clash, found " + clashes.size()
                                    + "; bolts still flying: " + describe(left));
                    check(helper, left.isEmpty(),
                            () -> "the clash left bolts flying: " + describe(left));
                    double midZ = (helper.absoluteVec(CASTER_A).z + helper.absoluteVec(CASTER_B).z) / 2.0;
                    Vec3 at = clashes.getFirst().position();
                    check(helper, Math.abs(at.z - midZ) <= MIDPOINT_TOLERANCE,
                            () -> "the clash locked at z=" + at.z + ", expected within " + MIDPOINT_TOLERANCE
                                    + " of the midpoint z=" + midZ);
                })
                .thenExecute(() -> {
                    clashes(helper, lane).forEach(SpellClashEntity::discard);
                    retire(helper, casterA);
                    retire(helper, casterB);
                })
                .thenSucceed();
    }

    // ── shared setup ────────────────────────────────────────────────────────────────────────────

    /** Air along the whole flight path, in case anything was ever built up here. */
    private static void clearTheLane(GameTestHelper helper) {
        for (int z = 0; z <= 7; z++) {
            for (int y = LANE_Y; y <= LANE_Y + 2; y++) {
                helper.setBlock(new BlockPos(0, y, z), Blocks.AIR);
            }
        }
    }

    /**
     * Force-loads every chunk between the two casters. The lane is 6.64 blocks long and a test lands at an
     * arbitrary spot inside its chunk, so it crosses a chunk boundary more often than not. The runner
     * releases every forced chunk when the batch ends, exactly as it does the test's own.
     */
    private static void loadTheLane(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos.rangeClosed(
                        new ChunkPos(BlockPos.containing(helper.absoluteVec(CASTER_A))),
                        new ChunkPos(BlockPos.containing(helper.absoluteVec(CASTER_B))))
                .forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, true));
    }

    /** A forced chunk starts ticking entities once its ticket propagates, which is not immediate. */
    private static boolean laneTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return level.isPositionEntityTicking(BlockPos.containing(helper.absoluteVec(CASTER_A)))
                && level.isPositionEntityTicking(BlockPos.containing(helper.absoluteVec(CASTER_B)));
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

    /**
     * Runs the cast as the caster from the server console.
     *
     * @return everything the command said back
     */
    private static List<String> cast(GameTestHelper helper, ServerPlayer caster) {
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
        CommandSourceStack console = helper.getLevel().getServer().createCommandSourceStack()
                .withSource(listener);
        helper.getLevel().getServer().getCommands().performPrefixedCommand(console,
                "execute as " + caster.getStringUUID() + " at @s run wandb magic spell cast " + SPELL_ID);
        return said;
    }

    private static List<SpellProjectileEntity> bolts(GameTestHelper helper, AABB lane) {
        return helper.getLevel().getEntitiesOfClass(SpellProjectileEntity.class, lane);
    }

    private static List<SpellClashEntity> clashes(GameTestHelper helper, AABB lane) {
        return helper.getLevel().getEntitiesOfClass(SpellClashEntity.class, lane);
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
