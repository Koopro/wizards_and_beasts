package at.koopro.wizardsandbeasts.client.gui.ui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The material a piece of UI is drawn on, and the inks that read on it.
 *
 * <p>Every shared component ({@link Ui}, {@link UiText}) takes a surface instead of a colour, which is
 * the whole point: a label drawn on parchment and the same label drawn on the night card pick their
 * own ink, and nobody types {@code 0xFF...} into a screen again. Before this, four screens drew light
 * HUD text on light paper because their colours were chosen against the leather wand HUD.
 *
 * <p>Three families:
 * <ul>
 *   <li>{@link #PAGE} -- the default parchment ({@code gui/theme/}). Every player screen that has no
 *       material of its own.</li>
 *   <li>{@link #NIGHT} -- the dark magical card: soot-violet field, gilt edge, cream ink. Tooltip cards,
 *       radial wheels, scrims, anything floating over the world rather than lying on a desk. The same
 *       family as the Control Center's Ministry frame ({@code AdminTheme}), one step less purple.</li>
 *   <li>{@link #of(WizardsPalette.GuiSkin)} -- a screen's own material (star chart, workbench, ledger...),
 *       its inks taken from the skin so a skinned screen never borrows parchment colours.</li>
 * </ul>
 *
 * <p>Text shadow is a property of the surface, not of the call site: shadows on paper look like a
 * printing fault, and on the dark card the lack of one makes thin glyphs fizz. {@code UiSurfaceTest}
 * holds every surface's inks to their contrast floors.
 *
 * @param field      the body of a panel
 * @param well       a recessed area on it: lists, viewports, bar tracks
 * @param edge       hairlines and dividers drawn procedurally
 * @param ink        body text and values
 * @param ink2       labels, secondary text
 * @param ink3       captions, hints, placeholders -- never anything a player must read to act
 * @param heading    section headings
 * @param good       a good outcome or bonus
 * @param bad        a bad outcome, an error
 * @param warn       caution: allowed, but think first
 * @param accent     highlights, focus rings, the active tab's underline
 * @param disabled   text of something that cannot be used right now
 * @param selectWash translucent wash for the selected row
 * @param hoverWash  translucent wash for the row under the mouse
 * @param shadow     whether text on this surface is drawn with a drop shadow
 */
@NullMarked
public record UiSurface(int field, int well, int edge,
                        int ink, int ink2, int ink3, int heading,
                        int good, int bad, int warn, int accent, int disabled,
                        int selectWash, int hoverWash, boolean shadow,
                        WizardsPalette.@Nullable GuiSkin skin) {

    /** The default parchment page. */
    public static final UiSurface PAGE = new UiSurface(
            WizardsPalette.PAGE, WizardsPalette.PAGE_SHADE, WizardsPalette.PAGE_DEEP,
            WizardsPalette.PAGE_INK, WizardsPalette.PAGE_INK_2, WizardsPalette.PAGE_INK_3, WizardsPalette.PAGE_RUBRIC,
            WizardsPalette.PAGE_GOOD, WizardsPalette.PAGE_BAD, WizardsPalette.GILT_DARK, WizardsPalette.GILT_DARK,
            WizardsPalette.PAGE_INK_3,
            wash(WizardsPalette.PAGE_SELECT, 0xAA), wash(WizardsPalette.PAGE_DEEP, 0x44), false, null);

    /** Soot-violet field of the night card. Opaque enough to read over a bright sky, never fully. */
    public static final int NIGHT_FIELD = 0xF01C1722;
    public static final int NIGHT_WELL = 0xFF120F16;

    /** The dark magical card. */
    public static final UiSurface NIGHT = new UiSurface(
            NIGHT_FIELD, NIGHT_WELL, WizardsPalette.GILT_DARK,
            WizardsPalette.TEXT, WizardsPalette.TEXT_DIM, 0xFF9A8878, WizardsPalette.GILT_LIGHT,
            0xFF9BD08A, 0xFFE0857D, WizardsPalette.GILT_LIGHT, WizardsPalette.GILT, 0xFF7C6F64,
            wash(WizardsPalette.GILT, 0x40), wash(WizardsPalette.TEXT, 0x18), true, null);

    /**
     * A skin's material. Its ink and muted ink come from the skin; meaning-carrying colours (good, bad,
     * warn) keep their hue and are moved only in luminance until they read on the skin's paper.
     */
    public static UiSurface of(WizardsPalette.GuiSkin skin) {
        int base = skin.base();
        return new UiSurface(base, darken(base, 0.92f), skin.muted(),
                skin.ink(), UiContrast.readableOn(skin.muted(), base), UiContrast.readableOn(mix(skin.muted(), base, 0.35f), base, 3.0),
                skin.ink(),
                UiContrast.readableOn(WizardsPalette.PAGE_GOOD, base), UiContrast.readableOn(WizardsPalette.PAGE_BAD, base),
                UiContrast.readableOn(WizardsPalette.GILT_DARK, base), UiContrast.readableOn(skin.accent(), base, 3.0),
                UiContrast.readableOn(mix(skin.muted(), base, 0.35f), base, 3.0),
                wash(skin.accent(), 0x55), wash(skin.muted(), 0x30), false, skin);
    }

    /** True for the dark family: components flip their bevels and use light ink. */
    public boolean dark() {
        return UiContrast.luminance(field) < 0.2;
    }

    private static int wash(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static int darken(int argb, float f) {
        int r = Math.round(((argb >> 16) & 0xFF) * f);
        int g = Math.round(((argb >> 8) & 0xFF) * f);
        int b = Math.round((argb & 0xFF) * f);
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
