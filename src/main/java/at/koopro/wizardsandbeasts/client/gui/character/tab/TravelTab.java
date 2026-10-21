package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.travel.ClientTravelProfileState;
import at.koopro.wizardsandbeasts.network.travel.TravelProfileC2SPayload;
import at.koopro.wizardsandbeasts.travel.profile.TravelProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Travel tab: one place to see how this character gets about — their brooms (condition, fittings), their Apparition
 * (licence, practice, memorised places), their Floo address book and tolls, and the flying work in hand.
 *
 * <p>Worded by the server ({@code TravelProfiles}); this tab draws it and turns a line's action into a button.
 * Pressing one sends the action back and the server answers with a fresh profile, so the tab never edits its own
 * copy and never shows a state the server did not confirm.
 */
public final class TravelTab implements CharacterTab {

    private static final String KEY = "gui.wizards_and_beasts.travel_tab.";
    private static final int REFRESH_TICKS = 40;

    private static final int COLOR_SECTION = WizardsPalette.PAGE_RUBRIC;
    private static final int COLOR_DETAIL  = WizardsPalette.PAGE_INK_3;
    private static final int COLOR_GOOD    = WizardsPalette.PAGE_GOOD;
    private static final int COLOR_BAD     = WizardsPalette.PAGE_BAD;
    private static final int COLOR_MIXED   = WizardsPalette.GILT_DARK;
    private static final int COLOR_NEUTRAL = WizardsPalette.PAGE_INK_2;
    private static final int COLOR_BUTTON  = WizardsPalette.PAGE_INK;

    private static final int LINE_H = 9;
    private static final int BUTTON_PAD = 3;

    /** A button drawn this frame: where it is (design space) and what it sends. */
    private record Button(int x, int y, int w, int h, TravelProfile.Action action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Button> buttons = new ArrayList<>();
    private float scrollOffset = 0f;
    private int lastTotalH = 0;
    private int framesSinceRequest = REFRESH_TICKS;

    @Override
    public String translationKey() {
        return "gui.wizards_and_beasts.character_sheet.tab.travel";
    }

    @Override
    public void onShown() {
        send(TravelProfileC2SPayload.refresh());
    }

    private void send(TravelProfileC2SPayload payload) {
        framesSinceRequest = 0;
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(payload);
        }
    }

    @Override
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        // Frame-counted like the Heritage tab: a few dozen requests a minute at most, throttled again by the server.
        if (++framesSinceRequest >= REFRESH_TICKS * 3) {
            send(TravelProfileC2SPayload.refresh());
        }
        Font font = Minecraft.getInstance().font;
        TravelProfile profile = ClientTravelProfileState.get();
        buttons.clear();

        float maxScroll = Math.max(0, lastTotalH - h);
        scrollOffset = Mth.clamp(scrollOffset, 0f, maxScroll);
        g.enableScissor(x, y, x + w, y + h);
        int cx = x + 2;
        int cy = y + 2 - (int) scrollOffset;
        int top = cy;
        int innerW = w - 4 - TabScrollbar.WIDTH;

        if (profile.isEmpty()) {
            cy = wrap(g, font, Component.translatable(ClientTravelProfileState.received() ? KEY + "none" : KEY + "loading"),
                    cx, cy, innerW, COLOR_DETAIL);
        } else {
            for (TravelProfile.Section section : TravelProfile.Section.values()) {
                List<TravelProfile.Line> lines = profile.section(section);
                if (lines.isEmpty()) {
                    continue;
                }
                g.drawString(font, Component.translatable(section.translationKey()), cx, cy, COLOR_SECTION, false);
                cy += LINE_H + 1;
                for (TravelProfile.Line line : lines) {
                    int textW = innerW - 8;
                    TravelProfile.Action action = line.action();
                    if (action.kind() != TravelProfile.ActionKind.NONE) {
                        int bw = font.width(action.label()) + 2 * BUTTON_PAD;
                        int bx = cx + innerW - bw;
                        int by = cy - 1;
                        boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + LINE_H + 1
                                && mouseY >= y && mouseY < y + h;
                        g.fill(bx, by, bx + bw, by + LINE_H + 1, hover ? 0x40000000 : 0x20000000);
                        g.renderOutline(bx, by, bw, LINE_H + 1, 0x80000000 | (COLOR_BUTTON & 0x00FFFFFF));
                        g.drawString(font, action.label(), bx + BUTTON_PAD, cy, COLOR_BUTTON, false);
                        buttons.add(new Button(bx, by, bw, LINE_H + 1, action));
                        textW -= bw + 4;
                    }
                    g.drawString(font, "•", cx + 1, cy, colour(line.tone()), false);
                    cy = wrap(g, font, line.text(), cx + 8, cy, textW, colour(line.tone()));
                    if (!line.detail().getString().isEmpty()) {
                        cy = wrap(g, font, line.detail(), cx + 8, cy, innerW - 8, COLOR_DETAIL);
                    }
                    cy += 1;
                }
                cy += 4;
            }
        }
        g.disableScissor();
        lastTotalH = cy - top + 4;
        // Buttons scrolled out of the content area must not take clicks.
        buttons.removeIf(b -> b.y() + b.h() <= y || b.y() >= y + h);
        TabScrollbar.draw(g, x, y, w, h, scrollOffset, lastTotalH);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        for (Button b : buttons) {
            if (b.contains(mouseX, mouseY)) {
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                send(new TravelProfileC2SPayload(b.action().kind().ordinal(), b.action().arg()));
                return true;
            }
        }
        return false;
    }

    private static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int w, int colour) {
        for (FormattedCharSequence row : font.split(text, Math.max(20, w))) {
            g.drawString(font, row, x, y, colour, false);
            y += LINE_H;
        }
        return y;
    }

    private static int colour(TravelProfile.Tone tone) {
        return switch (tone) {
            case GOOD -> COLOR_GOOD;
            case BAD -> COLOR_BAD;
            case MIXED -> COLOR_MIXED;
            case NEUTRAL -> COLOR_NEUTRAL;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (float) (delta * 10.0);
        return true;
    }
}
