package at.koopro.wizardsandbeasts.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A location's trade: what its keeper buys from players and what it sells them, declared under {@code "market"} in
 * the location file. Prices move with pressure ({@link MarketPricing}); this is only the book.
 *
 * <pre>{@code
 * "market": {
 *   "half_life_days": 2.0,
 *   "demand_bonus": 0.5,
 *   "buys":  [ { "id": "dittany", "item": "wizards_and_beasts:dittany", "price_knuts": 12, "depth": 32 } ],
 *   "sells": [ { "id": "bezoar", "item": "wizards_and_beasts:bezoar", "price_knuts": 290, "depth": 4,
 *                "weekly_limit": 2 } ]
 * }
 * }</pre>
 *
 * @param buys         goods players can sell here
 * @param sells        goods players can buy here (items only)
 * @param halfLifeDays in-game days for a good's pressure to halve
 * @param demandBonus  extra share paid for the one bought good the trader is short of today
 */
@NullMarked
public record MarketDefinition(List<MarketGood> buys, List<MarketGood> sells, float halfLifeDays, float demandBonus) {

    public static final Codec<MarketDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            MarketGood.CODEC.listOf().optionalFieldOf("buys", List.of()).forGetter(MarketDefinition::buys),
            MarketGood.CODEC.listOf().optionalFieldOf("sells", List.of()).forGetter(MarketDefinition::sells),
            Codec.floatRange(0.25f, 60.0f).optionalFieldOf("half_life_days", 2.0f).forGetter(MarketDefinition::halfLifeDays),
            Codec.floatRange(0.0f, 2.0f).optionalFieldOf("demand_bonus", 0.5f).forGetter(MarketDefinition::demandBonus)
    ).apply(i, MarketDefinition::new));

    public MarketDefinition {
        buys = List.copyOf(buys);
        sells = List.copyOf(sells);
    }

    /** Which side of the counter a good is on. */
    public enum Side {
        /** The trader buys: the player sells. */
        BUYS,
        /** The trader sells: the player buys. */
        SELLS
    }

    public List<MarketGood> side(Side side) {
        return side == Side.BUYS ? buys : sells;
    }

    public @Nullable MarketGood good(Side side, String id) {
        for (MarketGood good : side(side)) {
            if (good.id().equals(id)) {
                return good;
            }
        }
        return null;
    }

    /** Problems the codec cannot see, given the tier ids the location's standing declares. */
    public List<String> validate(Set<String> tiers) {
        List<String> problems = new ArrayList<>();
        for (Side side : Side.values()) {
            Set<String> ids = new HashSet<>();
            for (MarketGood good : side(side)) {
                if (!ids.add(good.id())) {
                    problems.add("duplicate " + side.name().toLowerCase() + " good " + good.id());
                }
                if (!good.minTier().isEmpty() && !tiers.contains(good.minTier())) {
                    problems.add("good " + good.id() + " names unknown tier " + good.minTier());
                }
                if (side == Side.SELLS && good.kind() != MarketGood.Kind.ITEM) {
                    problems.add("good " + good.id() + ": a trader only sells plain items");
                }
                if (side == Side.BUYS && good.weeklyLimit() > 0) {
                    problems.add("good " + good.id() + ": weekly limits are for goods a trader sells");
                }
            }
        }
        return problems;
    }
}
