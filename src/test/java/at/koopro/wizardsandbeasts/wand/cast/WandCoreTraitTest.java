package at.koopro.wizardsandbeasts.wand.cast;

import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules;
import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The core traits that used to be decoration -- consistency and dark affinity -- now act, a steady wand's negative
 * fizzle reaches the cast, and the two rare cores carry the trade-offs they were given (2026-10-22).
 */
class WandCoreTraitTest {

    private static final Path CORES = Path.of("src/main/resources/data/wizards_and_beasts/wizards_and_beasts/wand_cores");
    private static final Map<String, WandCoreDefinition> ALL = new HashMap<>();

    @BeforeAll
    static void decodeAll() throws IOException {
        try (Stream<Path> files = Files.list(CORES)) {
            for (Path f : files.toList()) {
                ALL.put(f.getFileName().toString().replace(".json", ""), WandCoreDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8))).getOrThrow());
            }
        }
    }

    @Test
    void consistencyAndDarkAffinityHaveTheDocumentedWeight() {
        assertEquals(-0.01f, WandStatsResolver.consistencyFizzle(0.95f), 1e-6f);
        assertEquals(0.01f, WandStatsResolver.consistencyFizzle(0.45f), 1e-6f);
        assertEquals(0.06f, WandStatsResolver.darkArtsBonus(0.8f), 1e-6f);
        assertEquals(-0.09f, WandStatsResolver.darkArtsBonus(0.05f), 1e-6f);
        assertEquals(1.2f, WandStatsResolver.corruptionRate(0.7f), 1e-6f);
        assertEquals(0.55f, WandStatsResolver.corruptionRate(0.05f), 1e-6f);
    }

    @Test
    void aSteadyWandOffsetsMisfireFromElsewhereButNeverBelowZero() {
        WandStats steady = new WandStats(1f, 1f, 1f, -0.06f, Map.of());
        ModifierStack stack = new ModifierStack();
        stack.addMisfireChance(0.10f, "unfamiliar spell");
        stack.addMisfireChance(steady.fizzleChance(), "wand");
        assertEquals(0.04f, stack.finalMisfireChance(), 1e-6f);

        ModifierStack calm = new ModifierStack();
        calm.addMisfireChance(steady.fizzleChance(), "wand");
        assertEquals(0.0f, calm.finalMisfireChance(), 0f, "a steady wand made misfire negative");
    }

    @Test
    void aDarkDrawnWandWarmsToDarkCastsAndBarelyToLightOnes() {
        var light = new WandAllegianceRules.CastCircumstances(false, false, false);
        var dark = new WandAllegianceRules.CastCircumstances(true, false, false);
        var rougarou = ALL.get("rougarou_hair").temperament();
        float afterLight = WandAllegianceRules.bondAfterSuccessfulCast(0.5f, rougarou, light) - 0.5f;
        float afterDark = WandAllegianceRules.bondAfterSuccessfulCast(0.5f, rougarou, dark) - 0.5f;
        assertTrue(afterLight > 0 && afterLight < WandAllegianceRules.BASE_BOND_GROWTH,
                "a light cast should deepen a rougarou bond, more slowly than an ordinary wand: " + afterLight);
        assertTrue(afterDark > 2 * afterLight, "a Dark cast should deepen it far faster: " + afterDark);

        assertTrue(WandAllegianceRules.bondAfterSuccessfulCast(0.5f, ALL.get("unicorn_hair").temperament(), dark) < 0.5f,
                "unicorn hair still resents the Dark Arts");
    }

    /** Rare is not strictly better: each rare core leads somewhere and pays for it somewhere else. */
    @Test
    void theRareCoresTradeRatherThanDominate() {
        WandCoreDefinition river = ALL.get("white_river_monster_spine");
        WandCoreDefinition rougarou = ALL.get("rougarou_hair");
        for (Map.Entry<String, WandCoreDefinition> e : ALL.entrySet()) {
            if (e.getValue() == river) {
                continue;
            }
            assertTrue(river.castModifiers().damage() >= e.getValue().castModifiers().damage(), "spine out-hit by " + e.getKey());
            assertTrue(river.castModifiers().range() > e.getValue().castModifiers().range(), "spine out-reached by " + e.getKey());
        }
        assertTrue(misfire(river) >= misfire(ALL.get("troll_whisker")), "the spine should be among the least steady");
        assertTrue(river.temperament().bondGrowth() < 1f && river.temperament().extraWins() > 0, "the spine bonds too easily");

        for (Map.Entry<String, WandCoreDefinition> e : ALL.entrySet()) {
            if (e.getValue() != rougarou) {
                assertTrue(dark(rougarou) > dark(e.getValue()), "rougarou hair is not the darkest core: " + e.getKey());
            }
        }
        assertTrue(misfire(rougarou) > 0, "rougarou hair should cost control");
        assertFalse(rougarou.castModifiers().damage() > 1f, "rougarou hair's power is Dark-only");
        assertTrue(WandStatsResolver.corruptionRate(rougarou.darkAffinity()) > 1f, "rougarou hair should corrupt faster");
    }

    private static float misfire(WandCoreDefinition d) {
        return d.castModifiers().fizzle() + WandStatsResolver.consistencyFizzle(d.consistency());
    }

    private static float dark(WandCoreDefinition d) {
        return d.castModifiers().categoryDamageBonus().getOrDefault(SpellCategory.DARK_ARTS, 0f)
                + WandStatsResolver.darkArtsBonus(d.darkAffinity());
    }
}
