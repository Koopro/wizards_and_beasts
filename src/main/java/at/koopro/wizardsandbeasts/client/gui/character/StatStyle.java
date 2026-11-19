package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.stats.PlayerStat;
import org.jspecify.annotations.NullMarked;

/**
 * Each stat's own ink: the one place a stat's colour is decided, so the sheet, the HUD and the admin preview cannot
 * drift apart.
 *
 * <p>Inks are parchment inks, not lights: every one reads at 5:1 or better on {@code WizardsPalette.PAGE} and 4.2:1
 * on {@code PAGE_SHADE}. None is the page's rubric red or its good/bad greens, which already mean "heading", "better"
 * and "worse" on the same page.
 *
 * <ul>
 *   <li>Power: ember copper, the heat of a spell landing.</li>
 *   <li>Precision: verdigris, the patina on an instrument.</li>
 *   <li>Willpower: indigo, steady and deep.</li>
 *   <li>Reflexes: tarnished gold, quick metal.</li>
 *   <li>Knowledge: arcane plum, the scholar's ink.</li>
 * </ul>
 *
 * <p>Indexed by {@link PlayerStat#ordinal()} like {@link StatIcons}; a sixth stat without an entry falls back to the
 * page ink rather than throwing.
 */
@NullMarked
public final class StatStyle {

    private static final int[] INK = {
            0xFF8C3B10, // POWER
            0xFF1E5C58, // PRECISION
            0xFF2F3C7A, // WILLPOWER
            0xFF6A4E06, // REFLEXES
            0xFF5E2A72, // KNOWLEDGE
    };

    private static final int FALLBACK = 0xFF2E1F16; // WizardsPalette.PAGE_INK

    private StatStyle() {}

    /** The stat's ink: its name, its meter, its card spine. */
    public static int ink(PlayerStat stat) {
        int i = stat.ordinal();
        return i < INK.length ? INK[i] : FALLBACK;
    }

    /** The ink at {@code alpha} (0..255): card washes and the training hairline. */
    public static int wash(PlayerStat stat, int alpha) {
        return (alpha & 0xFF) << 24 | (ink(stat) & 0x00FFFFFF);
    }
}
