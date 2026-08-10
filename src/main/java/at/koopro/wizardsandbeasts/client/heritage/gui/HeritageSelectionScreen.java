package at.koopro.wizardsandbeasts.client.heritage.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.character.widget.PlayerModelViewport;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.widget.CyclerWidget;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.network.heritage.HeritageSelectC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * First-join Heritage selection: two cyclers on the left, a dossier in the middle, and the player
 * you are about to become on the right.
 *
 * <p>This replaced a scrolling rail of ten heritages plus a wrap-around grid of lineage chips. The
 * rail meant the identity you were choosing competed for space with the list you were choosing it
 * from; collapsing both lists into {@code ◀ label ▶} cyclers hands that space to the dossier and the
 * live preview, which are the things a player is actually reading.
 *
 * <p><b>Gate mode (unchanged):</b> this screen is the hard first-join gate. {@link #shouldCloseOnEsc()}
 * is {@code false}, {@link #isPauseScreen()} is {@code false}, and ESC is swallowed at the root — the
 * only exit is committing an available heritage (or, while the confirm overlay is open, ESC steps back
 * to browsing). Opened server-side via {@code HeritageDataSyncS2CPayload.openSelector()}.
 */
public class HeritageSelectionScreen extends Screen {

    private static final int NAT_W = 416;
    private static final int NAT_H = 236;
    private static final int MARGIN = GuiScaleHelper.DEFAULT_MARGIN;

    /** Column widths in design space: 8 | 104 | 6 | 180 | 6 | 104 | 8. */
    private static final int COL_SIDE_W = 104;
    private static final int COL_MID_W = 180;
    private static final int COL_GAP = 6;
    private static final int EDGE = 8;

    /**
     * Cosmetic only — this picks what the screen <em>shows</em>. The Power roll that actually
     * matters happens server-side in {@code PlayerStatsAPI.initializeStatsForNewPlayer} off the
     * player's own {@code RandomSource}, so nothing here can influence a committed character.
     */
    private static final RandomSource RANDOM = RandomSource.create();

    private final PlayerModelViewport viewport = new PlayerModelViewport();

    @Nullable private Heritage selectedHeritage;
    @Nullable private HeritageVariant selectedVariant;
    private boolean confirmOpen;

    @Nullable private CyclerWidget<Heritage> heritageCycler;
    @Nullable private CyclerWidget<HeritageVariant> variantCycler;

    // ── Layout (recomputed in init) ──────────────────────────────────────
    private float scale = 1.0F;
    private int left, top, scaledW, scaledH;
    private int leftColX, midColX, rightColX, contentTop, contentBottom;
    private int cyclerH;
    private int previewY, previewH, traitsY;
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
        rebuild();
    }

    private void computeLayout() {
        scale = GuiScaleHelper.computeScale(NAT_W, NAT_H, width, height, MARGIN);
        scaledW = Math.round(NAT_W * scale);
        scaledH = Math.round(NAT_H * scale);
        left = GuiScaleHelper.clampedLeft(scaledW, width, MARGIN);
        top = GuiScaleHelper.clampedTop(scaledH, height, MARGIN);

        leftColX = left + s(EDGE);
        midColX = leftColX + s(COL_SIDE_W) + s(COL_GAP);
        rightColX = midColX + s(COL_MID_W) + s(COL_GAP);

        contentTop = top + s(28);
        contentBottom = top + scaledH - s(EDGE);

        cyclerH = Math.max(font.lineHeight + 4, s(15));

        previewY = contentTop;
        previewH = s(84);
        traitsY = previewY + previewH + s(6);

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
        if (selectedHeritage == null) {
            return;
        }
        int colW = s(COL_SIDE_W);

        heritageCycler = new CyclerWidget<>(
                Arrays.asList(Heritage.values()), selectedHeritage,
                Heritage::getDisplayName, this::selectHeritage);
        heritageCycler.setBounds(leftColX, contentTop, colW, cyclerH);
        heritageCycler.buttons().forEach(this::addRenderableWidget);

        List<HeritageVariant> variants = selectedHeritage.getSubtypes();
        if (selectedVariant != null && !variants.isEmpty()) {
            variantCycler = new CyclerWidget<>(
                    variants, selectedVariant,
                    HeritageVariant::getDisplayName, this::selectVariant);
            variantCycler.setBounds(leftColX, contentTop + cyclerH + s(8), colW, cyclerH);
            variantCycler.buttons().forEach(this::addRenderableWidget);
        } else {
            variantCycler = null;
        }

        // Randomise sits at the foot of the left column, opposite Confirm on the right, so the two
        // "commit something" actions bracket the dossier rather than crowding each other.
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.wizards_and_beasts.heritage.randomise"),
                        b -> randomise())
                .bounds(leftColX, contentBottom - s(18), colW, s(18)).build());

        Button confirm = Button.builder(
                        Component.translatable("gui.wizards_and_beasts.heritage.confirm"),
                        b -> openConfirm())
                .bounds(rightColX, contentBottom - s(18), s(COL_SIDE_W), s(18)).build();
        confirm.active = selectedHeritage.isAlphaAvailable() && selectedVariant != null;
        addRenderableWidget(confirm);
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
        selectedVariant = heritage.getSubtypes().isEmpty() ? null : heritage.getSubtypes().get(0);
        rebuild();
    }

    private void selectVariant(HeritageVariant variant) {
        selectedVariant = variant;
        rebuild();
    }

    /**
     * Rolls a random <em>selectable</em> heritage and lineage. Locked heritages are excluded: a
     * randomiser that can land on something you are not allowed to confirm is a dead end.
     */
    private void randomise() {
        List<Heritage> available = Arrays.stream(Heritage.values())
                .filter(Heritage::isAlphaAvailable)
                .filter(h -> !h.getSubtypes().isEmpty())
                .toList();
        if (available.isEmpty()) {
            return;
        }
        Heritage heritage = available.get(RANDOM.nextInt(available.size()));
        List<HeritageVariant> variants = heritage.getSubtypes();
        selectedHeritage = heritage;
        selectedVariant = variants.get(RANDOM.nextInt(variants.size()));
        rebuild();
    }

    /** Move the heritage selection by {@code dir} (±1), wrapping. */
    private void cycleHeritage(int dir) {
        Heritage[] vals = Heritage.values();
        int idx = selectedHeritage == null ? 0 : selectedHeritage.ordinal();
        selectHeritage(vals[Math.floorMod(idx + dir, vals.length)]);
    }

    /** Move the lineage selection by {@code dir} (±1), wrapping within the heritage's lineages. */
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
        g.fill(0, 0, width, height, WizardsPalette.INK);

        if (confirmOpen) {
            // Browse chrome is not drawn behind the confirm overlay — the overlay owns the screen.
            if (selectedHeritage != null && selectedVariant != null) {
                HeritageDossierRenderer.drawConfirmOverlay(g, font, width, height,
                        selectedHeritage, selectedVariant, overlayX, overlayY, overlayW, overlayH);
            }
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        g.drawCenteredString(font, getTitle(), left + scaledW / 2, top + s(10), WizardsPalette.BRASS_HI);

        if (selectedHeritage != null) {
            HeritageDossierRenderer.drawDossier(g, font, midColX, contentTop,
                    s(COL_MID_W), contentBottom - contentTop,
                    selectedHeritage, selectedVariant, !selectedHeritage.isAlphaAvailable());
        }

        // Right column: who you will be, then what that costs and grants.
        if (minecraft != null && minecraft.player instanceof LocalPlayer player) {
            viewport.render(g, rightColX, previewY, s(COL_SIDE_W), previewH,
                    partialTick, player, mouseX, mouseY);
        } else {
            McStylePanel.drawThemedInset(g, rightColX, previewY, s(COL_SIDE_W), previewH);
        }
        if (selectedHeritage != null) {
            HeritageDossierRenderer.drawTraits(g, font, rightColX, traitsY, s(COL_SIDE_W),
                    selectedHeritage, selectedVariant);
        }

        // Cycler label panels. The arrows are registered widgets and draw themselves in super.render.
        if (heritageCycler != null) {
            heritageCycler.renderLabel(g);
        }
        if (variantCycler != null) {
            variantCycler.renderLabel(g);
        }

        // Nav hint lives in the left column's dead space under the cyclers. Centred on the screen it
        // would sit inside the dossier panel (which spans the whole middle column) and overprint the
        // flavour line.
        int hintY = contentTop + cyclerH * 2 + s(20);
        for (var line : font.split(Component.translatable("gui.wizards_and_beasts.heritage.nav_hint"),
                s(COL_SIDE_W))) {
            g.drawString(font, line, leftColX, hintY, WizardsPalette.TEXT_DIM, false);
            hintY += font.lineHeight;
        }

        super.render(g, mouseX, mouseY, partialTick);
        renderLockedTooltip(g, mouseX, mouseY);
    }

    /** Explains why Confirm is dead when a browse-only heritage is on screen. */
    private void renderLockedTooltip(@NonNull GuiGraphics g, int mouseX, int mouseY) {
        if (confirmOpen || selectedHeritage == null || selectedHeritage.isAlphaAvailable()) {
            return;
        }
        int cw = s(COL_SIDE_W);
        int by = contentBottom - s(18);
        if (mouseX >= rightColX && mouseX < rightColX + cw && mouseY >= by && mouseY < by + s(18)) {
            g.setTooltipForNextFrame(font,
                    Component.translatable("gui.wizards_and_beasts.heritage.locked_tooltip"), mouseX, mouseY);
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
        // The rail this used to scroll is gone; the wheel now zooms the player preview.
        if (!confirmOpen && viewport.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
