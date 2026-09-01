package at.koopro.wizardsandbeasts.client.spell.wheel;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which spells the wheel offers, and which one the cursor is pointing at. Both are pure functions, so
 * the two things most likely to be wrong — an empty roster and the angle-to-index maths — are testable
 * without a client, a world or a player.
 *
 * <p>The wheel is a <em>view</em>: it never decides that a cast is legal. Its filter exists so the
 * player is not offered a choice the server would then refuse, and the confirm still travels as an
 * ordinary server-validated assign. If the two ever disagree, the server is right and the wheel is
 * showing something stale.
 */
@NullMarked
public final class SpellWheelModel {

    /**
     * Radius around the centre in which no entry is hovered. Without it, a cursor resting dead centre
     * would pick whichever sector won a coin-flip on sub-pixel jitter, and releasing would assign it.
     */
    public static final double DEADZONE_PX = 18.0;

    /** Nothing hovered. */
    public static final int NONE = -1;

    private SpellWheelModel() {}

    /**
     * The wheel's entries: every known spell that passes {@code usable}, ordered by id.
     *
     * <p>Sorted by <b>id</b> rather than display name on purpose. The order has to be the same every
     * time the wheel opens — muscle memory is the entire point of a hold-to-select radial — and a
     * display name is locale-dependent, so sorting by it would silently reshuffle the wheel for a
     * player who changed language.
     */
    public static List<String> entries(Collection<String> knownSpellIds, Predicate<String> usable) {
        List<String> result = new ArrayList<>(knownSpellIds.size());
        for (String id : knownSpellIds) {
            if (id != null && !id.isBlank() && usable.test(id)) {
                result.add(id);
            }
        }
        result.sort(null);
        return result;
    }

    /**
     * Index of the sector the cursor points at, or {@link #NONE}.
     *
     * @param dx cursor offset from the wheel centre, in scaled GUI pixels, positive right
     * @param dy the same, positive <em>down</em> (screen coordinates, not maths coordinates)
     */
    public static int hoveredIndex(double dx, double dy, int entryCount) {
        return hoveredIndex(dx, dy, entryCount, DEADZONE_PX);
    }

    /** As {@link #hoveredIndex(double, double, int)}, with the deadzone spelled out (tests, mostly). */
    public static int hoveredIndex(double dx, double dy, int entryCount, double deadzone) {
        if (entryCount <= 0) {
            return NONE;
        }
        if (Math.hypot(dx, dy) < deadzone) {
            return NONE;
        }
        // atan2 measures from +x anticlockwise; the quarter-turn puts 0 at the top, and because dy
        // grows downward the sweep then runs clockwise — the direction a player expects to read a
        // radial in. Rounding (not flooring) makes each entry own the wedge *centred* on its icon.
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
        if (angle < 0) {
            angle += Math.PI * 2.0;
        }
        int index = (int) Math.round(angle / (Math.PI * 2.0 / entryCount)) % entryCount;
        return index < 0 ? index + entryCount : index;
    }

    /** Angle in radians of entry {@code index}, measured for screen coordinates (0 = top, clockwise). */
    public static double angleOf(int index, int entryCount) {
        if (entryCount <= 0) {
            return -Math.PI / 2.0;
        }
        return -Math.PI / 2.0 + index * (Math.PI * 2.0 / entryCount);
    }
}
