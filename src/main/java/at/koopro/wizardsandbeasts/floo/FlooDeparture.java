package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * The seconds between naming a destination and leaving the grate.
 *
 * <h2>Why the hop is not instant</h2>
 * <p>Pressing Travel used to teleport on the same tick the packet arrived, and that single fact was
 * what made the whole system read as a warp menu with green particles: there was no moment at which
 * the player was <em>going</em>, only a before and an after. A windup is the cheapest thing that
 * turns a menu choice back into a ritual — the fire roars, the traveller is held in it, and the world
 * has a couple of seconds in which something can still go wrong.
 *
 * <h2>What can still go wrong</h2>
 * <p>Three things end a departure short, and they are all things a player can see:
 * <ul>
 *   <li><b>The fire goes out.</b> Someone throws water, the last charge burns down, the hearth is
 *       broken. Checked every tick against the origin, because that is the whole point of a window in
 *       which the origin still matters.</li>
 *   <li><b>The traveller walks out.</b> Leaving the hearth cancels it. Standing in the fire is the
 *       commitment, so stepping out of it is the withdrawal.</li>
 *   <li><b>The player disconnects.</b> Handled by {@link PlayerScopedState}'s own logout cleanup, so
 *       a pending departure cannot outlive the session that started it and fire into a world where
 *       the player is somewhere else entirely.</li>
 * </ul>
 *
 * <h2>What is not paid until the end</h2>
 * <p>No powder is spent and no charge is consumed while a departure is pending — both happen in
 * {@link FlooTravelHandler#commitTravel}, at the far side of the window. A cancelled departure
 * therefore costs exactly nothing, which is what makes cancelling it a safe thing to do rather than a
 * punishment for changing your mind. It also means the powder check at the start is advisory: a
 * player who throws their last pinch away mid-windup is refused at the commit, and that is correct.
 */
@NullMarked
public final class FlooDeparture {

    /** Windup used when nothing has configured one. Two seconds. */
    public static final int DEFAULT_WINDUP_TICKS = 40;

    private static final PlayerScopedState<Pending> PENDING =
            PlayerScopedState.create("floo_departure");

    private FlooDeparture() {
    }

    /**
     * A hop in flight.
     *
     * @param originPos     the hearth being left, so the tick can tell whether it is still burning
     * @param actualAddress where the traveller is really going — the misfire, if there was one, has
     *                      already been rolled and this is its result
     * @param intended      what the traveller asked for. Carried the whole way so the arrival toast
     *                      can say "you meant X"; identical to {@code actualAddress} on a clean hop
     * @param ticksLeft     counted down rather than compared against a deadline, so a server that
     *                      stalls does not swallow the whole windup on the tick it recovers
     */
    private record Pending(BlockPos originPos, String actualAddress, String intended, int ticksLeft) {
        Pending tick() {
            return new Pending(originPos, actualAddress, intended, ticksLeft - 1);
        }
    }

    public static int windupTicks() {
        int configured = Config.flooDepartureWindupTicks;
        return configured >= 0 ? configured : DEFAULT_WINDUP_TICKS;
    }

    public static boolean isDeparting(UUID playerId) {
        return PENDING.contains(playerId);
    }

    /**
     * Begin a departure, or perform it at once when the windup is configured to nothing.
     *
     * <p>The zero case is handled here rather than by letting a zero-tick entry fall through the tick
     * loop, because an entry that lives for part of a tick is still an entry: it would be visible to
     * {@link #isDeparting}, and a second Travel press in the same tick would be refused for a
     * departure that had not started.
     */
    public static void begin(ServerPlayer player, BlockPos originPos, String actualAddress,
                             String intended) {
        int windup = windupTicks();
        if (windup <= 0) {
            FlooTravelHandler.commitTravel(player, originPos, actualAddress, intended);
            return;
        }
        PENDING.put(player.getUUID(), new Pending(originPos, actualAddress, intended, windup));
        player.level().playSound(null, originPos, ModSounds.FLOO_IGNITE.get(),
                SoundSource.BLOCKS, 0.8f, 1.3f);
        // The traveller says where they are going, not where they are actually about to end up. A
        // misfire that announced itself here would stop being a misfire.
        PlayerFeedback.actionBar(player,
                Component.translatable("floo.wizards_and_beasts.depart.speaking", intended));
    }

    /**
     * Drop a pending departure with no message, no sound and no particles.
     *
     * <p>For the cases where the player is in no state to be told: death, and logout. A sputter at a
     * respawn point describes a fireplace that is not there.
     */
    public static void cancelSilently(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    /** Abandon a pending departure without travelling, telling the player why. */
    public static void cancel(ServerPlayer player, @Nullable Component reason) {
        if (PENDING.remove(player.getUUID()) == null) {
            return;
        }
        if (reason != null) {
            PlayerFeedback.actionBar(player, reason);
        }
        if (player.level() instanceof ServerLevel level) {
            FlooCues.sputter(level, player.blockPosition());
        }
    }

    /**
     * Cancel any departure leaving from {@code hearthPos}.
     *
     * <p>Called when a hearth is put out or broken. Scans the pending map rather than indexing by
     * position, because a hearth can be the origin for several travellers at once — three people can
     * leave one hearth on one pinch of powder — and dousing it has to end all of their journeys, not
     * whichever one happened to be stored last.
     */
    public static void cancelAllFrom(ServerLevel level, BlockPos hearthPos) {
        for (Map.Entry<UUID, Pending> entry : Map.copyOf(PENDING.view()).entrySet()) {
            if (!entry.getValue().originPos().equals(hearthPos)) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                PENDING.remove(entry.getKey());
                continue;
            }
            cancel(player, Component.translatable("floo.wizards_and_beasts.depart.fire_died"));
        }
    }

    /** One tick of a traveller's windup: the checks, the flare, and eventually the hop. */
    public static void tick(ServerPlayer player) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (!originStillBurning(level, pending.originPos())) {
            cancel(player, Component.translatable("floo.wizards_and_beasts.depart.fire_died"));
            return;
        }
        if (!stillInTheFire(player, pending.originPos())) {
            cancel(player, Component.translatable("floo.wizards_and_beasts.depart.stepped_out"));
            return;
        }
        // Climbing onto something during the window is as much a withdrawal as walking out of the
        // fire. beginTravel refuses a mounted player, so without this the one way to be halfway
        // through a hop while riding a broom would be to get on one after pressing Travel.
        if (player.isPassenger()) {
            cancel(player, Component.translatable("floo.wizards_and_beasts.fail.riding"));
            return;
        }

        Pending next = pending.tick();
        if (next.ticksLeft() > 0) {
            PENDING.put(player.getUUID(), next);
            spinUp(level, player, next.ticksLeft());
            return;
        }

        // Removed before the hop, not after: commitTravel teleports, and an entry still in the map at
        // that moment would be ticked once more in the destination dimension, against an origin that
        // is no longer anywhere near the player.
        PENDING.remove(player.getUUID());
        FlooTravelHandler.commitTravel(player, pending.originPos(),
                pending.actualAddress(), pending.intended());
    }

    /**
     * Whether the hearth being left is still a lit Floo hearth.
     *
     * <p>Reads the block, not the block entity's flag: a hearth that has been mined outright has no
     * block entity to ask, and {@code getBlockEntity} on air answers null in a way that cannot be told
     * apart from a chunk that unloaded.
     */
    private static boolean originStillBurning(ServerLevel level, BlockPos hearthPos) {
        BlockState state = level.getBlockState(hearthPos);
        return state.is(ModBlocks.FLOO_FIREPLACE.get())
                && state.hasProperty(FlooFireplaceBlock.LIT)
                && state.getValue(FlooFireplaceBlock.LIT);
    }

    /**
     * Whether the traveller is still standing in their own departure.
     *
     * <p>Measured against the hearth and its flames rather than a radius around where the player
     * started, so a traveller who shuffles between the flame block and the hearth's own opening —
     * a normal thing to do while waiting — is not thrown out of their own journey for it.
     */
    private static boolean stillInTheFire(ServerPlayer player, BlockPos hearthPos) {
        BlockPos feet = player.blockPosition();
        if (feet.distSqr(hearthPos) <= 2.0) {
            return true;
        }
        BlockState hearth = player.level().getBlockState(hearthPos);
        if (!hearth.hasProperty(FlooFireplaceBlock.FACING)) {
            return false;
        }
        return feet.equals(FlooFlamePlacement.flamePos(hearthPos, hearth.getValue(FlooFireplaceBlock.FACING)));
    }

    /**
     * The fire building under a traveller who has named where they are going.
     *
     * <p>Intensity rises as the window closes, so the moment of departure is legible from outside as
     * well as in: a bystander sees the grate roar up before someone vanishes out of it.
     */
    private static void spinUp(ServerLevel level, ServerPlayer player, int ticksLeft) {
        int windup = Math.max(1, windupTicks());
        float progress = 1.0f - (float) ticksLeft / windup;

        FlooCues.swirl(level, player.getX(), player.getY(), player.getZ(), progress);

        if (ticksLeft % 6 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(), player.getY() + 0.2, player.getZ(),
                    3, 0.25, 0.3, 0.25, 0.04);
        }
        // The loop is re-triggered rather than held: a genuinely looping SoundInstance would have to
        // be client-side and would need starting and stopping packets to survive a cancelled
        // departure. Re-firing a short sample on an interval that is shorter than the sample gives
        // the same continuous rush and cannot outlive the thing that is playing it.
        if (ticksLeft % 8 == 0) {
            FlooCues.travelLoop(level, player.blockPosition(), progress);
        }
    }

    /** Drop every pending departure. Used on shutdown, the way live calls are. */
    public static void clearAll() {
        PENDING.clear();
    }
}
