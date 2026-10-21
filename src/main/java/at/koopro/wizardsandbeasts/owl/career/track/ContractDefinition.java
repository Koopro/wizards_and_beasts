package at.koopro.wizardsandbeasts.owl.career.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * A piece of commissioned work a career's board can offer.
 *
 * <p>A contract is real work, never a token: it either counts activity the player does <em>after</em> taking
 * it (a survey, a breeding programme — {@link Activity}) or asks for materials handed over on turn-in
 * ({@link Delivery}), consumed from the inventory by the server. Its title and brief come from lang keys
 * derived from the track and contract ids, so a datapack adds a contract with one JSON entry and two lines.
 *
 * <pre>{@code
 * { "id": "unicorn_hair", "min_rank": "field_naturalist", "duration_days": 5,
 *   "objective": { "type": "delivery", "item": "wizards_and_beasts:unicorn_hair", "count": 2 },
 *   "reward": { "knuts": 580, "xp": 90 } }
 * }</pre>
 *
 * @param id           unique within the track
 * @param minRank      the lowest rank the board offers it to
 * @param objective    what completes it
 * @param durationDays in-game days to finish once taken
 * @param cooldownDays in-game days before a completed contract is offered again
 * @param weight       how often the daily board draws it relative to others
 * @param reward       paid on completion
 */
@NullMarked
public record ContractDefinition(String id, String minRank, Objective objective, int durationDays, int cooldownDays,
                                 int weight, Reward reward) {

    public static final Codec<ContractDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(ContractDefinition::id),
            Codec.STRING.fieldOf("min_rank").forGetter(ContractDefinition::minRank),
            Objective.CODEC.fieldOf("objective").forGetter(ContractDefinition::objective),
            Codec.intRange(1, 60).optionalFieldOf("duration_days", 3).forGetter(ContractDefinition::durationDays),
            Codec.intRange(0, 60).optionalFieldOf("cooldown_days", 1).forGetter(ContractDefinition::cooldownDays),
            Codec.intRange(1, 100).optionalFieldOf("weight", 10).forGetter(ContractDefinition::weight),
            Reward.CODEC.fieldOf("reward").forGetter(ContractDefinition::reward)
    ).apply(i, ContractDefinition::new));

    /** Coin into the vault and experience in the career. */
    public record Reward(long knuts, int xp) {
        public static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 493 * 100).optionalFieldOf("knuts", 0).forGetter(r -> (int) r.knuts()),
                Codec.intRange(0, 10_000).optionalFieldOf("xp", 0).forGetter(Reward::xp)
        ).apply(i, (knuts, xp) -> new Reward(knuts, xp)));
    }

    public sealed interface Objective {
        Codec<Objective> CODEC = Kind.CODEC.dispatch(Objective::kind, Kind::codec);

        Kind kind();

        int target();

        Component describe();

        enum Kind implements StringRepresentable {
            ACTIVITY(Activity.CODEC),
            DELIVERY(Delivery.CODEC);

            static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
            private final MapCodec<? extends Objective> codec;

            Kind(MapCodec<? extends Objective> codec) {
                this.codec = codec;
            }

            MapCodec<? extends Objective> codec() {
                return codec;
            }

            @Override
            public String getSerializedName() {
                return name().toLowerCase(Locale.ROOT);
            }
        }
    }

    /**
     * Matching activity done after the contract was taken. With {@code distinct}, each creature counts once —
     * a survey of three species is not three looks at one niffler.
     */
    public record Activity(ActivityFilter filter, int count, boolean distinct) implements Objective {
        static final MapCodec<Activity> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ActivityFilter.CODEC.fieldOf("when").forGetter(Activity::filter),
                Codec.intRange(1, 1000).fieldOf("count").forGetter(Activity::count),
                Codec.BOOL.optionalFieldOf("distinct", false).forGetter(Activity::distinct)
        ).apply(i, Activity::new));

        @Override public Kind kind() { return Kind.ACTIVITY; }
        @Override public int target() { return count; }
        @Override public Component describe() { return CareerText.activity(filter, distinct); }
    }

    /** {@code count} of {@code item}, taken from the inventory when the contract is turned in. */
    public record Delivery(Identifier item, int count) implements Objective {
        static final MapCodec<Delivery> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("item").forGetter(Delivery::item),
                Codec.intRange(1, 256).fieldOf("count").forGetter(Delivery::count)
        ).apply(i, Delivery::new));

        @Override public Kind kind() { return Kind.DELIVERY; }
        @Override public int target() { return count; }

        @Override
        public Component describe() {
            return Component.translatable("career.wizards_and_beasts.objective.delivery",
                    BuiltInRegistries.ITEM.getValue(item).getName());
        }
    }
}
