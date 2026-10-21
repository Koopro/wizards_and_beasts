package at.koopro.wizardsandbeasts.creature.companion;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The companion arithmetic, pure: stages, which commands a creature will take, bond and training gains under
 * their daily caps, personality rolls and inheritance, and who may harm whose creature. No entity, no level —
 * {@link Companions} reads the world, calls these and writes the result back, and every rule is unit-tested.
 */
@NullMarked
public final class CompanionRules {

    /** Training points one carried-out order is worth before personality. */
    public static final int TRAINING_PER_ORDER = 10;
    /** Training points a creature can earn in one in-game day: five orders' worth. */
    public static final int TRAINING_DAILY_CAP = 50;

    private CompanionRules() {}

    // -- stages ---------------------------------------------------------------------------------------------

    /**
     * Where a creature stands.
     *
     * @param owned          whether it has an owner at all — an unowned creature is wild whatever its bond
     * @param bond           its bond level
     * @param followThreshold the bond profile's follow threshold: the line between trusting and bonded
     * @param masteryBond    bond a mastered companion must hold (the bond profile's mastery mark, or its ceiling)
     */
    public static CompanionStage stage(CompanionProfile profile, boolean owned, int bond, int followThreshold,
                                       int masteryBond, int training) {
        if (!owned || bond < profile.curiousAt()) {
            return CompanionStage.WILD;
        }
        if (bond < profile.trustingAt()) {
            return CompanionStage.CURIOUS;
        }
        if (bond < Math.max(followThreshold, profile.trustingAt())) {
            return CompanionStage.TRUSTING;
        }
        if (training < profile.trainedAt()) {
            return CompanionStage.BONDED;
        }
        if (training >= profile.masteredAt() && bond >= masteryBond) {
            return CompanionStage.MASTERED;
        }
        return CompanionStage.TRAINED;
    }

    // -- commands -------------------------------------------------------------------------------------------

    /** Why a command is not taken; {@link #OK} when it is. */
    public enum Refusal { OK, UNKNOWN_TO_SPECIES, NOT_YET, CANNOT_FIGHT, TEMPERAMENT }

    /**
     * Whether a creature takes {@code command}.
     *
     * @param ableToFight the species may fight <em>and</em> this creature has an attack to fight with
     */
    public static Refusal check(CompanionCommand command, CompanionProfile profile, CompanionStage stage,
                                List<PersonalityTrait> traits, boolean ableToFight) {
        return check(command, profile, stage, traits, ableToFight, false);
    }

    /**
     * {@link #check} for an owner who may hold the Beastmaster capstone ({@code beast_masters_voice}): their
     * creature answers as if one stage further trained, and no temperament refuses them. What a species can do and
     * whether this creature can fight are unchanged — the voice persuades, it does not grow claws.
     */
    public static Refusal check(CompanionCommand command, CompanionProfile profile, CompanionStage stage,
                                List<PersonalityTrait> traits, boolean ableToFight, boolean beastMaster) {
        if (beastMaster) {
            stage = oneStageFurther(stage);
        }
        if (!profile.understands(command)) {
            return Refusal.UNKNOWN_TO_SPECIES;
        }
        if (command.isCombat() && !ableToFight) {
            return Refusal.CANNOT_FIGHT;
        }
        if (!stage.atLeast(command.requires())) {
            return Refusal.NOT_YET;
        }
        if (stage != CompanionStage.MASTERED && !beastMaster) {
            for (PersonalityTrait trait : traits) {
                if (trait.refuses().contains(command)) {
                    return Refusal.TEMPERAMENT;
                }
            }
        }
        return Refusal.OK;
    }

    /** The next stage up, or {@code MASTERED} itself; a wild creature stays wild (it has no owner to obey). */
    public static CompanionStage oneStageFurther(CompanionStage stage) {
        if (stage == CompanionStage.WILD || stage == CompanionStage.MASTERED) {
            return stage;
        }
        return CompanionStage.values()[stage.ordinal() + 1];
    }

    /**
     * The commands to offer the owner: those the species understands and that are not impossible for this
     * creature. Ones it has not reached yet are offered too, so the owner can see what training will open —
     * {@link #check} still decides.
     */
    public static List<CompanionCommand> offered(CompanionProfile profile, boolean ableToFight) {
        List<CompanionCommand> out = new ArrayList<>();
        for (CompanionCommand command : CompanionCommand.values()) {
            if (profile.understands(command) && (!command.isCombat() || ableToFight)) {
                out.add(command);
            }
        }
        return out;
    }

    // -- gains ----------------------------------------------------------------------------------------------

    /**
     * What an interaction is worth: {@code units} of today's cap used, {@code bond} gained.
     *
     * <p>The cap is counted in base units, before personality: it limits how <em>often</em> a kind of
     * interaction counts in a day, and personality decides how <em>much</em> each one means. A greedy niffler
     * still gets one favourite treat a day — it just values it twice as much.
     */
    public record Gain(int bond, int units) {
        public static final Gain NONE = new Gain(0, 0);
    }

