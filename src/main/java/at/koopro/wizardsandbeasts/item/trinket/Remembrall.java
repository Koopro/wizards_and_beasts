package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.ministry.law.MinistryFines;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * What a Remembrall can tell a player they have forgotten.
 *
 * <p>Canon: the smoke turns red when its holder has forgotten something, and it cannot say what (Neville,
 * <i>Philosopher's Stone</i> ch. 9). The mod gives it real things to notice, each one already tracked
 * elsewhere, so nothing new is stored and a "forgotten" thing stops counting the moment it is dealt with:
 * <ul>
 *   <li>{@link Forgotten#WHERE_YOU_FELL}: a death in the last five minutes, and the player has not been back
 *       within {@link #DEATH_RADIUS} blocks of it;</li>
 *   <li>{@link Forgotten#A_FINE}: a Ministry fine still outstanding;</li>
 *   <li>{@link Forgotten#LESSONS}: no longer raised (the skill web stopped owing lessons, 2026-11-27); kept so an old stack still reads.</li>
 * </ul>
 * The first one found wins; the order is how urgent each is.
 */
@NullMarked
public final class Remembrall {

    public static final int DEATH_RECENT_TICKS = 6000;
    public static final double DEATH_RADIUS = 8.0;

    public enum Forgotten { WHERE_YOU_FELL, A_FINE, LESSONS }

    private Remembrall() {}

    public static @Nullable Forgotten check(ServerPlayer player) {
        if (recentDeathElsewhere(player)) {
            return Forgotten.WHERE_YOU_FELL;
        }
        if (MinistryFines.owed(player) > 0) {
            return Forgotten.A_FINE;
        }
        return null;
    }

    private static boolean recentDeathElsewhere(ServerPlayer player) {
        int sinceDeath = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        if (sinceDeath >= DEATH_RECENT_TICKS) {
            return false;
        }
        Optional<GlobalPos> spot = player.getLastDeathLocation();
        if (spot.isEmpty()) {
            return false;
        }
        GlobalPos pos = spot.get();
        return pos.dimension() != player.level().dimension()
                || pos.pos().getCenter().distanceTo(player.position()) > DEATH_RADIUS;
    }
}
