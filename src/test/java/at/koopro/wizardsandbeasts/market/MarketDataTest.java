package at.koopro.wizardsandbeasts.market;

import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.owl.career.track.CareerTrack;
import at.koopro.wizardsandbeasts.owl.career.track.ContractDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped trader's book, joined against the items, brews, tags and contracts it names, and against every other
 * trader. The joins that matter for the economy:
 *
 * <ul>
 *   <li><b>no arbitrage</b> — nothing can be bought from one trader and sold to another (on its best day) for more;</li>
 *   <li><b>no contract arbitrage</b> — no delivery contract pays more than buying its goods would cost;</li>
 *   <li><b>the law holds</b> — a lawful counter lists no contraband, stolen goods or treasure;</li>
 *   <li><b>a bounded faucet</b> — the most every lawful good together can pay the whole server per day is pinned, so a
 *       data change that doubles it is a deliberate one.</li>
 * </ul>
 */
class MarketDataTest {

    private static final Path DATA = Path.of("src/main/resources/data/wizards_and_beasts");
    private static final Path LANG = Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json");
    private static final String NS = "wizards_and_beasts";

    private static final Map<String, LocationDefinition> LOCATIONS = new LinkedHashMap<>();
    private static final List<CareerTrack> CAREERS = new ArrayList<>();
    private static Set<String> contraband;
    private static Set<String> restricted;
    private static Set<String> lang;

    @BeforeAll
    static void load() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (Stream<Path> files = Files.list(DATA.resolve("locations"))) {
            for (Path file : files.sorted().toList()) {
                LOCATIONS.put(file.getFileName().toString().replace(".json", ""),
                        LocationDefinition.CODEC.parse(JsonOps.INSTANCE, read(file)).getOrThrow());
            }
        }
        try (Stream<Path> files = Files.list(DATA.resolve("career_tracks"))) {
            for (Path file : files.sorted().toList()) {
                CAREERS.add(CareerTrack.CODEC.parse(JsonOps.INSTANCE, read(file)).getOrThrow());
            }
        }
        contraband = tag("contraband");
        restricted = tag("restricted_ingredient");
        lang = read(LANG).keySet();
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Set<String> tag(String name) throws IOException {
        Set<String> out = new HashSet<>();
        for (JsonElement value : read(DATA.resolve("tags/item/" + name + ".json")).getAsJsonArray("values")) {
            out.add(value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString());
        }
        return out;
    }

    private static Map<String, MarketDefinition> markets() {
        Map<String, MarketDefinition> out = new LinkedHashMap<>();
        LOCATIONS.forEach((id, def) -> def.market().ifPresent(market -> out.put(id, market)));
        return out;
    }

    /** One good's identity across traders: the item, and the brew it holds if any. */
    private static String identity(MarketGood good) {
        return good.item().map(Identifier::toString).orElse("") + good.brew().map(b -> "#" + b).orElse("");
    }

    @Test
    void theTradersExistAndValidate() {
        for (String id : List.of("apothecary", "ollivanders", "ministry", "knockturn_dealer")) {
            assertTrue(LOCATIONS.containsKey(id) && LOCATIONS.get(id).market().isPresent(), id + " keeps no market");
        }
        LOCATIONS.forEach((id, def) -> assertEquals(List.of(), def.validate(), id));
        assertEquals(Module.MINISTRY, LOCATIONS.get("knockturn_dealer").module(), "a fence outside the Ministry module");
        assertTrue(LOCATIONS.get("knockturn_dealer").risk().isPresent(), "the fence carries no risk");
        assertTrue(LOCATIONS.get("apothecary").risk().isEmpty() && LOCATIONS.get("ollivanders").risk().isEmpty(),
                "a lawful trader carries a risk");
    }

    @Test
    void everyGoodNamesARealItemAndBrew() {
        markets().forEach((id, market) -> {
            for (MarketDefinition.Side side : MarketDefinition.Side.values()) {
                for (MarketGood good : market.side(side)) {
                    good.item().ifPresent(item -> assertTrue(BuiltInRegistries.ITEM.containsKey(item),
                            id + " " + good.id() + ": no item " + item));
                    good.brew().ifPresent(brew -> assertTrue(
                            Files.exists(DATA.resolve("brews/" + Identifier.parse(brew).getPath() + ".json")),
                            id + " " + good.id() + ": no brew " + brew));
                }
            }
        });
    }

    @Test
    void lawfulCountersListNoContrabandStolenGoodsOrTreasure() {
        LOCATIONS.forEach((id, def) -> {
            if (def.market().isEmpty() || def.risk().isPresent()) {
                return;
            }
            for (MarketDefinition.Side side : MarketDefinition.Side.values()) {
                for (MarketGood good : def.market().get().side(side)) {
                    assertFalse(good.item().map(i -> contraband.contains(i.toString())).orElse(false),
                            id + " lists contraband " + good.id());
                    assertFalse(good.kind() == MarketGood.Kind.TREASURE, id + " buys treasure past the bank's claim");
                    assertFalse(MarketGood.STOLEN.equals(good.condition().orElse("")), id + " buys stolen wands");
                }
            }
        });
        // The fence's reason to exist: it deals in something the law will not.
        MarketDefinition fence = LOCATIONS.get("knockturn_dealer").market().orElseThrow();
        assertTrue(fence.buys().stream().anyMatch(g -> g.item().map(i -> contraband.contains(i.toString())).orElse(false)));
        assertTrue(fence.buys().stream().anyMatch(g -> g.item().map(i -> restricted.contains(i.toString())).orElse(false)));
    }

