package at.koopro.wizardsandbeasts.client.spell.trace;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.network.spell.SpellTraceOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellTraceRequestC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellTraceResultS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellTraceSubmitC2SPayload;
import at.koopro.wizardsandbeasts.spell.learning.SpellSource;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceDifficulty;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceJudge;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceOutcome;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TracePath;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceProgress;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TracePathValidator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The tracing challenge.
 *
 * <p>Press on the filled start mark, hold, and follow the corridor to the ring before the time runs out.
 * The same {@link TraceProgress} the server judges with runs here live, so a slip is shown the moment it
 * happens and the attempt is sent at once; the verdict on screen is always the server's.
 *
 * <p>Not a pause screen (lesson: a screen that round-trips packets must not pause). Everything a state
 * means is said in words as well as colour. The corridor is rasterised once per resize into row spans,
 * so a frame is a few hundred fills, not a distance test per pixel.
 */
@NullMarked
final class SpellTraceScreen extends Screen {

    private static final String KEY = "spell.wizards_and_beasts.trace.";
    private static final UiSurface S = UiSurface.NIGHT;

    private static final int CORRIDOR = 0xFF2B2436;
    private static final int EDGE = 0xFFE8D9A8;
    private static final int EDGE_DARK = 0xFFE05A5A;
    private static final int PROGRESS = 0xFFFFD86B;
    private static final int TRAIL = 0xCCFFFFFF;
    private static final int START = 0xFF7FE08A;
    private static final int GOAL = 0xFF8FC8FF;
    private static final int FAIL = 0xFFFF5555;
    private static final int GUIDE = 0x40FFFFFF;

    private enum Phase { READY, TRACING, JUDGING, PASSED, FAILED }

    private final @Nullable Screen previous;
    private final SpellTraceOpenS2CPayload challenge;
    private final TraceDifficulty difficulty;
    private final TracePath path;
    private final long openedAt = Util.getMillis();

    private Phase phase = Phase.READY;
    private @Nullable TraceProgress progress;
    private final List<double[]> samples = new ArrayList<>();
    private long startedAt;
    private @Nullable SpellTraceResultS2CPayload result;
    private @Nullable double[] failPoint;

    // Layout (GUI pixels), recomputed in init().
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int canvasX;
    private int canvasY;
    private int canvasSize;
    private int[] edgeSpans = new int[0];
    private int[] corridorSpans = new int[0];

