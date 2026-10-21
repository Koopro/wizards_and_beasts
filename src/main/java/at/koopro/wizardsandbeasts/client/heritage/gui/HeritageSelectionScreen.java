package at.koopro.wizardsandbeasts.client.heritage.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.character.widget.PlayerModelViewport;
import at.koopro.wizardsandbeasts.client.gui.ui.NightChrome;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui.Rect;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.ui.UiTooltips;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.gui.widget.NightChip;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRules;
import at.koopro.wizardsandbeasts.network.heritage.HeritageSelectC2SPayload;
import at.koopro.wizardsandbeasts.util.MathUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * First-join heritage selection: every heritage in a list on the left, the dossier of the chosen one in
 * the middle with its lineage and starting condition above it, and the player you are about to become on
 * the right -- on the night sheet, the material of the mod's magical systems.
 *
 * <p>All eight heritages are listed rather than cycled: a choice this weighty should show its whole field
 * at once, including what is locked and why. Lineages are chips (at most five); the starting condition,
 * offered only where the lineage could carry one, is a cycler because its names are long. Geometry comes
 * from {@link HeritageSelectionLayout}; nothing here computes a position of its own.
 *
 * <p><b>Gate mode (unchanged):</b> this screen is the hard first-join gate. {@link #shouldCloseOnEsc()}
 * is {@code false}, {@link #isPauseScreen()} is {@code false}, and ESC is swallowed at the root — the
 * only exit is committing an available heritage (or, while the confirm card is open, ESC steps back to
 * browsing). Opened server-side via {@code HeritageDataSyncS2CPayload.openSelector()}.
 *
 * <p>Which heritages may be confirmed is the server's {@link HeritageRules}, not a compile-time flag; the
 * server enforces the same rule again when the choice arrives. Browsing sends nothing.
 *
 * <p><b>Preview mode</b> ({@link #preview}): the Control Center opens the same screen so an administrator can
 * walk through onboarding as a new player would see it under the current rules. ESC returns to the Control
 * Center, and confirming sends nothing — the administrator's own heritage is never touched.
 */
public class HeritageSelectionScreen extends Screen {

    private static final UiSurface S = UiSurface.NIGHT_SHEET;
    private static final int DOSSIER_PAD = 6;
    private static final int SCROLL_STEP = 10;

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

    private HeritageSelectionLayout layout;
    @Nullable private NightChip confirmButton;
    private int dossierScroll;
    private int dossierContentH;
    private int traitsScroll;
    private int traitsContentH;

    /** Non-null in preview mode: the screen to return to. Nothing is ever sent while it is set. */
    @Nullable private final Screen previewReturn;

    public HeritageSelectionScreen() {
        this(null);
    }

    private HeritageSelectionScreen(@Nullable Screen previewReturn) {
        super(Component.translatable(previewReturn == null
                ? "gui.wizards_and_beasts.heritage.title" : "gui.wizards_and_beasts.heritage.preview_title"));
        this.previewReturn = previewReturn;
    }

    /** The onboarding screen as a new player would see it, committing nothing; ESC returns to {@code returnTo}. */
    public static HeritageSelectionScreen preview(Screen returnTo) {
        return new HeritageSelectionScreen(returnTo);
    }

    public boolean isPreview() {
        return previewReturn != null;
    }

    @Override
    protected void init() {
        super.init();
        if (selectedHeritage == null) {
            selectedHeritage = Heritage.values()[0];
            selectedVariant = selectedHeritage.getSubtypes().get(0);
        }
        rebuild();
    }

    /** Nothing focused on open: the arrow keys browse at once, as the nav hint says. */
    @Override
    protected void setInitialFocus() {
    }

    // ── Widget building ──────────────────────────────────────────────────

    private Component randomiseLabel() {
        return Component.translatable("gui.wizards_and_beasts.heritage.randomise");
    }

    private Component confirmLabel() {
        return Component.translatable("gui.wizards_and_beasts.heritage.confirm");
    }

    /**
     * Lays out for the current pick and rebuilds the widgets. The lineage chips and the condition cycler
     * depend on the heritage, so a new heritage is a new layout; it is a pure function and costs nothing.
     */
    private void rebuild() {
        List<HeritageVariant> variants = selectedHeritage.getSubtypes();
        int[] chipW = new int[variants.size()];
        for (int i = 0; i < chipW.length; i++) {
            chipW[i] = NightChip.widthFor(font, Component.literal(variants.get(i).getDisplayName()));
        }
        layout = HeritageSelectionLayout.of(width, height, Heritage.values().length, chipW,
                availableConditions().size() > 1,
                NightChip.widthFor(font, randomiseLabel()) + 8, NightChip.widthFor(font, confirmLabel()) + 12);

        clearWidgets();
        confirmButton = null;
        if (confirmOpen) {
            buildConfirmCard();
        } else {
            buildBrowse(variants);
        }
    }

    private void buildBrowse(List<HeritageVariant> variants) {
        for (int i = 0; i < variants.size(); i++) {
            HeritageVariant v = variants.get(i);
            Rect r = layout.lineageChips().get(i);
            addRenderableWidget(new NightChip(r.x(), r.y(), r.w(), r.h(), Component.literal(v.getDisplayName()),
                    () -> selectVariant(v), () -> v == selectedVariant));
        }

        // Begin with a condition. Offered, not hidden away: a player who wants to play Remus Lupin should be able
        // to say so at creation instead of hunting a werewolf for a bite. The options are the conditions this
        // lineage could actually carry, so the row is absent for a goblin or a Squib choosing an Obscurus.
        if (layout.condition() != null) {
            Rect p = layout.conditionPrev();
            Rect n = layout.conditionNext();
            addRenderableWidget(new NightChip(p.x(), p.y(), p.w(), p.h(),
                    Component.translatable("gui.wizards_and_beasts.heritage.condition_prev"), () -> cycleCondition(-1), () -> false));
            addRenderableWidget(new NightChip(n.x(), n.y(), n.w(), n.h(),
                    Component.translatable("gui.wizards_and_beasts.heritage.condition_next"), () -> cycleCondition(1), () -> false));
        }

        Rect r = layout.randomise();
        addRenderableWidget(ThemedButton.skinned(r.x(), r.y(), r.w(), r.h(), randomiseLabel(), this::randomise,
                NightChrome.FOLDER, null, 0, S.ink(), S.disabled()));

        // The one control on this screen that starts something irreversible wears the chosen face; it
        // greys out (and explains itself in a tooltip) when the heritage on show cannot be chosen.
        Rect c = layout.confirm();
        confirmButton = NightChip.primary(c.x(), c.y(), c.w(), c.h(), confirmLabel(), this::openConfirm);
        confirmButton.active = HeritageRules.selectable(selectedHeritage) && selectedVariant != null;
        addRenderableWidget(confirmButton);
    }

    private void buildConfirmCard() {
        Rect c = layout.overlayCancel();
        Rect s = layout.overlaySeal();
        // Backing out stays neutral; sealing the character is the primary face.
        addRenderableWidget(ThemedButton.skinned(c.x(), c.y(), c.w(), c.h(),
                Component.translatable("gui.wizards_and_beasts.heritage.confirm_cancel"), this::closeConfirm,
                NightChrome.FOLDER, null, 0, S.ink(), S.disabled()));
        addRenderableWidget(NightChip.primary(s.x(), s.y(), s.w(), s.h(),
                Component.translatable("gui.wizards_and_beasts.heritage.confirm_yes"), this::commit));
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
        dossierScroll = 0;
        traitsScroll = 0;
        rebuild();
    }

    private void selectVariant(HeritageVariant variant) {
        selectedVariant = variant;
        if (selectedCondition.isPresent()
                && !selectedCondition.get().condition().canBeCarriedBy(selectedHeritage, variant)) {
            selectedCondition = Optional.empty();
        }
        traitsScroll = 0;
        rebuild();
    }

    private void cycleCondition(int dir) {
        List<Optional<ConditionOrigin>> options = availableConditions();
        int idx = Math.max(0, options.indexOf(selectedCondition));
        selectedCondition = options.get(Math.floorMod(idx + dir, options.size()));
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

    private Component conditionLabel() {
        return selectedCondition.map(HeritageDossierRenderer::conditionName)
                .orElseGet(() -> Component.translatable("gui.wizards_and_beasts.heritage.condition_none"));
    }

    /**
     * Rolls a random <em>selectable</em> heritage and lineage. Locked heritages are excluded: a
     * randomiser that can land on something you are not allowed to confirm is a dead end.
     */
    private void randomise() {
        List<Heritage> available = Arrays.stream(Heritage.values())
                .filter(HeritageRules::selectable)
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
        dossierScroll = 0;
        traitsScroll = 0;
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
        int idx = selectedVariant == null ? 0 : Math.max(0, variants.indexOf(selectedVariant));
        selectVariant(variants.get(Math.floorMod(idx + dir, variants.size())));
    }

    private void openConfirm() {
        if (selectedHeritage != null && HeritageRules.selectable(selectedHeritage) && selectedVariant != null) {
            confirmOpen = true;
            rebuild();
        }
    }

    private void closeConfirm() {
        confirmOpen = false;
        rebuild();
    }

    private void commit() {
        if (selectedHeritage != null && selectedVariant != null && HeritageRules.selectable(selectedHeritage)) {
            if (previewReturn != null) {
                onClose();
                return;
            }
            ClientPacketDistributor.sendToServer(new HeritageSelectC2SPayload(
                    selectedHeritage.getId(), selectedVariant.getId(),
                    selectedCondition.map(ConditionOrigin::getId).orElse("")));
            onClose();
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No full-viewport fill: the dimmed world shows around the sheet, on the very first screen of a new
        // character, before they have seen the place they are about to be a wizard in.
        renderSheet(g, mouseX, mouseY, partialTick);
        if (confirmOpen) {
            // The browse sheet stays behind the card, dimmed, so the choice being sealed is still in view.
            Ui.scrim(g, width, height);
            if (selectedVariant != null) {
                HeritageDossierRenderer.drawConfirm(g, font, S, layout.overlay(), selectedHeritage, selectedVariant,
                        selectedCondition);
            }
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltips(g, mouseX, mouseY);
    }

    private void renderSheet(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Rect p = layout.panel();
        Ui.panel(g, S, p.x(), p.y(), p.w(), p.h());
        Rect t = layout.title();
        UiText.drawFit(g, font, S, getTitle(), t.x(), t.y() + 3, t.w(), S.heading());
        Rect c = layout.content();
        NightChrome.divider(g, c.x(), layout.ruleY(), c.w());
        NightChrome.ornament(g, c.x() + c.w() / 2, layout.ruleY());

        renderList(g, mouseX, mouseY);
        renderChoices(g);
        renderDossier(g);
        renderPreview(g, mouseX, mouseY, partialTick);

        Rect h = layout.hint();
        UiText.drawFit(g, font, S, Component.translatable("gui.wizards_and_beasts.heritage.nav_hint"),
                h.x(), h.y(), h.w(), S.ink3());
    }

    /**
     * Every heritage, always: a gem in its signature colour, its name, and a chip when the gate will not
     * take it. A locked heritage can still be picked to read its dossier -- only Confirm refuses it.
     */
    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        Rect list = layout.list();
        Ui.inset(g, S, list.x(), list.y(), list.w(), list.h());
        g.drawString(font, Component.translatable("gui.wizards_and_beasts.heritage.section.heritage"),
                list.x() + 6, layout.listLabelY(), S.ink3(), S.shadow());
        int hovered = confirmOpen ? -1 : layout.rowAt(mouseX, mouseY);
        Heritage[] all = Heritage.values();
        for (int i = 0; i < all.length; i++) {
            Heritage h = all[i];
            Rect r = layout.rows().get(i);
            boolean selected = h == selectedHeritage;
            if (selected) {
                g.fill(r.x(), r.y(), r.right(), r.bottom(), S.selectWash());
                g.fill(r.x(), r.y(), r.x() + 2, r.bottom(), WizardsPalette.GILT);
            } else if (i == hovered) {
                g.fill(r.x(), r.y(), r.right(), r.bottom(), S.hoverWash());
            }
            gem(g, r.x() + 6, r.y() + r.h() / 2, UiContrast.readableOn(h.getColor(), S.well(), UiContrast.AA_LARGE));

            String refusal = HeritageRules.refusalKey(h);
            int right = r.right() - 2;
            if (refusal != null) {
                Component tag = HeritageDossierRenderer.lockChip(refusal);
                right -= Ui.chipWidth(font, tag);
                Ui.chip(g, font, S, right, r.y() + (r.h() - Ui.CHIP_H) / 2, tag, Ui.Tone.MUTED);
                right -= 3;
            }
            int colour = refusal != null ? S.ink3() : selected ? S.heading() : S.ink();
            int nameX = r.x() + 13;
            UiText.drawFit(g, font, S, Component.literal(h.getDisplayName()), nameX,
                    r.y() + (r.h() - font.lineHeight) / 2 + 1, right - nameX, colour);
        }
    }

    /** A 5px lozenge in the heritage's colour, outlined so a dark colour still reads on the well. */
    private static void gem(GuiGraphics g, int cx, int cy, int colour) {
        g.fill(cx - 1, cy - 3, cx + 2, cy + 4, 0xFF07060A);
        g.fill(cx - 3, cy - 1, cx + 4, cy + 2, 0xFF07060A);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF07060A);
        g.fill(cx, cy - 2, cx + 1, cy + 3, colour);
        g.fill(cx - 2, cy, cx + 3, cy + 1, colour);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, colour);
    }

    /** The labels over the lineage chips and the condition cycler's label plate; the chips draw themselves. */
    private void renderChoices(GuiGraphics g) {
        Rect d = layout.dossier();
        g.drawString(font, Component.translatable("gui.wizards_and_beasts.heritage.variant_header"),
                d.x(), layout.lineageLabelY(), S.ink3(), S.shadow());
        Rect cond = layout.condition();
        if (cond != null) {
            g.drawString(font, Component.translatable("gui.wizards_and_beasts.heritage.section.condition"),
                    d.x(), layout.conditionLabelY(), S.ink3(), S.shadow());
            Ui.inset(g, S, cond.x(), cond.y(), cond.w(), cond.h());
            String text = UiText.fit(font, conditionLabel().getString(), cond.w() - 8);
            g.drawString(font, text, cond.x() + (cond.w() - font.width(text)) / 2,
                    cond.y() + (cond.h() - font.lineHeight) / 2 + 1,
                    selectedCondition.isPresent() ? S.warn() : S.ink2(), S.shadow());
        }
    }

    /** The dossier, clipped to its well and scrolled by the wheel when it is taller than the well. */
    private void renderDossier(GuiGraphics g) {
        Rect well = layout.dossier();
        Ui.inset(g, S, well.x(), well.y(), well.w(), well.h());
        Rect area = well.inset(DOSSIER_PAD);
        dossierScroll = MathUtils.clampInt(dossierScroll, 0, Math.max(0, dossierContentH - area.h()));
        g.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        int top = area.y() - dossierScroll;
        int bottom = HeritageDossierRenderer.drawDossierFlow(g, font, S, area, top, selectedHeritage, selectedVariant,
                selectedCondition, HeritageRules.refusalKey(selectedHeritage));
        if (layout.traits() == null) {
            // Two columns: no room for a preview column, so the traits close the dossier.
            bottom += 6;
            Ui.hairline(g, S, area.x(), bottom, area.w());
            bottom = HeritageDossierRenderer.drawTraits(g, font, S, area.x(), bottom + 5, area.w(),
                    selectedHeritage, selectedVariant);
        }
        g.disableScissor();
        dossierContentH = bottom - top;
        scrollTrack(g, well, area, dossierContentH, dossierScroll);
    }

    private void renderPreview(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Rect pv = layout.preview();
        Rect tr = layout.traits();
        if (pv == null || tr == null) {
            return;
        }
        if (minecraft != null && minecraft.player instanceof LocalPlayer player) {
            viewport.render(g, S, pv.x(), pv.y(), pv.w(), pv.h(), partialTick, player, mouseX, mouseY);
        } else {
            Ui.inset(g, S, pv.x(), pv.y(), pv.w(), pv.h());
        }
        traitsScroll = MathUtils.clampInt(traitsScroll, 0, Math.max(0, traitsContentH - tr.h()));
        g.enableScissor(tr.x(), tr.y(), tr.right(), tr.bottom());
        int top = tr.y() - traitsScroll;
        int bottom = HeritageDossierRenderer.drawTraits(g, font, S, tr.x(), top, tr.w() - 3,
                selectedHeritage, selectedVariant);
        g.disableScissor();
        traitsContentH = bottom - top;
        scrollTrack(g, tr, tr, traitsContentH, traitsScroll);
    }

    /** A hairline track at the well's right edge when its contents are taller than it: there is more. */
    private static void scrollTrack(GuiGraphics g, Rect well, Rect area, int contentH, int scroll) {
        if (contentH <= area.h()) {
            return;
        }
        int x = well.right() - 2;
        g.fill(x, area.y(), x + 1, area.bottom(), 0x60000000 | (WizardsPalette.GILT_DARK & 0xFFFFFF));
        int thumbH = Math.max(8, area.h() * area.h() / contentH);
        int thumbY = area.y() + (area.h() - thumbH) * scroll / Math.max(1, contentH - area.h());
        g.fill(x, thumbY, x + 1, thumbY + thumbH, WizardsPalette.GILT);
    }

    /** Why a heritage is locked (on its row, and on the greyed Confirm). */
    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int row = layout.rowAt(mouseX, mouseY);
        Heritage h = row >= 0 ? Heritage.values()[row]
                : layout.confirm().contains(mouseX, mouseY) ? selectedHeritage : null;
        if (h == null) {
            return;
        }
        String refusal = HeritageRules.refusalKey(h);
        if (refusal != null) {
            UiTooltips.show(g, font, Component.literal(h.getDisplayName()),
                    List.of(Component.translatable(refusal), Component.translatable("gui.wizards_and_beasts.heritage.locked_tooltip")),
                    mouseX, mouseY);
        }
    }

    // ── Input ────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean isDoubleClick) {
        if (!confirmOpen && event.button() == 0) {
            int row = layout.rowAt(event.x(), event.y());
            if (row >= 0) {
                setFocused(null);
                selectHeritage(Heritage.values()[row]);
                return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    // The wheel scrolls the dossier or the traits -- never the player preview, which used to zoom here and
    // could be resized out of the framing the gate is composed around.
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (confirmOpen) {
            return true;
        }
        int step = (int) (scrollY * SCROLL_STEP);
        if (layout.dossier().contains(mouseX, mouseY)) {
            dossierScroll -= step;
        } else if (layout.traits() != null && layout.traits().contains(mouseX, mouseY)) {
            traitsScroll -= step;
        }
        return true;
    }

    // ── Gate-mode input handling (preserved) ─────────────────────────────

    @Override
    public boolean shouldCloseOnEsc() {
        return previewReturn != null;
    }

    @Override
    public void onClose() {
        if (previewReturn != null && minecraft != null) {
            minecraft.setScreen(previewReturn);
            return;
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (confirmOpen) {
                closeConfirm();
            } else if (previewReturn != null) {
                onClose();
            }
            // Root browse: ESC is swallowed — the gate cannot be dismissed without committing.
            return true;
        }
        if (confirmOpen) {
            // While the "this is final" card is open, only Enter seals it (Tab and Space still work the two
            // buttons); every other key is swallowed so nothing slips past the gate accidentally.
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commit();
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_SPACE) {
                return super.keyPressed(event);
            }
            return true;
        }
        boolean onControl = getFocused() instanceof AbstractButton;
        switch (key) {
            case GLFW.GLFW_KEY_UP -> { cycleHeritage(-1); return true; }
            case GLFW.GLFW_KEY_DOWN -> { cycleHeritage(1); return true; }
            case GLFW.GLFW_KEY_LEFT -> { cycleVariant(-1); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { cycleVariant(1); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                // A focused button takes its own press (Tab to a lineage, Space picks it); otherwise Enter
                // is "confirm", as the hint says.
                if (onControl) {
                    return super.keyPressed(event);
                }
                openConfirm();
                return true;
            }
            default -> { /* fall through to vanilla widget focus handling */ }
        }
        return super.keyPressed(event);
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim only, no blur — same choice the Character Sheet makes. The world stays crisp around the sheet.
        renderMenuBackground(g);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
