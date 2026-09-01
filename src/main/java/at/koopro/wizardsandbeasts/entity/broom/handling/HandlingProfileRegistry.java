package at.koopro.wizardsandbeasts.entity.broom.handling;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.HandlingProfile;

import java.util.EnumMap;
import java.util.Map;

/**
 * Maps the datapack-facing {@link HandlingProfile} enum onto the behaviour that implements it.
 *
 * <p>The two are kept apart on purpose. {@link HandlingProfile} is data: it lives in the {@code broom}
 * package, it is what {@code handlingProfile} parses to, and it carries the scalar defaults a
 * definition inherits. {@link BroomHandlingProfile} is behaviour, and it lives next to the movement
 * code it hooks into. Folding them together would put a {@code BroomEntity} dependency inside the
 * record that the codec decodes — and that record is read on both sides, by the loader, by the
 * command layer and by tests that never touch an entity.
 *
 * <p>The lookup lives here rather than as a method on the enum for the same reason: the arrow points
 * from behaviour to data, never back.
 */
public final class HandlingProfileRegistry {

    public static final BroomHandlingProfile SCHOOL = new SchoolHandling();
    public static final BroomHandlingProfile BALANCED = new BalancedHandling();
    public static final BroomHandlingProfile RACING = new RacingHandling();
    public static final BroomHandlingProfile TANK = new TankHandling();

    private static final Map<HandlingProfile, BroomHandlingProfile> BY_PROFILE =
            new EnumMap<>(HandlingProfile.class);

    static {
        BY_PROFILE.put(HandlingProfile.SCHOOL, SCHOOL);
        BY_PROFILE.put(HandlingProfile.BALANCED, BALANCED);
        BY_PROFILE.put(HandlingProfile.RACING, RACING);
        BY_PROFILE.put(HandlingProfile.TANK, TANK);
    }

    private HandlingProfileRegistry() {}

    /** The behaviour for a profile. Never null — the enum is closed and every value is mapped. */
    public static BroomHandlingProfile of(HandlingProfile profile) {
        return BY_PROFILE.getOrDefault(profile, BALANCED);
    }

    /** The behaviour a definition flies with. */
    public static BroomHandlingProfile of(BroomDefinition def) {
        return of(def.handling().profile());
    }

    /** Every mapped behaviour, so a test can hold the two enums against each other. */
    public static Map<HandlingProfile, BroomHandlingProfile> all() {
        return Map.copyOf(BY_PROFILE);
    }
}
