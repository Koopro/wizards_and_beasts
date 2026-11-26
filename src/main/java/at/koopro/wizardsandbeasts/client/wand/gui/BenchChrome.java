package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.wand.bench.BenchRole;
import at.koopro.wizardsandbeasts.wand.style.SwitchBenchRolePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What the three bench screens share (P5, owner 2026-11-26 "i like it very much"): the bench top seen from above
 * ({@code tools/bench_screen.py}), the parchment inventory strip under its front edge, and the leather tags over its
 * back edge, one per {@link BenchRole}. The open role's tag hangs in front of the edge; pressing another opens that role.
 */
@NullMarked
final class BenchChrome {

    static final Identifier TOP_MAKE = art("bench_top");
    static final Identifier TOP_MEND = art("bench_top_mend");
    static final Identifier TOP_RESTYLE = art("bench_top_restyle");
    private static final Identifier TAG = art("tag");

    private static final int TAG_W = 46, TAG_H = 16, TAG_STEP = 48, TAG_Y_OPEN = -12, TAG_Y_SHUT = -10;
    /** The strip begins under the front edge, as the bag hangs over its strip. */
    private static final int STRIP_OVERLAP = 6;

    private BenchChrome() {}

    private static Identifier art(String name) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                "textures/gui/container/wandmakers_bench/" + name + ".png");
    }

    private static int tagX(int imageWidth, BenchRole role) {
        return imageWidth - 4 - (BenchRole.values().length * TAG_STEP - 2) + role.ordinal() * TAG_STEP;
    }

    /**
     * The strip, the shut tags, the bench top, the open tag, and the player's inventory wells. Ends in a fresh stratum,
     * so what the screen draws next lies on the bench rather than under it.
     */
    static void back(GuiGraphics g, int x, int y, int w, int topH, int imageH, Identifier top, BenchRole open,
                     List<int[]> inventoryOrigins) {
        Ui.panel(g, UiSurface.PAGE, x, y + topH - STRIP_OVERLAP, w, imageH - topH + STRIP_OVERLAP);
        for (BenchRole role : BenchRole.values()) {
            if (role != open) {
                g.blit(RenderPipelines.GUI_TEXTURED, TAG, x + tagX(w, role), y + TAG_Y_SHUT, 0f, 0f, TAG_W, TAG_H, TAG_W, TAG_H);
            }
        }
        g.nextStratum();
        g.blit(RenderPipelines.GUI_TEXTURED, top, x, y, 0f, 0f, w, topH, w, topH);
        g.blit(RenderPipelines.GUI_TEXTURED, TAG, x + tagX(w, open), y + TAG_Y_OPEN, 0f, 0f, TAG_W, TAG_H, TAG_W, TAG_H);
        g.nextStratum();
        for (int[] o : inventoryOrigins) {
            Ui.slot(g, x + o[0], y + o[1]);
        }
    }

    /** The tags' names, in the screen's label space: the open one bright with a gilt stitch, the others dim. */
    static void tagLabels(GuiGraphics g, Font font, int w, BenchRole open) {
        for (BenchRole role : BenchRole.values()) {
            int tx = tagX(w, role);
            boolean isOpen = role == open;
            int ty = isOpen ? TAG_Y_OPEN : TAG_Y_SHUT;
            Component name = Component.translatable(role.key());
            g.drawString(font, name, tx + 4 + (TAG_W - 4 - font.width(name)) / 2, ty + 3,
                    isOpen ? WizardsPalette.BRASS_HI : WizardsPalette.TEXT_DIM, isOpen);
            if (isOpen) {
                g.fill(tx + 6, ty + 12, tx + TAG_W - 4, ty + 13, WizardsPalette.GILT);
            }
        }
    }

    /** The role whose tag is under the mouse (screen space), or {@code null}. */
    static @Nullable BenchRole tagAt(double mx, double my, int left, int top, int w) {
        for (BenchRole role : BenchRole.values()) {
            int tx = left + tagX(w, role);
            if (mx >= tx && mx < tx + TAG_W && my >= top + TAG_Y_OPEN && my < top + 2) {
                return role;
            }
        }
        return null;
    }

    /** Opens another role on this bench when a shut tag is pressed. True when the click was a tag's. */
    static boolean clickTag(double mx, double my, int left, int top, int w, BenchRole open, int containerId) {
        BenchRole role = tagAt(mx, my, left, top, w);
        if (role == null) {
            return false;
        }
        if (role != open) {
            ClientPacketDistributor.sendToServer(new SwitchBenchRolePayload(containerId, role));
        }
        return true;
    }
}
