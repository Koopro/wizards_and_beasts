package at.koopro.wizardsandbeasts.client.gui.character.widget;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The effects on the character right now: one flat row per effect, its name coloured by whether it is doing the
 * wizard a favour, and the time left. Moved here from the sheet's old identity column when that column became the
 * page rail; the rows are unchanged.
 *
 * <p>These were nine-slice chips once, which could not work at this size — {@code PANEL} carries a 3px border, so
 * an 8px-tall chip is 6px of border around 2px of middle. Rows match the heritage block, which is the same shape of
 * information: flat text, no box.
 */
public final class ActiveEffectsWidget {

    private static final int COLOR_TEXT = WizardsPalette.PAGE_INK;
    private static final int COLOR_MORE = WizardsPalette.PAGE_INK_2;
    /** Kept off pure red/green: these are inks, not status LEDs. */
    private static final int COLOR_GOOD = WizardsPalette.PAGE_GOOD;
    private static final int COLOR_BAD = WizardsPalette.PAGE_BAD;
    private static final int MAX_SHOWN = 6;
    private static final int ROW_H = WizardsMetrics.LINE_BODY;

    private ActiveEffectsWidget() {}

    /** Draws the effects list (rows only — the caller draws the heading); returns the y below it. */
    public static int draw(@NonNull GuiGraphics g, int x, int y, int w) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return y;
        }
        Font font = mc.font;
        Collection<MobEffectInstance> effects = mc.player.getActiveEffects();
        if (effects.isEmpty()) {
            // Said explicitly rather than left blank: an empty region under a heading reads as a panel that
            // failed to draw, not as a character who happens to have no effects.
            g.drawString(font, Component.translatable("gui.wizards_and_beasts.character_sheet.effects.none").getString(),
                    x, y, COLOR_MORE, false);
            return y + ROW_H;
        }
        List<MobEffectInstance> list = new ArrayList<>(effects);
        int shown = Math.min(list.size(), MAX_SHOWN);
        for (int i = 0; i < shown; i++) {
            drawRow(g, font, x, y, w, list.get(i));
            y += ROW_H;
        }
        if (list.size() > MAX_SHOWN) {
            g.drawString(font, Component.translatable("gui.wizards_and_beasts.character_sheet.effects.more",
                    list.size() - MAX_SHOWN).getString(), x, y, COLOR_MORE, false);
            y += ROW_H;
        }
        return y;
    }

    private static void drawRow(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w,
                                @NonNull MobEffectInstance fx) {
        MobEffect effect = fx.getEffect().value();
        String name = Component.translatable(effect.getDescriptionId()).getString();
        if (fx.getAmplifier() > 0) {
            name = name + " " + roman(fx.getAmplifier() + 1);
        }
        String duration = formatDuration(fx);
        int durW = font.width(duration);
        GuiText.drawFitted(g, font, name, x, y, w - durW - 3, colour(effect));
        g.drawString(font, duration, x + w - durW, y, COLOR_MORE, false);
    }

    /** Beneficial reads warm, harmful reads hot, neutral stays the column's own ink. */
    private static int colour(@NonNull MobEffect effect) {
        return switch (effect.getCategory()) {
            case BENEFICIAL -> COLOR_GOOD;
            case HARMFUL -> COLOR_BAD;
            case NEUTRAL -> COLOR_TEXT;
        };
    }

    /** {@code m:ss}, or {@code ∞} for the effects that do not run out. */
    @NonNull
    private static String formatDuration(@NonNull MobEffectInstance fx) {
        if (fx.isInfiniteDuration()) {
            return "∞";
        }
        int seconds = fx.getDuration() / 20;
        return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }

    /**
     * Level numeral. Vanilla's {@code potion.potency.N} keys only ship for 1–5, and mod effects are not bound by
     * that, so the numeral is built rather than looked up.
     */
    @NonNull
    private static String roman(int value) {
        if (value < 1 || value > 3999) {
            return Integer.toString(value);
        }
        int[] nums = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] sym = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < nums.length; i++) {
            while (value >= nums[i]) {
                value -= nums[i];
                out.append(sym[i]);
            }
        }
        return out.toString();
    }
}
