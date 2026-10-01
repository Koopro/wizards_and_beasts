package at.koopro.wizardsandbeasts.entity.broom;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.broom.SnidgetFeather;
import at.koopro.wizardsandbeasts.skill.PlayerSkillBonusData;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * The server's check on how fast a ridden broom actually travels.
 *
 * <p>Broom flight is client-authoritative — the vanilla ridden-vehicle contract: the rider's client simulates the
 * flight and the server takes its positions. That makes the rider's client the one place speed is decided, and so
 * the one place it could be forged. This guard is the server's side of it: every tick it compares the distance the
 * broom really covered with the fastest the <em>server's</em> definition allows — top speed × boost, plus the rider's
 * broom-skill bonus and a Golden Snidget feather's, with a generous tolerance for packet timing — and a broom that
 * keeps going faster than that is stopped and its rider set down.
 *
 * <p>Strikes build up and decay, so a single late or bunched packet (which delivers two ticks' travel at once) never
 * trips it; only sustained excess does. Server thread only; off with {@code Config.broomSpeedGuard}.
 */
@NullMarked
public final class BroomSpeedGuard {

    /** How far past the ceiling a tick may go before it counts, for packet jitter. */
    static final float TOLERANCE = 1.5f;
    static final float SLACK = 0.3f;
    static final int STRIKE_LIMIT = 20;
    private static final Logger LOGGER = LogUtils.getLogger();

    private BroomSpeedGuard() {}

    /** The most a broom may cover in one tick horizontally, from the server's own numbers. */
    public static float ceiling(BroomEntity broom, ServerPlayer rider) {
        float cruise = broom.getCruiseSpeed() + PlayerSkillBonusData.forPlayer(rider).broomSpeedBonus();
        if (SnidgetFeather.isHeld(rider)) {
            cruise = SnidgetFeather.applySpeedBonus(cruise);
        }
        return cruise * Math.max(1.0f, broom.resolveDefinition().boostMultiplier()) * TOLERANCE + SLACK;
    }

    /** One observed tick of a ridden broom on the server. */
    static void observe(BroomEntity broom, double horizontalTravel) {
        if (!Config.broomSpeedGuard || !(broom.getControllingPassenger() instanceof ServerPlayer rider)) {
            broom.speedStrikes = 0;
            return;
        }
        if (horizontalTravel > ceiling(broom, rider)) {
            broom.speedStrikes += 2;
        } else if (broom.speedStrikes > 0) {
            broom.speedStrikes--;
        }
        if (broom.speedStrikes >= STRIKE_LIMIT) {
            broom.speedStrikes = 0;
            LOGGER.warn("[Broom] {} flew {} faster than the server allows ({} > {} blocks/tick); set down",
                    rider.getName().getString(), broom.resolveDefinition().id(),
                    String.format("%.2f", horizontalTravel), String.format("%.2f", ceiling(broom, rider)));
            rider.stopRiding();
            broom.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            rider.displayClientMessage(Component.translatable("broom.wizards_and_beasts.speed_guard"), true);
        }
    }
}
