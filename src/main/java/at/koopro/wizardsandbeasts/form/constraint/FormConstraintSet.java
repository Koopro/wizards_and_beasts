package at.koopro.wizardsandbeasts.form.constraint;

import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * An immutable set of {@link FormConstraint}s, plus the two presets the mod actually ships.
 *
 * <p>Sets compose by {@link #union(FormConstraintSet)} rather than by precedence, and that is the rule
 * that makes {@link FormConstraints} safe to extend: when two systems both have a claim on a player —
 * an Animagus who is also a werewolf, mid-moon — the player gets the strictest reading of both, and
 * neither system has to know the other exists.
 */
@NullMarked
public final class FormConstraintSet {

    /** No restrictions. What an untransformed player gets, and what a source returns when it has no claim. */
    public static final FormConstraintSet NONE = new FormConstraintSet(EnumSet.noneOf(FormConstraint.class));

    /**
     * <b>A beast holds no wand.</b> The hard no-hands rule every transformed player is under: no item
     * use, no block or entity interaction, no breaking, no inventory, no dropping, no casting.
     *
     * <p>Notably <em>excludes</em> {@link FormConstraint#NO_VOLUNTARY_EXIT}. This is the set an
     * Animagus wears, and an Animagus may change back whenever they like — that is the whole difference
     * between a discipline and an affliction.
     */
    public static final FormConstraintSet BEAST_HANDS = of(
            FormConstraint.NO_ITEM_USE,
            FormConstraint.NO_BLOCK_INTERACT,
            FormConstraint.NO_BLOCK_BREAK,
            FormConstraint.NO_ENTITY_INTERACT,
            FormConstraint.NO_INVENTORY,
            FormConstraint.NO_ITEM_DROP,
            FormConstraint.NO_SPELLCASTING);

    /**
     * {@link #BEAST_HANDS} plus {@link FormConstraint#NO_VOLUNTARY_EXIT}: a body its owner did not
     * choose and cannot give back. The forced-transformation set — currently the unmedicated werewolf.
     */
    public static final FormConstraintSet FERAL =
            BEAST_HANDS.with(FormConstraint.NO_VOLUNTARY_EXIT);

    private final Set<FormConstraint> denied;

    private FormConstraintSet(Set<FormConstraint> denied) {
        this.denied = denied;
    }

    public static FormConstraintSet of(FormConstraint... constraints) {
        EnumSet<FormConstraint> set = EnumSet.noneOf(FormConstraint.class);
        for (FormConstraint constraint : constraints) {
            set.add(constraint);
        }
        return new FormConstraintSet(set);
    }

    public static FormConstraintSet copyOf(Collection<FormConstraint> constraints) {
        EnumSet<FormConstraint> set = EnumSet.noneOf(FormConstraint.class);
        set.addAll(constraints);
        return new FormConstraintSet(set);
    }

    public boolean denies(FormConstraint constraint) {
        return denied.contains(constraint);
    }

    public boolean isEmpty() {
        return denied.isEmpty();
    }

    /** The constraints in this set, as an unmodifiable view. */
    public Set<FormConstraint> denied() {
        return Set.copyOf(denied);
    }

    /** This set plus {@code extra}. Returns {@code this} when nothing would change. */
    public FormConstraintSet with(FormConstraint... extra) {
        EnumSet<FormConstraint> set = EnumSet.copyOf(denied.isEmpty()
                ? EnumSet.noneOf(FormConstraint.class) : denied);
        boolean changed = false;
        for (FormConstraint constraint : extra) {
            changed |= set.add(constraint);
        }
        return changed ? new FormConstraintSet(set) : this;
    }

    /** This set minus {@code removed}. Returns {@code this} when nothing would change. */
    public FormConstraintSet without(FormConstraint... removed) {
        EnumSet<FormConstraint> set = EnumSet.copyOf(denied.isEmpty()
                ? EnumSet.noneOf(FormConstraint.class) : denied);
        boolean changed = false;
        for (FormConstraint constraint : removed) {
            changed |= set.remove(constraint);
        }
        return changed ? new FormConstraintSet(set) : this;
    }

    /** The strictest reading of both sets. Returns an existing instance when one already covers the other. */
    public FormConstraintSet union(FormConstraintSet other) {
        if (other.denied.isEmpty() || denied.containsAll(other.denied)) {
            return this;
        }
        if (denied.isEmpty() || other.denied.containsAll(denied)) {
            return other;
        }
        EnumSet<FormConstraint> set = EnumSet.copyOf(denied);
        set.addAll(other.denied);
        return new FormConstraintSet(set);
    }

    @Override
    public String toString() {
        return "FormConstraintSet" + denied;
    }
}
