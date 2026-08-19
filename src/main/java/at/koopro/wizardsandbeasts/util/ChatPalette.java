package at.koopro.wizardsandbeasts.util;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.NullMarked;

/**
 * The colours command output draws from.
 *
 * <p>Separate from {@code client.gui.WizardsPalette} for two reasons, not by oversight. That class is
 * client-side and command output is built on the server, so it cannot be imported here at all. And its
 * values are tuned for tooled leather panels; chat renders over a translucent black strip, where the
 * mid-tone leathers turn to mud and the parchment tones glare. These are the same brass-and-leather
 * family read against a dark ground instead — {@link #ACCENT} is {@code WizardsPalette.BRASS} exactly,
 * so a report and a panel still look like the same mod.
 *
 * <p>Vanilla {@link net.minecraft.ChatFormatting} is deliberately not used for anything structural. Its
 * sixteen colours are the palette of every other mod's chat spam, and {@code GOLD} beside {@code GRAY}
 * is what "no one chose this" looks like.
 */
@NullMarked
public final class ChatPalette {

    private ChatPalette() {}

    // ── brand, straight from the leather family ──────────────────────────────────────────────────
    /** Headers and the mod's voice. {@code WizardsPalette.BRASS}. */
    public static final int ACCENT = 0xDBA86D;
    /** A header's emphasis, and a value worth looking at twice. {@code WizardsPalette.BRASS_HI}. */
    public static final int ACCENT_HI = 0xF5E4B0;
    /** Body text. */
    public static final int TEXT = 0xF3E6D2;
    /** The label half of a labelled row. */
    public static final int LABEL = 0xC2A78F;
    /** Furniture: rules, bullets, separators. Present, never competing. */
    public static final int RULE = 0xA4764A;
    /** Trailing notes, counts, hints — readable, but the last thing the eye lands on. */
    public static final int MUTED = 0x8A6A55;

    // ── semantic ─────────────────────────────────────────────────────────────────────────────────
    // WizardsPalette leaves these out on the grounds that they carry meaning rather than theme and
    // belong with the feature that means them. A report has no feature: it renders whatever it is
    // handed, so it needs a generic good/bad/warning it can apply without knowing what it is showing.
    /** Granted, enabled, passed. */
    public static final int OK = 0x86C06A;
    /** Reachable but unfinished — the colour PREVIEW wears. */
    public static final int WARN = 0xE0A33C;
    /** Refused, disabled, missing. */
    public static final int BAD = 0xD9584F;
    /** Clickable. Underlined as well as coloured, so it does not rely on colour alone. */
    public static final int LINK = 0xC08A5A;

    /** A style carrying just this colour. */
    public static Style color(int rgb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb));
    }
}
