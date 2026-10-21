package at.koopro.wizardsandbeasts.client.gui.ui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tooltip cards: every tooltip a mod screen shows, in one card.
 *
 * <p>Drawn by vanilla's own tooltip renderer with a style id, so positioning, screen-edge flipping and
 * the "draw above everything" stratum are vanilla's and correct; only the card is ours
 * ({@code gui/sprites/tooltip/night_background|frame}, written by {@code tools/gui_parchment.py}).
 *
 * <h2>Rules</h2>
 * <ol>
 *   <li>The first line names the thing, in the heading gilt. Nothing else is gilt.</li>
 *   <li>Then what it is (one line of ink), then numbers (good green, bad red), then lore in grey italic,
 *       last. A tooltip is read top-down and stops being read early.</li>
 *   <li>A disabled control's tooltip says <em>why</em> it is disabled, in the warn colour.</li>
 *   <li>Lines wrap at {@link #MAX_W}. A tooltip wider than a third of the screen at GUI scale 4 hides
 *       what it describes.</li>
 *   <li>Tooltips appear on hover over the thing itself, never on a separate "?" icon.</li>
 * </ol>
 */
@NullMarked
public final class UiTooltips {

    /** The night card. */
    public static final Identifier STYLE = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "night");

    private static final int GILT = at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GILT_LIGHT & 0xFFFFFF;

    /** Widest a tooltip line wraps to, in GUI pixels. */
    public static final int MAX_W = 180;

    private UiTooltips() {}

    /** Shows the card for this frame at the mouse. Call from {@code render} while the mouse is over the thing. */
    public static void show(GuiGraphics g, Font font, List<Component> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        g.setTooltipForNextFrame(font, wrap(font, lines), Optional.empty(), mouseX, mouseY, STYLE);
    }

    /** A titled card: the name in gilt, then the body lines as given. */
    public static void show(GuiGraphics g, Font font, Component title, List<Component> body, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>(body.size() + 1);
        lines.add(title.copy().withColor(GILT));
        lines.addAll(body);
        show(g, font, lines, mouseX, mouseY);
    }

    /** A card that only has a name. */
    public static void show(GuiGraphics g, Font font, Component title, int mouseX, int mouseY) {
        show(g, font, title, List.of(), mouseX, mouseY);
    }

    /** Why something cannot be used, as a tooltip line. */
    public static MutableComponent disabledReason(Component reason) {
        return reason.copy().withColor(GILT).withStyle(ChatFormatting.ITALIC);
    }

    /** Lore, as the last tooltip lines. */
    public static MutableComponent lore(Component text) {
        return text.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    }

    /** Shows {@code full} as a card when the text was shortened and the mouse is over it. */
    public static void ifTruncated(GuiGraphics g, Font font, boolean truncated, @Nullable Component full,
                                   Ui.Rect over, int mouseX, int mouseY) {
        if (truncated && full != null && over.contains(mouseX, mouseY)) {
            show(g, font, List.of(full), mouseX, mouseY);
        }
    }

    private static List<Component> wrap(Font font, List<Component> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (Component line : lines) {
            if (font.width(line) <= MAX_W) {
                out.add(line);
                continue;
            }
            for (var split : font.getSplitter().splitLines(line, MAX_W, line.getStyle())) {
                MutableComponent part = Component.empty();
                split.visit((style, text) -> {
                    part.append(Component.literal(text).withStyle(style));
                    return Optional.empty();
                }, line.getStyle());
                out.add(part);
            }
        }
        return out;
    }
}
