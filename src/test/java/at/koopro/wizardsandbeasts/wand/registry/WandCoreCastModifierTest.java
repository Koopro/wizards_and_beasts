package at.koopro.wizardsandbeasts.wand.registry;

import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the authored core cast contributions — the counterpart of {@link WandWoodCastModifierTest}.
 *
 * <p>Cores are the pillar that does the heavy lifting in {@code WandStatsResolver}, and they were the
 * last part of the wand to stop being a hardcoded switch. Until all ten carried a block, the resolver
 * kept a ten-case {@code WandCore} table as a fallback and read a neutral result as "not authored
 * yet". This test is what allows that fallback to stay deleted: if a core loses its block, or a new
 * one arrives without one, the silent consequence is that the core contributes nothing and the wand
 * still casts perfectly well.
 *
 * <p>The numbers are deliberately narrow. They were once the dominant component of a wand (0.88x to 1.25x
 * damage), which made a wand a stat stick; since 2026-09-17 a core's identity lives in its
 * {@code temperament} (how it bonds, how hard it is to win, what it resents), pinned by
 * {@code WandTemperamentLoreTest}, and these contributions span 0.95x (unicorn) to 1.10x (dragon
 * heartstring). If one moves, that is a balance decision and should be a deliberate one.
 *
 * <p>Category bonuses asked for as Transfiguration (thunderbird), Charms (veela) and Healing
 * (unicorn) are absent on purpose: {@link SpellCategory} has only {@code COMBAT}, {@code UTILITY},
 * {@code DEFENSE} and {@code DARK_ARTS}, and mapping a magical school onto one of those is the same
 * unmade balance ruling that keeps {@code spell_modifiers} unread.
 */
class WandCoreCastModifierTest {

    private static final Path CORES = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "wizards_and_beasts", "wand_cores");

    /** core id -> its authored contribution. All ten, so a new core cannot slip in unpinned. */
    private static final Map<String, WandCastModifiers> EXPECTED = Map.ofEntries(
            Map.entry("dragon_heartstring", new WandCastModifiers(1.10f, 1.00f, 1.00f, 0.02f, Map.of())),
            Map.entry("phoenix_feather", new WandCastModifiers(1.00f, 1.00f, 1.05f, 0.00f, Map.of())),
            // Healing omitted: no SpellCategory counterpart.
            Map.entry("unicorn_hair", new WandCastModifiers(0.95f, 1.00f, 1.00f, -0.05f, Map.of())),
            Map.entry("thestral_tail_hair", new WandCastModifiers(1.05f, 1.00f, 1.00f, 0.00f, Map.of())),
            // Charms omitted: no SpellCategory counterpart.
            Map.entry("veela_hair", new WandCastModifiers(1.05f, 1.00f, 1.00f, 0.04f, Map.of())),
            // Transfiguration omitted: no SpellCategory counterpart.
            Map.entry("thunderbird_tail_feather", new WandCastModifiers(1.08f, 1.00f, 1.00f, 0.00f, Map.of())),
            Map.entry("wampus_cat_hair", new WandCastModifiers(1.04f, 1.00f, 1.00f, 0.01f, Map.of())),
            Map.entry("troll_whisker", new WandCastModifiers(1.04f, 1.00f, 0.95f, 0.05f, Map.of())),
            Map.entry("rougarou_hair", new WandCastModifiers(1.00f, 1.00f, 1.00f, 0.03f,
                    Map.of(SpellCategory.DARK_ARTS, 0.10f))),
            Map.entry("white_river_monster_spine",
                    new WandCastModifiers(1.00f, 1.00f, 1.08f, -0.02f, Map.of())));

    @Test
    void everyCoreDefinition_decodes() throws IOException {
        Map<String, WandCoreDefinition> decoded = decodeAll();
        assertEquals(10, decoded.size(), "Expected 10 core definitions, found " + decoded.keySet());
    }

    @Test
    void everyCoreContributesItsAuthoredValues() throws IOException {
        Map<String, WandCoreDefinition> decoded = decodeAll();
        for (Map.Entry<String, WandCastModifiers> e : EXPECTED.entrySet()) {
            WandCoreDefinition def = decoded.get(e.getKey());
            assertTrue(def != null, "Missing core definition: " + e.getKey());
            assertEquals(e.getValue(), def.castModifiers(),
                    e.getKey() + " no longer contributes its authored values — if that was a balance "
                            + "change, update this table deliberately.");
        }
    }

    /**
     * No core ships neutral. This is the assertion that lets {@code WandStatsResolver} keep its enum
     * fallback deleted: a neutral core now contributes nothing at all, with nothing behind it.
     */
    @Test
    void noCoreShipsNeutral() throws IOException {
        List<String> neutral = new ArrayList<>();
        for (Map.Entry<String, WandCoreDefinition> e : decodeAll().entrySet()) {
            if (e.getValue().castModifiers().isNeutral()) {
                neutral.add(e.getKey());
            }
        }
        assertTrue(neutral.isEmpty(),
                "these cores contribute nothing to a cast: " + neutral);
    }

    /** Every core is pinned above, so a newly added one cannot arrive untested. */
    @Test
    void everyCoreOnDiskIsPinned() throws IOException {
        for (String id : decodeAll().keySet()) {
            assertTrue(EXPECTED.containsKey(id),
                    "core '" + id + "' has no expected contribution here — add it to EXPECTED");
        }
    }

    /**
     * The other direction, and the one that used to be broken. A wand stores its core as an
     * {@code Identifier} built from {@link WandCore#getDefinitionPath()}, so a constant whose
     * definition path names no file resolves to a registry miss and silently contributes nothing.
     * That is exactly how Thestral lost its core modifier, and it stayed invisible because the enum
     * fallback answered instead.
     */
    @Test
    void everyCoreConstant_hasADefinitionUnderTheIdTheCastPathUses() throws IOException {
        Map<String, WandCoreDefinition> decoded = decodeAll();
        List<String> missing = new ArrayList<>();
        for (WandCore core : WandCore.values()) {
            if (!decoded.containsKey(core.getDefinitionPath())) {
                missing.add(core.name() + " -> " + core.getDefinitionPath());
            }
        }
        assertTrue(missing.isEmpty(),
                "WandCore constants whose definition path names no wand_cores file, so they "
                        + "contribute nothing to a cast: " + missing);
    }

    /**
     * Still the component a wizard feels more than the wood, but no longer by a mile: a wand's character is its
     * temperament, not its damage multiplier. The spread is capped as well as floored, so the stat stick this
     * replaced cannot creep back in one balance pass at a time.
     */
    @Test
    void theCoreBandIsNarrowButWiderThanTheWoodBand() {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (WandCastModifiers mods : EXPECTED.values()) {
            min = Math.min(min, mods.damage());
            max = Math.max(max, mods.damage());
        }
        float spread = max - min;
        assertTrue(spread >= 0.10f, "cores span only " + spread + "x on damage; they should still out-spread wood");
        assertTrue(spread <= 0.20f, "cores span " + spread + "x on damage; a wand is not a stat stick "
                + "- put a core's identity in its temperament");
    }

    private static Map<String, WandCoreDefinition> decodeAll() throws IOException {
        assertTrue(Files.isDirectory(CORES), "Missing core definition directory: " + CORES);
        Map<String, WandCoreDefinition> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(CORES)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = path.getFileName().toString().replace(".json", "");
                JsonElement json = JsonParser.parseString(Files.readString(path));
                out.put(id, WandCoreDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(msg -> new AssertionError(path + ": " + msg)));
            }
        }
        return out;
    }
}
