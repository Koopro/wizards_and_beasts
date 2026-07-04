package at.koopro.wizardsandbeasts.client.heritage.gui;

import at.koopro.wizardsandbeasts.client.gui.config.InkRevealRenderer;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.network.heritage.HeritageSelectC2SPayload;
import at.koopro.wizardsandbeasts.stats.PowerBandTable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * First-join Heritage selection — a ceremonial master-detail "character-creation" surface:
 * a left rail of all ten heritages (available vs locked-but-browsable) and a right detail panel
 * with lore, an identity card, and a variant selector, sealed by a final-confirm overlay.
 *
 * <p><b>Gate mode (unchanged):</b> this screen is the hard first-join gate. {@link #shouldCloseOnEsc()}
 * is {@code false}, {@link #isPauseScreen()} is {@code false}, and ESC is swallowed at the root — the
 * only exit is committing an available heritage (or, while the confirm overlay is open, ESC steps back
 * to browsing). Opened server-side via {@code HeritageDataSyncS2CPayload.openSelector()}; the
 * parameterless constructor and these overrides preserve the existing gate contract exactly.</p>
 */
public class HeritageSelectionScreen extends Screen {

    private static final int NAT_W = 400;
    private static final int NAT_H = 232;
    private static final int MARGIN = GuiScaleHelper.DEFAULT_MARGIN;
    private static final int CHIP_STAGGER_MS = 55;

    private final InkRevealRenderer inkReveal = new InkRevealRenderer();

    @Nullable private Heritage selectedHeritage;
    @Nullable private HeritageVariant selectedVariant;
    private boolean confirmOpen;

    // ── Layout (recomputed in init) ──────────────────────────────────────
    private float scale = 1.0F;
    private int left, top, scaledW, scaledH;
    private int railX, railTop, railW, entryH, entryGap;
    private int railVisible;          // rail entries that fit the viewport
    private int railScroll;           // index of the first visible rail entry
    private int detailX, detailTop, detailW;
    private int nameY, loreY, cardX, cardY, cardW, cardH, variantHeaderY, chipsY;
    private int chipW, chipH, chipGapX, chipGapY;
    private int confirmX, confirmY, confirmW, confirmH;
    private int overlayX, overlayY, overlayW, overlayH;

    public HeritageSelectionScreen() {
        super(Component.translatable("gui.wizards_and_beasts.heritage.title"));
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        if (selectedHeritage == null) {
            selectedHeritage = Heritage.values()[0];
            selectedVariant = selectedHeritage.getSubtypes().get(0);
        }
        inkReveal.reset();
        rebuild();
    }

    private void computeLayout() {
        scale = GuiScaleHelper.computeScale(NAT_W, NAT_H, width, height, MARGIN);
        scaledW = Math.round(NAT_W * scale);
        scaledH = Math.round(NAT_H * scale);
        left = GuiScaleHelper.clampedLeft(scaledW, width, MARGIN);
        top = GuiScaleHelper.clampedTop(scaledH, height, MARGIN);

        int line = font.lineHeight;

        railX = left + s(8);
        railTop = top + s(26);
        railW = s(116);
        entryH = s(15);
        entryGap = s(2);

        // How many whole rail entries fit between railTop and the panel's bottom margin.
        int railViewH = scaledH - (railTop - top) - s(10);
        railVisible = Math.max(1, (railViewH + entryGap) / (entryH + entryGap));
        railVisible = Math.min(railVisible, Heritage.values().length);
        clampRailScroll();

        detailX = left + s(132);
        detailTop = top + s(26);
        int detailRight = left + scaledW - s(8);
        detailW = detailRight - detailX;

        nameY = detailTop + s(2);
        loreY = nameY + line + s(8);
        cardX = detailX + s(8);
        cardY = loreY + line * 3 + s(6);
        cardW = detailW - s(16);
        cardH = line * 4 + s(14);
        variantHeaderY = cardY + cardH + s(6);
        chipsY = variantHeaderY + line + s(3);

        chipW = s(58);
        chipH = Math.max(line + 4, s(13));
        chipGapX = s(4);
        chipGapY = s(3);

        confirmW = s(104);
        confirmH = s(18);
        confirmX = detailX + (detailW - confirmW) / 2;
        confirmY = top + scaledH - s(22);

        overlayW = s(228);
        overlayH = s(122);
        overlayX = left + (scaledW - overlayW) / 2;
        overlayY = top + (scaledH - overlayH) / 2;
    }

    private int s(int v) {
        return Math.max(1, Math.round(v * scale));
    }

    // ── Widget building ──────────────────────────────────────────────────

    private void rebuild() {
        clearWidgets();
        if (confirmOpen) {
            buildConfirmOverlay();
        } else {
            buildBrowse();
        }
    }

    private void buildBrowse() {
        clampRailScroll();
        Heritage[] heritages = Heritage.values();
        int end = Math.min(heritages.length, railScroll + railVisible);
        for (int i = railScroll; i < end; i++) {
            final Heritage h = heritages[i];
            boolean locked = !h.isAlphaAvailable();
            int y = railTop + (i - railScroll) * (entryH + entryGap);
            addRenderableWidget(new HeritageRailEntry(railX, y, railW, entryH, h, locked,
                    () -> h == selectedHeritage, this::selectHeritage));
        }

        if (selectedHeritage != null) {
            List<HeritageVariant> variants = selectedHeritage.getSubtypes();
            int cx = detailX + s(8);
            int cy = chipsY;
            int detailRight = detailX + detailW - s(8);
            for (int i = 0; i < variants.size(); i++) {
                final HeritageVariant v = variants.get(i);
                if (cx + chipW > detailRight) {
                    cx = detailX + s(8);
                    cy += chipH + chipGapY;
                }
                HeritageVariantChip chip = new HeritageVariantChip(cx, cy, chipW, chipH, v,
                        () -> v == selectedVariant, this::selectVariant);
                addRenderableWidget(chip);
                inkReveal.register(chip, i + 2, CHIP_STAGGER_MS);
                cx += chipW + chipGapX;
            }

            Button confirm = Button.builder(
                    Component.translatable("gui.wizards_and_beasts.heritage.confirm"),
                    b -> openConfirm())
                    .bounds(confirmX, confirmY, confirmW, confirmH).build();
            confirm.active = selectedHeritage.isAlphaAvailable() && selectedVariant != null;
            addRenderableWidget(confirm);
        }
    }

    private void buildConfirmOverlay() {
        int bw = s(96);
        int bh = s(18);
        int by = overlayY + overlayH - s(24);
        int gap = s(8);
        addRenderableWidget(Button.builder(
                Component.translatable("gui.wizards_and_beasts.heritage.confirm_cancel"),
                b -> closeConfirm())
                .bounds(overlayX + overlayW / 2 - bw - gap / 2, by, bw, bh).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.wizards_and_beasts.heritage.confirm_yes"),
                b -> commit())
                .bounds(overlayX + overlayW / 2 + gap / 2, by, bw, bh).build());
    }

    // ── Selection actions ────────────────────────────────────────────────

    private void selectHeritage(Heritage heritage) {
        if (heritage == selectedHeritage) {
            return;
        }
        selectedHeritage = heritage;
        selectedVariant = heritage.getSubtypes().get(0);
        inkReveal.reset();
        ensureSelectedVisible();
        rebuild();
    }

    private void selectVariant(HeritageVariant variant) {
        selectedVariant = variant;
        rebuild();
    }

    /** Move the rail selection by {@code dir} (±1), wrapping, and scroll it into view. */
    private void cycleHeritage(int dir) {
        Heritage[] vals = Heritage.values();
        int idx = selectedHeritage == null ? 0 : selectedHeritage.ordinal();
        selectHeritage(vals[Math.floorMod(idx + dir, vals.length)]);
    }

    /** Move the variant selection by {@code dir} (±1), wrapping within the heritage's lineages. */
    private void cycleVariant(int dir) {
        if (selectedHeritage == null) {
            return;
        }
        List<HeritageVariant> variants = selectedHeritage.getSubtypes();
        if (variants.isEmpty()) {
            return;
        }
        int idx = selectedVariant == null ? 0 : variants.indexOf(selectedVariant);
        if (idx < 0) {
            idx = 0;
        }
        selectVariant(variants.get(Math.floorMod(idx + dir, variants.size())));
    }

    private void clampRailScroll() {
        int max = Math.max(0, Heritage.values().length - railVisible);
        railScroll = Math.max(0, Math.min(railScroll, max));
    }

    private void ensureSelectedVisible() {
        int idx = selectedHeritage == null ? 0 : selectedHeritage.ordinal();
        if (idx < railScroll) {
            railScroll = idx;
        } else if (idx >= railScroll + railVisible) {
            railScroll = idx - railVisible + 1;
        }
        clampRailScroll();
    }

    private void openConfirm() {
        if (selectedHeritage != null && selectedHeritage.isAlphaAvailable() && selectedVariant != null) {
            confirmOpen = true;
            rebuild();
        }
    }

    private void closeConfirm() {
        confirmOpen = false;
        rebuild();
    }

    private void commit() {
        if (selectedHeritage != null && selectedVariant != null && selectedHeritage.isAlphaAvailable()) {
            ClientPacketDistributor.sendToServer(
                    new HeritageSelectC2SPayload(selectedHeritage.getId(), selectedVariant.getId()));
            onClose();
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        inkReveal.update();

        // Candlelight glow tracks the selected entry's on-screen row (clamped to the viewport).
        int selectedIndex = selectedHeritage == null ? 0 : selectedHeritage.ordinal();
        int visibleRow = selectedIndex - railScroll;
        int glowCx = railX + railW / 2;
        int glowCy = (visibleRow < 0 || visibleRow >= railVisible)
                ? railTop + railVisible * (entryH + entryGap) / 2
                : railTop + visibleRow * (entryH + entryGap) + entryH / 2;
        float pulse = 0.5F + 0.5F * (float) Math.sin((System.currentTimeMillis() % 4000L) / 4000.0 * 2.0 * Math.PI);
        HeritageCeremonyRenderer.renderBackdrop(g, width, height, glowCx, glowCy, pulse);

        // Rail panel + title.
        int railPanelH = (entryH + entryGap) * railVisible + s(24);
        HeritageCeremonyRenderer.drawParchmentPanel(g, railX - s(4), railTop - s(20), railW + s(8), railPanelH);
        g.drawCenteredString(font, getTitle(), left + scaledW / 2, top + s(8), 0xFFF3E2C0);

        // Scroll chevrons when the rail overflows its viewport.
        int chevronCx = railX + railW / 2;
        if (railScroll > 0) {
            HeritageCeremonyRenderer.drawScrollChevron(g, chevronCx, railTop - s(6), false);
        }
        if (railScroll + railVisible < Heritage.values().length) {
            HeritageCeremonyRenderer.drawScrollChevron(g, chevronCx,
                    railTop + railVisible * (entryH + entryGap) - entryGap + s(2), true);
        }

        // Detail panel + content.
        int detailPanelH = scaledH - (detailTop - top) - s(6);
        HeritageCeremonyRenderer.drawParchmentPanel(g, detailX - s(4), detailTop - s(4), detailW + s(8), detailPanelH);

        if (selectedHeritage != null && inkReveal.isRevealed(1, CHIP_STAGGER_MS)) {
            HeritageCeremonyRenderer.drawDetail(g, font, detailX, detailW, selectedHeritage, selectedVariant,
                    !selectedHeritage.isAlphaAvailable(),
                    nameY, loreY, 3, cardX, cardY, cardW, cardH, variantHeaderY);
            // Wax-seal motif beside the Confirm button.
            HeritageCeremonyRenderer.drawWaxSeal(g, confirmX - s(12), confirmY + confirmH / 2, s(8));
        }

        if (confirmOpen && selectedHeritage != null && selectedVariant != null) {
            HeritageCeremonyRenderer.drawConfirmOverlay(g, font, width, height, selectedHeritage, selectedVariant,
                    overlayX, overlayY, overlayW, overlayH);
        }

        super.render(g, mouseX, mouseY, partialTick);
        renderHoverTooltips(g, mouseX, mouseY);
    }

    /** Hover hints: why a locked heritage can't be picked, and each variant's power band. */
    private void renderHoverTooltips(@NonNull GuiGraphics g, int mouseX, int mouseY) {
        if (confirmOpen) {
            return;
        }
        for (var child : children()) {
            if (child instanceof HeritageRailEntry entry && entry.isHovered() && entry.isLockedEntry()) {
                g.setTooltipForNextFrame(font,
                        Component.translatable("gui.wizards_and_beasts.heritage.locked_tooltip"), mouseX, mouseY);
                return;
            }
            if (child instanceof HeritageVariantChip chip && chip.isHovered()) {
                HeritageVariant v = chip.variantValue();
                g.setTooltipForNextFrame(font,
                        Component.translatable("gui.wizards_and_beasts.heritage.variant_tooltip",
                                v.getDisplayName(), "≤ " + PowerBandTable.getBandMax(v)), mouseX, mouseY);
                return;
            }
        }
    }

    // ── Gate-mode input handling (preserved) ─────────────────────────────

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();
        if (key == 256) { // ESC
            if (confirmOpen) {
                closeConfirm();
            }
            // Root browse: ESC is swallowed — the gate cannot be dismissed without committing.
            return true;
        }
        if (confirmOpen) {
            // While the "this is final" overlay is open, only the explicit Enter key seals it;
            // every other key is swallowed so nothing slips past the gate accidentally.
            if (key == 257 || key == 335) { // Enter / numpad Enter
                commit();
            }
            return true;
        }
        switch (key) {
            case 265 -> { cycleHeritage(-1); return true; } // Up
            case 264 -> { cycleHeritage(1); return true; }  // Down
            case 263 -> { cycleVariant(-1); return true; }  // Left
            case 262 -> { cycleVariant(1); return true; }   // Right
            case 257, 335, 32 -> { openConfirm(); return true; } // Enter / numpad Enter / Space
            default -> { /* fall through to vanilla widget focus handling */ }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!confirmOpen && scrollY != 0) {
            int before = railScroll;
            railScroll -= (int) Math.signum(scrollY);
            clampRailScroll();
            if (before != railScroll) {
                rebuild();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
