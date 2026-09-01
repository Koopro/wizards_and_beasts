package at.koopro.wizardsandbeasts.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the two ways the character sheet can start lying to a player.
 *
 * <p><b>Numbers.</b> The sheet prints a consequence beside every stat — "Spell Damage +2.6%". If that
 * percentage is worked out in the GUI, then the sheet and {@code SpellExecutor} are two
 * implementations of one curve and the first retune desynchronises them silently. Nothing would fail;
 * the screen would simply describe a game that no longer exists. Every figure below is asserted
 * against {@link StatEffects} directly.
 *
 * <p><b>Keys.</b> {@link StatReadout} and {@link StatTraining.Source} build their translation keys at
 * runtime from stat ids, so {@code LangParityTest}'s literal-key scan cannot see them — it explicitly
 * counts concatenated keys as unverifiable. A missing one renders as a raw key in a tooltip and would
 * ship. This walks the enums and checks every key they can produce.
 */
class StatReadoutTest {

    private static final String PREFIX = "gui.wizards_and_beasts.stat.";
    private static final String CAPPED_HERITAGE = PREFIX + "tooltip.capped_heritage";
    private static final String PRODIGY = PREFIX + "tooltip.prodigy";

    private static final Path GENERATED_EN_US = Path.of("src", "generated", "resources",
            "assets", "wizards_and_beasts", "lang", "en_us.json");
    private static final Path MAIN_EN_US = Path.of("src", "main", "resources",
            "assets", "wizards_and_beasts", "lang", "en_us.json");

    // ── the sheet's numbers are the game's numbers ───────────────────────────────────────────

    @Test
    void everyEffectValueIsDerivedFromStatEffects() {
        for (int value : new int[]{0, 1, 33, 50, 74, 99, 100}) {
            assertEquals(pct(StatEffects.damageMultiplier(value) - 1f),
                    text(PlayerStat.POWER, value), "POWER at " + value);
            assertEquals(pct(StatEffects.misfireDelta(value)),
                    text(PlayerStat.PRECISION, value), "PRECISION at " + value);
            assertEquals(pct(StatEffects.cooldownMultiplier(value) - 1f),
                    text(PlayerStat.REFLEXES, value), "REFLEXES at " + value);
            assertEquals(pct(StatEffects.tuitionMultiplier(value) - 1f),
                    text(PlayerStat.KNOWLEDGE, value), "KNOWLEDGE at " + value);
            assertEquals(String.format(java.util.Locale.ROOT, "x%.2f", StatEffects.resistScalar(value)),
                    text(PlayerStat.WILLPOWER, value), "WILLPOWER at " + value);
        }
    }

    /** The endpoints the design targets, as a player would read them off the sheet. */
    @Test
    void theEndpointsReadTheWayTheDesignPromises() {
        assertEquals("-10.0%", text(PlayerStat.POWER, 0));
        assertEquals("+20.0%", text(PlayerStat.POWER, 100));

        assertEquals("0.0%", text(PlayerStat.PRECISION, 0));
        assertEquals("-8.0%", text(PlayerStat.PRECISION, 100));

        assertEquals("+10.0%", text(PlayerStat.REFLEXES, 0));
        assertEquals("-12.0%", text(PlayerStat.REFLEXES, 100));

        assertEquals("x0.40", text(PlayerStat.WILLPOWER, 0));
        assertEquals("x1.00", text(PlayerStat.WILLPOWER, 100));

        assertEquals("0.0%", text(PlayerStat.KNOWLEDGE, 0));
        assertEquals("-40.0%", text(PlayerStat.KNOWLEDGE, 100));
    }

    @Test
    void aStatSittingOnNeutralNeverReadsAsMinusZero() {
        // Precision 0 and Knowledge 0 are both exactly neutral. "-0.0%" is what naive formatting
        // produces there, and it reads as a bug rather than as nothing happening.
        for (PlayerStat stat : PlayerStat.values()) {
            for (int value = 0; value <= 100; value++) {
                assertNotEquals("-0.0%", text(stat, value), stat + " at " + value);
            }
        }
    }

