package at.koopro.wizardsandbeasts.wand;

import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wand tooltip's cast rows.
 *
 * <p>Two things here are easy to get backwards and invisible when you do. A lower cooldown is
 * <em>better</em> while a lower damage multiplier is worse, so the sign and the colour do not agree
 * for every row; and a contribution that rounds to zero has to be omitted rather than printed as
 * {@code +0%}, or a neutral wand grows four meaningless lines.
 */
class WandCastLinesTest {

    private static WandStats stats(float damage, float cooldown, float range, float fizzle,
                                   Map<SpellCategory, Float> categories) {
        return new WandStats(damage, cooldown, range, fizzle, categories);
    }

    /**
     * The rendered value of each line — its translation argument, not {@code getString()}.
     * No language is loaded in a unit test, so {@code getString()} returns the raw key and every
     * assertion against the visible text would pass or fail for the wrong reason.
     */
    private static List<String> text(WandStats s) {
        return WandCastLines.build(s).stream().map(WandCastLinesTest::rendered).toList();
    }

    private static String rendered(Component line) {
        if (line.getContents() instanceof TranslatableContents t) {
            StringBuilder sb = new StringBuilder(t.getKey());
            for (Object arg : t.getArgs()) {
                sb.append(' ').append(arg instanceof Component c ? key(c) : String.valueOf(arg));
            }
            return sb.toString();
        }
        return line.getString();
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static TextColor colourOf(Component line) {
        return line.getStyle().getColor();
    }

    @Test
    void aNeutralWandProducesNoLines() {
        assertEquals(List.of(), WandCastLines.build(WandStats.NEUTRAL),
                "a wand that contributes nothing must say nothing, not print four zeroes");
    }

    @Test
    void aSubOnePercentContributionIsOmittedRatherThanRoundedToZero() {
        List<Component> lines = WandCastLines.build(stats(1.004f, 1.0f, 1.0f, 0.0f, Map.of()));
        assertEquals(List.of(), lines, "+0% is not worth a line");
    }

    @Test
    void multipliersRenderAsSignedPercentages() {
        List<String> lines = text(stats(1.20f, 0.85f, 1.15f, 0.04f, Map.of()));
        assertEquals(List.of(
                "wandcraft.tooltip.cast.damage +20%",
                "wandcraft.tooltip.cast.cooldown -15%",
                "wandcraft.tooltip.cast.range +15%",
                "wandcraft.tooltip.cast.misfire +4%"), lines);
    }

    /** Elder's real authored values, as a spot-check that the table survives the arithmetic. */
    @Test
    void elderWoodReadsAsAuthored() {
        List<String> lines = text(stats(1.20f, 0.85f, 1.15f, -0.03f, Map.of()));
        assertEquals(List.of(
                "wandcraft.tooltip.cast.damage +20%",
                "wandcraft.tooltip.cast.cooldown -15%",
                "wandcraft.tooltip.cast.range +15%"), lines,
                "elder authors fizzle -0.03, but WandStats clamps fizzleChance to [0,1], so its net "
                        + "misfire contribution really is zero and must not be claimed as a benefit");
    }

    /**
     * A net-negative misfire contribution is not representable, and must not be shown as one.
     *
     * <p>{@code WandStats}'s compact constructor clamps {@code fizzleChance} to [0, 1] — there is no
     * such thing as less than no chance to misfire — so a wand whose fizzle sums below zero carries
     * exactly zero. The cast pipeline reads the same clamped value, so omitting the row keeps the
     * tooltip and the cast in agreement. A negative contribution still does real work inside the
     * builder, by cancelling a positive one from the other pillar.
     */
    @Test
    void aNetNegativeMisfireContributionShowsNoRow() {
        assertEquals(List.of(), text(stats(1.0f, 1.0f, 1.0f, -0.06f, Map.of())));
        // ...but it cancels a positive one, and the remainder is shown.
        assertEquals(List.of("wandcraft.tooltip.cast.misfire +2%"),
                text(stats(1.0f, 1.0f, 1.0f, 0.02f, Map.of())));
    }

    @Test
    void lowerCooldownIsGoodAndHigherCooldownIsBad() {
        Component faster = WandCastLines.build(stats(1.0f, 0.85f, 1.0f, 0.0f, Map.of())).get(0);
        Component slower = WandCastLines.build(stats(1.0f, 1.05f, 1.0f, 0.0f, Map.of())).get(0);

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), colourOf(faster),
                "a shorter cooldown is a benefit — a negative number here must not read as a penalty");
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_RED), colourOf(slower));
    }

    @Test
    void moreMisfireChanceIsBad() {
        Component risky = WandCastLines.build(stats(1.0f, 1.0f, 1.0f, 0.05f, Map.of())).get(0);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_RED), colourOf(risky));
    }

    @Test
    void lowerDamageIsBad() {
        Component weak = WandCastLines.build(stats(0.92f, 1.0f, 1.0f, 0.0f, Map.of())).get(0);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_RED), colourOf(weak));
        assertTrue(rendered(weak).contains("-8%"), rendered(weak));
    }

    /** Rowan carries both a positive and a negative category bonus — the only wand that does. */
    @Test
    void categoryBonusesRenderWithTheirOwnSignAndTone() {
        List<Component> lines = WandCastLines.build(stats(1.0f, 1.0f, 1.0f, 0.0f,
                Map.of(SpellCategory.DEFENSE, 0.15f, SpellCategory.DARK_ARTS, -0.10f)));
        assertEquals(2, lines.size());

        Component defense = lines.stream()
                .filter(l -> rendered(l).contains("+15%")).findFirst().orElseThrow();
        Component darkArts = lines.stream()
                .filter(l -> rendered(l).contains("-10%")).findFirst().orElseThrow();
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), colourOf(defense));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_RED), colourOf(darkArts));
    }

    /** Enum order, not map order: two identical wands must list their bonuses the same way round. */
    @Test
    void categoryOrderIsStable() {
        Map<SpellCategory, Float> bonuses = Map.of(
                SpellCategory.DARK_ARTS, 0.15f,
                SpellCategory.COMBAT, 0.10f,
                SpellCategory.DEFENSE, 0.12f);
        List<String> first = text(stats(1.0f, 1.0f, 1.0f, 0.0f, bonuses));
        for (int i = 0; i < 20; i++) {
            assertEquals(first, text(stats(1.0f, 1.0f, 1.0f, 0.0f, bonuses)));
        }
        // COMBAT, DEFENSE, DARK_ARTS is the declaration order in SpellCategory.
        assertTrue(first.get(0).contains("+10%"), first.toString());
        assertTrue(first.get(2).contains("+15%"), first.toString());
    }

    /**
     * The sign must be an ASCII hyphen. A typographic minus is outside Minecraft's bitmap font
     * providers and falls through to unifont, so it can render as a missing-glyph box — on the one
     * character that says whether a number helps or hurts.
     */
    @Test
    void theMinusSignIsOneMinecraftCanDraw() {
        String rendered = String.join(" ", text(stats(0.88f, 1.05f, 1.0f, 0.05f,
                Map.of(SpellCategory.DARK_ARTS, -0.10f))));
        assertTrue(rendered.contains("-"), "expected an ASCII hyphen: " + rendered);
        assertFalse(rendered.contains("−"), "typographic minus U+2212 is not in a bitmap provider");
        for (char c : rendered.toCharArray()) {
            assertTrue(c < 0x80, String.format("non-ASCII U+%04X in a cast row: %s", (int) c, rendered));
        }
    }
}
