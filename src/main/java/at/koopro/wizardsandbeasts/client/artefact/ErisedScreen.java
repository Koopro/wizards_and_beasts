package at.koopro.wizardsandbeasts.client.artefact;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.artefact.erised.ErisedVisionS2CPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Looking into the Mirror of Erised. The screen is the mirror: its gilded frame, the inscription on the crest
 * (read by hovering it), and in the glass what the server chose to show -- the vision, a clue beneath it if the
 * day's first gaze brought one, and at the foot how many more looks the day allows.
 *
 * <p>It decides nothing; {@link ErisedVisionS2CPayload} carries every word. The vision rises out of the dark over
 * the first second, the way a reflection would surface, then holds.
 */
public class ErisedScreen extends Screen {

    private static final Identifier MIRROR =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/erised/mirror.png");
    private static final String KEY = "erised." + WizardsAndBeastsMod.MODID + ".screen.";

    private static final int VISION = 0xEAD9A8;
    private static final int CLUE = 0xA9C2DA;
    private static final int DIM = 0x8A8FA8;
    private static final int PULL = 0xC79A9A;
    private static final int PAD = 8;
    private static final int LINE = 10;
    private static final float FADE_TICKS = 20f;

    private final ErisedVisionS2CPayload shown;
    private ErisedLayout layout = ErisedLayout.of(1, 1);
    private int age;

    public ErisedScreen(ErisedVisionS2CPayload shown) {
        super(Component.translatable(KEY + "title"));
        this.shown = shown;
    }

    @Override
    protected void init() {
        layout = ErisedLayout.of(width, height);
    }

    @Override
    public void tick() {
        age++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderMenuBackground(g);
        ErisedLayout l = layout;
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, MIRROR, l.x(), l.y(), 0.0F, 0.0F,
                l.w(), l.h(), ErisedLayout.TEX_W, ErisedLayout.TEX_H, ErisedLayout.TEX_W, ErisedLayout.TEX_H);

        float fade = Mth.clamp((age + partialTick) / FADE_TICKS, 0f, 1f);
        int textW = l.glassW() - 2 * PAD;
        List<Line> lines = new ArrayList<>();
        if (shown.vision().isPresent()) {
            add(lines, shown.vision().get(), VISION, textW);
            shown.clue().ifPresent(clue -> {
                lines.add(Line.GAP);
                add(lines, clue.copy().withStyle(ChatFormatting.ITALIC), CLUE, textW);
            });
        } else {
            add(lines, Component.translatable(KEY + "reflection"), DIM, textW);
        }

        List<Line> foot = new ArrayList<>();
        if (shown.pulled()) {
            add(foot, Component.translatable(KEY + "pulled"), PULL, textW);
        }
        add(foot, Component.translatable(KEY + "gazes_left." + shown.gazesLeft()), DIM, textW);

        int footH = foot.size() * LINE;
        int bodyH = lines.size() * LINE;
        int bodyTop = l.glassY() + Math.max(PAD, (l.glassH() - footH - PAD - bodyH) / 2);
        int alpha = Math.max(8, Math.round(255 * fade));
        draw(g, lines, bodyTop, alpha);
        draw(g, foot, l.glassY() + l.glassH() - PAD - footH + 2, 255);

        if (l.overRail(mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable(KEY + "inscription").withStyle(ChatFormatting.GOLD),
                    Component.translatable(KEY + "inscription.reading").withStyle(ChatFormatting.GRAY,
                            ChatFormatting.ITALIC)), mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void add(List<Line> out, Component text, int rgb, int width) {
        for (FormattedCharSequence seq : font.split(text, width)) {
            out.add(new Line(seq, rgb));
        }
    }

    private void draw(GuiGraphics g, List<Line> lines, int top, int alpha) {
        int centre = layout.glassX() + layout.glassW() / 2;
        int y = top;
        for (Line line : lines) {
            if (line.text() != null) {
                g.drawString(font, line.text(), centre - font.width(line.text()) / 2, y,
                        (alpha << 24) | line.rgb(), true);
            }
            y += LINE;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Line(FormattedCharSequence text, int rgb) {
        static final Line GAP = new Line(null, 0);
    }
}