    @Test
    void betterIsGreenAndWorseIsRedInEveryChannel() {
        assertEquals(ChatFormatting.RED, StatReadout.effectTone(PlayerStat.POWER, 0));
        assertEquals(ChatFormatting.GREEN, StatReadout.effectTone(PlayerStat.POWER, 100));
        // Cooldown crosses neutral partway up, so its low end must read as the penalty it is.
        assertEquals(ChatFormatting.RED, StatReadout.effectTone(PlayerStat.REFLEXES, 0));
        assertEquals(ChatFormatting.GREEN, StatReadout.effectTone(PlayerStat.REFLEXES, 100));
        assertEquals(ChatFormatting.GRAY, StatReadout.effectTone(PlayerStat.PRECISION, 0));
        assertEquals(ChatFormatting.GREEN, StatReadout.effectTone(PlayerStat.PRECISION, 100));
        assertEquals(ChatFormatting.GRAY, StatReadout.effectTone(PlayerStat.WILLPOWER, 0));
        assertEquals(ChatFormatting.GREEN, StatReadout.effectTone(PlayerStat.WILLPOWER, 100));
        assertEquals(ChatFormatting.GRAY, StatReadout.effectTone(PlayerStat.KNOWLEDGE, 0));
        assertEquals(ChatFormatting.GREEN, StatReadout.effectTone(PlayerStat.KNOWLEDGE, 100));
    }

    // ── the tooltip says the things it exists to say ─────────────────────────────────────────

    @Test
    void everyStatProducesANonEmptyCardAtEveryValue() {
        for (PlayerStat stat : PlayerStat.values()) {
            for (int value = 0; value <= 100; value += 7) {
                List<Component> card = StatReadout.tooltip(stat, value, 0.5f, 100, false);
                assertTrue(card.size() >= 4, stat + " produced a card of " + card.size() + " lines");
                assertEquals(stat.displayName().getString(), card.get(0).getString(),
                        stat + "'s card does not lead with its own name");
            }
        }
    }

    @Test
    void aCappedPowerCardSaysWhyItIsCapped() {
        Set<String> uncapped = keysIn(StatReadout.tooltip(PlayerStat.POWER, 40, 0f, 85, false));
        List<Component> cappedCard = StatReadout.tooltip(PlayerStat.POWER, 40, 0f, 40, false);
        Set<String> capped = keysIn(cappedCard);

        assertFalse(uncapped.contains(CAPPED_HERITAGE),
                "a Power well short of its band was reported as capped");
        assertTrue(capped.contains(CAPPED_HERITAGE),
                "a Power sitting on its heritage band said nothing about it");
        assertTrue(literalsIn(cappedCard).contains("40"), "the cap card does not name the ceiling");
    }

    @Test
    void onlyProdigyCardsMentionProdigy() {
        assertFalse(keysIn(StatReadout.tooltip(PlayerStat.POWER, 50, 0f, 85, false)).contains(PRODIGY));
        assertTrue(keysIn(StatReadout.tooltip(PlayerStat.POWER, 50, 0f, 85, true)).contains(PRODIGY));
        // Prodigy is a property of the Power roll, so it has no business on the other four cards.
        for (PlayerStat stat : PlayerStat.values()) {
            if (stat == PlayerStat.POWER) continue;
            assertFalse(keysIn(StatReadout.tooltip(stat, 50, 0.5f, 100, true)).contains(PRODIGY),
                    stat + " claims prodigy, which only ever describes the Power roll");
        }
    }

    @Test
    void aTrainableCardListsWhatTrainsItAndAnInnateOneSaysItCannotBeTrained() {
        for (PlayerStat stat : PlayerStat.values()) {
            Set<String> card = keysIn(StatReadout.tooltip(stat, 30, 0.4f, 100, false));
            if (stat.isDerived()) {
                assertTrue(card.contains(PREFIX + "tooltip.derived"),
                        stat + " does not say it is derived");
                assertFalse(card.contains(PREFIX + "tooltip.training"),
                        stat + " is derived but shows a training bar");
            } else if (stat.isTrainable()) {
                assertTrue(card.contains(PREFIX + "tooltip.training"),
                        stat + " shows no training progress");
                assertTrue(card.contains(PREFIX + "tooltip.trained_by"),
                        stat + " does not say what trains it");
                for (StatTraining.Source source : StatTraining.Source.forStat(stat)) {
                    assertTrue(card.contains(source.descriptionKey()),
                            stat + " does not list " + source + " among its training sources");
                }
            } else {
                assertTrue(card.contains(PREFIX + "tooltip.innate"),
                        stat + " does not say it cannot be trained");
            }
        }
    }

