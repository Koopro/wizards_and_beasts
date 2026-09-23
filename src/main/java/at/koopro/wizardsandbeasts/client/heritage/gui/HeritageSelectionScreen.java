package at.koopro.wizardsandbeasts.client.heritage.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.character.widget.PlayerModelViewport;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.widget.CyclerWidget;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.network.heritage.HeritageSelectC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

    // Grown from 416x236 when the gate became a parchment sheet: the frame's double rule runs 4-6px
    // in, so the outer edge went from 8 to 12 and the columns keep the widths they had.
    private static final int NAT_W = 424;
    private static final int NAT_H = 240;
    private static final int MARGIN = GuiScaleHelper.DEFAULT_MARGIN;
    /**
     * How far the layout may grow on a large viewport. Past this the vanilla font's own pixel grid
     * becomes the thing you notice, and the dossier's line length grows past comfortable reading.
     */
    private static final float MAX_SCALE = 2.25F;

    /** Column widths in design space: 12 | 104 | 6 | 180 | 6 | 104 | 12. */
    private static final int COL_SIDE_W = 104;
    private static final int COL_MID_W = 180;
    private static final int COL_GAP = 6;
    /** Clearance from the sheet's edge: the parchment frame's inner rule sits at 6px. */
    private static final int EDGE = WizardsMetrics.SPACE_L;
    /** Title row and the rule under it, as on the Character Sheet: text at 10, rule sprite at 18. */
    private static final int TITLE_TEXT_Y = 10;
    private static final int TITLE_RULE_Y = 18;

    /**
     * Cosmetic only — this picks what the screen <em>shows</em>. The Power roll that actually
     * matters happens server-side in {@code PlayerStatsAPI.initializeStatsForNewPlayer} off the
     * player's own {@code RandomSource}, so nothing here can influence a committed character.
     */
    private static final RandomSource RANDOM = RandomSource.create();

    private final PlayerModelViewport viewport = new PlayerModelViewport();

    @Nullable private Heritage selectedHeritage;
    @Nullable private HeritageVariant selectedVariant;
    /** The condition this character begins with, if the player chooses one. Empty for almost everybody. */
    private Optional<ConditionOrigin> selectedCondition = Optional.empty();
    private boolean confirmOpen;

    @Nullable private CyclerWidget<Heritage> heritageCycler;
    @Nullable private CyclerWidget<HeritageVariant> variantCycler;
    @Nullable private CyclerWidget<Optional<ConditionOrigin>> conditionCycler;

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
        // Grows as well as shrinks: this screen owns the whole viewport and is drawn procedurally,
        // so there is no art to blur and no reason to sit at 1.0 in the middle of a wide monitor.
        scale = GuiScaleHelper.computeFullscreenScale(NAT_W, NAT_H, width, height, MARGIN, MAX_SCALE);
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
        overlayH = s(128);
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

        // Begin with a condition. Offered, not hidden away: a player who wants to play Remus Lupin should be able
        // to say so at creation instead of hunting a werewolf for a bite. The list is the conditions this lineage
        // could actually carry, so it disappears entirely for a goblin or a Squib choosing an Obscurus.
        List<Optional<ConditionOrigin>> conditions = availableConditions();
        if (conditions.size() > 1) {
            conditionCycler = new CyclerWidget<>(
                    conditions, selectedCondition,
                    HeritageSelectionScreen::conditionLabel, this::selectCondition);
            conditionCycler.setBounds(leftColX, contentTop + (cyclerH + s(8)) * 2, colW, cyclerH);
            conditionCycler.buttons().forEach(this::addRenderableWidget);
        } else {
            conditionCycler = null;
        }

        // Randomise sits at the foot of the left column, opposite Confirm on the right, so the two
        // "commit something" actions bracket the dossier rather than crowding each other.
        addRenderableWidget(ThemedButton.randomise(
                leftColX, contentBottom - s(18), colW, s(18),
                Component.translatable("gui.wizards_and_beasts.heritage.randomise"),
                this::randomise));

        // Green, because it is the one control on this screen that starts something irreversible.
        // Disabled state still comes from ControlState, so a locked heritage greys it out as before.
        ThemedButton confirm = new ThemedButton(
                rightColX, contentBottom - s(18), s(COL_SIDE_W), s(18),
                Component.translatable("gui.wizards_and_beasts.heritage.confirm"),
                this::openConfirm).tone(McStylePanel.ButtonTone.CONFIRM);
        confirm.active = selectedHeritage.isAlphaAvailable() && selectedVariant != null;
        addRenderableWidget(confirm);
    }

    private void buildConfirmOverlay() {
        int bw = s(96);
        int bh = s(18);
        // 30 up from the foot, so the buttons clear the overlay sheet's double rule at 4 and 6.
        int by = overlayY + overlayH - s(30);
        int gap = s(8);
        // Back out stays neutral; sealing the character is green. Side by side in one colour, the
        // overlay asked "are you sure?" and then offered two identical answers.
        addRenderableWidget(new ThemedButton(
                overlayX + overlayW / 2 - bw - gap / 2, by, bw, bh,
                Component.translatable("gui.wizards_and_beasts.heritage.confirm_cancel"),
                this::closeConfirm));
        addRenderableWidget(new ThemedButton(
                overlayX + overlayW / 2 + gap / 2, by, bw, bh,
                Component.translatable("gui.wizards_and_beasts.heritage.confirm_yes"),
                this::commit).tone(McStylePanel.ButtonTone.CONFIRM));
    }

    // ── Selection actions ────────────────────────────────────────────────

    private void selectHeritage(Heritage heritage) {
        if (heritage == selectedHeritage) {
            return;
        }
        selectedHeritage = heritage;
        selectedVariant = heritage.getSubtypes().isEmpty() ? null : heritage.getSubtypes().get(0);
        // A condition belongs to the character they were building, not to whoever they just became.
        selectedCondition = Optional.empty();
        rebuild();
    }

    private void selectVariant(HeritageVariant variant) {
        selectedVariant = variant;
        if (selectedCondition.isPresent()
                && !selectedCondition.get().condition().canBeCarriedBy(selectedHeritage, variant)) {
            selectedCondition = Optional.empty();
        }
        rebuild();
    }

    private void selectCondition(Optional<ConditionOrigin> condition) {
        selectedCondition = condition;
        rebuild();
    }

    /** "None", then every origin of every condition this heritage and lineage could carry. */
    private List<Optional<ConditionOrigin>> availableConditions() {
        List<Optional<ConditionOrigin>> options = new ArrayList<>();
        options.add(Optional.empty());
        for (ConditionOrigin origin : ConditionOrigin.values()) {
            if (origin.condition().canBeCarriedBy(selectedHeritage, selectedVariant)) {
                options.add(Optional.of(origin));
            }
        }
        return options;
    }

    private static String conditionLabel(Optional<ConditionOrigin> condition) {
        if (condition.isEmpty()) {
            return Component.translatable("gui.wizards_and_beasts.heritage.condition_none").getString();
        }
        ConditionOrigin origin = condition.get();
        return Component.translatable(origin.condition().getTranslationKey()).getString()
                + ": " + Component.translatable(origin.getTranslationKey()).getString();
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
        // Never rolled. Lycanthropy and an Obscurus shape a whole character; being handed one by a dice button
        // you pressed for a starting lineage is not a choice anybody made.
        selectedCondition = Optional.empty();
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
            ClientPacketDistributor.sendToServer(new HeritageSelectC2SPayload(
                    selectedHeritage.getId(), selectedVariant.getId(),
                    selectedCondition.map(ConditionOrigin::getId).orElse("")));
            onClose();
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No full-viewport fill here. This used to paint the whole viewport with WizardsPalette.INK,
        // which hid the world behind a flat black field — on the very first screen of a new
        // character, before they have seen the place they are about to be a wizard in. The screen
        // is one parchment sheet sized to the layout, and the dimmed world shows around it.

        if (confirmOpen) {
            // Browse chrome is not drawn behind the confirm overlay — the overlay owns the screen.
            if (selectedHeritage != null && selectedVariant != null) {
                HeritageDossierRenderer.drawConfirmOverlay(g, font, width, height,
                        selectedHeritage, selectedVariant, overlayX, overlayY, overlayW, overlayH);
            }
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        McStylePanel.drawThemedPanel(g, left, top, scaledW, scaledH);
        // Written on the sheet, no shadow: a shadow on paper reads as a doubled glyph.
        g.drawString(font, getTitle(), left + (scaledW - font.width(getTitle())) / 2, top + s(TITLE_TEXT_Y),
                WizardsPalette.PAGE_INK, false);
        McStylePanel.drawDivider(g, leftColX, top + s(TITLE_RULE_Y), scaledW - 2 * s(EDGE));

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
        if (conditionCycler != null) {
            conditionCycler.renderLabel(g);
        }

        // Nav hint lives in the left column's dead space under the cyclers. Centred on the screen it
        // would sit inside the dossier panel (which spans the whole middle column) and overprint the
        // flavour line.
        int hintY = contentTop + (cyclerH + s(8)) * (conditionCycler == null ? 2 : 3) + s(12);
        for (var line : font.split(Component.translatable("gui.wizards_and_beasts.heritage.nav_hint"),
                s(COL_SIDE_W))) {
            g.drawString(font, line, leftColX, hintY, WizardsPalette.PAGE_INK_2, false);
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

    // No mouseScrolled override. The wheel used to zoom the player preview here, which let the
    // figure be resized out of the framing this screen is composed around — the gate is a fixed
    // portrait, not an inspector. PlayerModelViewport keeps its zoom for the Character Sheet, which
    // is a screen you are meant to poke at; this one simply never routes the wheel to it.

    @Override
    public void renderBackground(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim only, no blur — same choice the Character Sheet makes. The world stays crisp around
        // the sheet; everything that has to be read is on the parchment itself.
        renderMenuBackground(g);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
