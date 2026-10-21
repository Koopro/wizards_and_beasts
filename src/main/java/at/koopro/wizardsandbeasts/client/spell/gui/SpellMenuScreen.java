package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui.Rect;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.ui.UiTooltips;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedChip;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedTextField;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.hud.WandHudSprites;
import at.koopro.wizardsandbeasts.client.skill.gui.SkillScreenRouter;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.network.spell.SpellAssignC2SPayload;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.util.MathUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The wand loadout: which four spells the wand answers with, and what each of them is.
 *
 * <p>Three regions on the parchment sheet, all placed by {@link SpellMenuLayout}: the <b>library</b> of
 * known spells (search, category filters, a clipped scrolling list), the <b>wand</b> (four sockets in
 * the HUD diamond's order, with a row per socket naming what it holds) and the <b>details</b> of the
 * picked spell (only the fields that spell has). On a narrow screen the details move under the wand.
 *
 * <h2>Direct manipulation, and the paths around it</h2>
 *
 * <p>Drag a spell onto a socket; right-click a socket to empty it. Click a spell and then a socket
 * still works, and so does the keyboard: up/down walks the list, 1-4 binds the picked spell to
 * up/right/down/left, and typing a letter anywhere starts a search. A socket's row under the diamond
 * is the same target as the socket, only bigger.
 *
 * <h2>Why the list and the sockets are not widgets</h2>
 *
 * <p>They used to be, and the whole set was torn down and rebuilt on every scroll tick -- which took
 * the search box with it and dropped its focus mid-word. Rows and sockets are painted and hit-tested
 * from the layout, so scrolling changes one integer. Only the search box, the filters and the Skills
 * button are widgets, built once in {@link #init()}, at the layout's rectangles.
 */
public class SpellMenuScreen extends Screen {

    private static final UiSurface S = UiSurface.PAGE;

    /** Slot order matches the HUD diamond in {@code SpellDiamondOverlay}: up, right, down, left. */
    static final String[] SLOT_KEYS = {"up", "right", "down", "left"};

    /**
     * How far the mouse must move before a press becomes a drag. Below it the press stays a click and
     * selects, which keeps the two-click flow alive.
     */
    private static final double DRAG_THRESHOLD_PX = 4.0;
    private static final int DETAIL_PAD = 6;
    private static final int DETAIL_SCROLL_STEP = 10;

    @Nullable
    private String selectedSpellId = null;
    /** The category filter; null shows every category under its own heading. */
    @Nullable
    private SpellCategory filter = null;
    private final List<SpellEntry> spellEntries = new ArrayList<>();
    private int scrollOffset = 0;
    private String searchText = "";
    private SpellMenuLayout layout;
    @Nullable
    private ThemedTextField searchBox;

    private int detailScroll = 0;
    /** Height the details took when last drawn, so the wheel knows how far it may scroll them. */
    private int detailContentH = 0;

    /** Spell under the cursor since the last press, promoted to {@link #draggingSpellId} on move. */
    @Nullable
    private String pressedSpellId = null;
    @Nullable
    private String draggingSpellId = null;
    private double pressX;
    private double pressY;
    /** Where the dragged spell was lifted from, so the thread has somewhere to start. */
    private int dragOriginX;
    private int dragOriginY;
    private boolean draggingScrollbar = false;

    /** The filter chips with the category each stands for (null = all), to re-judge after a sync. */
    private final List<ThemedChip> chips = new ArrayList<>();
    private final List<@Nullable SpellCategory> chipCategories = new ArrayList<>();
    /** {@link ClientSpellDataState#version()} the list was built from. */
    private long seenVersion = -1;

    public SpellMenuScreen() {
        super(Component.translatable("gui.wizards_and_beasts.spell_menu.title"));
    }

    /** Opens with {@code spellId} picked, its details showing. */
    public SpellMenuScreen(@Nullable String spellId) {
        this();
        this.selectedSpellId = spellId;
    }

    @Override
    protected void init() {
        super.init();
        List<@Nullable SpellCategory> filters = new ArrayList<>();
        filters.add(null);
        filters.addAll(List.of(SpellCategory.values()));
        int[] widths = new int[filters.size()];
        List<Component> labels = new ArrayList<>(filters.size());
        for (int i = 0; i < filters.size(); i++) {
            SpellCategory c = filters.get(i);
            Component label = c == null
                    ? Component.translatable("gui.wizards_and_beasts.spell_menu.filter.all")
                    : SpellMenuRenderHelper.categoryName(c);
            labels.add(label);
            widths[i] = ThemedChip.widthFor(font, label);
        }
        layout = SpellMenuLayout.of(width, height, widths);
        rebuildSpellList();

        // Built once, at the layout's rectangles. Nothing below rebuilds widgets -- see the class doc.
        Rect s = layout.search();
        searchBox = new ThemedTextField(font, s.x(), s.y(), s.w(), s.h(),
                Component.translatable("gui.wizards_and_beasts.spell_menu.search"))
                .inkHint(Component.translatable("gui.wizards_and_beasts.spell_menu.search_hint"));
        searchBox.setValue(searchText);
        searchBox.setResponder(text -> {
            searchText = text;
            scrollOffset = 0;
            rebuildSpellList();
        });
        addRenderableWidget(searchBox);

        chips.clear();
        chipCategories.clear();
        for (int i = 0; i < filters.size(); i++) {
            SpellCategory c = filters.get(i);
            Rect r = layout.chips().get(i);
            ThemedChip chip = new ThemedChip(r.x(), r.y(), r.w(), r.h(), labels.get(i),
                    () -> setFilter(c), () -> filter == c);
            chips.add(chip);
            chipCategories.add(c);
            addRenderableWidget(chip);
        }
        judgeChips();
        seenVersion = ClientSpellDataState.version();

        Rect k = layout.skills();
        addRenderableWidget(new ThemedButton(k.x(), k.y(), k.w(), k.h(),
                Component.translatable("gui.wizards_and_beasts.spell_menu.skills"),
                SkillScreenRouter::openForCurrentPlayer));
    }

    /**
     * Nothing is focused on open, so the number keys bind at once; the first letter typed focuses the
     * search box instead ({@link #charTyped}). Vanilla would focus the first widget, the search box,
     * and the number keys would type into it.
     */
    @Override
    protected void setInitialFocus() {
    }

    /**
     * A sync that lands while the screen is open -- a spell learned, a server refusal undoing a socket
     * this screen filled optimistically -- rebuilds the list. Without it the list stayed as it was when
     * the screen opened, and a refused bind looked accepted until the screen was reopened.
     */
    @Override
    public void tick() {
        super.tick();
        if (seenVersion != ClientSpellDataState.version()) {
            seenVersion = ClientSpellDataState.version();
            judgeChips();
            rebuildSpellList();
        }
    }

    /** A category the player knows nothing in cannot be picked: it would only show an empty list. */
    private void judgeChips() {
        for (int i = 0; i < chips.size(); i++) {
            SpellCategory c = chipCategories.get(i);
            chips.get(i).active = c == null || knownIn(c) > 0;
        }
        if (filter != null && knownIn(filter) == 0) {
            filter = null;
        }
    }

    // ── data ─────────────────────────────────────────────────────────────────

    /** Whether the list shows this spell at all: known, and castable by this player's heritage. */
    private static boolean listed(Spell spell, PlayerSpellData data) {
        return data.knowsSpell(spell.getId())
                && !ObscurialRules.isObscurialAbility(spell)
                && ObscurialRules.canHeritageUseSpell(ClientHeritageDataState.get(), spell);
    }

    private static int knownIn(SpellCategory category) {
        PlayerSpellData data = ClientSpellDataState.get();
        int n = 0;
        for (Spell spell : Spells.all()) {
            if (spell.getCategory() == category && listed(spell, data)) {
                n++;
            }
        }
        return n;
    }

    private void setFilter(@Nullable SpellCategory category) {
        filter = category;
        scrollOffset = 0;
        rebuildSpellList();
    }

    private void rebuildSpellList() {
        spellEntries.clear();
        PlayerSpellData data = ClientSpellDataState.get();
        String q = searchText.toLowerCase(Locale.ROOT).trim();
        for (SpellCategory category : SpellCategory.values()) {
            if (filter != null && category != filter) {
                continue;
            }
            boolean headed = false;
            for (Spell spell : Spells.all()) {
                if (spell.getCategory() != category || !listed(spell, data)) {
                    continue;
                }
                boolean matches = q.isEmpty()
                        || GuiText.resolve(spell.getDisplayName()).toLowerCase(Locale.ROOT).contains(q)
                        || spell.getId().toLowerCase(Locale.ROOT).contains(q);
                if (!matches) {
                    continue;
                }
                // Headings only when every category is shown: under a filter the chip already says it.
                if (!headed && filter == null) {
                    spellEntries.add(new SpellEntry(null, category));
                }
                headed = true;
                spellEntries.add(new SpellEntry(spell, category));
            }
        }
        scrollOffset = MathUtils.clampInt(scrollOffset, 0, maxScroll());
    }

    private void select(@Nullable String spellId) {
        if (spellId != null && !spellId.equals(selectedSpellId)) {
            detailScroll = 0;
        }
        selectedSpellId = spellId;
    }

    private void assign(int slot, @Nullable String spellId) {
        // The one choke point every assignment passes through -- click, drag, keys -- so refusing a
        // placeholder spell once covers all of them. The server refuses the same assign independently;
        // this only keeps the local mirror from showing a socket filled before the sync undoes it.
        if (spellId != null) {
            Spell spell = Spells.byId(spellId);
            if (spell != null && !spell.isImplemented()) {
                return;
            }
        }
        ClientSpellDataState.get().setLoadoutSpell(slot, spellId);
        // The server reads a blank id as "clear this slot" -- see SpellAssignC2SPayload.handle.
        ClientPacketDistributor.sendToServer(new SpellAssignC2SPayload(slot, spellId == null ? "" : spellId));
    }

    private int maxScroll() {
        return Math.max(0, spellEntries.size() - layout.visibleRows());
    }

    /** Index into {@link #spellEntries} of the spell row under the cursor, or -1. Headings never match. */
    private int entryAt(double mouseX, double mouseY) {
        int row = layout.rowAt(mouseX, mouseY);
        if (row < 0) {
            return -1;
        }
        int idx = scrollOffset + row;
        return idx < spellEntries.size() && spellEntries.get(idx).spell() != null ? idx : -1;
    }

    /** The first socket holding {@code spellId}, or -1. */
    private static int boundSlot(String spellId) {
        PlayerSpellData data = ClientSpellDataState.get();
        for (int i = 0; i < 4; i++) {
            if (spellId.equals(data.getLoadoutSpell(i))) {
                return i;
            }
        }
        return -1;
    }

    private static List<Integer> boundSlots(String spellId) {
        PlayerSpellData data = ClientSpellDataState.get();
        List<Integer> out = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            if (spellId.equals(data.getLoadoutSpell(i))) {
                out.add(i);
            }
        }
        return out;
    }

    // ── input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        int slot = layout.slotAt(mouseX, mouseY);
        if (slot >= 0) {
            setFocused(null);
            if (button == 1) {
                assign(slot, null);
                return true;
            }
            if (button == 0) {
                if (selectedSpellId != null) {
                    assign(slot, selectedSpellId);
                } else {
                    // Nothing picked: a click on a filled socket opens its spell's details instead.
                    select(ClientSpellDataState.get().getLoadoutSpell(slot));
                }
                return true;
            }
        }

        if (button == 0) {
            int idx = entryAt(mouseX, mouseY);
            if (idx >= 0) {
                setFocused(null);
                Spell spell = spellEntries.get(idx).spell();
                select(spell.getId());
                if (spell.isImplemented()) {
                    // Placeholder spells are selectable so their details can be read, but never
                    // draggable: a ghost that can only be dropped onto a refusal is worse than none.
                    pressedSpellId = spell.getId();
                    pressX = mouseX;
                    pressY = mouseY;
                    dragOriginX = (int) mouseX;
                    dragOriginY = (int) mouseY;
                }
                return true;
            }
            if (maxScroll() > 0 && layout.scrollbar().contains(mouseX, mouseY)) {
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
        draggingScrollbar = false;
        if (draggingSpellId != null) {
            int slot = layout.slotAt(event.x(), event.y());
            if (slot >= 0) {
                assign(slot, draggingSpellId);
            }
            // Released anywhere else: the drag is cancelled, which the hint said while it was held.
            draggingSpellId = null;
            pressedSpellId = null;
            return true;
        }
        pressedSpellId = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout.detail().contains(mouseX, mouseY)) {
            int max = Math.max(0, detailContentH - (layout.detail().h() - 2 * DETAIL_PAD));
            detailScroll = MathUtils.clampInt((int) (detailScroll - scrollY * DETAIL_SCROLL_STEP), 0, max);
            return true;
        }
        scrollOffset = MathUtils.clampInt((int) (scrollOffset - scrollY), 0, maxScroll());
        return true;
    }

    private void scrollTo(double mouseY) {
        Rect bar = layout.scrollbar();
        double fraction = (mouseY - bar.y()) / Math.max(1, bar.h());
        scrollOffset = MathUtils.clampInt((int) Math.round(fraction * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            moveSelection(key == GLFW.GLFW_KEY_DOWN ? 1 : -1);
            return true;
        }
        boolean typing = searchBox != null && searchBox.isFocused();
        if (!typing && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4 && selectedSpellId != null) {
            assign(key - GLFW.GLFW_KEY_1, selectedSpellId);
            return true;
        }
        return super.keyPressed(event);
    }

    /** A letter typed with nothing focused starts a search, so the list can be searched without aiming. */
    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchBox != null && getFocused() == null && Character.isLetter(event.codepoint())) {
            setFocused(searchBox);
            return searchBox.charTyped(event);
        }
        return super.charTyped(event);
    }

    /** Up/down through the spells in the list (headings skipped), keeping the pick on screen. */
    private void moveSelection(int delta) {
        List<Integer> spellRows = new ArrayList<>();
        int current = -1;
        for (int i = 0; i < spellEntries.size(); i++) {
            Spell spell = spellEntries.get(i).spell();
            if (spell != null) {
                if (spell.getId().equals(selectedSpellId)) {
                    current = spellRows.size();
                }
                spellRows.add(i);
            }
        }
        if (spellRows.isEmpty()) {
            return;
        }
        int next = current < 0 ? (delta > 0 ? 0 : spellRows.size() - 1)
                : MathUtils.clampInt(current + delta, 0, spellRows.size() - 1);
        int idx = spellRows.get(next);
        select(spellEntries.get(idx).spell().getId());
        int visible = layout.visibleRows();
        if (idx < scrollOffset) {
            // One row more when a heading sits right above, so the pick keeps its category in view.
            scrollOffset = idx > 0 && spellEntries.get(idx - 1).spell() == null ? idx - 1 : idx;
        } else if (idx >= scrollOffset + visible) {
            scrollOffset = idx - visible + 1;
        }
        scrollOffset = MathUtils.clampInt(scrollOffset, 0, maxScroll());
    }

    // ── painting ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        renderFrame(graphics);
        boolean nameTruncated = renderList(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
        renderWand(graphics, mouseX, mouseY);
        renderDetail(graphics);
        renderHint(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        renderTooltips(graphics, mouseX, mouseY, nameTruncated);
        renderDragGhost(graphics, mouseX, mouseY);
    }

    /** The sheet, the title over the gilt rule, and the three wells. */
    private void renderFrame(GuiGraphics g) {
        Rect p = layout.panel();
        Ui.panel(g, S, p.x(), p.y(), p.w(), p.h());
        Rect t = layout.title();
        UiText.drawFit(g, font, S, title, t.x(), t.y() + 3, t.w(), S.ink());
        Rect c = layout.content();
        Ui.divider(g, S, c.x(), layout.ruleY(), c.w());
        Rect lib = layout.library();
        Ui.inset(g, S, lib.x(), lib.y(), lib.w(), lib.h());
        Rect d = layout.detail();
        Ui.inset(g, S, d.x(), d.y(), d.w(), d.h());
    }

    /** Returns whether the hovered row's name was shortened, for its tooltip. */
    private boolean renderList(GuiGraphics g, int mouseX, int mouseY) {
        Rect list = layout.list();
        if (spellEntries.isEmpty()) {
            boolean searching = !searchText.isBlank();
            Ui.emptyState(g, font, S, list,
                    Component.translatable(searching ? "gui.wizards_and_beasts.spell_menu.empty"
                            : "gui.wizards_and_beasts.spell_menu.empty_none"),
                    searching ? null : Component.translatable("gui.wizards_and_beasts.spell_menu.empty_none_hint"));
            return false;
        }
        int hovered = draggingSpellId == null ? entryAt(mouseX, mouseY) : -1;
        boolean truncatedUnderMouse = false;
        int visible = Math.min(layout.visibleRows(), spellEntries.size() - scrollOffset);
        // Rows are whole by construction (the list is a whole number of rows tall), so no scissor is
        // needed for them; the scissor guards the icon blits against a future change to that.
        g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        for (int v = 0; v < visible; v++) {
            int idx = scrollOffset + v;
            SpellEntry entry = spellEntries.get(idx);
            Rect r = layout.row(v);
            if (entry.spell() == null) {
                SpellMenuRenderHelper.renderHeader(g, font, entry.category(), r);
                continue;
            }
            String id = entry.spell().getId();
            boolean truncated = SpellMenuRenderHelper.renderRow(g, font, entry.spell(), r, idx == hovered,
                    id.equals(selectedSpellId), id.equals(draggingSpellId), boundSlot(id));
            if (idx == hovered) {
                truncatedUnderMouse = truncated;
            }
        }
        g.disableScissor();
        return truncatedUnderMouse;
    }

    /** A groove in the well with the wax ribbon, only when the list is longer than its well. */
    private void renderScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        Rect bar = layout.scrollbar();
        g.fill(bar.x() + 1, bar.y(), bar.right() - 1, bar.bottom(), WizardsPalette.PAGE_DEEP);
        int thumbH = Math.max(10, bar.h() * layout.visibleRows() / Math.max(1, spellEntries.size()));
        int thumbY = bar.y() + (bar.h() - thumbH) * scrollOffset / max;
        g.fill(bar.x() + 1, thumbY, bar.right() - 1, thumbY + thumbH, WizardsPalette.WAX);
    }

    /**
     * The diamond: channels first, then the hub, then the sockets on top, so the channels tuck under
     * the metal. A filled socket shows its spell's icon inside a ring of its category's hue and its
     * proficiency pips under it; an empty one shows the chevron of the direction it answers to.
     */
    private void renderWand(GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        PlayerSpellData data = ClientSpellDataState.get();
        float age = mc.level == null ? 0f : mc.level.getGameTime() % 100000L;
        int hovered = layout.slotAt(mouseX, mouseY);

        for (int i = 0; i < 4; i++) {
            Rect s = layout.sockets().get(i);
            String spellId = data.getLoadoutSpell(i);
            SpellSigilRenderer.leyLine(g, layout.hubX(), layout.hubY(), s.x() + s.w() / 2, s.y() + s.h() / 2,
                    spellId != null, accentOf(spellId), age);
        }
        SpellSigilRenderer.hub(g, layout.hubX(), layout.hubY(), 5, age);

        for (int i = 0; i < 4; i++) {
            Rect s = layout.sockets().get(i);
            String spellId = data.getLoadoutSpell(i);
            boolean over = i == hovered;
            SpellSigilRenderer.socket(g, s.x(), s.y(), s.w(), spellId != null, over,
                    over && draggingSpellId != null, accentOf(spellId), age);
            if (spellId != null) {
                int icon = 16;
                g.blitSprite(RenderPipelines.GUI_TEXTURED, WandHudSprites.spellIcon(spellId),
                        s.x() + (s.w() - icon) / 2, s.y() + (s.h() - icon) / 2, icon, icon);
                SpellSigilRenderer.proficiencyPips(g, s.x(), s.y(), s.w(),
                        SpellMenuRenderHelper.proficiencyPips(spellId));
            } else {
                Ui.arrow(g, i, s.x() + (s.w() - Ui.ARROW) / 2, s.y() + (s.h() - Ui.ARROW) / 2,
                        WizardsPalette.PAGE_INK_2);
            }
        }

        for (int i = 0; i < layout.bindings().size(); i++) {
            String spellId = data.getLoadoutSpell(i);
            SpellMenuRenderHelper.renderBinding(g, font, i, layout.bindings().get(i),
                    spellId == null ? null : Spells.byId(spellId),
                    i == hovered && draggingSpellId == null, i == hovered && draggingSpellId != null);
        }
    }

    /**
     * A filled socket is read by hue. The category hues were picked for a dark plate and several all but
     * vanish on paper, so the hue is clamped to the 3 : 1 a ring or a line needs against the page.
     */
    private static int accentOf(@Nullable String spellId) {
        Spell spell = spellId == null ? null : Spells.byId(spellId);
        return spell == null ? WizardsPalette.PAGE_INK_2
                : UiContrast.readableOn(spell.getCategory().getColor(), WizardsPalette.PAGE, UiContrast.AA_LARGE);
    }

    /**
     * The details card, clipped to its well and scrolled by the wheel when it is taller. Empty until a
     * spell is picked, and then it says what it will show.
     */
    private void renderDetail(GuiGraphics g) {
        Rect area = layout.detail().inset(DETAIL_PAD);
        Spell spell = selectedSpellId == null ? null : Spells.byId(selectedSpellId);
        if (spell == null) {
            detailContentH = 0;
            Ui.emptyState(g, font, S, area,
                    Component.translatable("gui.wizards_and_beasts.spell_menu.no_selection"),
                    Component.translatable("gui.wizards_and_beasts.spell_menu.no_selection_hint"));
            return;
        }
        int max = Math.max(0, detailContentH - area.h());
        detailScroll = MathUtils.clampInt(detailScroll, 0, max);
        g.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        int top = area.y() - detailScroll;
        int bottom = SpellMenuRenderHelper.renderDetail(g, font, spell, area, top, boundSlots(spell.getId()));
        g.disableScissor();
        detailContentH = bottom - top;

        if (detailContentH > area.h()) {
            // A hairline track on the card's right edge: there is more below, and roughly how much.
            int x = layout.detail().right() - 3;
            int trackTop = area.y();
            g.fill(x, trackTop, x + 1, area.bottom(), WizardsPalette.PAGE_DEEP);
            int thumbH = Math.max(8, area.h() * area.h() / detailContentH);
            int thumbY = trackTop + (area.h() - thumbH) * detailScroll / Math.max(1, detailContentH - area.h());
            g.fill(x, thumbY, x + 1, thumbY + thumbH, WizardsPalette.WAX);
        }
    }

    /**
     * The one line of instruction, and it follows what you are doing: what a drag will do on release
     * where you are holding it, how to bind what you picked, or how to start.
     */
    private void renderHint(GuiGraphics g, int mouseX, int mouseY) {
        Component hint;
        if (draggingSpellId != null) {
            int slot = layout.slotAt(mouseX, mouseY);
            hint = slot >= 0
                    ? Component.translatable("gui.wizards_and_beasts.spell_menu.hint_drop_on",
                    Component.translatable("gui.wizards_and_beasts.spell_menu.slot." + SLOT_KEYS[slot]))
                    : Component.translatable("gui.wizards_and_beasts.spell_menu.hint_drop_cancel");
        } else if (selectedSpellId != null) {
            Spell spell = Spells.byId(selectedSpellId);
            hint = Component.translatable(spell != null && !spell.isImplemented()
                    ? "gui.wizards_and_beasts.spell_menu.hint_unavailable"
                    : "gui.wizards_and_beasts.spell_menu.hint_assign");
        } else {
            hint = Component.translatable("gui.wizards_and_beasts.spell_menu.hint_pick");
        }
        Rect r = layout.hint();
        String text = UiText.fit(font, hint.getString(), r.w());
        UiText.drawCentered(g, font, S, Component.literal(text), r.x() + r.w() / 2, r.y(), S.ink2());
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY, boolean nameTruncated) {
        if (draggingSpellId != null) {
            return;
        }
        int slot = layout.slotAt(mouseX, mouseY);
        if (slot >= 0) {
            String spellId = ClientSpellDataState.get().getLoadoutSpell(slot);
            Spell spell = spellId == null ? null : Spells.byId(spellId);
            Component direction = Component.translatable("gui.wizards_and_beasts.spell_menu.slot." + SLOT_KEYS[slot]);
            if (spell == null) {
                UiTooltips.show(g, font, Component.translatable("gui.wizards_and_beasts.spell_menu.socket_empty", direction),
                        List.of(Component.translatable("gui.wizards_and_beasts.spell_menu.socket_empty_hint")),
                        mouseX, mouseY);
            } else {
                UiTooltips.show(g, font, Component.literal(GuiText.resolve(spell.getDisplayName())),
                        List.of(SpellMenuRenderHelper.categoryName(spell.getCategory()),
                                Component.translatable("gui.wizards_and_beasts.spell_menu.socket_filled_hint", direction)),
                        mouseX, mouseY);
            }
            return;
        }
        int idx = entryAt(mouseX, mouseY);
        if (idx >= 0 && nameTruncated) {
            Spell spell = spellEntries.get(idx).spell();
            UiTooltips.show(g, font, Component.literal(GuiText.resolve(spell.getDisplayName())), mouseX, mouseY);
        }
    }

    /**
     * The spell on the end of a thread. Full strength over a socket that will take it, faded anywhere a
     * release would cancel -- with the hint line saying which, so the fade is not the only cue.
     */
    private void renderDragGhost(GuiGraphics g, int mouseX, int mouseY) {
        if (draggingSpellId == null) {
            return;
        }
        boolean overSocket = layout.slotAt(mouseX, mouseY) >= 0;
        SpellSigilRenderer.dragThread(g, dragOriginX, dragOriginY, mouseX, mouseY, accentOf(draggingSpellId));
        int card = 22;
        Ui.inset(g, S, mouseX - card / 2, mouseY - card / 2, card, card);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, WandHudSprites.spellIcon(draggingSpellId),
                mouseX - 8, mouseY - 8, 16, 16, overSocket ? 1.0f : 0.55f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public record SpellEntry(@Nullable Spell spell, SpellCategory category) {
    }
}
