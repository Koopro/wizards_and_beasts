package at.koopro.wizardsandbeasts.client.heritage.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens.HeritageSelection;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.stats.PowerBandTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Procedural-first ceremonial skin for {@link HeritageSelectionScreen}: dark vignette ground,
 * warm candlelight glow behind the active heritage, torn-edge parchment panels, identity card,
 * wax-seal motif, and the "this is final" confirmation overlay. Procedural-first: optional textures
 * ({@code parchment.png} panel grain, {@code wax_seal.png} crest) are blitted when present and degrade
 * gracefully to {@link GuiGraphics} primitives (flat fill / disc) when absent.
 *
 * <p>All geometry arrives pre-scaled (absolute pixels) from the screen; vanilla font is drawn at
 * native size, matching the house style of {@code HeritageSelectionRenderHelper} /
 * {@code WizardsConfigScreen}.</p>
 *
 * <h2>Optional seal/crest texture spec (Deliverable 4)</h2>
 * <ul>
 *   <li><b>Expected asset:</b> {@code assets/wizards_and_beasts/textures/gui/heritage/wax_seal.png}, 32×32.</li>
 *   <li><b>Layout:</b> a centred crimson wax blob with irregular dripped edge filling ~28px; an embossed
 *       inner ring 2px from the rim; at the centre a single serif glyph (a stylised "W" or a quill-and-wand
 *       cross) pressed darker, with a soft top-left highlight to read as warm wax. Transparent corners.</li>
 *   <li><b>Fallback:</b> {@link #drawWaxSeal} renders a layered octagonal disc + ring + spark when the PNG
 *       is missing, so the screen is never broken by absent art.</li>
 * </ul>
 */
public final class HeritageCeremonyRenderer {

    private static final Identifier WAX_SEAL_TEX = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/heritage/wax_seal.png");
    private static final int WAX_SEAL_TEX_SIZE = 32;

    private static final Identifier PARCHMENT_TEX = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/heritage/parchment.png");
    private static final int PARCHMENT_TEX_SIZE = 64;

    // Ceremonial palette (warm parchment + ink), reused from the existing heritage token block.
    private static final int BACKDROP = 0xE6140C05;
    private static final int GLOW_WARM = 0x22FFC061;
    private static final int PANEL_BRIGHT = HeritageSelection.COLOR_PARCHMENT_BRIGHT;
    private static final int PANEL_DARK = HeritageSelection.COLOR_PARCHMENT_DARK;
    private static final int BORDER_LIGHT = HeritageSelection.COLOR_BORDER_LIGHT;
    private static final int BORDER_DARK = HeritageSelection.COLOR_BORDER_DARK;
    private static final int CARD_BG = HeritageSelection.COLOR_CARD_BG;
    private static final int INK = HeritageSelection.COLOR_INK;
    private static final int TEXT = HeritageSelection.COLOR_TEXT;
    private static final int DIM = HeritageSelection.COLOR_DIM;
    private static final int ACCENT = HeritageSelection.COLOR_ACCENT;
    private static final int DIVIDER = HeritageSelection.COLOR_DIVIDER;
    private static final int RED = HeritageSelection.COLOR_RED;
    private static final int OVERLAY_DIM = 0xCC0A0603;

    private HeritageCeremonyRenderer() {}

    // ── Backdrop ─────────────────────────────────────────────────────────

    /**
     * Dark vignette ground + a warm candlelight wash that follows the active rail entry.
     * {@code pulse} (0..1) softly breathes the glow alpha so the candlelight feels alive.
     *
     * <p>This used to stack six concentric {@code fill} squares of the same alpha, which compounded into
     * a hard-edged bright blob in the middle of the screen — an "orb", not candlelight. Everything here is
     * a true gradient now, so the falloff has no visible step at any GUI scale.</p>
     */
    public static void renderBackdrop(@NonNull GuiGraphics g, int screenW, int screenH,
                                      int glowCy, float pulse) {
        g.fill(0, 0, screenW, screenH, BACKDROP);

        // Warm wash: two opposed gradients meeting at the active entry's row, so the light fades out
        // smoothly above and below it instead of terminating on an edge.
        int alpha = 0x14 + Math.round(Math.max(0.0F, Math.min(1.0F, pulse)) * 0x10);
        int warm = (alpha << 24) | (GLOW_WARM & 0x00FFFFFF);
        int clear = GLOW_WARM & 0x00FFFFFF;
        int reach = Math.max(48, screenH / 3);
        g.fillGradient(0, glowCy - reach, screenW, glowCy, clear, warm);
        g.fillGradient(0, glowCy, screenW, glowCy + reach, warm, clear);

        // Vignette: pull the top and bottom edges down so the ceremony sits in a pool of light.
        int vignette = Math.max(24, screenH / 5);
        g.fillGradient(0, 0, screenW, vignette, 0x99000000, 0x00000000);
        g.fillGradient(0, screenH - vignette, screenW, screenH, 0x00000000, 0x99000000);
    }

    // ── Parchment panel (torn/deckled edge) ──────────────────────────────

    /** Parchment panel with a layered torn/deckled edge built from offset fills. */
    public static void drawParchmentPanel(@NonNull GuiGraphics g, int x, int y, int w, int h) {
        // Drop shadow.
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x66000000);
        // Deckled rim: dark base inset by the bright sheet, with nibbled corners.
        g.fill(x, y, x + w, y + h, PANEL_DARK);
        // Interior parchment: real grain texture (stretched) when present, else flat fill.
        if (Minecraft.getInstance().getResourceManager().getResource(PARCHMENT_TEX).isPresent()) {
            g.blit(RenderPipelines.GUI_TEXTURED, PARCHMENT_TEX, x + 1, y + 1, 0.0F, 0.0F,
                    w - 2, h - 2, PARCHMENT_TEX_SIZE, PARCHMENT_TEX_SIZE, PARCHMENT_TEX_SIZE, PARCHMENT_TEX_SIZE);
        } else {
            g.fill(x + 2, y + 1, x + w - 2, y + h - 1, PANEL_BRIGHT);
            g.fill(x + 1, y + 2, x + w - 1, y + h - 2, PANEL_BRIGHT);
        }
        // Torn nibbles along the top + bottom edges for a hand-deckled look.
        for (int nx = x + 6; nx < x + w - 6; nx += 9) {
            g.fill(nx, y, nx + 4, y + 1, PANEL_DARK);
            g.fill(nx + 2, y + h - 1, nx + 6, y + h, PANEL_DARK);
        }
        outline(g, x, y, w, h, BORDER_LIGHT, BORDER_DARK);
    }

    // ── Detail panel (header, lore, identity card) ───────────────────────

    /**
     * Draws the right-hand detail content for {@code heritage} at the explicit Y anchors the screen
     * also uses to place the variant chips and Confirm button (deterministic layout — no reflow drift).
     * The chips and Confirm button are widgets owned by the screen; this draws only header / lore /
     * identity-card and (when locked) the "Coming soon" ribbon. {@code variant} selects which variant's
     * power-band delta the card shows.
     */
    public static void drawDetail(@NonNull GuiGraphics g, @NonNull Font font,
                                  int x, int w,
                                  @NonNull Heritage heritage, @Nullable HeritageVariant variant,
                                  boolean locked,
                                  int nameY, int loreY, int loreMaxLines,
                                  int cardX, int cardY, int cardW, int cardH,
                                  int variantHeaderY) {
        int pad = 8;
        int tx = x + pad;
        int innerW = w - pad * 2;

        // Header — heritage name in its signature colour, clamped against the parchment: the pale
        // heritages (Veela, Wizard, House-elf) are near-white and were reading as blank paper here,
        // even though the same colour is correct as a rail sigil. No shadow — see drawOnParchment.
        drawOnParchment(g, font, Component.literal(heritage.getDisplayName()), tx, nameY,
                inkOn(heritage.getColor(), PANEL_BRIGHT));
        int dividerY = nameY + font.lineHeight + 3;
        g.fill(tx, dividerY, x + w - pad, dividerY + 1, DIVIDER);

        // Lore blurb (translation-key sourced).
        List<FormattedCharSequence> lore = font.split(loreOf(heritage), innerW);
        int cursorY = loreY;
        for (int i = 0; i < Math.min(loreMaxLines, lore.size()); i++) {
            g.drawString(font, lore.get(i), tx, cursorY, TEXT, false);
            cursorY += font.lineHeight;
        }

        // Identity card.
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, CARD_BG);
        outline(g, cardX, cardY, cardW, cardH, BORDER_LIGHT, BORDER_DARK);
        int icx = cardX + 6;
        int icy = cardY + 5;
        g.drawString(font, Component.translatable("gui.wizards_and_beasts.heritage.identity_header"), icx, icy, ACCENT, false);
        icy += font.lineHeight + 2;

        // Power-band descriptor (client-side PowerBandTable; uses the chosen or first variant).
        HeritageVariant bandVariant = variant != null ? variant : heritage.getSubtypes().get(0);
        int bandMax = PowerBandTable.getBandMax(bandVariant);
        g.drawString(font, Component.translatable("gui.wizards_and_beasts.heritage.power_band",
                Component.literal("≤ " + bandMax)), icx, icy, TEXT, false);
        icy += font.lineHeight;

        // Signature trait.
        g.drawString(font, Component.translatable("gui.wizards_and_beasts.heritage.signature_trait",
                Component.translatable("heritage.wizards_and_beasts." + heritage.getId() + ".trait")),
                icx, icy, TEXT, false);
        icy += font.lineHeight;

        // Flavor line.
        g.drawString(font, Component.translatable("heritage.wizards_and_beasts." + heritage.getId() + ".flavor")
                .withStyle(style -> style.withItalic(true)), icx, icy, DIM, false);

        // Variant section header (chips drawn by the screen beneath this).
        g.drawString(font, Component.translatable("gui.wizards_and_beasts.heritage.variant_header"),
                tx, variantHeaderY, ACCENT, false);

        if (locked) {
            drawComingSoonRibbon(g, font, x, nameY, w);
        }
    }

    /** Diagonal "Coming soon" ribbon across the detail panel's top-right corner for locked heritages. */
    private static void drawComingSoonRibbon(GuiGraphics g, Font font, int x, int y, int w) {
        Component label = Component.translatable("gui.wizards_and_beasts.heritage.coming_soon");
        int lw = font.width(label);
        int rx = x + w - lw - 16;
        int ry = y + 4;
        g.fill(rx - 4, ry - 2, rx + lw + 4, ry + font.lineHeight + 2, 0xCC7A1E1E);
        outline(g, rx - 4, ry - 2, lw + 8, font.lineHeight + 4, 0xFFB85050, BORDER_DARK);
        g.drawString(font, label, rx, ry, 0xFFF2D7D7, true);
    }

    /** Small stacked-chevron hint that more rail entries exist above/below the viewport. */
    public static void drawScrollChevron(@NonNull GuiGraphics g, int cx, int cy, boolean down) {
        // Gold-on-parchment measured 1.69:1 — the "more heritages below" affordance was invisible.
        int color = 0xCC4A3418;
        for (int i = 0; i < 3; i++) {
            int yy = cy + (down ? i : -i);
            int half = 3 - i;
            if (half <= 0) {
                break;
            }
            g.fill(cx - half, yy, cx + half, yy + 1, color);
        }
    }

    // ── Wax seal ─────────────────────────────────────────────────────────

    /** Wax-seal motif: optional {@code wax_seal.png}, else a procedural crimson disc + ring + spark. */
    public static void drawWaxSeal(@NonNull GuiGraphics g, int cx, int cy, int radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getResourceManager().getResource(WAX_SEAL_TEX).isPresent()) {
            int d = radius * 2;
            g.blit(RenderPipelines.GUI_TEXTURED, WAX_SEAL_TEX, cx - radius, cy - radius, 0.0F, 0.0F,
                    d, d, WAX_SEAL_TEX_SIZE, WAX_SEAL_TEX_SIZE, WAX_SEAL_TEX_SIZE, WAX_SEAL_TEX_SIZE);
            return;
        }
        filledDisc(g, cx, cy, radius, 0xFF6E1212);
        filledDisc(g, cx, cy, radius - 1, 0xFF8B1A1A);
        ring(g, cx, cy, radius - 3, 0xFF5A0E0E);
        // Central spark / pressed glyph.
        g.fill(cx - 1, cy - radius + 4, cx + 1, cy + radius - 4, 0x804A0A0A);
        g.fill(cx - radius + 4, cy - 1, cx + radius - 4, cy + 1, 0x804A0A0A);
        // Warm top-left highlight.
        g.fill(cx - radius + 3, cy - radius + 3, cx - radius + 7, cy - radius + 5, 0x55FFC9A0);
    }

    // ── Confirm overlay ──────────────────────────────────────────────────

    /** Dim ground + parchment panel making permanence explicit; Confirm/Cancel are screen widgets. */
    public static void drawConfirmOverlay(@NonNull GuiGraphics g, @NonNull Font font,
                                          int screenW, int screenH,
                                          @NonNull Heritage heritage, @NonNull HeritageVariant variant,
                                          int panelX, int panelY, int panelW, int panelH) {
        g.fill(0, 0, screenW, screenH, OVERLAY_DIM);
        drawParchmentPanel(g, panelX, panelY, panelW, panelH);

        int cx = panelX + panelW / 2;
        int ty = panelY + 12;
        drawCentered(g, font, Component.translatable("gui.wizards_and_beasts.heritage.confirm_title"), cx, ty, INK);
        ty += font.lineHeight + 6;

        Component choice = Component.literal(heritage.getDisplayName() + " — " + variant.getDisplayName());
        drawCentered(g, font, choice, cx, ty, inkOn(heritage.getColor(), PANEL_BRIGHT));
        ty += font.lineHeight + 8;

        List<FormattedCharSequence> body = font.split(
                Component.translatable("gui.wizards_and_beasts.heritage.confirm_body"), panelW - 24);
        for (FormattedCharSequence line : body) {
            drawCenteredSeq(g, font, line, cx, ty, TEXT);
            ty += font.lineHeight;
        }
        ty += 4;
        drawCentered(g, font, Component.translatable("gui.wizards_and_beasts.heritage.confirm_final"),
                cx, ty, RED);

        // Wax seal pressed above the buttons.
        drawWaxSeal(g, cx, panelY + panelH - 30, 9);
    }

    // ── Primitives ───────────────────────────────────────────────────────

    /**
     * Draws ink on the parchment with the drop shadow <em>off</em>.
     *
     * <p>Vanilla's shadow is a second copy of the glyph, offset a pixel and multiplied to about a quarter
     * brightness — near-black for any ink colour. That is right for light text on a dark HUD, and wrong
     * here: on parchment it puts a black ghost against a dark glyph, which reads as doubled, smeared text
     * rather than as depth. Everything painted on a parchment ground goes through this.</p>
     */
    private static void drawOnParchment(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    /**
     * A signature colour made legible as ink on {@code ground}.
     *
     * <p>The drop shadow tempts you to settle for {@link UiContrast#AA_LARGE} here, but this font is drawn
     * at native size inside a panel that scales up, so the glyphs stay small however big the screen is —
     * and the shadow muddies thin strokes rather than sharpening them. {@link UiContrast#AA_TEXT} is the
     * target that actually reads: Wizardkind lands on {@code 5E4B8C}, still plainly violet.</p>
     */
    private static int inkOn(int signature, int ground) {
        return UiContrast.readableOn(signature, ground, UiContrast.AA_TEXT);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int topLeft, int bottomRight) {
        g.fill(x, y, x + w, y + 1, topLeft);
        g.fill(x, y, x + 1, y + h, topLeft);
        g.fill(x, y + h - 1, x + w, y + h, bottomRight);
        g.fill(x + w - 1, y, x + w, y + h, bottomRight);
    }

    private static void filledDisc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int a = 0; a < 360; a += 12) {
            double rad = Math.toRadians(a);
            int px = cx + (int) Math.round(Math.cos(rad) * r);
            int py = cy + (int) Math.round(Math.sin(rad) * r);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static void drawCentered(GuiGraphics g, Font font, Component text, int cx, int y, int color) {
        drawOnParchment(g, font, text, cx - font.width(text) / 2, y, color);
    }

    private static void drawCenteredSeq(GuiGraphics g, Font font, FormattedCharSequence seq, int cx, int y, int color) {
        g.drawString(font, seq, cx - font.width(seq) / 2, y, color, false);
    }

    private static Component loreOf(Heritage heritage) {
        return Component.translatable("heritage.wizards_and_beasts." + heritage.getId() + ".lore");
    }
}
