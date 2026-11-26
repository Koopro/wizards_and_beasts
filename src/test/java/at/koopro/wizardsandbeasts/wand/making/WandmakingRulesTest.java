package at.koopro.wizardsandbeasts.wand.making;

import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every wood makes a wand with every core (owner, 2026-11-26), by rule. The per-pair recipe files were deleted the
 * same day; these checks pin the values they held, so the move changed no wand.
 */
class WandmakingRulesTest {

    private static final String NS = "wizards_and_beasts";
    private static final Path DEFS = Path.of("src", "main", "resources", "data", NS, NS);

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NS, path);
    }

    private static Map<Identifier, JsonObject> json(String dir) throws IOException {
        Map<Identifier, JsonObject> out = new TreeMap<>();
        try (Stream<Path> files = Files.list(DEFS.resolve(dir))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = f.getFileName().toString();
                out.put(id(name.substring(0, name.length() - 5)), JsonParser.parseString(Files.readString(f)).getAsJsonObject());
            }
        }
        return out;
    }

    private static Map<Identifier, WandWoodDefinition> woods() throws IOException {
        Map<Identifier, WandWoodDefinition> out = new TreeMap<>();
        json("wand_woods").forEach((k, v) -> out.put(k, WandWoodDefinition.CODEC.parse(JsonOps.INSTANCE, v).getOrThrow()));
        return out;
    }

    private static Map<Identifier, WandCoreDefinition> cores() throws IOException {
        Map<Identifier, WandCoreDefinition> out = new TreeMap<>();
        json("wand_cores").forEach((k, v) -> out.put(k, WandCoreDefinition.CODEC.parse(JsonOps.INSTANCE, v).getOrThrow()));
        return out;
    }

    private static WandmakingSpec spec(String wood, String core) throws IOException {
        return WandmakingRules.spec(id(wood), woods().get(id(wood)), id(core), cores().get(id(core)));
    }

    @Test
    void everyWoodPairsWithEveryCore() throws IOException {
        Map<Identifier, WandWoodDefinition> woods = woods();
        Map<Identifier, WandCoreDefinition> cores = cores();
        assertEquals(10, woods.size());
        assertEquals(10, cores.size());
        int pairs = 0;
        for (var w : woods.entrySet()) {
            for (var c : cores.entrySet()) {
                WandmakingSpec s = WandmakingRules.spec(w.getKey(), w.getValue(), c.getKey(), c.getValue());
                assertTrue(s.difficulty() > 0, s.toString());
                assertTrue(s.lengthMin() >= 8.5f && s.lengthMax() <= 15.0f && s.lengthMin() < s.lengthMax(), s.toString());
                pairs++;
            }
        }
        assertEquals(100, pairs);
    }

    /** Values copied from the deleted recipe files: wandmaking_<wood>_<core>.json. */
    @Test
    void theDeletedRecipeValuesAreUnchanged() throws IOException {
        record Old(String wood, String core, float tier, float min, float max) {}
        List<Old> old = List.of(
                new Old("holly", "phoenix_feather", 2.5f, 9.5f, 13.0f),
                new Old("ash", "unicorn_hair", 3.0f, 10.0f, 13.5f),
                new Old("ash", "phoenix_feather", 3.5f, 10.5f, 14.0f),
                new Old("willow", "troll_whisker", 2.6f, 8.9f, 11.9f),
                new Old("hawthorn", "wampus_cat_hair", 3.2f, 9.9f, 12.9f),
                new Old("yew", "rougarou_hair", 5.8f, 10.5f, 14.0f),
                new Old("blackthorn", "veela_hair", 4.6f, 11.2f, 14.5f),
                new Old("elder", "thunderbird_tail_feather", 7.8f, 14.0f, 15.0f),
                new Old("elder", "white_river_monster_spine", 9.8f, 14.0f, 15.0f));
        List<String> wrong = new ArrayList<>();
        for (Old o : old) {
            WandmakingSpec s = spec(o.wood(), o.core());
            if (Math.abs(s.difficulty() - o.tier()) > 1e-4 || Math.abs(s.lengthMin() - o.min()) > 1e-4
                    || Math.abs(s.lengthMax() - o.max()) > 1e-4) {
                wrong.add(o + " -> " + s);
            }
        }
        assertTrue(wrong.isEmpty(), "pairs changed by the move: " + wrong);
    }

    /** The stand-ins are for datapacks; a shipped definition relying on one is an authoring gap. */
    @Test
    void everyShippedDefinitionAuthorsItsMaking() throws IOException {
        List<String> missing = new ArrayList<>();
        json("wand_woods").forEach((k, v) -> { if (!v.has("making")) missing.add(k.toString()); });
        json("wand_cores").forEach((k, v) -> { if (!v.has("making")) missing.add(k.toString()); });
        assertTrue(missing.isEmpty(), "no making block: " + missing);
    }

    @Test
    void aDatapackWoodWithoutMakingPairsAtItsRaritysStandIn() throws IOException {
        WandWoodDefinition holly = woods().get(id("holly"));
        WandWoodDefinition bare = new WandWoodDefinition(holly.displayName(), List.of(), Map.of(), List.of(), "rare", 0.2f,
                holly.castModifiers(), holly.temperament(), holly.appearance());
        int pairs = 0;
        for (var c : cores().entrySet()) {
            WandmakingSpec s = WandmakingRules.spec(id("hornbeam"), bare, c.getKey(), c.getValue());
            assertEquals(WandmakingRules.round(3.6f + c.getValue().making().difficulty()), s.difficulty(), 1e-4);
            pairs++;
        }
        assertEquals(10, pairs, "a new wood pairs with every shipped core");
    }

    @Test
    void aDatapackCoreWithoutMakingUsesTheDefault() {
        WandCoreDefinition bare = WandCoreDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"display_name\":\"Kneazle whisker\",\"source_key\":\"kneazle\",\"raw_power\":1.0,"
                        + "\"consistency\":0.8,\"dark_affinity\":0.2}")).getOrThrow();
        assertEquals(CoreMaking.DEFAULT, bare.making());
    }

    /** Canon pairs from famous wands are complementary; everything else is neutral. */
    @Test
    void canonPairsAreComplementary() throws IOException {
        assertEquals(1, spec("holly", "phoenix_feather").affinity(), "Harry, PS ch.5");
        assertEquals(1, spec("yew", "phoenix_feather").affinity(), "Voldemort, PS ch.5");
        assertEquals(1, spec("vine", "dragon_heartstring").affinity(), "Hermione");
        assertEquals(1, spec("walnut", "dragon_heartstring").affinity(), "Bellatrix, DH ch.24");
        assertEquals(1, spec("hawthorn", "unicorn_hair").affinity(), "Draco, DH ch.24");
        assertEquals(1, spec("ash", "unicorn_hair").affinity(), "Cedric, GoF ch.18");
        assertEquals(1, spec("elder", "thestral_tail_hair").affinity(), "the Elder Wand");
        assertEquals(0, spec("holly", "dragon_heartstring").affinity());
    }

    /** An affinity naming a wood that does not exist is a silent typo: the pair would just read neutral. */
    @Test
    void everyAffinityNamesARealWood() throws IOException {
        var woods = woods().keySet();
        List<String> unknown = new ArrayList<>();
        cores().forEach((core, def) -> def.making().affinity().keySet().stream()
                .filter(w -> !woods.contains(w)).forEach(w -> unknown.add(core + " -> " + w)));
        assertTrue(unknown.isEmpty(), "affinity for unknown woods: " + unknown);
    }

    /**
     * Ollivander's core notes, as the marrying plays them: unicorn the steadiest of all, dragon the one that flares
     * most (jitter), phoenix the fastest and the most shifting (drift). Every shipped core authors its pulse.
     */
    @Test
    void corePulsesFollowOllivandersNotes() throws IOException {
        var cores = cores();
        json("wand_cores").forEach((k, v) -> assertTrue(v.getAsJsonObject("making").has("pulse"), k + " has no pulse"));
        CoreMaking.Pulse unicorn = cores.get(id("unicorn_hair")).making().pulse();
        CoreMaking.Pulse dragon = cores.get(id("dragon_heartstring")).making().pulse();
        CoreMaking.Pulse phoenix = cores.get(id("phoenix_feather")).making().pulse();
        for (WandCoreDefinition def : cores.values()) {
            CoreMaking.Pulse p = def.making().pulse();
            assertTrue(unicorn.jitter() <= p.jitter() && unicorn.drift() <= p.drift(), "unicorn is the steadiest");
            assertTrue(dragon.jitter() >= p.jitter(), "dragon flares most");
            assertTrue(phoenix.drift() >= p.drift(), "phoenix shifts most");
            assertTrue(phoenix.tempo() <= p.tempo(), "phoenix is the fastest");
        }
    }
}
