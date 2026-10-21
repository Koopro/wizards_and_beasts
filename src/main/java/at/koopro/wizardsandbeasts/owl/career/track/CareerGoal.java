package at.koopro.wizardsandbeasts.owl.career.track;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * A milestone a rank asks for besides experience — the thing that makes a promotion mean something.
 *
 * <p>Experience alone is a grind meter; a goal names an accomplishment the rank stands for ("has raised a
 * creature to full trust", "has an Outstanding in Care of Magical Creatures"). Every goal reads state a
 * system already keeps — the O.W.L. sheet, the bestiary, the career's own activity counters — through
 * {@link CareerContext}, so a goal can never be met by anything but doing the thing.
 */
@NullMarked
public sealed interface CareerGoal {

    Type type();

    /** How far along the player is, compared against {@link #target()}. */
    int progress(CareerContext context);

    int target();

    /** One line for the Career tab, e.g. "Bestiary pages Studied or deeper". */
    Component describe();

    default boolean met(CareerContext context) {
        return progress(context) >= target();
    }

    enum Type implements StringRepresentable {
        OWL_GRADE(OwlGrade.CODEC),
        NEWT_GRADE(NewtGrade.CODEC),
        BESTIARY_PAGES(BestiaryPages.CODEC),
        RESEARCH_COMPLETED(ResearchCompleted.CODEC),
        ACTIVITY_TOTAL(ActivityTotal.CODEC),
        CONTRACTS_COMPLETED(ContractsCompleted.CODEC),
        FEE(Fee.CODEC),
        STANDING(Standing.CODEC);

        static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        private final MapCodec<? extends CareerGoal> codec;

        Type(MapCodec<? extends CareerGoal> codec) {
            this.codec = codec;
        }

        MapCodec<? extends CareerGoal> codec() {
            return codec;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    String KEY = "career.wizards_and_beasts.goal.";

    Codec<OWLSubject> SUBJECT = Codec.STRING.comapFlatMap(raw -> {
        try {
            return DataResult.success(OWLSubject.valueOf(raw.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Unknown O.W.L. subject: " + raw);
        }
    }, s -> s.name().toLowerCase(Locale.ROOT));

    Codec<OWLGrade> GRADE = Codec.STRING.comapFlatMap(raw -> {
        try {
            return DataResult.success(OWLGrade.valueOf(raw.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Unknown O.W.L. grade: " + raw);
        }
    }, OWLGrade::name);

    /**
     * Declared after {@link #SUBJECT} and {@link #GRADE} on purpose: interface fields initialise in textual
     * order, and building this initialises the goal records, whose codecs read those two.
     */
    Codec<CareerGoal> CODEC = Type.CODEC.dispatch(CareerGoal::type, Type::codec);

    /** At least {@code grade} in {@code subject}. Progress is the grade's value, so the line reads naturally. */
    record OwlGrade(OWLSubject subject, OWLGrade grade) implements CareerGoal {
        static final MapCodec<OwlGrade> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                SUBJECT.fieldOf("subject").forGetter(OwlGrade::subject),
                GRADE.fieldOf("grade").forGetter(OwlGrade::grade)
        ).apply(i, OwlGrade::new));

        @Override public Type type() { return Type.OWL_GRADE; }
        @Override public int progress(CareerContext c) { return c.grade(subject).value; }
        @Override public int target() { return grade.value; }

        @Override
        public Component describe() {
            return Component.translatable(KEY + "owl_grade", Component.translatable(subject.translationKey()),
                    Component.translatable(grade.translationKey()));
        }
    }

    /** At least {@code grade} in the N.E.W.T. in {@code subject} — the top of a track asks for advanced study. */
    record NewtGrade(OWLSubject subject, OWLGrade grade) implements CareerGoal {
        static final MapCodec<NewtGrade> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                SUBJECT.fieldOf("subject").forGetter(NewtGrade::subject),
                GRADE.fieldOf("grade").forGetter(NewtGrade::grade)
        ).apply(i, NewtGrade::new));

        @Override public Type type() { return Type.NEWT_GRADE; }
        @Override public int progress(CareerContext c) { return c.newtGrade(subject).value; }
        @Override public int target() { return grade.value; }

        @Override
        public Component describe() {
            return Component.translatable(KEY + "newt_grade", Component.translatable(subject.translationKey()),
                    Component.translatable(grade.translationKey()));
        }
    }

    /** {@code count} bestiary pages at {@code min_tier} or deeper, optionally of dangerous creatures only. */
    record BestiaryPages(DiscoveryTier minTier, int count, int minRating) implements CareerGoal {
        static final MapCodec<BestiaryPages> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DiscoveryTier.CODEC.fieldOf("min_tier").forGetter(BestiaryPages::minTier),
                Codec.intRange(1, 512).fieldOf("count").forGetter(BestiaryPages::count),
                Codec.intRange(0, 5).optionalFieldOf("min_rating", 0).forGetter(BestiaryPages::minRating)
        ).apply(i, BestiaryPages::new));

        @Override public Type type() { return Type.BESTIARY_PAGES; }
        @Override public int progress(CareerContext c) { return c.pages(minTier, minRating); }
        @Override public int target() { return count; }

        @Override
        public Component describe() {
            Component tier = Component.translatable("career.wizards_and_beasts.tier." + minTier.name().toLowerCase(Locale.ROOT));
            return minRating > 0
                    ? Component.translatable(KEY + "bestiary_pages.rated", tier, "X".repeat(minRating))
                    : Component.translatable(KEY + "bestiary_pages", tier);
        }
    }

    record ResearchCompleted(int count) implements CareerGoal {
        static final MapCodec<ResearchCompleted> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 512).fieldOf("count").forGetter(ResearchCompleted::count)
        ).apply(i, ResearchCompleted::new));

        @Override public Type type() { return Type.RESEARCH_COMPLETED; }
        @Override public int progress(CareerContext c) { return c.researchCompleted(); }
        @Override public int target() { return count; }
        @Override public Component describe() { return Component.translatable(KEY + "research_completed"); }
    }

    /** {@code count} matching activities done in this career, or distinct creatures when {@code distinct}. */
    record ActivityTotal(ActivityFilter filter, int count, boolean distinct) implements CareerGoal {
        static final MapCodec<ActivityTotal> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ActivityFilter.CODEC.fieldOf("when").forGetter(ActivityTotal::filter),
                Codec.intRange(1, 10_000).fieldOf("count").forGetter(ActivityTotal::count),
                Codec.BOOL.optionalFieldOf("distinct", false).forGetter(ActivityTotal::distinct)
        ).apply(i, ActivityTotal::new));

        @Override public Type type() { return Type.ACTIVITY_TOTAL; }
        @Override public int progress(CareerContext c) { return c.activityTotal(filter, distinct); }
        @Override public int target() { return count; }

        @Override
        public Component describe() {
            return CareerText.activity(filter, distinct);
        }
    }

    record ContractsCompleted(int count) implements CareerGoal {
        static final MapCodec<ContractsCompleted> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 10_000).fieldOf("count").forGetter(ContractsCompleted::count)
        ).apply(i, ContractsCompleted::new));

        @Override public Type type() { return Type.CONTRACTS_COMPLETED; }
        @Override public int progress(CareerContext c) { return c.contractsCompleted(); }
        @Override public int target() { return count; }
        @Override public Component describe() { return Component.translatable(KEY + "contracts_completed"); }
    }

    /**
     * A certification fee: the rank is an examined qualification, and the examining body charges for sitting it.
     * Met while the vault holds the fee (progress is the vault, capped at the fee), and charged once, at the moment of
     * promotion ({@code CareerProgression.promote}, {@code LocationProgression.promote}) — a rank whose fee cannot be
     * paid then simply waits. Free while Gringotts is closed: {@link CareerContext#vaultKnuts} reads as unlimited.
     */
    record Fee(int knuts) implements CareerGoal {
        static final MapCodec<Fee> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 493 * 1000).fieldOf("knuts").forGetter(Fee::knuts)
        ).apply(i, Fee::new));

        @Override public Type type() { return Type.FEE; }
        @Override public int progress(CareerContext c) { return (int) Math.min(c.vaultKnuts(), knuts); }
        @Override public int target() { return knuts; }

        @Override
        public Component describe() {
            return Component.translatable(KEY + "fee",
                    at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper.formatFromKnuts(knuts));
        }
    }

    /**
     * At least {@code min_band} on a standing axis — an Auror Office that will not promote someone the public
     * despises, a guild that wants its members' respect. Progress is the band's step from the bottom, so the
     * Career tab's line reads as a climb.
     */
    record Standing(at.koopro.wizardsandbeasts.standing.StandingAxis axis,
                    at.koopro.wizardsandbeasts.standing.StandingBand minBand) implements CareerGoal {
        static final MapCodec<Standing> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                at.koopro.wizardsandbeasts.standing.StandingAxis.CODEC.fieldOf("axis").forGetter(Standing::axis),
                at.koopro.wizardsandbeasts.standing.StandingBand.CODEC.fieldOf("min_band").forGetter(Standing::minBand)
        ).apply(i, Standing::new));

        @Override public Type type() { return Type.STANDING; }
        @Override public int progress(CareerContext c) { return c.standingBand(axis).step() + 2; }
        @Override public int target() { return minBand.step() + 2; }

        @Override
        public Component describe() {
            return Component.translatable(KEY + "standing", axis.displayName(), axis.bandName(minBand));
        }
    }

    /** The certification fees {@code rank} asks, summed: what promotion to it charges. */
    static long feeOf(CareerTrack.Rank rank) {
        long total = 0L;
        for (CareerGoal goal : rank.requires()) {
            if (goal instanceof Fee fee) {
                total += fee.knuts();
            }
        }
        return total;
    }
}
