package at.koopro.wizardsandbeasts.settlement.post;

import org.jspecify.annotations.NullMarked;

/**
 * What a settlement's post does about its person, as a rule. Pure, so every case is tested without a world.
 *
 * <p>The record says what is true, and an unloaded entity is not a dead one. A post never concludes that its person has
 * gone from not finding them once: death is reported by the entity ({@code SettlementEvents}), and a person is taken as
 * lost only after many looks with every chunk around the post's entities loaded. That is the difference from
 * {@code ChamberEncounter}, which forgets a boss the moment it cannot see it.
 */
@NullMarked
public final class PostRules {

    /** The post's record of its person. */
    public enum Status {
        /** No one has stood here yet. */
        VACANT,
        /** Someone should be here, loaded or not. */
        PRESENT,
        /** Apparated away, and due back at dawn ({@link #due}). */
        AWAY
    }

    /** What a look decides. */
    public enum Act { NOTHING, SPAWN, RETURN }

    /** Looks, one a second, with everything around the post loaded and nobody found, before they count as lost. */
    public static final int LOST_AFTER_LOOKS = 30;
    /** How far a person may be from their post before they are brought back. */
    public static final int STRAY_BLOCKS = 24;
    /** The shortest time away, however close dawn is: a blow at five to six is not undone by six. */
    public static final long MIN_AWAY_TICKS = 2400;
    /** The day time of an away record written before {@link #dawn} existed: such a record waits on game time alone. */
    public static final long NO_DAWN = Long.MAX_VALUE;
    private static final long DAY = 24000;

    private PostRules() {}

    /**
     * @param found      the person is loaded
     * @param distanceSq their distance from the post, squared; ignored when not found
     * @param watched    a player is near enough to see them jump
     * @param looks      looks in a row they have not been found
     * @param due        an absent person's time has come ({@link #due}); ignored unless away
     */
    public static Act decide(Status status, boolean found, double distanceSq, boolean watched, int looks, boolean due) {
        return switch (status) {
            case VACANT -> Act.SPAWN;
            case AWAY -> due ? Act.SPAWN : Act.NOTHING;
            case PRESENT -> {
                if (found) {
                    yield distanceSq > (double) STRAY_BLOCKS * STRAY_BLOCKS && !watched ? Act.RETURN : Act.NOTHING;
                }
                yield looks >= LOST_AFTER_LOOKS ? Act.SPAWN : Act.NOTHING;
            }
        };
    }

    /**
     * The day time of the dawn someone who Apparated away at {@code dayTime} returns at: the next dawn, and never at once.
     * Day time, not game time, because a night slept through or a {@code /time add} moves only day time, and a server can
     * run a day at a speed of its own ({@code /neoforge day}); game time knows nothing of either.
     */
    public static long dawn(long dayTime) {
        return dayTime + awayFor(dayTime);
    }

    /**
     * The game time by which they are back however the clock has been set: the same moment as {@link #dawn} while the day
     * runs as it did at the blow. It is the backstop for a clock turned back ({@code /time set} writes day time outright),
     * which would otherwise keep them away for as long as the clock was turned back, and for a stopped clock.
     *
     * @param dayTimePerTick day time per game tick ({@code ServerLevel.getDayTimePerTick}); negative is the vanilla 1
     */
    public static long backAt(long gameTime, long dayTime, float dayTimePerTick) {
        double rate = dayTimePerTick > 0f ? dayTimePerTick : 1.0;
        return gameTime + (long) Math.ceil(awayFor(dayTime) / rate);
    }

    /** Whether an absent person is due back: their dawn has come, or the backstop has passed. */
    public static boolean due(long gameTime, long dayTime, long backAt, long dawn) {
        return dayTime >= dawn || gameTime >= backAt;
    }

    private static long awayFor(long dayTime) {
        return Math.max(MIN_AWAY_TICKS, DAY - Math.floorMod(dayTime, DAY));
    }
}
