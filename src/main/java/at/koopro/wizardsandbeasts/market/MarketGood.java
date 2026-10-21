package at.koopro.wizardsandbeasts.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One line of a trader's book: something it buys from players or sells to them, as its location file declares it.
 *
 * <pre>{@code
 * { "id": "unicorn_hair", "item": "wizards_and_beasts:unicorn_hair", "price_knuts": 87, "depth": 12 }
 * { "id": "felix", "item": "wizards_and_beasts:brew", "brew": "felix_felicis", "price_knuts": 1479, "depth": 3 }
 * { "id": "artefacts", "kind": "treasure", "rate": 0.85, "depth": 6 }
 * { "id": "spare_wands", "kind": "wand", "condition": "unbonded", "price_knuts": 348, "depth": 6 }
 * }</pre>
 *
 * @param id          unique within its list; the board's buttons name it
 * @param kind        what is matched and how it is valued ({@link Kind})
 * @param item        the item, for {@link Kind#ITEM}
 * @param brew        for the brew item, the brew it must hold
 * @param priceKnuts  base price per unit before pressure ({@link Kind#ITEM}, {@link Kind#WAND})
 * @param rate        share of an artefact's worth paid ({@link Kind#TREASURE})
 * @param depth       units that halve (bought) or double (sold) the price; how deep the market is
 * @param minTier     the lowest standing tier at the location allowed to trade it
 * @param weeklyLimit units one player may buy of it per in-game week; 0 for no limit (sold goods only)
 * @param condition   for wands: {@code unbonded} (never chose a master) or {@code stolen} (another wizard's);
 *                    for treasure: {@code clean} or {@code any}
 */
@NullMarked
public record MarketGood(String id, Kind kind, Optional<Identifier> item, Optional<String> brew, long priceKnuts,
                         float rate, int depth, String minTier, int weeklyLimit, Optional<String> condition) {

    /** What a good is. */
    public enum Kind implements StringRepresentable {
        /** A plain item (with an optional brew): priced per unit from {@code price_knuts}. */
        ITEM,
        /** A recovered artefact ({@code treasure_artefact}): priced at {@code rate} of its own worth. */
        TREASURE,
        /** A finished wand: {@code price_knuts} × its wood's rarity × its condition. */
        WAND;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final String UNBONDED = "unbonded";
    public static final String STOLEN = "stolen";
    public static final String CLEAN = "clean";
    public static final String ANY = "any";

    public static final Codec<MarketGood> CODEC = RecordCodecBuilder.<MarketGood>create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(MarketGood::id),
            Kind.CODEC.optionalFieldOf("kind", Kind.ITEM).forGetter(MarketGood::kind),
            Identifier.CODEC.optionalFieldOf("item").forGetter(MarketGood::item),
            Codec.STRING.optionalFieldOf("brew").forGetter(MarketGood::brew),
            Codec.intRange(0, 493 * 1000).optionalFieldOf("price_knuts", 0).forGetter(g -> (int) g.priceKnuts()),
            Codec.floatRange(0.0f, 1.0f).optionalFieldOf("rate", 0.0f).forGetter(MarketGood::rate),
            Codec.intRange(1, 4096).optionalFieldOf("depth", 16).forGetter(MarketGood::depth),
            Codec.STRING.optionalFieldOf("min_tier", "").forGetter(MarketGood::minTier),
            Codec.intRange(0, 4096).optionalFieldOf("weekly_limit", 0).forGetter(MarketGood::weeklyLimit),
            Codec.STRING.optionalFieldOf("condition").forGetter(MarketGood::condition)
    ).apply(i, (id, kind, item, brew, price, rate, depth, tier, limit, condition) ->
            new MarketGood(id, kind, item, brew, price, rate, depth, tier, limit, condition)))
            .validate(MarketGood::checked);

    private static DataResult<MarketGood> checked(MarketGood good) {
        List<String> problems = good.problems();
        return problems.isEmpty() ? DataResult.success(good)
                : DataResult.error(() -> "good " + good.id() + ": " + String.join("; ", problems));
    }

    /** What the codec's field ranges cannot see. */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        if (!id.matches("[a-z0-9_]+")) {
            out.add("id must be lower_snake_case");
        }
        switch (kind) {
            case ITEM -> {
                if (item.isEmpty()) out.add("an item good names its item");
                if (priceKnuts <= 0) out.add("an item good has a price");
                if (condition.isPresent()) out.add("an item good takes no condition");
            }
            case TREASURE -> {
                if (item.isPresent() || brew.isPresent()) out.add("a treasure good matches any artefact");
                if (rate <= 0.0f) out.add("a treasure good pays a rate of the artefact's worth");
                String c = condition.orElse(CLEAN);
                if (!c.equals(CLEAN) && !c.equals(ANY)) out.add("treasure condition is clean or any");
            }
            case WAND -> {
                if (item.isPresent() || brew.isPresent()) out.add("a wand good matches any wand");
                if (priceKnuts <= 0) out.add("a wand good has a price");
                String c = condition.orElse("");
                if (!c.equals(UNBONDED) && !c.equals(STOLEN)) out.add("wand condition is unbonded or stolen");
            }
        }
        return out;
    }

    /** The treasure condition, defaulting to clean artefacts only. */
    public String treasureCondition() {
        return condition.orElse(CLEAN);
    }
}