    // ── every runtime-built key resolves ─────────────────────────────────────────────────────

    @Test
    void everyKeyTheStatUiCanBuildExistsInEnUs() throws IOException {
        Map<String, String> enUs = mergedEnUs();
        var missing = new TreeSet<String>();

        for (PlayerStat stat : PlayerStat.values()) {
            require(enUs, missing, "stat.wizards_and_beasts." + stat.getId());
            require(enUs, missing, "gui.wizards_and_beasts.stat.effect." + stat.getId());
            require(enUs, missing, "gui.wizards_and_beasts.stat.desc." + stat.getId());
        }
        for (StatTraining.Source source : StatTraining.Source.values()) {
            require(enUs, missing, source.descriptionKey());
        }
        for (MilestoneType type : MilestoneType.values()) {
            require(enUs, missing, StatMilestones.sourceKey(type));
        }
        for (String id : new String[]{"stats", "attributes", "wand", "wand_affinity", "currency"}) {
            require(enUs, missing, "gui.wizards_and_beasts.character_sheet.section." + id);
        }
        for (String id : new String[]{"wood", "core", "flexibility", "length", "integrity", "allegiance"}) {
            require(enUs, missing, "gui.wizards_and_beasts.character_sheet.wand." + id);
        }
        for (String id : new String[]{"galleons", "sickles", "knuts"}) {
            require(enUs, missing, "gui.wizards_and_beasts.character_sheet.currency." + id);
        }

        assertTrue(missing.isEmpty(),
                () -> "translation keys the stat UI builds at runtime but en_us does not have:\n  "
                        + String.join("\n  ", missing));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private static void require(Map<String, String> enUs, TreeSet<String> missing, String key) {
        if (!enUs.containsKey(key)) missing.add(key);
    }

    private static String text(PlayerStat stat, int value) {
        return StatReadout.effectValue(stat, value).getString();
    }

    private static String pct(float fraction) {
        float p = fraction * 100f;
        if (Math.abs(p) < 0.05f) return "0.0%";
        return String.format(java.util.Locale.ROOT, "%+.1f%%", p);
    }

    /**
     * Every translation key a card uses, including nested ones.
     *
     * <p>Asserted on rather than the rendered English, because there is no language bound in a unit
     * JVM -- {@code getString()} on a translatable hands back the key itself, so a test written
     * against English words would really be testing that fallback. Keys are what the card is built
     * from, and they survive the day someone rewords the copy.
     */
    private static Set<String> keysIn(List<Component> lines) {
        Set<String> keys = new LinkedHashSet<>();
        for (Component line : lines) {
            collectKeys(line, keys);
        }
        return keys;
    }

    private static void collectKeys(Component component, Set<String> into) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            into.add(translatable.getKey());
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component nested) {
                    collectKeys(nested, into);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            collectKeys(sibling, into);
        }
    }

    /** The literal text of a card -- the numbers, rather than the copy around them. */
    private static String literalsIn(List<Component> lines) {
        StringBuilder out = new StringBuilder();
        for (Component line : lines) {
            out.append(line.getString()).append('\n');
        }
        return out.toString();
    }

    private static Map<String, String> mergedEnUs() throws IOException {
        Map<String, String> merged = new LinkedHashMap<>(readLang(GENERATED_EN_US));
        merged.putAll(readLang(MAIN_EN_US));
        return merged;
    }

    private static Map<String, String> readLang(Path path) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        if (!Files.exists(path)) return entries;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            json.entrySet().forEach(e -> entries.put(e.getKey(), e.getValue().getAsString()));
        }
        return entries;
    }
}