    @Test
    void nothingCanBeBoughtCheaperThanAnyTraderPaysForIt() {
        // The cheapest any trader ever asks (pressure 0) against the most any trader ever pays (pressure 0, its
        // short good of the day). Equal scales on both sides, so the server's price scale cannot open a gap.
        Map<String, Double> cheapestAsk = new HashMap<>();
        Map<String, Double> bestBid = new HashMap<>();
        Map<String, String> where = new HashMap<>();
        markets().forEach((id, market) -> {
            for (MarketGood good : market.sells()) {
                cheapestAsk.merge(identity(good), (double) good.priceKnuts(), Math::min);
            }
            for (MarketGood good : market.buys()) {
                if (good.kind() == MarketGood.Kind.ITEM) {
                    double bid = good.priceKnuts() * (1.0 + market.demandBonus());
                    if (bid > bestBid.getOrDefault(identity(good), 0.0)) {
                        bestBid.put(identity(good), bid);
                        where.put(identity(good), id);
                    }
                }
            }
        });
        cheapestAsk.forEach((good, ask) -> {
            double bid = bestBid.getOrDefault(good, 0.0);
            assertTrue(ask > bid, good + ": sold for " + ask + " but " + where.get(good) + " pays up to " + bid);
        });
    }

    @Test
    void noDeliveryContractPaysMoreThanItsGoodsCost() {
        Map<String, Long> cheapestAsk = new HashMap<>();
        markets().forEach((id, market) -> {
            for (MarketGood good : market.sells()) {
                if (good.brew().isEmpty()) {
                    cheapestAsk.merge(identity(good), good.priceKnuts(), Math::min);
                }
            }
        });
        List<ContractDefinition> contracts = new ArrayList<>();
        LOCATIONS.values().forEach(def -> contracts.addAll(def.standing().contracts()));
        CAREERS.forEach(track -> contracts.addAll(track.contracts()));
        for (ContractDefinition contract : contracts) {
            if (contract.objective() instanceof ContractDefinition.Delivery delivery) {
                Long ask = cheapestAsk.get(delivery.item().toString());
                if (ask != null) {
                    assertTrue(contract.reward().knuts() < ask * delivery.count(), contract.id() + " pays "
                            + contract.reward().knuts() + " for goods that cost " + ask * delivery.count());
                }
            }
        }
    }

    @Test
    void theLawfulFaucetIsBounded() {
        // The most selling every lawful plain good as fast as anyone likes could pay the whole server per day, at
        // the shipped scales. Supply (grooming cooldowns, harvest gates, brewing time) keeps real income far below it.
        double total = 0.0;
        for (Map.Entry<String, MarketDefinition> entry : markets().entrySet()) {
            if (LOCATIONS.get(entry.getKey()).risk().isPresent()) {
                continue;
            }
            MarketDefinition market = entry.getValue();
            for (MarketGood good : market.buys()) {
                if (good.kind() == MarketGood.Kind.ITEM) {
                    total += MarketPricing.dailyIncomeCeiling(good.priceKnuts(), good.depth(), market.halfLifeDays());
                }
            }
        }
        // Pinned (documentation/ECONOMY.md §6): raising it is a deliberate economy change, not a data accident.
        assertTrue(total < 25_000, "the lawful markets can pay " + Math.round(total) + " Knuts a day");
        assertTrue(total > 2_000, "the lawful markets pay almost nothing: " + Math.round(total));
    }

    @Test
    void everyMarketWordExists() {
        for (String key : List.of("market.wizards_and_beasts.good.treasure.clean",
                "market.wizards_and_beasts.good.treasure.any", "market.wizards_and_beasts.good.wand.unbonded",
                "market.wizards_and_beasts.good.wand.stolen", "location.wizards_and_beasts.gui.buys",
                "location.wizards_and_beasts.gui.sells", "market.wizards_and_beasts.illicit.reported")) {
            assertTrue(lang.contains(key), key);
        }
        LOCATIONS.values().forEach(def -> def.services().forEach(offer -> assertTrue(lang.contains(
                "location." + NS + ".service." + offer.service().getPath()), offer.service().toString())));
    }

    @Test
    void aCommissionCostsMoreThanAnyWandSells() {
        // The best-paid finished wand anywhere, on a trader's best day, per unit of wood rarity.
        double bestWand = 0.0;
        for (MarketDefinition market : markets().values()) {
            for (MarketGood good : market.buys()) {
                if (good.kind() == MarketGood.Kind.WAND) {
                    bestWand = Math.max(bestWand, good.priceKnuts() * (1.0 + market.demandBonus()));
                }
            }
        }
        assertTrue(bestWand > 0.0, "no trader buys wands any more: the pin below proves nothing");
        boolean offered = false;
        for (LocationDefinition def : LOCATIONS.values()) {
            for (var offer : def.services()) {
                if (!offer.service().getPath().equals("wand_commission")) {
                    continue;
                }
                offered = true;
                int ranks = def.standing().ranks().size();
                long cheapest = offer.feeFor(ranks);
                assertTrue(cheapest > bestWand, "a commission at " + cheapest + " K undercuts a wand that sells for "
                        + bestWand + " K: commission to sell would print money");
                for (String rarity : List.of("common", "uncommon", "rare")) {
                    assertTrue(at.koopro.wizardsandbeasts.location.ollivanders.WandCommission.priceOf(cheapest, rarity)
                            > bestWand * MarketGoods.rarityMultiplier(rarity), rarity);
                }
                assertEquals(0L, at.koopro.wizardsandbeasts.location.ollivanders.WandCommission
                        .priceOf(cheapest, "legendary"), "an Elder-grade wood was priced for order");
            }
        }
        assertTrue(offered, "no location offers wand commissions");
    }
}