    SpellTraceScreen(@Nullable Screen previous, SpellTraceOpenS2CPayload challenge) {
        super(Component.translatable(KEY + "title", SpellSource.writtenName(challenge.spellId())));
        this.previous = previous;
        this.challenge = challenge;
        this.difficulty = TraceDifficulty.byOrdinal(challenge.difficulty());
        int[] q = challenge.points();
        double[] xs = new double[q.length / 2];
        double[] ys = new double[q.length / 2];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = TraceJudge.dequantise(q[2 * i]);
            ys[i] = TraceJudge.dequantise(q[2 * i + 1]);
        }
        this.path = new TracePath(xs, ys);
    }

    @Nullable Screen previous() {
        return previous;
    }

    long challengeId() {
        return challenge.challengeId();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── layout ───────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        panelW = Math.min(width - 32, 320);
        int header = 44;
        int footer = 62;
        canvasSize = Math.max(80, Math.min(panelW - 24, height - 32 - header - footer));
        panelH = header + canvasSize + footer;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        canvasX = panelX + (panelW - canvasSize) / 2;
        canvasY = panelY + header;
        rasterise();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int y = panelY + panelH - 24;
        int x = panelX + 12;
        int w = (panelW - 24 - 8) / 3;
        ThemedButton steady = new ThemedButton(x, y, w, 16, steadyLabel(), () -> {
            if (phase == Phase.READY) {
                SpellTraceClient.steadyHand = !SpellTraceClient.steadyHand;
                ClientPacketDistributor.sendToServer(
                        new at.koopro.wizardsandbeasts.network.spell.SpellTraceAssistC2SPayload(SpellTraceClient.steadyHand));
                rasterise();
                rebuildButtons();
            }
        });
        steady.setTooltip(Tooltip.create(Component.translatable(KEY + "steady.tooltip")));
        steady.active = phase == Phase.READY;
        addRenderableWidget(steady);
        if (phase == Phase.FAILED && result != null && result.sourceKept()) {
            ThemedButton retry = new ThemedButton(x + w + 4, y, w, 16, Component.translatable(KEY + "button.retry"),
                    () -> ClientPacketDistributor.sendToServer(new SpellTraceRequestC2SPayload(challenge.spellId())));
            retry.active = retryRemainingTicks() <= 0;
            addRenderableWidget(retry);
        }
        addRenderableWidget(new ThemedButton(x + 2 * (w + 4), y, w, 16, Component.translatable(KEY + "button.close"),
                this::onClose));
    }

    private Component steadyLabel() {
        return Component.translatable(KEY + "button.steady",
                Component.translatable(KEY + (SpellTraceClient.steadyHand ? "on" : "off")));
    }

    private double halfWidth() {
        return TraceJudge.halfWidth(difficulty, SpellTraceClient.steadyHand);
    }

    /**
     * The corridor and its edge as row spans: marks every pixel within the half-width (corridor) or just
     * outside it (edge) segment by segment over each segment's bounding box, then run-length encodes rows.
     */
    private void rasterise() {
        int n = canvasSize;
        boolean[] inner = new boolean[n * n];
        boolean[] edge = new boolean[n * n];
        double r = halfWidth() * n;
        double rEdge = r + 2.0;
        for (int i = 0; i < path.size() - 1; i++) {
            double ax = path.x(i) * n;
            double ay = path.y(i) * n;
            double bx = path.x(i + 1) * n;
            double by = path.y(i + 1) * n;
            int x0 = Math.max(0, (int) Math.floor(Math.min(ax, bx) - rEdge));
            int x1 = Math.min(n - 1, (int) Math.ceil(Math.max(ax, bx) + rEdge));
            int y0 = Math.max(0, (int) Math.floor(Math.min(ay, by) - rEdge));
            int y1 = Math.min(n - 1, (int) Math.ceil(Math.max(ay, by) + rEdge));
            double dx = bx - ax;
            double dy = by - ay;
            double lenSq = dx * dx + dy * dy;
            for (int py = y0; py <= y1; py++) {
                for (int px = x0; px <= x1; px++) {
                    double cx = px + 0.5;
                    double cy = py + 0.5;
                    double t = lenSq <= 0 ? 0 : Math.max(0, Math.min(1, ((cx - ax) * dx + (cy - ay) * dy) / lenSq));
                    double d = Math.hypot(cx - (ax + t * dx), cy - (ay + t * dy));
                    if (d <= r) {
                        inner[py * n + px] = true;
                    } else if (d <= rEdge) {
                        edge[py * n + px] = true;
                    }
                }
            }
        }
        corridorSpans = spans(inner, null, n);
        edgeSpans = spans(edge, inner, n);
    }

    /** {@code [y, x0, x1, …]} runs of {@code on && !(off)} per row. */
    private static int[] spans(boolean[] on, boolean @Nullable [] off, int n) {
        List<Integer> out = new ArrayList<>();
        for (int y = 0; y < n; y++) {
            int start = -1;
            for (int x = 0; x <= n; x++) {
                boolean v = x < n && on[y * n + x] && (off == null || !off[y * n + x]);
                if (v && start < 0) {
                    start = x;
                } else if (!v && start >= 0) {
                    out.add(y);
                    out.add(start);
                    out.add(x);
                    start = -1;
                }
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    // ── input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && phase == Phase.READY && insideCanvas(event.x(), event.y())) {
            double[] c = toCanvas(event.x(), event.y());
            if (Math.hypot(c[0] - path.startX(), c[1] - path.startY())
                    <= halfWidth() * TracePathValidator.MARKER_RADIUS) {
                phase = Phase.TRACING;
                startedAt = Util.getMillis();
                progress = new TraceProgress(path, halfWidth());
                rebuildButtons();
                addSample(c);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (phase == Phase.TRACING && event.button() == 0) {
            addSample(toCanvas(event.x(), event.y()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (phase == Phase.TRACING && event.button() == 0) {
            submit(); // let go early: the server will call it unfinished, and say so
            return true;
        }
        return super.mouseReleased(event);
    }

    private void addSample(double[] c) {
        if (!samples.isEmpty()) {
            double[] last = samples.get(samples.size() - 1);
            if (Math.hypot(c[0] - last[0], c[1] - last[1]) < 0.002) {
                return;
            }
        }
        samples.add(c);
        TraceProgress p = progress;
        if (p == null) {
            return;
        }
        p.accept(c[0], c[1]);
        if (p.failed()) {
            failPoint = new double[] {p.failX(), p.failY()};
        }
        if (p.failed() || p.finished() || samples.size() >= TraceJudge.MAX_SAMPLES) {
            submit();
        }
    }

    private void submit() {
        if (phase != Phase.TRACING) {
            return;
        }
        phase = Phase.JUDGING;
        int[] q = new int[samples.size() * 2];
        for (int i = 0; i < samples.size(); i++) {
            q[2 * i] = TraceJudge.quantise(samples.get(i)[0]);
            q[2 * i + 1] = TraceJudge.quantise(samples.get(i)[1]);
        }
        ClientPacketDistributor.sendToServer(
                new SpellTraceSubmitC2SPayload(challenge.challengeId(), q));
        rebuildButtons();
    }

    void onResult(SpellTraceResultS2CPayload pkt) {
        result = pkt;
        resultMillis = Util.getMillis();
        TraceOutcome outcome = TraceOutcome.byOrdinal(pkt.outcome());
        phase = outcome.passed() ? Phase.PASSED : Phase.FAILED;
        if (failPoint == null && pkt.failIndex() >= 0 && pkt.failIndex() < samples.size()) {
            failPoint = samples.get(pkt.failIndex());
        }
        rebuildButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (phase == Phase.TRACING && remainingMillis() <= 0) {
            submit(); // out of time: the server times it too, on its own clock
        }
        if (phase == Phase.FAILED && result != null) {
            // Re-enable Try again once the retry delay has passed.
            rebuildButtonsIfRetryReady();
        }
    }

    private boolean retryWasBlocked = true;

    private void rebuildButtonsIfRetryReady() {
        boolean blocked = retryRemainingTicks() > 0;
        if (retryWasBlocked && !blocked) {
            rebuildButtons();
        }
        retryWasBlocked = blocked;
    }

    @Override
    public void onClose() {
        if (phase == Phase.READY || phase == Phase.TRACING) {
            // Closing is giving up this attempt: tell the server, so the challenge does not linger.
            ClientPacketDistributor.sendToServer(new SpellTraceSubmitC2SPayload(challenge.challengeId(), new int[0]));
        }
        minecraft.setScreen(previous);
    }

    // ── render ───────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.scrim(g, width, height);
        Ui.panel(g, S, panelX, panelY, panelW, panelH);
        int x = panelX + 12;
        int w = panelW - 24;
        Component chip = Component.translatable(KEY + "difficulty." + difficulty.key());
        int y = Ui.title(g, font, S, title, x, panelY + 8, w, null);
        int cx = x;
        cx += Ui.chip(g, font, S, cx, y, chip, difficulty == TraceDifficulty.DARK ? Ui.Tone.BAD : Ui.Tone.ACCENT) + 4;
        if (challenge.spendsSource()) {
            Ui.chip(g, font, S, cx, y, Component.translatable(KEY + "spends"), Ui.Tone.WARN);
        }

        Ui.inset(g, S, canvasX - 2, canvasY - 2, canvasSize + 4, canvasSize + 4);
        drawSpans(g, edgeSpans, difficulty == TraceDifficulty.DARK ? EDGE_DARK : EDGE);
        drawSpans(g, corridorSpans, CORRIDOR);
        drawGuide(g);
        drawProgress(g);
        drawMarkers(g);
        drawTrail(g);
        if (failPoint != null) {
            drawCross(g, failPoint[0], failPoint[1], FAIL);
        }

        drawFooter(g, x, canvasY + canvasSize + 6, w);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawSpans(GuiGraphics g, int[] spans, int colour) {
        for (int i = 0; i + 2 < spans.length; i += 3) {
            g.fill(canvasX + spans[i + 1], canvasY + spans[i], canvasX + spans[i + 2], canvasY + spans[i] + 1, colour);
        }
    }

    /** Faint centre-line dots: the direction of travel, without tracing it for the player. */
    private void drawGuide(GuiGraphics g) {
        double step = 6.0 / canvasSize;
        for (double s = 0; s < path.length(); s += step) {
            double[] p = path.pointAt(s);
            int px = canvasX + (int) (p[0] * canvasSize);
            int py = canvasY + (int) (p[1] * canvasSize);
            g.fill(px, py, px + 1, py + 1, GUIDE);
        }
    }

    private void drawProgress(GuiGraphics g) {
        TraceProgress p = progress;
        if (p == null) {
            return;
        }
        double upTo = p.fraction() * path.length();
        double step = 1.0 / canvasSize;
        for (double s = 0; s <= upTo; s += step) {
            double[] q = path.pointAt(s);
            int px = canvasX + (int) (q[0] * canvasSize);
            int py = canvasY + (int) (q[1] * canvasSize);
            g.fill(px - 1, py - 1, px + 2, py + 2, PROGRESS);
        }
    }

    private void drawMarkers(GuiGraphics g) {
        int r = Math.max(3, (int) Math.round(halfWidth() * TracePathValidator.MARKER_RADIUS * canvasSize));
        int alpha = 0xFF;
        if (phase == Phase.READY && !Config.reduceScreenEffects) {
            // A slow pulse on the start mark only while waiting to begin. Off under reduced effects.
            double t = (Util.getMillis() - openedAt) / 600.0;
            alpha = 0xA0 + (int) (0x5F * (0.5 + 0.5 * Math.sin(t)));
        }
        disc(g, path.startX(), path.startY(), r, (alpha << 24) | (START & 0xFFFFFF), true);
        disc(g, path.goalX(), path.goalY(), r, GOAL, false);
        disc(g, path.goalX(), path.goalY(), Math.max(1, r / 3), GOAL, true);
    }

    /** A filled disc or a 2px ring, by rows. */
    private void disc(GuiGraphics g, double nx, double ny, int r, int colour, boolean filled) {
        int cx = canvasX + (int) (nx * canvasSize);
        int cy = canvasY + (int) (ny * canvasSize);
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.floor(Math.sqrt((double) r * r - dy * dy));
            if (filled) {
                g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour);
            } else {
                int innerR = r - 2;
                int innerHalf = Math.abs(dy) > innerR ? -1 : (int) Math.floor(Math.sqrt((double) innerR * innerR - dy * dy));
                if (innerHalf < 0) {
                    g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour);
                } else {
                    g.fill(cx - half, cy + dy, cx - innerHalf, cy + dy + 1, colour);
                    g.fill(cx + innerHalf + 1, cy + dy, cx + half + 1, cy + dy + 1, colour);
                }
            }
        }
    }

    private void drawTrail(GuiGraphics g) {
        for (double[] p : samples) {
            int px = canvasX + (int) (p[0] * canvasSize);
            int py = canvasY + (int) (p[1] * canvasSize);
            g.fill(px, py, px + 1, py + 1, TRAIL);
        }
    }

    private void drawCross(GuiGraphics g, double nx, double ny, int colour) {
        int cx = canvasX + (int) (nx * canvasSize);
        int cy = canvasY + (int) (ny * canvasSize);
        for (int i = -4; i <= 4; i++) {
            g.fill(cx + i, cy + i, cx + i + 2, cy + i + 2, colour);
            g.fill(cx + i, cy - i, cx + i + 2, cy - i + 2, colour);
        }
    }

    private void drawFooter(GuiGraphics g, int x, int y, int w) {
        float fraction;
        int barColour;
        Component status;
        int statusColour = S.ink2();
        switch (phase) {
            case READY -> {
                long left = challenge.startAllowanceTicks() * 50L - (Util.getMillis() - openedAt);
                fraction = Math.max(0, left) / (challenge.startAllowanceTicks() * 50f);
                barColour = S.accent();
                status = Component.translatable(KEY + "ready", seconds(left));
            }
            case TRACING -> {
                fraction = remainingMillis() / (float) limitMillis();
                barColour = fraction < 0.25f ? S.warn() : PROGRESS;
                int pct = progress == null ? 0 : (int) Math.round(progress.fraction() * 100);
                status = Component.translatable(KEY + "tracing", pct, seconds(remainingMillis()));
            }
            case JUDGING -> {
                fraction = 0;
                barColour = S.ink3();
                status = Component.translatable(KEY + "judging");
            }
            case PASSED -> {
                fraction = 1;
                barColour = S.good();
                statusColour = S.good();
                status = Component.translatable("spell.wizards_and_beasts.learning.learned",
                        SpellSource.writtenName(challenge.spellId()));
            }
            default -> {
                fraction = 0;
                barColour = S.bad();
                statusColour = S.bad();
                SpellTraceResultS2CPayload r = result;
                TraceOutcome outcome = r == null ? TraceOutcome.CANCELLED : TraceOutcome.byOrdinal(r.outcome());
                status = r != null && !r.detail().isEmpty()
                        ? Component.translatable(outcome.langKey()).append(" ").append(Component.literal(r.detail()))
                        : Component.translatable(outcome.langKey());
            }
        }
        Ui.bar(g, S, x, y, w, Ui.BAR_H, fraction, barColour, -1f);
        y += Ui.BAR_H + 4;
        UiText.drawFit(g, font, S, status, x, y, w, statusColour);
        y += font.lineHeight + 2;
        Component second = secondLine();
        if (second != null) {
            UiText.drawFit(g, font, S, second, x, y, w, S.ink3());
        }
    }

    private @Nullable Component secondLine() {
        SpellTraceResultS2CPayload r = result;
        if (phase == Phase.PASSED && r != null) {
            return Component.translatable(KEY + "accuracy", r.accuracy() / 10);
        }
        if (phase == Phase.FAILED && r != null) {
            long retry = retryRemainingTicks();
            if (!r.sourceKept()) {
                return Component.translatable(KEY + "source_lost");
            }
            return retry > 0
                    ? Component.translatable(KEY + "retry_in", (retry + 19) / 20)
                    : Component.translatable(KEY + "source_kept");
        }
        if (phase == Phase.READY) {
            return Component.translatable(KEY + "legend");
        }
        return null;
    }

    private long retryRemainingTicks() {
        SpellTraceResultS2CPayload r = result;
        if (r == null || r.retryTicks() <= 0) {
            return 0;
        }
        long sinceResult = (Util.getMillis() - resultMillis) / 50;
        return Math.max(0, r.retryTicks() - sinceResult);
    }

    private long resultMillis;

    private long limitMillis() {
        return (long) Math.ceil(challenge.timeLimitTicks() * (SpellTraceClient.steadyHand ? TraceDifficulty.ASSIST_FACTOR : 1.0)) * 50L;
    }

    private long remainingMillis() {
        return limitMillis() - (Util.getMillis() - startedAt);
    }

    private static String seconds(long millis) {
        return String.valueOf(Math.max(0, (millis + 999) / 1000));
    }

    private boolean insideCanvas(double mx, double my) {
        return mx >= canvasX && my >= canvasY && mx < canvasX + canvasSize && my < canvasY + canvasSize;
    }

    private double[] toCanvas(double mx, double my) {
        return new double[] {(mx - canvasX) / canvasSize, (my - canvasY) / canvasSize};
    }
}
