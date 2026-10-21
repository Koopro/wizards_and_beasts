package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageProfileState;
import at.koopro.wizardsandbeasts.heritage.identity.HeritageProfile;
import at.koopro.wizardsandbeasts.network.heritage.HeritageProfileC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * What this character's people and condition mean in play — advantages, disadvantages, what they can do, how the
 * world treats them, how they grow, and how they are right now. Drawn at the foot of the Identity page; it was the
 * Heritage tab until that folded into Identity (2026-10-15).
 *
 * <p>Pure readout. Every line arrives worded by the server ({@code HeritageProfiles}); the page asks for a fresh copy
 * when shown and every couple of seconds while open, because "right now" (the moon, the blood pool, whether the
 * master is online) changes while the sheet is up. The request carries nothing — the server answers about the sender
 * only.
 */
final class HeritageProfileSection {

    private static final String KEY = "gui.wizards_and_beasts.heritage_tab.";
    /** Frames between refreshes: two seconds at 60 fps, throttled again by the server. */
    private static final int REFRESH_FRAMES = 120;

    private static final int COLOR_TITLE   = WizardsPalette.PAGE_INK;
    private static final int COLOR_SECTION = WizardsPalette.PAGE_RUBRIC;
    private static final int COLOR_DETAIL  = WizardsPalette.PAGE_INK_3;
    private static final int COLOR_GOOD    = WizardsPalette.PAGE_GOOD;
    private static final int COLOR_BAD     = WizardsPalette.PAGE_BAD;
    private static final int COLOR_MIXED   = WizardsPalette.GILT_DARK;
    private static final int COLOR_NEUTRAL = WizardsPalette.PAGE_INK_2;

    private static final int LINE_H = 9;

    private int framesSinceRequest = REFRESH_FRAMES;

    void request() {
        framesSinceRequest = 0;
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new HeritageProfileC2SPayload());
        }
    }

    /** Draws the profile and returns the y below it; refreshes it on the frame cadence above. */
    int draw(GuiGraphics g, Font font, int x, int y, int w) {
        if (++framesSinceRequest >= REFRESH_FRAMES) {
            request();
        }
        HeritageProfile profile = ClientHeritageProfileState.get();
        if (profile.isEmpty()) {
            return wrap(g, font, Component.translatable(ClientHeritageProfileState.received() ? KEY + "none" : KEY + "loading"),
                    x, y, w, COLOR_DETAIL);
        }
        y = wrap(g, font, profile.title(), x, y, w, COLOR_TITLE) + 3;
        for (HeritageProfile.Section section : HeritageProfile.Section.values()) {
            List<HeritageProfile.Line> lines = profile.section(section);
            if (lines.isEmpty()) {
                continue;
            }
            g.drawString(font, Component.translatable(section.translationKey()), x, y, COLOR_SECTION, false);
            y += LINE_H + 1;
            for (HeritageProfile.Line line : lines) {
                g.drawString(font, "•", x + 1, y, colour(line.tone()), false);
                y = wrap(g, font, line.text(), x + 8, y, w - 8, colour(line.tone()));
                if (!line.detail().getString().isEmpty()) {
                    y = wrap(g, font, line.detail(), x + 8, y, w - 8, COLOR_DETAIL);
                }
                y += 1;
            }
            y += 4;
        }
        return y;
    }

    private static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int w, int colour) {
        for (FormattedCharSequence row : font.split(text, Math.max(20, w))) {
            g.drawString(font, row, x, y, colour, false);
            y += LINE_H;
        }
        return y;
    }

    private static int colour(HeritageProfile.Tone tone) {
        return switch (tone) {
            case GOOD -> COLOR_GOOD;
            case BAD -> COLOR_BAD;
            case MIXED -> COLOR_MIXED;
            case NEUTRAL -> COLOR_NEUTRAL;
        };
    }
}
