package at.koopro.wizardsandbeasts.creature.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * What a companion can do for its person, declared per species.
 *
 * <p>The active kinds run when their command is given ({@link #command()}): {@code search} runs every search
 * ability the species has, {@code assist} every assist one. {@link Innate} names a mechanic the species
 * already has in its own entity class — the hippogriff's saddle, the phoenix's rebirth — so the Beasts tab
 * can say what the creature is for without this layer pretending to run it.
 */
@NullMarked
public sealed interface CompanionAbility {

    Kind kind();

    /** The command that triggers it; {@code null} for innate abilities. */
    default @Nullable CompanionCommand command() {
        return kind().command;
    }

    /** Lang key of the ability's name. */
    default String key() {
        return "companion.wizards_and_beasts.ability." + kind().getSerializedName();
    }

    enum Kind implements StringRepresentable {
        HEAL_OWNER(CompanionCommand.ASSIST, HealOwner.CODEC),
        CLEANSE_OWNER(CompanionCommand.ASSIST, CleanseOwner.CODEC),
        REVEAL(CompanionCommand.SEARCH, Reveal.CODEC),
        RETRIEVE_ITEMS(CompanionCommand.SEARCH, RetrieveItems.CODEC),
        SCOUT(CompanionCommand.SEARCH, Scout.CODEC),
        INNATE(null, Innate.CODEC);

        static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        final @Nullable CompanionCommand command;
        final MapCodec<? extends CompanionAbility> codec;

        Kind(@Nullable CompanionCommand command, MapCodec<? extends CompanionAbility> codec) {
            this.command = command;
            this.codec = codec;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    Codec<CompanionAbility> CODEC = Kind.CODEC.dispatch("kind", CompanionAbility::kind, k -> k.codec);

    /** What a {@link Reveal} looks for. */
    enum RevealTarget implements StringRepresentable {
        /** Ore and precious blocks — a niffler's nose. */
        VALUABLES,
        /** Wandwood logs and saplings — a bowtruckle's trees. */
        WANDWOOD,
        /** Hostile creatures and anything hiding by magic. */
        THREATS,
        /** The Dark: dark creatures and wizards stained by dark magic — a unicorn's unease. */
        DARK;

        static final Codec<RevealTarget> CODEC = StringRepresentable.fromEnum(RevealTarget::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Heals the owner when they are hurt and near. */
    record HealOwner(float amount, int cooldownSeconds) implements CompanionAbility {
        static final MapCodec<HealOwner> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0.5f, 40.0f).fieldOf("amount").forGetter(HealOwner::amount),
                Codec.intRange(1, 3600).fieldOf("cooldown_seconds").forGetter(HealOwner::cooldownSeconds)
        ).apply(i, HealOwner::new));

        @Override public Kind kind() { return Kind.HEAL_OWNER; }
    }

    /** Lifts harmful effects from the owner. */
    record CleanseOwner(int cooldownSeconds) implements CompanionAbility {
        static final MapCodec<CleanseOwner> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 3600).fieldOf("cooldown_seconds").forGetter(CleanseOwner::cooldownSeconds)
        ).apply(i, CleanseOwner::new));

        @Override public Kind kind() { return Kind.CLEANSE_OWNER; }
    }

    /** Points out what it senses within {@code radius}: outlines the owner can see for a while. */
    record Reveal(RevealTarget target, int radius, int cooldownSeconds) implements CompanionAbility {
        static final MapCodec<Reveal> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RevealTarget.CODEC.fieldOf("target").forGetter(Reveal::target),
                Codec.intRange(2, 32).fieldOf("radius").forGetter(Reveal::radius),
                Codec.intRange(1, 3600).fieldOf("cooldown_seconds").forGetter(Reveal::cooldownSeconds)
        ).apply(i, Reveal::new));

        @Override public Kind kind() { return Kind.REVEAL; }

        @Override
        public String key() {
            return "companion.wizards_and_beasts.ability.reveal." + target.getSerializedName();
        }
    }

    /** Fetches loose items lying within {@code radius} and brings them to the owner's feet. */
    record RetrieveItems(int radius, int maxStacks, int cooldownSeconds) implements CompanionAbility {
        static final MapCodec<RetrieveItems> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(2, 24).fieldOf("radius").forGetter(RetrieveItems::radius),
                Codec.intRange(1, 32).optionalFieldOf("max_stacks", 8).forGetter(RetrieveItems::maxStacks),
                Codec.intRange(1, 3600).fieldOf("cooldown_seconds").forGetter(RetrieveItems::cooldownSeconds)
        ).apply(i, RetrieveItems::new));

        @Override public Kind kind() { return Kind.RETRIEVE_ITEMS; }
    }

    /** Goes unseen and marks threats around it — a demiguise's watch. */
    record Scout(int radius, int seconds, int cooldownSeconds) implements CompanionAbility {
        static final MapCodec<Scout> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(2, 32).fieldOf("radius").forGetter(Scout::radius),
                Codec.intRange(1, 300).fieldOf("seconds").forGetter(Scout::seconds),
                Codec.intRange(1, 3600).fieldOf("cooldown_seconds").forGetter(Scout::cooldownSeconds)
        ).apply(i, Scout::new));

        @Override public Kind kind() { return Kind.SCOUT; }
    }

    /** A mechanic the species' own entity class already runs; listed, not executed here. */
    record Innate(String id) implements CompanionAbility {
        static final MapCodec<Innate> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Innate::id)
        ).apply(i, Innate::new));

        @Override public Kind kind() { return Kind.INNATE; }

        @Override
        public String key() {
            return "companion.wizards_and_beasts.ability.innate." + id;
        }
    }
}
