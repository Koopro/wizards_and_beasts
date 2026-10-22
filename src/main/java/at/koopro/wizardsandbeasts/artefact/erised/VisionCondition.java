package at.koopro.wizardsandbeasts.artefact.erised;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.MagicalCondition;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.career.track.CareerGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;

/**
 * One thing a vision asks of the person looking: what they are, what they carry, what they have lost, how far
 * they have come. A vision is shown only when every one of its conditions holds ({@link ErisedSelector}).
 *
 * <p>Each reads the {@link Gazer} snapshot and nothing else, so a vision table is checked the same way on a live
 * player and in a unit test. {@code goal} reuses the career goals (O.W.L. grades, bestiary pages, research,
 * standing) rather than repeating them.
 */
@NullMarked
public sealed interface VisionCondition {

    Type type();

    boolean test(Gazer gazer);

    enum Type implements StringRepresentable {
        HERITAGE(HeritageIs.CODEC),
        CONDITION(Carries.CODEC),
        LOST_COMPANION(LostCompanion.CODEC),
        SPELLS_KNOWN(SpellsKnown.CODEC),
        CREATURES_KNOWN(CreaturesKnown.CODEC),
        PROFESSION(ProfessionIs.CODEC),
        GOAL(Goal.CODEC),
        NOT(Not.CODEC);

        static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        private final MapCodec<? extends VisionCondition> codec;

        Type(MapCodec<? extends VisionCondition> codec) {
            this.codec = codec;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** An enum read by its lower-case constant name ({@code "house_elf"}, {@code "muggle_born"}). */
    static <E extends Enum<E>> Codec<E> byName(Class<E> type) {
        return Codec.STRING.comapFlatMap(raw -> {
            try {
                return DataResult.success(Enum.valueOf(type, raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + raw);
            }
        }, e -> e.name().toLowerCase(Locale.ROOT));
    }

    /** Declared after {@link #byName} is usable: building this initialises the records, whose codecs call it. */
    Codec<VisionCondition> CODEC = Type.CODEC.dispatch(VisionCondition::type, t -> t.codec);

    /** Of one of {@code heritages}, and -- when {@code variants} is given -- of one of those variants. */
    record HeritageIs(List<Heritage> heritages, List<HeritageVariant> variants) implements VisionCondition {
        static final MapCodec<HeritageIs> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                byName(Heritage.class).listOf().optionalFieldOf("heritages", List.of()).forGetter(HeritageIs::heritages),
                byName(HeritageVariant.class).listOf().optionalFieldOf("variants", List.of()).forGetter(HeritageIs::variants)
        ).apply(i, HeritageIs::new));

        @Override public Type type() { return Type.HERITAGE; }

        @Override
        public boolean test(Gazer g) {
            return (heritages.isEmpty() || (g.heritage() != null && heritages.contains(g.heritage())))
                    && (variants.isEmpty() || (g.variant() != null && variants.contains(g.variant())));
        }
    }

    /** Carries the condition: lycanthropy, or an Obscurus. */
    record Carries(MagicalCondition condition) implements VisionCondition {
        static final MapCodec<Carries> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                byName(MagicalCondition.class).fieldOf("condition").forGetter(Carries::condition)
        ).apply(i, Carries::new));

        @Override public Type type() { return Type.CONDITION; }
        @Override public boolean test(Gazer g) { return g.conditions().contains(condition); }
    }

    /** Has lost a companion creature. The vision may name it ({@code "arg": "lost_companion"}). */
    record LostCompanion() implements VisionCondition {
        static final MapCodec<LostCompanion> CODEC = MapCodec.unit(LostCompanion::new);

        @Override public Type type() { return Type.LOST_COMPANION; }
        @Override public boolean test(Gazer g) { return !g.lostCompanions().isEmpty(); }
    }

    /** Knows between {@code min} and {@code max} spells, inclusive. */
    record SpellsKnown(int min, int max) implements VisionCondition {
        static final MapCodec<SpellsKnown> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(0, 4096).optionalFieldOf("min", 0).forGetter(SpellsKnown::min),
                Codec.intRange(0, 4096).optionalFieldOf("max", 4096).forGetter(SpellsKnown::max)
        ).apply(i, SpellsKnown::new));

        @Override public Type type() { return Type.SPELLS_KNOWN; }
        @Override public boolean test(Gazer g) { return g.spellsKnown() >= min && g.spellsKnown() <= max; }
    }

    /** Has between {@code min} and {@code max} bestiary pages at Observed or deeper, inclusive. */
    record CreaturesKnown(int min, int max) implements VisionCondition {
        static final MapCodec<CreaturesKnown> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(0, 4096).optionalFieldOf("min", 0).forGetter(CreaturesKnown::min),
                Codec.intRange(0, 4096).optionalFieldOf("max", 4096).forGetter(CreaturesKnown::max)
        ).apply(i, CreaturesKnown::new));

        @Override public Type type() { return Type.CREATURES_KNOWN; }
        @Override public boolean test(Gazer g) { return g.creaturesKnown() >= min && g.creaturesKnown() <= max; }
    }

    /** Has chosen one of these professions. */
    record ProfessionIs(List<Profession> professions) implements VisionCondition {
        static final MapCodec<ProfessionIs> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                byName(Profession.class).listOf().fieldOf("professions").forGetter(ProfessionIs::professions)
        ).apply(i, ProfessionIs::new));

        @Override public Type type() { return Type.PROFESSION; }
        @Override public boolean test(Gazer g) { return g.profession() != null && professions.contains(g.profession()); }
    }

    /**
     * A career goal (O.W.L. or N.E.W.T. grade, bestiary pages, research, standing). Goals that count a career's own
     * activity or contracts read an empty record here, so they never hold; a vision table should not use them.
     */
    record Goal(CareerGoal goal) implements VisionCondition {
        static final MapCodec<Goal> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                CareerGoal.CODEC.fieldOf("goal").forGetter(Goal::goal)
        ).apply(i, Goal::new));

        @Override public Type type() { return Type.GOAL; }
        @Override public boolean test(Gazer g) { return goal.met(g.career()); }
    }

    /** The opposite of {@code condition}. */
    record Not(VisionCondition condition) implements VisionCondition {
        static final MapCodec<Not> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> VisionCondition.CODEC).fieldOf("condition").forGetter(Not::condition)
        ).apply(i, Not::new));

        @Override public Type type() { return Type.NOT; }
        @Override public boolean test(Gazer g) { return !condition.test(g); }
    }
}
