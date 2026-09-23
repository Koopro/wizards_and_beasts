package at.koopro.wizardsandbeasts.creature.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

/**
 * A creature's rhythm in a fight.
 *
 * <p>Twenty-nine hostile creatures currently share one: {@code MeleeAttackGoal} at 1.2× speed, or
 * 1.45× with {@code CHARGE}. What differs between a Nundu and a Crup is the damage number and which
 * ability fires; the approach and the bite are the same beat. There is no telegraph, no retreat and
 * no preferred distance anywhere in the shared path.
 *
 * <p>Rhythm is kept separate from ability on purpose, and the brief puts it best:
 * <i>combat rhythm + creature ability = creature combat identity</i>. The ability system already
 * works — forty-eight types across ninety-one creatures — and is not touched here. This describes
 * <em>how</em> a creature closes and strikes; an ability describes <em>what</em> it does when it
 * arrives.
 *
 * <p>{@link #DEFAULT} reproduces the existing behaviour exactly, so a creature that declares no
 * combat profile fights precisely as it did before.
 *
 * @param style            how it prefers to engage
 * @param approachSpeed    speed multiplier while closing
 * @param windupTicks      telegraph before a strike lands; 0 keeps vanilla's untelegraphed swing
 * @param recoveryTicks    pause after a strike, on top of vanilla's attack cooldown
 * @param preferredRange   distance it tries to hold, in blocks; 0 means "as close as possible"
 * @param retreatAtHealth  fraction of max health below which it disengages; 0 means it never does
 */
@NullMarked
public record CombatProfile(
        Style style,
        double approachSpeed,
        int windupTicks,
        int recoveryTicks,
        double preferredRange,
        float retreatAtHealth) {

    /**
     * Exactly what every hostile creature does today: close at 1.2×, swing on vanilla's timer, never
     * retreat. Declaring no profile therefore changes nothing, which is what makes this safe to add
     * to a shared class used by ninety-six creatures.
     */
    public static final CombatProfile DEFAULT =
            new CombatProfile(Style.BRUTE, 1.2, 0, 0, 0.0, 0.0f);

    public static final Codec<CombatProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Style.CODEC.optionalFieldOf("style", Style.BRUTE).forGetter(CombatProfile::style),
            Codec.DOUBLE.optionalFieldOf("approachSpeed", 1.2).forGetter(CombatProfile::approachSpeed),
            Codec.INT.optionalFieldOf("windupTicks", 0).forGetter(CombatProfile::windupTicks),
            Codec.INT.optionalFieldOf("recoveryTicks", 0).forGetter(CombatProfile::recoveryTicks),
            Codec.DOUBLE.optionalFieldOf("preferredRange", 0.0).forGetter(CombatProfile::preferredRange),
            Codec.FLOAT.optionalFieldOf("retreatAtHealth", 0.0f).forGetter(CombatProfile::retreatAtHealth)
    ).apply(instance, CombatProfile::new));

    public CombatProfile {
        approachSpeed = Math.max(0.1, Math.min(3.0, approachSpeed));
        windupTicks = Math.max(0, Math.min(100, windupTicks));
        recoveryTicks = Math.max(0, Math.min(200, recoveryTicks));
        preferredRange = Math.max(0.0, Math.min(32.0, preferredRange));
        retreatAtHealth = Math.max(0.0f, Math.min(1.0f, retreatAtHealth));
    }

    /** True when this profile asks for anything the default melee goal does not already do. */
    public boolean isDefault() {
        return equals(DEFAULT);
    }

    /** How a creature prefers to engage. */
    public enum Style implements StringRepresentable {
        /** Walks in and hits things. Vanilla's melee goal, and the current behaviour of all 29. */
        BRUTE("brute"),
        /** Waits, then commits. Wants a windup and a long recovery. */
        AMBUSHER("ambusher"),
        /** Closes, strikes, backs off. Wants a preferred range it returns to. */
        HARRIER("harrier"),
        /** Keeps its distance and lets an ability do the work. */
        SKIRMISHER("skirmisher"),
        /** Fights alongside its own kind; pairs with the PACK trait. */
        PACK_HUNTER("pack_hunter");

        public static final Codec<Style> CODEC = StringRepresentable.fromEnum(Style::values);

        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
