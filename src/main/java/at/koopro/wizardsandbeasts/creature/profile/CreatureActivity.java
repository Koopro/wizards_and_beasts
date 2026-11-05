package at.koopro.wizardsandbeasts.creature.profile;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.function.Predicate;

/**
 * What a creature is visibly doing, as one synced byte (BEAST_RENAISSANCE §4.2). The server writes it from the
 * ecology goal; the client's movement controller turns it into a looping clip through {@link #loop}.
 *
 * <p><b>Fallback, not requirement.</b> Each activity names the clips that would show it best, in order, and a way
 * to fake it from the clips every rig already has: a sleeping creature with no {@code sleep} or {@code rest} clip
 * plays its {@code idle} at a third of the speed; a fleeing one with no {@code run} plays its walk half again as
 * fast; an eating one loops its {@code graze}. So every existing rig shows the difference today, and a clip
 * authored later lights up with no code change. GeckoLib throws inside the render pass on a clip its file does
 * not define, so a clip is only ever asked for when the rig is known to have it ({@code has}).
 *
 * <p>Not saved: the server derives it again within a tick of loading. The list is the whole BR-1 vocabulary;
 * activities without a mapping yet ({@code TAKEOFF}, {@code PERCH}…) fall back to the plain loop.
 */
@NullMarked
public enum CreatureActivity {
    IDLE(List.of(), 1.0, false),
    WALK(List.of(), 1.0, false),
    RUN(List.of("run", "gallop"), 1.5, false),
    SNEAK(List.of("sneak", "stalk"), 0.6, false),
    REST(List.of("rest", "sleep"), 0.5, true),
    SLEEP(List.of("sleep", "rest"), 0.35, true),
    EAT(List.of("eat", "graze", "sniff"), 1.0, true),
    DRINK(List.of("drink", "graze", "sniff"), 1.0, true),
    GROOM(List.of("groom", "preen", "lick", "shake"), 1.0, true),
    ALERT(List.of("alert", "sniff"), 1.0, true),
    THREATEN(List.of("threaten", "hiss", "rear"), 1.0, true),
    FLEE(List.of("run", "gallop"), 1.5, false),
    SWIM(List.of(), 1.0, false),
    FLY(List.of(), 1.0, false),
    GLIDE(List.of("glide"), 0.7, false),
    HOVER(List.of("hover"), 1.0, false),
    TAKEOFF(List.of("takeoff"), 1.0, false),
    LAND(List.of("land"), 1.0, false),
    PERCH(List.of("perch"), 0.8, true),
    BURROWED(List.of("burrowed"), 0.5, true),
    CLIMB(List.of("climb"), 1.0, false);

    private static final CreatureActivity[] BY_ID = values();

    /** Clips that show this activity best, first choice first. */
    private final List<String> clips;
    /** Animation speed when none of them exists and the plain loop stands in. */
    private final double fallbackSpeed;
    /** Shown standing still: its clip (or slowed idle) plays in place of idle, never of the moving loop. */
    private final boolean inPlace;

    CreatureActivity(List<String> clips, double fallbackSpeed, boolean inPlace) {
        this.clips = clips;
        this.fallbackSpeed = fallbackSpeed;
        this.inPlace = inPlace;
    }

    public byte id() {
        return (byte) ordinal();
    }

    public static CreatureActivity byId(byte id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : IDLE;
    }

    /** A clip to loop and the speed to play it at. */
    public record Loop(String clip, double speed) {}

    /**
     * The loop to play.
     *
     * @param moving  the client sees it moving (GeckoLib's own test)
     * @param has     whether the rig defines a clip
     * @param base    the rig's moving loop: {@code walk}, {@code fly} or {@code swim}
     */
    public Loop loop(boolean moving, Predicate<String> has, String base) {
        if (inPlace && moving) {
            return new Loop(base, 1.0); // on its way to the water, it walks
        }
        for (String clip : clips) {
            if (has.test(clip)) {
                return new Loop(clip, 1.0);
            }
        }
        if (inPlace) {
            return new Loop("idle", fallbackSpeed);
        }
        return moving ? new Loop(base, fallbackSpeed) : new Loop("idle", 1.0);
    }
}
