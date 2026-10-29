package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Whether a character may take on a {@link MagicalCondition}, and whether they may keep the one they carry when their
 * heritage changes underneath it. Pure, so the matrix unit-tests without a world.
 *
 * <p>A character has one condition slot, and the slot used to have three writers that checked nothing: a werewolf's
 * bite replaced an Obscurus without a word, {@code condition set} overwrote whatever was there, and an operator moving
 * a werewolf to Goblin left a Goblin werewolf that {@link MagicalCondition#canBeCarriedBy} says cannot exist. Every
 * writer now asks here first, and a refusal is always a sentence the player or operator can read — never a silent
 * no-op and never half a change.
 */
@NullMarked
public final class ConditionRules {

    private static final String KEY = "heritage." + WizardsAndBeastsMod.MODID + ".condition_refused.";

    /** Why a condition cannot be taken on or kept. */
    public enum Refusal {
        /** Nobody to carry it yet: the character has not passed the heritage gate. */
        NO_HERITAGE("no_heritage"),
        /** Their people or lineage cannot carry it (a Goblin bitten, an Obscurus in a Squib). */
        CANNOT_CARRY("cannot_carry"),
        /** They already carry this condition. */
        ALREADY_CARRIES("already_carries"),
        /** They carry a different condition; there is one slot and it is taken. */
        OTHER_CONDITION("other_condition"),
        /** A heritage change would leave them carrying a condition the new heritage cannot carry. */
        WOULD_ORPHAN("would_orphan");

        private final String id;

        Refusal(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return KEY + id;
        }
    }

    private ConditionRules() {}

    /**
     * Why a character of {@code heritage}/{@code variant}, carrying {@code current}, cannot take on {@code wanted};
     * {@code null} when they can.
     */
    public static @Nullable Refusal toAcquire(@Nullable Heritage heritage, @Nullable HeritageVariant variant,
                                              @Nullable ConditionOrigin current, ConditionOrigin wanted) {
        if (heritage == null || variant == null) {
            return Refusal.NO_HERITAGE;
        }
        if (!wanted.condition().canBeCarriedBy(heritage, variant)) {
            return Refusal.CANNOT_CARRY;
        }
        if (current != null) {
            return current.condition() == wanted.condition() ? Refusal.ALREADY_CARRIES : Refusal.OTHER_CONDITION;
        }
        return null;
    }

    /**
     * Why a character carrying {@code current} cannot be moved to {@code heritage}/{@code variant}; {@code null} when
     * they carry nothing, or nothing the new heritage cannot carry.
     */
    public static @Nullable Refusal toReassign(@Nullable ConditionOrigin current, Heritage heritage,
                                               HeritageVariant variant) {
        if (current != null && !current.condition().canBeCarriedBy(heritage, variant)) {
            return Refusal.WOULD_ORPHAN;
        }
        return null;
    }

    /**
     * The refusal as a sentence: {@code %1$s} who, {@code %2$s} the condition asked about, {@code %3$s} the condition
     * already carried, {@code %4$s} the lineage that decides it.
     */
    public static Component describe(Refusal refusal, Component who, ConditionOrigin asked,
                                     @Nullable ConditionOrigin carried, @Nullable HeritageVariant lineage) {
        return Component.translatable(refusal.translationKey(), who,
                Component.translatable(asked.condition().getTranslationKey()),
                carried == null ? Component.empty() : Component.translatable(carried.condition().getTranslationKey()),
                lineage == null ? Component.empty() : Component.literal(lineage.getDisplayName()));
    }
}
