package at.koopro.wizardsandbeasts.stats.debug;

import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Stats high enough that they are not what is failing.
 *
 * <p>Most gates in the mod read a stat somewhere — a spell that needs Power, a brew whose failure
 * chance is scored against skill. When you are testing something else, a low stat is a false
 * negative that costs half an hour. So the trainable four go to a high value and stay there.
 *
 * <p>Not to the cap, and not through the training path. {@link PlayerStatsAPI#setStat} is the
 * admin write and respects the same clamps the rest of the system does; pushing through
 * {@code addTrainingProgress} instead would take the scenic route and land somewhere unpredictable.
 * {@code KNOWLEDGE} is skipped because it is derived — writing it would be overwritten by the next
 * recompute, which is a confusing thing for a dev command to do.
 */
@NullMarked
public final class StatsDevKit implements FeatureDevKit {

    /** High enough to clear every gate the mod checks, low enough to still show a cap being hit. */
    private static final int DEV_VALUE = 80;
    /** What {@code reset} puts them back to — the value a fresh character starts around. */
    private static final int BASELINE = 10;

    @Override
    public String id() {
        return "stats";
    }

    @Override
    public String title() {
        return "Player Stats";
    }

    @Override
    public String summary() {
        return "Raise the four trainable stats to " + DEV_VALUE + " so they are never the blocker.";
    }

    @Override
    public void open(ServerPlayer target, DevLog log) {
        writeAll(target, log, DEV_VALUE);
    }

    @Override
    public void reset(ServerPlayer target, DevLog log) {
        writeAll(target, log, BASELINE);
    }

    private static void writeAll(ServerPlayer target, DevLog log, int value) {
        int written = 0;
        for (PlayerStat stat : PlayerStat.values()) {
            if (stat.isDerived()) {
                continue;
            }
            PlayerStatsAPI.setStat(target, stat, value);
            written++;
        }
        log.changed(written + " stats set", value);
        // A stat that reads back lower than what was just written is the power cap doing its job,
        // and saying so beats leaving a developer to wonder why Power stopped short.
        int power = PlayerStatsAPI.getStat(target, PlayerStat.POWER);
        if (power < value) {
            log.skip("POWER clamped to " + power + " by the cap ("
                    + PlayerStatsAPI.getPowerCap(target) + ")");
        }
    }
}
