package at.koopro.wizardsandbeasts.client.heritage.hud;

import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

/**
 * The Obscurial HUD's own violet identity: a separate dark surface on purpose, since it is the one HUD
 * that belongs to a condition rather than to the wizard. Its words come from
 * {@code hud.wizards_and_beasts.obscurial.*}; {@link #text} is the one door to them.
 */
public final class ObscurialHudTheme {
    public static final int PANEL_BG = 0xA0120B19;
    public static final int PANEL_BG_SOFT = 0x90120B19;
    public static final int TITLE = 0xFFE8D7FA;
    public static final int LABEL = 0xFFD7C2F2;
    public static final int TRACK = 0xFF2A2133;
    public static final int CONTROL_FILL = 0xFF8B46D1;
    public static final int PRESSURE_FILL = 0xFF2F8FDF;
    public static final int STRESS_FILL = 0xFFCF4F84;
    /** Semi-opaque overlay for the stress track on the textured HUD bar. */
    public static final int STRESS_FILL_OVERLAY = 0xD8CF4F84;
    public static final int POSITIVE = 0xFFC8F2D6;
    public static final int WARN = 0xFFFFC777;
    public static final int DANGER = 0xFFFF7C8B;
    public static final int EMPHASIS = 0xFFFF98D2;
    public static final int AUXILIARY = 0xFFEEB5F9;

    private static final String KEY = "hud.wizards_and_beasts.obscurial.";

    private ObscurialHudTheme() {}

    /** A HUD string by its short key. */
    public static String text(String key, Object... args) {
        return I18n.get(KEY + key, args);
    }

    /** A tier the model names ("Catastrophic", "Calm") in the player's language. */
    public static String tier(String modelTier) {
        return text("tier." + modelTier.toLowerCase(Locale.ROOT));
    }
}
