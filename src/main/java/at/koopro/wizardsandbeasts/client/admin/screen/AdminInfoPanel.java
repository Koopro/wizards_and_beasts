package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A read-only page: a heading, then a scrolling document of sub-headings, wrapped paragraphs and label/value rows.
 * For the parts of a section that are facts rather than settings — the law as written, the coinage, where the
 * world's places come from — so an administrator can see what the game does without the page pretending any of it
 * is adjustable. The document is rebuilt whenever server state arrives, so a value it quotes stays current.
 */
@NullMarked
abstract class AdminInfoPanel implements AdminPanel {

    private static final int HEADER_H = 32;
    private static final int LINE = 10;
    private static final int SCROLLBAR_W = 4;

    /** One piece of the document. */
    protected sealed interface Block permits Heading, Para, Row, Gap {}

    protected record Heading(Component text) implements Block {}

    protected record Para(Component text, int color) implements Block {}

    protected record Row(Component label, Component value, int valueColor) implements Block {}

    protected record Gap(int height) implements Block {}

    private record Line(@Nullable FormattedCharSequence text, @Nullable String right, int color, int rightColor,
                        @Nullable Component heading, int height) {}

    protected @Nullable AdminPanelHost host;
    private int x;
    private int y;
    private int w;
    private int h;
    private double scroll;
    private final List<Line> lines = new ArrayList<>();
    private int docHeight;

    protected abstract Component title();

    protected abstract Component summary();

    /** The document, built fresh from current client state. */
    protected abstract List<Block> blocks();

    // ── helpers for subclasses ──

    protected static Heading heading(String key, Object... args) {
        return new Heading(Component.translatable(key, args));
    }

    protected static Para para(String key, Object... args) {
        return new Para(Component.translatable(key, args), AdminTheme.INK_2);
    }

    protected static Para note(String key, Object... args) {
        return new Para(Component.translatable(key, args), AdminTheme.INK_3);
    }

    protected static Row row(Component label, Component value) {
        return new Row(label, value, AdminTheme.INK);
    }

    protected static Row row(Component label, String value) {
        return new Row(label, Component.literal(value), AdminTheme.INK);
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        layout();
    }

    private void layout() {
        if (host == null) {
            return;
        }
        Font font = host.font();
        lines.clear();
        int width = w - SCROLLBAR_W - 4;
        for (Block block : blocks()) {
            switch (block) {
                case Heading heading -> lines.add(new Line(heading.text().getVisualOrderText(), null, AdminTheme.RUBRIC, 0,
                        heading.text(), LINE + 8));
                case Para para -> {
                    for (FormattedCharSequence seq : font.split(para.text(), width)) {
                        lines.add(new Line(seq, null, para.color(), 0, null, LINE));
                    }
                }
                case Row row -> {
                    String value = row.value().getString();
                    int labelRoom = width - Math.min(font.width(value), width / 2) - 8;
                    String label = AdminText.clip(font, row.label().getString(), labelRoom);
                    lines.add(new Line(Component.literal(label).getVisualOrderText(),
                            AdminText.clip(font, value, width - font.width(label) - 8), AdminTheme.INK_2,
                            row.valueColor(), null, LINE + 1));
                }
                case Gap gap -> lines.add(new Line(null, null, 0, 0, null, gap.height()));
            }
        }
        docHeight = lines.stream().mapToInt(Line::height).sum();
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private int viewTop() {
        return y + HEADER_H;
    }

    private double maxScroll() {
        return Math.max(0, docHeight - (y + h - viewTop()));
    }

    @Override
    public void onServerState() {
        layout();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (mouseX < x || mouseX >= x + w || mouseY < viewTop() || mouseY >= y + h || maxScroll() <= 0) {
            return false;
        }
        scroll = Mth.clamp(scroll - deltaY * LINE * 3, 0, maxScroll());
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        AdminSectionHeader.render(g, font, x, y, w, title(), summary());
        int top = viewTop();
        int bottom = y + h;
        g.enableScissor(x, top, x + w, bottom);
        int ly = top - (int) scroll;
        int width = w - SCROLLBAR_W - 4;
        for (Line line : lines) {
            if (ly + line.height() >= top && ly <= bottom && line.text() != null) {
                if (line.heading() != null) {
                    AdminSectionHeader.renderSub(g, font, x, ly + 2, width, line.heading());
                } else {
                    g.drawString(font, line.text(), x + (line.right() != null ? 4 : 0), ly, line.color(), false);
                    if (line.right() != null) {
                        g.drawString(font, line.right(), x + width - font.width(line.right()), ly, line.rightColor(), false);
                    }
                }
            }
            ly += line.height();
        }
        g.disableScissor();
        double max = maxScroll();
        if (max > 0) {
            int trackX = x + w - SCROLLBAR_W;
            int trackH = bottom - top;
            g.fill(trackX, top, trackX + SCROLLBAR_W, bottom, AdminTheme.PAPER_SHADE);
            int thumbH = Math.max(12, trackH * trackH / Math.max(1, docHeight));
            int thumbY = top + (int) ((trackH - thumbH) * (scroll / max));
            g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, AdminTheme.GOLD);
        }
    }
}
