package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.ModTextures;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.skill.gui.SkillScreenRouter;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.network.spell.SpellAssignC2SPayload;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.util.MathUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Assigning spells to the four wand slots.
 *
 * <p>Rebuilt around <b>direct manipulation</b>: drag a spell out of the list and drop it on a slot,
 * right-click a slot to empty it. Click-to-select then click-a-slot still works, because it is what
 * the screen used to do and it is the only path a player without a mouse drag can take.
 *
 * <h2>Why the list and the slots are not widgets</h2>
 *
 * <p>They used to be. Every row was a {@code Button} and every slot was an invisible {@code Button},
 * and the whole set was torn down and rebuilt through {@code clearWidgets()} on every scroll tick —
 * which took the search box with it and dropped its focus and caret mid-type. Rows and slots are now
 * painted and hit-tested directly, so scrolling changes one integer and touches nothing else. Only
 * the search box and the Skills button are widgets, and they are built once in {@link #init()}.
 *
 * <p>It also buys the two interactions vanilla {@code Button} cannot express: a right-click (which
 * {@code Button} swallows) and a drag that begins on one control and ends on another.
 *
 * <h2>One source of geometry</h2>
 *
 * <p>{@link #rowY(int)} and {@link #slotX(int)}/{@link #slotY(int)} are used by the painting and by
 * the hit-testing, and that is deliberate. The previous split had the screen place its row buttons at
 * {@code layout.s(LIST_ROW_SPACING)} while the painter drew the row text at an unscaled
 * {@code LIST_ROW_SPACING}, so at any GUI scale other than 100% the labels and the things you could
 * click drifted apart down the list. Geometry that is computed twice will eventually be computed two
 * ways; this one is computed once.
 */
public class SpellMenuScreen extends Screen {

    private static final Identifier SLOT_TEX = Identifier.fromNamespaceAndPath(
            "wizards_and_beasts", "textures/gui/spell_menu/slot.png");
    private static final Identifier SLOT_ACTIVE_TEX = Identifier.fromNamespaceAndPath(
            "wizards_and_beasts", "textures/gui/spell_menu/slot_active.png");

    private static final int PANEL_W = WizardsAndBeastsUiTokens.SpellMenu.PANEL_WIDTH;
    private static final int PANEL_H = WizardsAndBeastsUiTokens.SpellMenu.PANEL_HEIGHT;
    private static final int LEFT_W = WizardsAndBeastsUiTokens.SpellMenu.LEFT_WIDTH;

    /** Slot order matches the HUD diamond in {@code SpellDiamondOverlay}: up, right, down, left. */
    private static final String[] SLOT_KEYS = {"up", "right", "down", "left"};

    /**
     * How far the mouse must move before a press becomes a drag.
     *
     * <p>Without it every click is a one-pixel drag, and releasing over the list — where you already
     * are — would look like a drop that did nothing. Below the threshold the press stays a click and
     * selects, which is what keeps the old two-click flow alive.
     */
    private static final double DRAG_THRESHOLD_PX = 4.0;

    private static final int SCROLLBAR_W = 4;
    private static final int LIST_ICON_SIZE = 12;

    @Nullable
    private String selectedSpellId = null;
    private final List<SpellEntry> spellEntries = new ArrayList<>();
    private int scrollOffset = 0;
    private String searchQuery = "";
    private GuiScaleHelper.Layout layout;

    /** Spell under the cursor since the last press, promoted to {@link #draggingSpellId} on move. */
    @Nullable
    private String pressedSpellId = null;
    @Nullable
    private String draggingSpellId = null;
    private double pressX;
    private double pressY;
    /** Where the dragged spell was lifted from, so the trail has somewhere to start. */
    private int dragOriginX;
    private int dragOriginY;
    private boolean draggingScrollbar = false;

    public SpellMenuScreen() {
        super(Component.translatable("gui.wizards_and_beasts.spell_menu.title"));
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height, PANEL_W, PANEL_H);
        rebuildSpellList();

        // Built once. Nothing below rebuilds widgets, which is the whole point — see the class doc.
        addRenderableWidget(new ThemedButton(
                layout.panelX() + layout.panelW() - layout.s(62), layout.panelY() + layout.s(4),
                layout.s(56), layout.s(14),
                Component.translatable("gui.wizards_and_beasts.spell_menu.skills"),
                SkillScreenRouter::openForCurrentPlayer));

        EditBox searchBox = new EditBox(this.font, listX(), searchY(),
                listW(), layout.s(WizardsAndBeastsUiTokens.SpellMenu.SEARCH_HEIGHT),
                Component.translatable("gui.wizards_and_beasts.spell_menu.search"));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(text -> {
            searchQuery = text.toLowerCase(java.util.Locale.ROOT);
            scrollOffset = 0;
            rebuildSpellList();
        });
        addRenderableWidget(searchBox);
    }

    // ── geometry: used by both the painting and the hit-testing ──────────────

    private int listX() {
        return layout.panelX() + layout.s(WizardsAndBeastsUiTokens.SpellMenu.LEFT_PADDING);
    }

    private int listW() {
        return layout.s(LEFT_W) - layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_BUTTON_SIDE_PADDING);
    }

    private int searchY() {
        return layout.panelY() + layout.s(WizardsAndBeastsUiTokens.SpellMenu.TOP_PADDING);
    }

    private int listTop() {
        return searchY() + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SEARCH_HEIGHT
                + WizardsAndBeastsUiTokens.SpellMenu.SEARCH_TO_LIST_GAP);
    }

    private int rowH() {
        return layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_BUTTON_HEIGHT);
    }

    /** Top edge of the {@code i}-th visible row, {@code i} counted from the scroll offset. */
    private int rowY(int visibleIndex) {
        return listTop() + visibleIndex * layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_ROW_SPACING);
    }

    private int slotSize() {
        return layout.s(WizardsAndBeastsUiTokens.SpellMenu.SLOT_BUTTON_SIZE);
    }

    private int slotX(int slot) {
        int cx = layout.panelX() + layout.s(LEFT_W)
                + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SLOT_CENTER_X_OFFSET);
        int spacing = layout.s(WizardsAndBeastsUiTokens.SpellMenu.SLOT_SPACING);
        int dx = switch (slot) {
            case 1 -> spacing;
            case 3 -> -spacing;
            default -> 0;
        };
        return cx + dx - slotSize() / 2;
    }

    private int slotY(int slot) {
        int cy = layout.panelY() + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SLOT_CENTER_Y_OFFSET);
        int spacing = layout.s(WizardsAndBeastsUiTokens.SpellMenu.SLOT_SPACING);
        int dy = switch (slot) {
            case 0 -> -spacing;
            case 2 -> spacing;
            default -> 0;
        };
        return cy + dy - slotSize() / 2;
    }

    private int maxVisible() {
        int available = layout.panelH()
                - layout.s(WizardsAndBeastsUiTokens.SpellMenu.TOP_PADDING)
                - layout.s(WizardsAndBeastsUiTokens.SpellMenu.SEARCH_HEIGHT)
                - layout.s(WizardsAndBeastsUiTokens.SpellMenu.SEARCH_TO_LIST_GAP)
                - layout.s(24);
        return Math.max(4, available / Math.max(1, layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_ROW_SPACING)));
    }

    private int maxScroll() {
        return Math.max(0, spellEntries.size() - maxVisible());
    }

    /** Index into {@link #spellEntries} under the cursor, or -1. Category headers never match. */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listX() || mouseX > listX() + listW()) {
            return -1;
        }
        int visible = Math.min(maxVisible(), spellEntries.size() - scrollOffset);
        for (int v = 0; v < visible; v++) {
            int y = rowY(v);
            if (mouseY >= y && mouseY < y + rowH()) {
                int idx = scrollOffset + v;
                return spellEntries.get(idx).spell() == null ? -1 : idx;
            }
        }
        return -1;
    }

    private int slotAt(double mouseX, double mouseY) {
        for (int i = 0; i < 4; i++) {
            int x = slotX(i);
            int y = slotY(i);
            if (mouseX >= x && mouseX < x + slotSize() && mouseY >= y && mouseY < y + slotSize()) {
                return i;
            }
        }
        return -1;
    }

    // ── data ─────────────────────────────────────────────────────────────────

    private void rebuildSpellList() {
        spellEntries.clear();
        PlayerSpellData data = ClientSpellDataState.get();
        Heritage type = ClientHeritageDataState.get().getSelectedHeritage();
        String q = searchQuery == null ? "" : searchQuery;

        for (SpellCategory category : SpellCategory.values()) {
            boolean hasSpells = false;
            for (Spell spell : Spells.all()) {
                if (spell.getCategory() != category
                        || !data.knowsSpell(spell.getId())
                        || ObscurialRules.isObscurialAbility(spell)
                        || !ObscurialRules.canHeritageUseSpell(type, spell)) {
                    continue;
                }
                boolean matches = q.isEmpty()
                        || GuiText.resolve(spell.getDisplayName()).toLowerCase(java.util.Locale.ROOT).contains(q)
                        || spell.getId().toLowerCase(java.util.Locale.ROOT).contains(q);
                if (!matches) {
                    continue;
                }
                if (!hasSpells) {
                    spellEntries.add(new SpellEntry(null, category));
                    hasSpells = true;
                }
                spellEntries.add(new SpellEntry(spell, category));
            }
        }
        scrollOffset = Math.min(scrollOffset, maxScroll());
    }

    private void assign(int slot, @Nullable String spellId) {
        ClientSpellDataState.get().setLoadoutSpell(slot, spellId);
        // The server reads a blank id as "clear this slot" — see SpellAssignC2SPayload.handle. That
        // branch existed from the start and nothing in the UI had ever reached it.
        ClientPacketDistributor.sendToServer(new SpellAssignC2SPayload(slot, spellId == null ? "" : spellId));
    }

    // ── input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0) {
            if (button == 1) {
                assign(slot, null);
                return true;
            }
            if (button == 0 && selectedSpellId != null) {
                assign(slot, selectedSpellId);
                return true;
            }
        }

        if (button == 0) {
            int row = rowAt(mouseX, mouseY);
            if (row >= 0) {
                Spell spell = spellEntries.get(row).spell();
                if (spell != null) {
                    selectedSpellId = spell.getId();
                    pressedSpellId = spell.getId();
                    pressX = mouseX;
                    pressY = mouseY;
                    dragOriginX = (int) mouseX;
                    dragOriginY = (int) mouseY;
                    return true;
                }
            }
            if (maxScroll() > 0 && overScrollbar(mouseX, mouseY)) {
                draggingScrollbar = true;
                scrollTo(mouseY);
                return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (draggingScrollbar) {
            scrollTo(mouseY);
            return true;
        }
        if (pressedSpellId != null && draggingSpellId == null) {
            double dx = mouseX - pressX;
            double dy = mouseY - pressY;
            if (dx * dx + dy * dy >= DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX) {
                draggingSpellId = pressedSpellId;
            }
        }
        if (draggingSpellId != null) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        draggingScrollbar = false;
        if (draggingSpellId != null) {
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                assign(slot, draggingSpellId);
            }
            draggingSpellId = null;
            pressedSpellId = null;
            return true;
        }
        pressedSpellId = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = MathUtils.clampInt((int) (scrollOffset - scrollY), 0, maxScroll());
        return true;
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        int x = scrollbarX();
        return mouseX >= x && mouseX <= x + layout.s(SCROLLBAR_W)
                && mouseY >= listTop() && mouseY <= listTop() + trackH();
    }

    private int scrollbarX() {
        return listX() + listW() + layout.s(2);
    }

    private int trackH() {
        return maxVisible() * layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_ROW_SPACING);
    }

    private void scrollTo(double mouseY) {
        double fraction = (mouseY - listTop()) / Math.max(1, trackH());
        scrollOffset = MathUtils.clampInt((int) Math.round(fraction * maxScroll()), 0, maxScroll());
    }

    // ── painting ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        SpellMenuRenderHelper.renderFrame(graphics, font, width, height, PANEL_W, PANEL_H, LEFT_W, layout);

        renderList(graphics, mouseX, mouseY);
        renderScrollbar(graphics);

        renderInfoCard(graphics);
        SpellMenuRenderHelper.renderSelectedSpellPanel(graphics, font, width, height, PANEL_W, PANEL_H,
                LEFT_W, selectedSpellId, layout);

        super.render(graphics, mouseX, mouseY, partialTick);

        renderSlots(graphics, mouseX, mouseY);
        renderHint(graphics);
        renderDragGhost(graphics, mouseX, mouseY);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (spellEntries.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.wizards_and_beasts.spell_menu.empty"),
                    listX(), listTop() + WizardsAndBeastsUiTokens.SpellMenu.EMPTY_LIST_TEXT_Y_OFFSET,
                    WizardsPalette.TEXT_DIM, false);
            return;
        }
        int hovered = rowAt(mouseX, mouseY);
        int visible = Math.min(maxVisible(), spellEntries.size() - scrollOffset);
        for (int v = 0; v < visible; v++) {
            int idx = scrollOffset + v;
            SpellMenuRenderHelper.renderRow(graphics, font, spellEntries.get(idx),
                    listX(), rowY(v), listW(), rowH(),
                    idx == hovered,
                    selectedSpellId != null && spellEntries.get(idx).spell() != null
                            && selectedSpellId.equals(spellEntries.get(idx).spell().getId()),
                    LIST_ICON_SIZE);
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int x = scrollbarX();
        int w = layout.s(SCROLLBAR_W);
        int track = trackH();
        graphics.fill(x, listTop(), x + w, listTop() + track, WizardsPalette.WELL);

        int thumbH = Math.max(layout.s(8), track * maxVisible() / Math.max(1, spellEntries.size()));
        int thumbY = listTop() + (track - thumbH) * scrollOffset / max;
        graphics.fill(x, thumbY, x + w, thumbY + thumbH, WizardsPalette.THUMB);
    }

    /**
     * The rune-socket plate.
     *
     * <p>Engraving first, then the sockets on top of it, so the ley-lines tuck under the brass rather
     * than crossing it.
     */
    private void renderSlots(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        PlayerSpellData data = ClientSpellDataState.get();
        int size = slotSize();
        int iconSize = Math.max(8, size - 14);
        int hoveredSlot = slotAt(mouseX, mouseY);
        float age = mc.level == null ? 0f : mc.level.getGameTime() % 100000L;

        int hubX = layout.panelX() + layout.s(LEFT_W)
                + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SLOT_CENTER_X_OFFSET);
        int hubY = layout.panelY() + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SLOT_CENTER_Y_OFFSET);

        for (int i = 0; i < 4; i++) {
            String spellId = data.getLoadoutSpell(i);
            SpellSigilRenderer.leyLine(graphics, hubX, hubY,
                    slotX(i) + size / 2, slotY(i) + size / 2,
                    spellId != null, accentOf(spellId), age);
        }
        SpellSigilRenderer.hub(graphics, hubX, hubY, Math.max(3, size / 6), age);

        for (int i = 0; i < 4; i++) {
            int sx = slotX(i);
            int sy = slotY(i);
            String spellId = data.getLoadoutSpell(i);
            boolean hovered = i == hoveredSlot;

            SpellSigilRenderer.socket(graphics, sx, sy, size, spellId != null, hovered,
                    hovered && draggingSpellId != null, accentOf(spellId), age);

            Component label = Component.translatable(
                    "gui.wizards_and_beasts.spell_menu.slot." + SLOT_KEYS[i]);
            if (spellId != null) {
                Spell spell = Spells.byId(spellId);
                if (spell != null) {
                    label = Component.literal(GuiText.resolve(spell.getDisplayName()));
                }
                Identifier icon = ModTextures.resolveWandHudSpellIcon(mc.getResourceManager(), spellId);
                graphics.blit(RenderPipelines.GUI_TEXTURED, icon,
                        sx + (size - iconSize) / 2, sy + (size - iconSize) / 2,
                        0f, 0f, iconSize, iconSize, 92, 92, 92, 92);
                SpellSigilRenderer.proficiencyPips(graphics, sx, sy, size,
                        proficiencyPipsFor(spellId));
            }

            // Clamped to the socket's own width plus a little. Spell names run long ("Expelliarmus"),
            // and an unclamped centred label reaches into the ley-lines either side of it.
            String text = font.plainSubstrByWidth(label.getString(), size + layout.s(8));
            graphics.drawString(font, text, sx + size / 2 - font.width(text) / 2,
                    sy - font.lineHeight - 2,
                    spellId != null ? WizardsPalette.TEXT : WizardsPalette.TEXT_DIM, true);
        }
    }

    /** A filled socket is read by hue; an empty one has no spell to take a hue from. */
    private static int accentOf(@Nullable String spellId) {
        if (spellId == null) {
            return WizardsPalette.LINE;
        }
        Spell spell = Spells.byId(spellId);
        return spell == null ? WizardsPalette.LINE : 0xFF000000 | spell.getCategory().getColor();
    }

    private static int proficiencyPipsFor(String spellId) {
        return switch (at.koopro.wizardsandbeasts.spell.core.Proficiency.fromCastCount(
                ClientSpellDataState.get().getSuccessfulHits(spellId))) {
            case MASTERED -> 3;
            case PROFICIENT -> 2;
            default -> 1;
        };
    }

    /**
     * A recessed card under the detail text.
     *
     * <p>Without it the spell's name, cooldown and proficiency sat directly on the leather with
     * nothing holding them, and the lower half of the right column read as unused panel rather than
     * as a place information appears.
     */
    private void renderInfoCard(GuiGraphics graphics) {
        int x = layout.panelX() + layout.s(LEFT_W) + layout.s(4);
        int right = layout.panelX() + layout.panelW() - layout.s(4);
        int top = layout.panelY() + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SELECTED_INFO_BASE_Y)
                - layout.s(6);
        int bottom = layout.panelY() + layout.panelH()
                - layout.s(WizardsAndBeastsUiTokens.SpellMenu.ASSIGN_HINT_BOTTOM_OFFSET)
                - layout.s(4);
        graphics.fill(x, top, right, bottom, WizardsPalette.WELL);
        graphics.fill(x, top, right, top + 1, WizardsPalette.LINE);
    }

    /**
     * The one line of instruction, and it changes with what you are holding — a static "click slot to
     * assign" was the only affordance the screen had, and it did not mention that a slot could be
     * emptied at all.
     */
    private void renderHint(GuiGraphics graphics) {
        String key = draggingSpellId != null
                ? "gui.wizards_and_beasts.spell_menu.hint_drop"
                : selectedSpellId != null
                        ? "gui.wizards_and_beasts.spell_menu.hint_assign"
                        : "gui.wizards_and_beasts.spell_menu.hint_pick";
        graphics.drawCenteredString(font, Component.translatable(key),
                layout.panelX() + layout.s(LEFT_W)
                        + layout.s(WizardsAndBeastsUiTokens.SpellMenu.ASSIGN_HINT_CENTER_X),
                layout.panelY() + layout.panelH()
                        - layout.s(WizardsAndBeastsUiTokens.SpellMenu.ASSIGN_HINT_BOTTOM_OFFSET),
                WizardsPalette.TEXT_DIM);
    }

    private void renderDragGhost(GuiGraphics graphics, int mouseX, int mouseY) {
        if (draggingSpellId == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int accent = accentOf(draggingSpellId);
        SpellSigilRenderer.dragThread(graphics, dragOriginX, dragOriginY, mouseX, mouseY, accent);
        Identifier icon = ModTextures.resolveWandHudSpellIcon(mc.getResourceManager(), draggingSpellId);
        int s = LIST_ICON_SIZE + 4;
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, mouseX - s / 2, mouseY - s / 2,
                0f, 0f, s, s, 92, 92, 92, 92);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public record SpellEntry(@Nullable Spell spell, SpellCategory category) {
    }
}
