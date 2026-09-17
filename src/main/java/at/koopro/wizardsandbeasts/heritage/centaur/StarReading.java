package at.koopro.wizardsandbeasts.heritage.centaur;

import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading the stars, as centaurs do.
 *
 * <p>Canon is careful about what this is. Firenze tells Harry that centaurs read what is <em>foretold</em> —
 * "Mars is bright tonight" — and is equally clear that they refuse to be fortune-tellers on demand; they watch for
 * years and speak in what they have seen. So this gives no prophecy and no quest marker. It states what the sky over
 * this world genuinely says right now, before it becomes obvious to anyone else:
 *
 * <ul>
 *   <li>the moon's phase, and how many nights until it is full — which is the werewolf calendar, read from the same
 *       clock {@link WerewolfRules} transforms on;</li>
 *   <li>whether tonight is that night;</li>
 *   <li>whether a storm is already gathering overhead.</li>
 * </ul>
 *
 * <p>All of it is true information the world already holds, which is the whole point: a centaur's advantage is that
 * they looked up.
 *
 * <p><b>Conditions to read at all</b>: the stars have to be visible. Night, open sky, and a sky to begin with — no
 * reading in the Nether, and none at noon. A trait nobody can satisfy indoors is a trait that means something.
 */
@NullMarked
public final class StarReading {

    /** Nothing to read: why not, as a presentable line. */
    public sealed interface Reading {

        /** The sky was not readable. */
        record Refused(Component reason) implements Reading {}

        /** What the sky says, line by line. */
        record Seen(List<Component> lines) implements Reading {}
    }

    private static final String KEY = "heritage.wizards_and_beasts.centaur.stars.";

    private StarReading() {}

    /** Whether this character reads the stars at all. */
    public static boolean canRead(ServerPlayer player) {
        return HeritageAPI.getData(player).hasTrait("star_reading");
    }

    /**
     * What the sky over {@code player} says, or why it cannot be read.
     *
     * <p>Pure with respect to the world: it looks and reports, changing nothing. That is deliberate — it makes the
     * reading safe to run from a command, an ability or a test without a lifecycle to think about.
     */
    public static Reading read(ServerPlayer player) {
        if (!canRead(player)) {
            return new Reading.Refused(Component.translatable(KEY + "not_a_reader"));
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return new Reading.Refused(Component.translatable(KEY + "no_sky"));
        }
        var dimension = level.dimensionType();
        if (!dimension.hasSkyLight() || dimension.hasFixedTime()) {
            return new Reading.Refused(Component.translatable(KEY + "no_sky"));
        }
        long dayTime = level.getDayTime();
        if (!WerewolfRules.isNight(dayTime)) {
            return new Reading.Refused(Component.translatable(KEY + "daylight"));
        }
        if (!level.canSeeSky(player.blockPosition())) {
            return new Reading.Refused(Component.translatable(KEY + "roofed"));
        }

        return new Reading.Seen(describe(dayTime, level.isRaining(), level.isThundering()));
    }

    /**
     * What the sky says, as lines, from the facts alone.
     *
     * <p>Pure, and split out for that reason: the moon arithmetic — which night is full, how many nights until the
     * next one — is the part that can be silently wrong, and it needs no level, no player and no clock to check.
     */
    public static List<Component> describe(long dayTime, boolean raining, boolean thundering) {
        List<Component> lines = new ArrayList<>();
        int phase = WerewolfRules.moonPhase(dayTime);
        lines.add(Component.translatable(KEY + "phase", Component.translatable(KEY + "phase." + phase)));

        if (phase == WerewolfRules.FULL_MOON) {
            lines.add(Component.translatable(KEY + "full_tonight"));
        } else {
            // Phases run 0..7 with the full moon at 0, so the wait is however many steps remain to wrap around.
            lines.add(Component.translatable(KEY + "until_full", 8 - phase));
        }

        if (thundering) {
            lines.add(Component.translatable(KEY + "thunder"));
        } else if (raining) {
            lines.add(Component.translatable(KEY + "rain"));
        } else {
            lines.add(Component.translatable(KEY + "clear"));
        }
        return List.copyOf(lines);
    }

    /** How many nights until the moon is full, counting tonight as zero. Public so a test can pin the calendar. */
    public static int nightsUntilFull(long dayTime) {
        int phase = WerewolfRules.moonPhase(dayTime);
        return phase == WerewolfRules.FULL_MOON ? 0 : 8 - phase;
    }
}
