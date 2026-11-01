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
        /** Apparated away, and due back at {@code backAt}. */
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
    private static final long DAY = 24000;

    private PostRules() {}

    /**
     * @param found      the person is loaded
     * @param distanceSq their distance from the post, squared; ignored when not found
     * @param watched    a player is near enough to see them jump
     * @param looks      looks in a row they have not been found
     * @param now        game time
     * @param backAt     game time an absent person returns
     */
    public static Act decide(Status status, boolean found, double distanceSq, boolean watched, int looks, long now,
                             long backAt) {
        return switch (status) {
            case VACANT -> Act.SPAWN;
            case AWAY -> now >= backAt ? Act.SPAWN : Act.NOTHING;
            case PRESENT -> {
                if (found) {
                    yield distanceSq > (double) STRAY_BLOCKS * STRAY_BLOCKS && !watched ? Act.RETURN : Act.NOTHING;
                }
                yield looks >= LOST_AFTER_LOOKS ? Act.SPAWN : Act.NOTHING;
            }
        };
    }

    /** When someone who Apparated away at {@code gameTime} is back: at the next dawn, and never at once. */
    public static long backAt(long gameTime, long dayTime) {
        return gameTime + Math.max(MIN_AWAY_TICKS, DAY - Math.floorMod(dayTime, DAY));
    }
}
