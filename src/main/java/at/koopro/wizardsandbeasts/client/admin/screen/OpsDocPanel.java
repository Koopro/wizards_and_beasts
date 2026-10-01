package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.client.admin.ClientAdminOpsState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
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
 * A Performance or Debug page: a heading, a strip of controls, then a scrolling document of lines. The document is
 * laid out when an answer arrives ({@link ClientAdminOpsState#version()} moved), never per frame; the controls are
 * rebuilt then too, so a toggle always shows the state the server reported. A page that reads live data asks for it
 * every {@link #pollTicks()} ticks while it is shown and not at all otherwise.
 */
@NullMarked
abstract class OpsDocPanel implements AdminPanel {

    private static final int HEADER_H = 32;
    private static final int LINE = 10;
    private static final int SCROLLBAR_W = 4;

    /** One line of the document: a sub-heading, or text in a colour. */
    protected record Line(Component text, int color, boolean heading) {
        static Line text(Component text, int color) {
            return new Line(text, color, false);
        }

        static Line heading(Component text) {
            return new Line(text, AdminTheme.RUBRIC, true);
        }
    }

    private record Laid(@Nullable FormattedCharSequence text, @Nullable Component heading, int color, int height) {}

    protected @Nullable AdminPanelHost host;
    protected int x;
    protected int y;
    protected int w;
    protected int h;
    private int docTop;
    private double scroll;
    private final List<Laid> laid = new ArrayList<>();
    private int docHeight;
    private int builtVersion = -1;
    private int ticks;
    /** The first request goes out when the page is first shown, not on every rebuild. */
    private boolean polled;

    protected abstract Component title();

    protected abstract Component summary();

    /** Adds the controls in the strip starting at {@code top}; returns the strip's height (0 for none). */
    protected int controls(AdminPanelHost host, Font font, int left, int top, int width) {
        return 0;
    }

    /** The document, built from current state. */
    protected abstract List<Line> lines();

    /** Ticks between requests for fresh data while shown; 0 never asks. */
    protected int pollTicks() {
        return 0;
    }

    /** Asks the server for fresh data. */
    protected void poll() {}

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int strip = controls(host, host.font(), x, y + HEADER_H, w - SCROLLBAR_W - 4);
        docTop = y + HEADER_H + (strip > 0 ? strip + AdminTheme.GAP : 0);
        builtVersion = ClientAdminOpsState.version();
        layout();
        if (pollTicks() > 0 && !polled) {
            polled = true;
            poll();
        }
    }

    private void layout() {
        if (host == null) {
            return;
        }
        Font font = host.font();
        laid.clear();
        int width = w - SCROLLBAR_W - 4;
        for (Line line : lines()) {
            if (line.heading()) {
                laid.add(new Laid(null, line.text(), line.color(), LINE + 8));
            } else {
                for (FormattedCharSequence seq : font.split(line.text(), width)) {
                    laid.add(new Laid(seq, null, line.color(), LINE));
                }
            }
        }
        docHeight = laid.stream().mapToInt(Laid::height).sum();
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private double maxScroll() {
        return Math.max(0, docHeight - (y + h - docTop));
    }

    @Override
    public void tick() {
        int every = pollTicks();
        if (every > 0 && ++ticks % every == 0) {
            poll();
        }
    }

    @Override
    public void onServerState() {
        if (host != null && builtVersion != ClientAdminOpsState.version()) {
            builtVersion = ClientAdminOpsState.version();
            if (rebuildsControls()) {
                host.requestRebuild();
            } else {
                layout();
            }
        }
    }

    /** Whether an answer can change the controls (a toggle's label), so the page is rebuilt rather than relaid. */
    protected boolean rebuildsControls() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (mouseX < x || mouseX >= x + w || mouseY < docTop || mouseY >= y + h || maxScroll() <= 0) {
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
        int bottom = y + h;
        g.enableScissor(x, docTop, x + w, bottom);
        int ly = docTop - (int) scroll;
        int width = w - SCROLLBAR_W - 4;
        for (Laid line : laid) {
            if (ly + line.height() >= docTop && ly <= bottom) {
                if (line.heading() != null) {
                    AdminSectionHeader.renderSub(g, font, x, ly + 2, width, line.heading());
                } else if (line.text() != null) {
                    g.drawString(font, line.text(), x, ly, line.color(), false);
                }
            }
            ly += line.height();
        }
        g.disableScissor();
        double max = maxScroll();
        if (max > 0) {
            int trackX = x + w - SCROLLBAR_W;
            int trackH = bottom - docTop;
            g.fill(trackX, docTop, trackX + SCROLLBAR_W, bottom, AdminTheme.PAPER_SHADE);
            int thumbH = Math.max(12, trackH * trackH / Math.max(1, docHeight));
            int thumbY = docTop + (int) ((trackH - thumbH) * (scroll / max));
            g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, AdminTheme.GOLD);
        }
    }

    // ── helpers ──

    protected static Line heading(String key, Object... args) {
        return Line.heading(Component.translatable(key, args));
    }

    protected static Line text(String key, Object... args) {
        return Line.text(Component.translatable(key, args), AdminTheme.INK);
    }

    protected static Line note(String key, Object... args) {
        return Line.text(Component.translatable(key, args), AdminTheme.INK_3);
    }

    protected static Line plain(String text, int color) {
        return Line.text(Component.literal(text), color);
    }
}
