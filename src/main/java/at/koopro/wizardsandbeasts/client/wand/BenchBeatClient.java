package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.network.wand.BenchBeatResultPayload;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The player's half of an act at the wandmaker's bench: plays the beat the server sent ({@link BenchCraft#beat}),
 * records which ticks the use key was pressed and held on, and sends them back when the beat has run out. Judges
 * nothing; the server scores the same beat from its own seed.
 *
 * <p>While an act plays, the use key belongs to it: presses are drained before vanilla sees them, and a held key does
 * not keep using the bench. Its own HUD layer, under the screen: the spell HUD is untouched.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class BenchBeatClient {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "bench_beat");

    /** Walk further than this from the bench and the act is abandoned (the server would refuse it anyway). */
    private static final double REACH_SQR = 8.0 * 8.0;
    /** How long the last act's score stays on screen, ticks. */
    private static final int RESULT_HOLD = 40;
    /** Lane pixels per tick. */
    private static final float PX_PER_TICK = 3.0f;

    private static final int W = 220;
    private static final int H = 50;

    private record Play(BlockPos pos, long seed, BenchCraft.Odds odds, BenchCraft.Beat beat,
                        List<Integer> taps, boolean[] held) {}

    private static @Nullable Play play;
    private static int tick;
    /** The finished act, kept on screen for a moment with the score the client expects. */
    private static @Nullable Play shown;
    private static int shownFor;
    private static float shownScore;

    private BenchBeatClient() {}

    public static void start(BlockPos pos, long seed, BenchCraft.Odds odds) {
        BenchCraft.Beat beat = BenchCraft.beat(odds, seed);
        play = new Play(pos.immutable(), seed, odds, beat, new ArrayList<>(), new boolean[BenchCraft.BORE_TICKS]);
        tick = 0;
        shown = null;
        Minecraft mc = Minecraft.getInstance();
        // Drop a press already queued (the click on the screen's button) so it cannot land as the first tap.
        while (mc.options.keyUse.consumeClick()) {
            // drained
        }
    }

    public static boolean active() {
        return play != null;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        play = null;
        shown = null;
    }

    /** While an act plays, the use key does not use the bench (or anything else). */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (play != null && event.isUseItem()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (shown != null && --shownFor <= 0) {
            shown = null;
        }
        Play p = play;
        if (p == null) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.player.distanceToSqr(p.pos.getCenter()) > REACH_SQR) {
            play = null;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        // A press is drained here, before vanilla's keybind handling in the same tick, so it never uses the bench.
        while (mc.options.keyUse.consumeClick()) {
            if (mc.screen == null) {
                p.taps.add(tick);
            }
        }
        int boreTick = tick - BenchCraft.LEAD;
        if (boreTick >= 0 && boreTick < p.held.length) {
            p.held[boreTick] = mc.screen == null && mc.options.keyUse.isDown();
        }
        tick++;
        if (tick >= p.beat.duration()) {
            int[] taps = p.taps.stream().mapToInt(Integer::intValue).toArray();
            ClientPacketDistributor.sendToServer(new BenchBeatResultPayload(p.pos, p.seed, taps, p.held));
            shownScore = BenchCraft.score(p.odds, p.seed, taps, p.held);
            shown = p;
            shownFor = RESULT_HOLD;
            play = null;
        }
    }

    // ── drawing ──────────────────────────────────────────────────────────────

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Play p = play != null ? play : shown;
        if (p == null || mc.options.hideGui || mc.player == null) {
            return;
        }
        Font font = mc.font;
        UiSurface s = UiSurface.PAGE;
        int x = (g.guiWidth() - W) / 2;
        int y = g.guiHeight() - 64 - H;
        Ui.panel(g, s, x, y, W, H);

        BenchCraft.Odds odds = p.odds;
        g.drawString(font, Component.translatable(odds.act().key()), x + 8, y + 6, WizardsPalette.PAGE_INK, false);
        if (odds.act().risk() != BenchCraft.Risk.NONE) {
            Component risk = Component.translatable("wandcraft.bench.beat.risk." + odds.act().risk().name().toLowerCase(Locale.ROOT),
                    String.format(Locale.ROOT, "%.0f", odds.risk() * 100f));
            g.drawString(font, risk, x + W - 8 - font.width(risk), y + 6, WizardsPalette.PAGE_RUBRIC, false);
        }

        float now = p == play ? tick + delta.getGameTimeDeltaPartialTick(false) : p.beat.duration();
        int laneX = x + 8;
        int laneY = y + 18;
        int laneW = W - 16;
        int laneH = 12;
        g.fill(laneX, laneY, laneX + laneW, laneY + laneH, WizardsPalette.PAGE_SHADE);
        if (odds.act().kind() == BenchCraft.Kind.HOLD) {
            drawBore(g, p, now, laneX, laneY, laneW, laneH);
        } else {
            drawTaps(g, p, now, laneX, laneY, laneW, laneH);
        }

        Component foot;
        if (p != play) {
            foot = Component.translatable("wandcraft.bench.beat.done", Math.round(shownScore * 100f));
        } else if (now < BenchCraft.LEAD) {
            foot = Component.translatable("wandcraft.bench.beat.ready");
        } else {
            foot = Component.translatable(odds.act().kind() == BenchCraft.Kind.HOLD
                    ? "wandcraft.bench.beat.hint_hold" : "wandcraft.bench.beat.hint_tap",
                    mc.options.keyUse.getTranslatedKeyMessage());
        }
        g.drawString(font, foot, x + 8, y + H - 14, WizardsPalette.PAGE_INK_2, false);
    }

    /** Beats slide in from the right to the mark; a beat turns green once a tap has caught it, red once missed. */
    private static void drawTaps(GuiGraphics g, Play p, float now, int lx, int ly, int lw, int lh) {
        int mark = lx + 28;
        int half = Math.round(p.odds.window() * PX_PER_TICK);
        g.fill(mark - half, ly, mark + half + 1, ly + lh, WizardsPalette.PAGE_SELECT);
        g.fill(mark, ly - 2, mark + 1, ly + lh + 2, WizardsPalette.GILT_DARK);
        boolean[] caught = caught(p);
        int[] beats = p.beat.beats();
        for (int i = 0; i < beats.length; i++) {
            float bx = mark + (beats[i] - now) * PX_PER_TICK;
            if (bx < lx || bx > lx + lw - 2) {
                continue;
            }
            boolean past = beats[i] + p.odds.window() < now;
            int colour = caught[i] ? WizardsPalette.PAGE_GOOD : past ? WizardsPalette.PAGE_BAD : WizardsPalette.PAGE_INK;
            int cx = Math.round(bx);
            g.fill(cx - 1, ly + 2, cx + 2, ly + lh - 2, colour);
        }
    }

    /** Which beats a tap has caught so far, matched the way the server scores them. */
    private static boolean[] caught(Play p) {
        int[] beats = p.beat.beats();
        boolean[] caught = new boolean[beats.length];
        boolean[] used = new boolean[p.taps.size()];
        for (int b = 0; b < beats.length; b++) {
            for (int t = 0; t < p.taps.size(); t++) {
                if (!used[t] && Math.abs(p.taps.get(t) - beats[b]) <= p.odds.window()) {
                    used[t] = true;
                    caught[b] = true;
                    break;
                }
            }
        }
        return caught;
    }

    /** The band wanders along the lane; the bit (the marker) sinks unless the use key holds it up. */
    private static void drawBore(GuiGraphics g, Play p, float now, int lx, int ly, int lw, int lh) {
        int i = Math.max(0, Math.min(BenchCraft.BORE_TICKS - 1, (int) now - BenchCraft.LEAD));
        BenchCraft.BoreFrame frame = BenchCraft.bore(p.odds, p.seed, p.held)[i];
        int bandFrom = lx + Math.round((frame.center() - p.odds.band()) * lw);
        int bandTo = lx + Math.round((frame.center() + p.odds.band()) * lw);
        g.fill(Math.max(lx, bandFrom), ly, Math.min(lx + lw, bandTo), ly + lh, WizardsPalette.PAGE_SELECT);
        int mx = lx + Math.round(frame.marker() * (lw - 3));
        g.fill(mx, ly - 2, mx + 3, ly + lh + 2, frame.inside() ? WizardsPalette.PAGE_GOOD : WizardsPalette.PAGE_BAD);
        if (now >= BenchCraft.LEAD) {
            float left = 1f - Math.min(1f, (now - BenchCraft.LEAD) / BenchCraft.BORE_TICKS);
            g.fill(lx, ly + lh + 1, lx + Math.round(lw * left), ly + lh + 2, WizardsPalette.GILT_DARK);
        }
    }
}