    public static Gain bondGain(Interaction kind, int base, List<PersonalityTrait> traits, int unitsToday) {
        if (base <= 0) {
            return Gain.NONE;
        }
        int units = kind.dailyCap() > 0 ? Math.min(base, kind.dailyCap() - unitsToday) : base;
        if (units <= 0) {
            return Gain.NONE;
        }
        float multiplier = 1.0f;
        for (PersonalityTrait trait : traits) {
            multiplier *= trait.multiplier(kind);
        }
        if (multiplier <= 0.0f) {
            return new Gain(0, units);
        }
        return new Gain(Math.max(1, Math.round(units * multiplier)), units);
    }

    /** Training points an order is worth after personality, within what is left of today's cap. */
    public static int trainingGain(List<PersonalityTrait> traits, int trainingToday) {
        float rate = 1.0f;
        for (PersonalityTrait trait : traits) {
            rate *= trait.trainingRate();
        }
        int points = Math.round(TRAINING_PER_ORDER * rate);
        return Math.max(0, Math.min(points, TRAINING_DAILY_CAP - trainingToday));
    }

    /** Fraction of health below which it breaks off and runs to its owner: the most cautious trait wins. */
    public static float fleeBelow(List<PersonalityTrait> traits) {
        float flee = 0.0f;
        for (PersonalityTrait trait : traits) {
            flee = Math.max(flee, trait.fleeBelowHealth());
        }
        return flee;
    }

    // -- personality ----------------------------------------------------------------------------------------

    /** Rolls {@code count} distinct traits from a weighted pool, skipping any the game does not know. */
    public static List<String> roll(List<CompanionProfile.WeightedTrait> pool, int count, Random random,
                                    Predicate<String> known) {
        List<CompanionProfile.WeightedTrait> left = new ArrayList<>();
        for (CompanionProfile.WeightedTrait entry : pool) {
            if (known.test(entry.trait())) {
                left.add(entry);
            }
        }
        List<String> out = new ArrayList<>();
        while (out.size() < count && !left.isEmpty()) {
            int total = 0;
            for (CompanionProfile.WeightedTrait entry : left) {
                total += entry.weight();
            }
            int roll = random.nextInt(total);
            for (int i = 0; i < left.size(); i++) {
                roll -= left.get(i).weight();
                if (roll < 0) {
                    out.add(left.remove(i).trait());
                    break;
                }
            }
        }
        return out;
    }

    /**
     * A juvenile's personality: one trait from each parent where it can, so a young creature is recognisably
     * theirs — and now and then a fresh trait from the species' pool instead of the second, so a line does
     * not freeze into one temperament forever.
     */
    public static List<String> inherit(List<String> mother, List<String> father, List<CompanionProfile.WeightedTrait> pool,
                                       int count, float mutationChance, Random random, Predicate<String> known) {
        Set<String> out = new LinkedHashSet<>();
        List<List<String>> parents = List.of(mother, father);
        for (List<String> parent : parents) {
            if (out.size() >= count) {
                break;
            }
            List<String> candidates = new ArrayList<>();
            for (String trait : parent) {
                if (known.test(trait) && !out.contains(trait)) {
                    candidates.add(trait);
                }
            }
            if (!candidates.isEmpty()) {
                out.add(candidates.get(random.nextInt(candidates.size())));
            }
        }
        if (out.size() >= 2 && random.nextFloat() < mutationChance) {
            List<String> kept = new ArrayList<>(out);
            kept.removeLast();
            out = new LinkedHashSet<>(kept);
        }
        if (out.size() < count) {
            List<CompanionProfile.WeightedTrait> rest = new ArrayList<>();
            for (CompanionProfile.WeightedTrait entry : pool) {
                if (!out.contains(entry.trait())) {
                    rest.add(entry);
                }
            }
            out.addAll(roll(rest, count - out.size(), random, known));
        }
        return List.copyOf(out);
    }

    // -- protection -----------------------------------------------------------------------------------------

    /** How a player stands with a creature. */
    public enum Relation { OWNER, TRUSTED, HOSTILE, STRANGER }

    /** The server's rule for strangers; owners and marked enemies are the same under both. */
    public enum Protection { OWNER_ONLY, PVP_FLAG }

    /**
     * Whether {@code relation} may hurt an owned companion. The owner may (and pays the betrayal penalty);
     * a player the owner marked hostile may; a trusted friend may not; a stranger may only when the server
     * says companions follow its PvP rule and PvP is on.
     */
    public static boolean mayHarm(Relation relation, Protection rule, boolean pvpEnabled) {
        return switch (relation) {
            case OWNER, HOSTILE -> true;
            case TRUSTED -> false;
            case STRANGER -> rule == Protection.PVP_FLAG && pvpEnabled;
        };
    }

    /** Whether {@code relation} may feed, ride or calm it: the owner and trusted friends. */
    public static boolean mayHandle(Relation relation) {
        return relation == Relation.OWNER || relation == Relation.TRUSTED;
    }
}
