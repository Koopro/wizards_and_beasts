package at.koopro.wizardsandbeasts.settlement.npc;

import at.koopro.wizardsandbeasts.artefact.erised.Gazer;
import at.koopro.wizardsandbeasts.artefact.erised.VisionCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Who a role is: {@code data/<ns>/npc_roles/<id>.json}. A profession is this file, skins and some lang lines: one
 * skin per name ({@code settlement_npc/<id>_<n>.png} for {@code ….name.<n>}), or {@code <id>.png} for all. What a
 * role can do lives in the location it keeps, so there is no NPC-only copy of a shop, a board or a service.
 *
 * <pre>{@code
 * { "location": "wizards_and_beasts:apothecary",
 *   "names": ["npc.wizards_and_beasts.apothecary.name.0"],
 *   "greetings": [ { "text": "npc.wizards_and_beasts.apothecary.greet.default" },
 *                  { "text": "npc.wizards_and_beasts.apothecary.greet.vampire", "weight": 3,
 *                    "when": [ { "type": "condition", "condition": "vampirism" } ] } ],
 *   "topics": [ { "id": "mandrakes", "label": "...", "reply": "...", "when": [] } ],
 *   "shop": { "carry": 0.75, "stock": 8, "restock_days": 3, "goods": { "felix": { "carry": 0.25, "stock": 1 } } } }
 * }</pre>
 *
 * @param location  the location this role keeps
 * @param entity    who stands at the post; the shared settlement NPC unless named, as the goblin teller is
 * @param names     lang keys, one picked per person
 * @param greetings what they say on being spoken to, the eligible ones picked by weight
 * @param topics    what can be asked of them, shown only when eligible
 * @param shop      how this role stocks a shop at a site; none for a role that sells nothing or sells without limit
 */
@NullMarked
public record NpcRole(Identifier location, Optional<Identifier> entity, List<String> names, List<Line> greetings,
                      List<Topic> topics, Optional<Shop> shop) {

    /** Conditions are the Mirror's: one vocabulary for "what does the world know of this person". */
    private static final Codec<List<VisionCondition>> WHEN = VisionCondition.CODEC.listOf();

    /** One thing said. Always a lang key: a line reads in the listener's language, and takes the site's name as %s. */
    public record Line(String text, int weight, List<VisionCondition> when) {
        static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(Line::text),
                Codec.intRange(1, 1000).optionalFieldOf("weight", 1).forGetter(Line::weight),
                WHEN.optionalFieldOf("when", List.of()).forGetter(Line::when)
        ).apply(i, Line::new));

        public boolean heardBy(Gazer listener) {
            return when.stream().allMatch(condition -> condition.test(listener));
        }
    }

    /** One thing a player can ask. A topic is a label and a reply, not a branch: there is no conversation graph. */
    public record Topic(String id, String label, String reply, List<VisionCondition> when) {
        static final Codec<Topic> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Topic::id),
                Codec.STRING.fieldOf("label").forGetter(Topic::label),
                Codec.STRING.fieldOf("reply").forGetter(Topic::reply),
                WHEN.optionalFieldOf("when", List.of()).forGetter(Topic::when)
        ).apply(i, Topic::new));

        public boolean openTo(Gazer listener) {
            return when.stream().allMatch(condition -> condition.test(listener));
        }
    }

    /**
     * How one good is stocked.
     *
     * @param carry chance a site carries it at all
     * @param stock units on a full shelf, before the site's own variation
     */
    public record GoodRule(float carry, int stock) {
        static final Codec<GoodRule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.0f, 1.0f).fieldOf("carry").forGetter(GoodRule::carry),
                Codec.intRange(1, 512).fieldOf("stock").forGetter(GoodRule::stock)
        ).apply(i, GoodRule::new));
    }

    /**
     * A role's shelves.
     *
     * @param carry       chance a site carries any good not named in {@code goods}
     * @param stock       units on a full shelf of such a good
     * @param restockDays in-game days an empty shelf takes to fill
     * @param goods       goods stocked differently, by their id in the market
     */
    public record Shop(float carry, int stock, int restockDays, Map<String, GoodRule> goods) {
        static final Codec<Shop> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0.0f, 1.0f).optionalFieldOf("carry", 1.0f).forGetter(Shop::carry),
                Codec.intRange(1, 512).optionalFieldOf("stock", 16).forGetter(Shop::stock),
                Codec.intRange(1, 60).optionalFieldOf("restock_days", 3).forGetter(Shop::restockDays),
                Codec.unboundedMap(Codec.STRING, GoodRule.CODEC).optionalFieldOf("goods", Map.of()).forGetter(Shop::goods)
        ).apply(i, Shop::new));

        public GoodRule rule(String good) {
            GoodRule named = goods.get(good);
            return named != null ? named : new GoodRule(carry, stock);
        }
    }

    public static final Codec<NpcRole> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("location").forGetter(NpcRole::location),
            Identifier.CODEC.optionalFieldOf("entity").forGetter(NpcRole::entity),
            Codec.STRING.listOf().optionalFieldOf("names", List.of()).forGetter(NpcRole::names),
            Line.CODEC.listOf().optionalFieldOf("greetings", List.of()).forGetter(NpcRole::greetings),
            Topic.CODEC.listOf().optionalFieldOf("topics", List.of()).forGetter(NpcRole::topics),
            Shop.CODEC.optionalFieldOf("shop").forGetter(NpcRole::shop)
    ).apply(i, NpcRole::new));
}
