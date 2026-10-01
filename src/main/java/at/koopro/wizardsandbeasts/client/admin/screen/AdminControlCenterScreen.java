package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchIndex;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminConfirmDialog;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminSessionInfo;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import at.koopro.wizardsandbeasts.admin.spell.SpellSettingIds;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Wizards &amp; Beasts Control Center: a title bar with the server's state, a section sidebar, the selected
 * section's panel, and Back / Revert / Reset section / Apply along the bottom.
 *
 * <p>A client view of server state and nothing more. It opens only when the server sends a snapshot in
 * answer to an authorised request; every value it shows came from the server; every change it makes is a
 * request the server may refuse. Edits are held as drafts until Apply, dangerous ones pass through a
 * confirmation, and each row reports what the server actually did.
 *
 * <p>Never a pause screen: in singleplayer a pause screen stops the integrated server, and every request
 * this screen sends would sit unanswered until it closed.
 */
@NullMarked
public final class AdminControlCenterScreen extends Screen implements AdminPanelHost {

    private static final int TOP_H = 22;
    private static final int BOTTOM_H = 26;
    private static final int SIDEBAR_W = 112;
    private static final int SIDEBAR_NARROW_W = 24;
    private static final int ENTRY_H = 18;
    /** Caps that keep the frame readable on a big window at a small GUI scale, not a fixed size. */
    private static final int MAX_W = 720;
    private static final int MAX_H = 420;
    private static final int FOOTER_BUTTON_H = 18;
    /** How long the footer keeps reporting a refusal or a dropped edit. */
    private static final long STATUS_WINDOW_MS = 12_000L;
    /** How long it says "saved" after the last change was stored. */
    private static final long SAVED_WINDOW_MS = 5_000L;

    /** Reopening lands where the admin left off. */
    private static AdminCategory lastSection = AdminCategory.DASHBOARD;

    private final AdminEditSession edits = new AdminEditSession();
    private AdminCategory section = lastSection;
    private AdminPanel panel;
    private @Nullable AdminConfirmDialog dialog;
    private boolean rebuildRequested;
    private double sidebarScroll;
    /** Ticks left of a "peek": the panel is hidden so a preview or test cast can be watched in the world. */
    private int peekTicks;

    private int left;
    private int top;
    private int frameW;
    private int frameH;
    private int sidebarW;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;

    /** Sections visited before this one, newest last; Back walks it before it closes the Control Center. */
    private final java.util.ArrayDeque<AdminCategory> history = new java.util.ArrayDeque<>();
    private static final int MAX_HISTORY = 32;
    /** Global search, laid over the content area while open. */
    private @Nullable AdminSearchOverlay search;
    /** A setting search led to: revealed on its page once the page has built its rows. */
    private @Nullable Identifier pendingReveal;
    private int revealTicks;
    /** Drafts that had to be dropped (their setting became read-only), and when — announced in the footer. */
    private int droppedDrafts;
    private long droppedAt;
    /** Sections that have a page, recomputed on server state rather than per frame. */
    private java.util.Set<AdminCategory> live = java.util.EnumSet.noneOf(AdminCategory.class);

    private @Nullable AdminButton back;
    private @Nullable AdminButton refreshButton;
    private @Nullable AdminButton findButton;
    private net.minecraft.client.gui.components.@Nullable EditBox searchField;
    private @Nullable AdminButton apply;
    private @Nullable AdminButton revert;
    private @Nullable AdminButton resetSection;

    public AdminControlCenterScreen() {
        super(Component.translatable("admin.wizards_and_beasts.title"));
        this.panel = AdminPanels.create(section);
    }

    // ── AdminPanelHost ──

    @Override
    public <T extends AbstractWidget> T addPanelWidget(T widget) {
        return addRenderableWidget(widget);
    }

    @Override
    public Font font() {
        return font;
    }

    @Override
    public AdminEditSession edits() {
        return edits;
    }

    @Override
    public void navigate(AdminCategory target) {
        if (target != section) {
            history.addLast(section);
            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }
        go(target);
    }

    /** Back: the previous section if there is one, else close (asking first when edits are unsaved). */
    private void back() {
        if (search != null) {
            closeSearch();
            return;
        }
        AdminCategory previous = history.pollLast();
        if (previous == null) {
            onClose();
        } else {
            go(previous);
        }
    }

    private void go(AdminCategory target) {
        search = null; // any navigation leaves search
        section = target;
        lastSection = target;
        panel.dispose();
        panel = AdminPanels.create(target);
        // Ask for fresh state on every visit: another administrator may have been busy.
        if (target == AdminCategory.DASHBOARD) {
            AdminClientRequests.refresh();
        } else {
            AdminClientRequests.section(target);
        }
        rebuildWidgets();
    }

    /** Opens Magic → Spells on {@code spellId}'s page. */
    public void showSpell(String spellId) {
        SpellBrowserPanel.selected = spellId;
        MagicPanel.tab = MagicPanel.Tab.SPELLS;
        AdminClientRequests.spellDetail(spellId);
        navigate(AdminCategory.MAGIC);
    }

    /** Opens Heritages on {@code heritage}'s page, or on the Rules / Players tab. */
    public void showHeritage(at.koopro.wizardsandbeasts.heritage.Heritage heritage, String tab) {
        HeritageBrowserPanel.selected = heritage;
        HeritagePanel.tab = switch (tab) {
            case "rules" -> HeritagePanel.Tab.RULES;
            case "players" -> HeritagePanel.Tab.PLAYERS;
            default -> HeritagePanel.Tab.HERITAGES;
        };
        navigate(AdminCategory.HERITAGES);
    }

    /** Opens Brewing on {@code brewId}'s page, or on the Rules tab. */
    public void showBrew(String brewId, String tab) {
        BrewBrowserPanel.selected = brewId;
        BrewBrowserPanel.reveal = true;
        BrewingPanel.tab = "rules".equals(tab) ? BrewingPanel.Tab.RULES : BrewingPanel.Tab.BREWS;
        at.koopro.wizardsandbeasts.client.admin.AdminClientRequests.brewDetail(brewId);
        navigate(AdminCategory.BREWING);
    }

    /** Opens Wands on a tab ({@code woods|cores|generator|rules}), selecting {@code partId} on Woods or Cores. */
    public void showWand(String tab, String partId) {
        WandsPanel.tab = switch (tab) {
            case "cores" -> WandsPanel.Tab.CORES;
            case "generator" -> WandsPanel.Tab.GENERATOR;
            case "rules" -> WandsPanel.Tab.RULES;
            default -> WandsPanel.Tab.WOODS;
        };
        if (!partId.isEmpty()) {
            if (WandsPanel.tab == WandsPanel.Tab.CORES) {
                WandPartsPanel.selectedCore = partId;
            } else {
                WandPartsPanel.selectedWood = partId;
            }
        }
        navigate(AdminCategory.WANDS);
    }

    /** Opens the generator on one make; the server previews it once the selection settles. */
    public void showWandMake(String wood, String core, float length, String flexibility, String preset) {
        WandGeneratorPanel.wood = wood;
        WandGeneratorPanel.core = core;
        WandGeneratorPanel.length = length;
        WandGeneratorPanel.flexibility = flexibility;
        WandGeneratorPanel.preset = preset;
        WandGeneratorPanel.dirty = true;
        showWand("generator", "");
    }

    /** Opens Travel on {@code broomId}'s page, or on the Rules tab. */
    public void showBroom(String broomId, String tab) {
        BroomBrowserPanel.selected = broomId;
        TravelPanel.tab = "rules".equals(tab) ? TravelPanel.Tab.RULES : TravelPanel.Tab.BROOMS;
        navigate(AdminCategory.TRAVEL);
    }

    /**
     * Opens a tabbed section on one tab: Travel ({@code brooms|floo|apparition|rules}), Ministry ({@code rules|law}),
     * Economy ({@code gringotts|rules}), World ({@code overview|rules}),
     * Modules ({@code modules|profiles}), Profiles ({@code profiles|snapshots|history|import}), or any section with an
     * enum of tabs (Magic, Brewing, Creatures, Heritages, Wands, Visuals) by the tab's name.
     */
    public void showTab(AdminCategory target, String tabName) {
        switch (target) {
            case TRAVEL -> {
                for (TravelPanel.Tab candidate : TravelPanel.Tab.values()) {
                    if (candidate.name().equalsIgnoreCase(tabName)) {
                        TravelPanel.tab = candidate;
                    }
                }
            }
            case MINISTRY -> MinistryPanel.tab = "law".equals(tabName) ? 1 : 0;
            case ECONOMY -> EconomyPanel.tab = "rules".equals(tabName) ? 1 : 0;
            case WORLD -> WorldPanel.tab = "rules".equals(tabName) ? 1 : 0;
            case MODULES -> ModulesPanel.tab = "profiles".equals(tabName) ? 1 : 0;
            case PROFILES -> ProfilesPanel.tab = ProfilesPanel.tabIndex(tabName);
            case PLAYERS -> PlayersPanel.tab = PlayersPanel.tabIndex(tabName);
            case PERFORMANCE -> PerformancePanel.tab = PerformancePanel.tabIndex(tabName);
            case DEBUG -> DebugPanel.tab = DebugPanel.tabIndex(tabName);
            case MAGIC -> MagicPanel.tab = byName(MagicPanel.Tab.values(), tabName, MagicPanel.tab);
            case BREWING -> BrewingPanel.tab = byName(BrewingPanel.Tab.values(), tabName, BrewingPanel.tab);
            case CREATURES -> CreaturesPanel.tab = byName(CreaturesPanel.Tab.values(), tabName, CreaturesPanel.tab);
            case HERITAGES -> HeritagePanel.tab = byName(HeritagePanel.Tab.values(), tabName, HeritagePanel.tab);
            case WANDS -> WandsPanel.tab = byName(WandsPanel.Tab.values(), tabName, WandsPanel.tab);
            case VISUALS -> VisualsPanel.tab = byName(VisualsPanel.Tab.values(), tabName, VisualsPanel.tab);
            default -> { }
        }
        if (section == target) {
            panel.dispose();
            panel = AdminPanels.create(target);
            rebuildWidgets();
        } else {
            navigate(target);
        }
    }

    /** The tab named {@code name} (any case), or {@code fallback} for a name the section does not have. */
    private static <E extends Enum<E>> E byName(E[] tabs, String name, E fallback) {
        for (E tab : tabs) {
            if (tab.name().equalsIgnoreCase(name)) {
                return tab;
            }
        }
        return fallback;
    }

    /**
     * Opens Profiles on a tab ({@code profiles|snapshots|history|import}) with one entry selected: a profile id,
     * {@code @current}, a history sequence or {@code @newest}, a file name. Null keeps the tab's selection.
     */
    public void showProfiles(String tabName, @Nullable String entry) {
        ProfilesPanel.select(tabName, entry);
        showTab(AdminCategory.PROFILES, tabName);
    }

    /**
     * Opens Players on a tab ({@code overview|heritage|skills|spells|effects|ministry|economy|debug|log}) with one
     * player (UUID text) selected; null keeps the selection.
     */
    public void showPlayers(String tabName, @Nullable String player) {
        PlayersPanel.select(player);
        showTab(AdminCategory.PLAYERS, tabName);
    }

    /** Opens Modules on one module's page ({@code moduleId} is the module's id path, e.g. {@code apparition}). */
    public void showModule(String moduleId) {
        ModuleBrowserPanel.selected = moduleId;
        showTab(AdminCategory.MODULES, "modules");
    }

    /** Opens Visuals on a tab ({@code beams|particles|impacts|hud|screen|entities|debug}), on one beam spell's page. */
    public void showVisuals(String tab, String beamSpell) {
        VisualsPanel.Tab target = VisualsPanel.Tab.BEAMS;
        for (VisualsPanel.Tab candidate : VisualsPanel.Tab.values()) {
            if (candidate.name().equalsIgnoreCase(tab)) {
                target = candidate;
            }
        }
        VisualsPanel.tab = target;
        if (!beamSpell.isEmpty()) {
            BeamBrowserPanel.selected = beamSpell;
        }
        if (section == AdminCategory.VISUALS) {
            // Already here: the old tab still has to let go of its previews.
            panel.dispose();
            panel = AdminPanels.create(AdminCategory.VISUALS);
            rebuildWidgets();
        } else {
            navigate(AdminCategory.VISUALS);
        }
    }

    /** Opens Creatures on {@code creatureId}'s page, or on the Rules tab. */
    public void showCreature(String creatureId, String tab) {
        CreatureBrowserPanel.selected = creatureId;
        CreatureBrowserPanel.reveal = true;
        CreaturesPanel.tab = "rules".equals(tab) ? CreaturesPanel.Tab.RULES : CreaturesPanel.Tab.CREATURES;
        at.koopro.wizardsandbeasts.client.admin.AdminClientRequests.creatureDetail(creatureId);
        navigate(AdminCategory.CREATURES);
    }

    /** Opens Heritages → Players on one online player's inspection. */
    public void showPlayer(java.util.UUID player) {
        HeritagePlayersPanel.select(player);
        HeritagePanel.tab = HeritagePanel.Tab.PLAYERS;
        navigate(AdminCategory.HERITAGES);
    }

    // ── search ──

    private void openSearch() {
        if (dialog != null) {
            return;
        }
        search = new AdminSearchOverlay();
        rebuildWidgets();
    }

    /** Opens search with {@code query} typed in — what Ctrl+F and typing do, for tools that drive the screen. */
    public void search(String query) {
        AdminSearchOverlay.setText(query);
        openSearch();
    }

    private void closeSearch() {
        search = null;
        rebuildWidgets();
    }

    /** Opens what a search result names, and closes search. */
    private void openTarget(AdminSearchIndex.Target target) {
        search = null;
        switch (target) {
            case AdminSearchIndex.Target.Section t -> {
                if (t.section() == section) {
                    rebuildWidgets();
                } else {
                    navigate(t.section());
                }
            }
            case AdminSearchIndex.Target.Setting t -> openSetting(t.id(), t.category());
            case AdminSearchIndex.Target.Spell t -> showSpell(t.id());
            case AdminSearchIndex.Target.Creature t -> showCreature(t.id(), "");
            case AdminSearchIndex.Target.Brew t -> showBrew(t.id(), "");
            case AdminSearchIndex.Target.Heritage t -> {
                at.koopro.wizardsandbeasts.heritage.Heritage heritage = heritageById(t.id());
                if (heritage != null) {
                    showHeritage(heritage, "");
                } else {
                    navigate(AdminCategory.HERITAGES);
                }
            }
            case AdminSearchIndex.Target.WandPart t -> showWand(t.core() ? "cores" : "woods", t.id());
            case AdminSearchIndex.Target.Broom t -> showBroom(t.id(), "");
            case AdminSearchIndex.Target.Beam t -> showVisuals("beams", t.spell());
            case AdminSearchIndex.Target.Module t -> showModule(t.id());
            case AdminSearchIndex.Target.Profile t -> showProfiles("profiles", t.id());
            case AdminSearchIndex.Target.Player t -> showPlayers("overview", t.uuid());
        }
    }

    private static at.koopro.wizardsandbeasts.heritage.@Nullable Heritage heritageById(String id) {
        for (at.koopro.wizardsandbeasts.heritage.Heritage heritage : at.koopro.wizardsandbeasts.heritage.Heritage.values()) {
            if (heritage.getId().equals(id)) {
                return heritage;
            }
        }
        return null;
    }

    /** Opens the page a setting is edited on — its entity's page, or its section's settings tab — and marks its row. */
    private void openSetting(Identifier id, AdminCategory category) {
        pendingReveal = id;
        revealTicks = 100;
        String[] parts = id.getPath().split("/");
        if (parts.length > 1 && "creature".equals(parts[0])) {
            showCreature(parts[1], "");
            return;
        }
        if (parts.length > 1 && "heritage".equals(parts[0])) {
            at.koopro.wizardsandbeasts.heritage.Heritage heritage = heritageById(parts[1]);
            if (heritage != null) {
                showHeritage(heritage, "");
                return;
            }
        }
        switch (category) {
            case MAGIC -> MagicPanel.tab = MagicPanel.Tab.RULES;
            case BREWING -> BrewingPanel.tab = BrewingPanel.Tab.RULES;
            case CREATURES -> CreaturesPanel.tab = CreaturesPanel.Tab.RULES;
            case HERITAGES -> HeritagePanel.tab = HeritagePanel.Tab.RULES;
            case WANDS -> WandsPanel.tab = WandsPanel.Tab.RULES;
            case TRAVEL -> TravelPanel.tab = TravelPanel.isFloo(id) ? TravelPanel.Tab.FLOO
                    : TravelPanel.isApparition(id) ? TravelPanel.Tab.APPARITION : TravelPanel.Tab.RULES;
            case VISUALS -> VisualsPanel.tab = VisualsPanel.tabFor(id);
            case MINISTRY -> MinistryPanel.tab = 0;
            case ECONOMY, WORLD -> {
                EconomyPanel.tab = category == AdminCategory.ECONOMY ? 1 : EconomyPanel.tab;
                WorldPanel.tab = category == AdminCategory.WORLD ? 1 : WorldPanel.tab;
            }
            case PERFORMANCE -> PerformancePanel.tab = PerformancePanel.tabIndex("settings");
            case DEBUG -> DebugPanel.tab = DebugPanel.tabIndex("settings");
            default -> { }
        }
        if (category == section) {
            panel.dispose();
            panel = AdminPanels.create(category);
            rebuildWidgets();
        } else {
            navigate(category);
        }
    }

    @Override
    public void requestRebuild() {
        // Deferred to the next tick: a panel asks from inside an event handler, while children are iterated.
        rebuildRequested = true;
    }

    /** A snapshot or result arrived. */
    public void onServerState() {
        int lost = edits.reconcile();
        if (lost > 0) {
            droppedDrafts += lost;
            droppedAt = net.minecraft.util.Util.getMillis();
        }
        live = AdminPanels.liveSet();
        if (search != null) {
            search.onServerState();
        }
        if (!AdminPanels.fits(panel)) {
            requestRebuild();
        } else {
            guard(() -> panel.onServerState());
        }
        updateFooter();
    }

    /**
     * Runs one call into the page. A page that throws — a setting vanished, a datapack reload changed what it
     * expected, a resource is missing — is replaced by an error page with Retry; the Control Center never crashes.
     */
    private void guard(Runnable call) {
        try {
            call.run();
        } catch (RuntimeException failure) {
            if (panel instanceof PanelErrorPanel) {
                throw failure; // the error page itself failing is a real bug
            }
            com.mojang.logging.LogUtils.getLogger().error("Control Center page {} failed", section.id(), failure);
            String cause = failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
            panel = new PanelErrorPanel(section, cause, () -> {
                AdminClientRequests.section(section);
                panel = AdminPanels.create(section);
                requestRebuild(); // pressed from inside the click handler: rebuild on the next tick
            });
            requestRebuild();
        }
    }

    // ── layout ──

    @Override
    protected void init() {
        frameW = Math.min(width - 2 * AdminTheme.PAD, MAX_W);
        frameH = Math.min(height - 2 * AdminTheme.PAD, MAX_H);
        left = (width - frameW) / 2;
        top = (height - frameH) / 2;
        sidebarW = frameW >= 400 ? SIDEBAR_W : SIDEBAR_NARROW_W;
        contentX = left + sidebarW;
        contentY = top + TOP_H;
        contentW = frameW - sidebarW;
        contentH = frameH - TOP_H - BOTTOM_H;

        if (dialog != null) {
            dialog.layout(font, width, height);
            for (AdminButton button : dialog.buttons()) {
                addRenderableWidget(button);
            }
            return;
        }

        live = AdminPanels.liveSet();
        if (!(panel instanceof PanelErrorPanel) && (panel.section() != section || !AdminPanels.fits(panel))) {
            panel.dispose();
            panel = AdminPanels.create(section);
        }

        searchField = null;
        AdminButton refresh = addRenderableWidget(new AdminButton(left + frameW - 4 - 16, top + 3, 16, 16,
                Component.literal("⟳"), AdminButton.Tone.FRAME, AdminClientRequests::refresh));
        refresh.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.button.refresh.tooltip")));
        AdminButton find = addRenderableWidget(new AdminButton(left + frameW - 4 - 16 - 4 - 16, top + 3, 16, 16,
                Component.literal("⌕"), search != null ? AdminButton.Tone.PRIMARY : AdminButton.Tone.FRAME,
                () -> {
                    if (search == null) {
                        openSearch();
                    } else {
                        closeSearch();
                    }
                }));
        find.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.button.search.tooltip")));
        refreshButton = refresh;
        findButton = find;

        int footerY = top + frameH - BOTTOM_H + (BOTTOM_H - FOOTER_BUTTON_H) / 2;
        boolean canGoBack = search != null || !history.isEmpty();
        back = addRenderableWidget(new AdminButton(left + 6, footerY, 56, FOOTER_BUTTON_H,
                Component.translatable(canGoBack ? "admin.wizards_and_beasts.button.back" : "admin.wizards_and_beasts.button.close"),
                AdminButton.Tone.FRAME, this::back));
        back.setTooltip(Tooltip.create(Component.translatable(canGoBack
                ? "admin.wizards_and_beasts.button.back.tooltip" : "admin.wizards_and_beasts.button.close.tooltip")));
        int right = left + frameW - 6;
        apply = addRenderableWidget(new AdminButton(right - 64, footerY, 64, FOOTER_BUTTON_H,
                Component.translatable("admin.wizards_and_beasts.button.apply"), AdminButton.Tone.PRIMARY, this::apply));
        apply.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.button.apply.tooltip")));
        revert = addRenderableWidget(new AdminButton(right - 64 - 4 - 60, footerY, 60, FOOTER_BUTTON_H,
                Component.translatable("admin.wizards_and_beasts.button.revert"), AdminButton.Tone.FRAME, this::revert));
        revert.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.button.revert.tooltip")));
        resetSection = addRenderableWidget(new AdminButton(right - 64 - 4 - 60 - 4 - 84, footerY, 84, FOOTER_BUTTON_H,
                Component.translatable("admin.wizards_and_beasts.button.reset_section"), AdminButton.Tone.FRAME,
                this::confirmResetSection));
        resetSection.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.button.reset_section.tooltip")));

        if (search != null) {
            searchField = addRenderableWidget(search.init(font, contentX + AdminTheme.PAD, contentY + AdminTheme.PAD,
                    contentW - 2 * AdminTheme.PAD, contentH - 2 * AdminTheme.PAD));
        } else {
            guard(() -> panel.init(this, contentX + AdminTheme.PAD, contentY + AdminTheme.PAD,
                    contentW - 2 * AdminTheme.PAD, contentH - 2 * AdminTheme.PAD));
        }
        updateFooter();
    }

    /**
     * Where focus starts after every rebuild: the dialog's Cancel, the search box, or — when the administrator is
     * navigating by keyboard — the page's first control. Vanilla would pick the first widget added, the title bar's
     * ⟳, and show its tooltip after every keyboard navigation.
     */
    @Override
    protected void setInitialFocus() {
        if (dialog != null) {
            setInitialFocus(dialog.cancelButton());
            return;
        }
        if (search != null && searchField != null) {
            setInitialFocus(searchField);
            return;
        }
        if (minecraft == null || !minecraft.getLastInputType().isKeyboard()) {
            return;
        }
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (child != refreshButton && child != findButton && child != back && child != apply && child != revert
                    && child != resetSection && child instanceof AbstractWidget widget && widget.visible && widget.active) {
                setInitialFocus(child);
                return;
            }
        }
    }

    private void updateFooter() {
        if (apply == null || revert == null || resetSection == null) {
            return;
        }
        boolean hasDrafts = edits.size() > 0;
        apply.active = hasDrafts;
        revert.active = hasDrafts;
        resetSection.visible = search == null && panel.hasSectionReset();
        resetSection.active = !panel.resettable().isEmpty();
    }

    // ── actions ──

    /**
     * Sends every draft unconfirmed. The server applies the harmless ones at once and answers the dangerous ones
     * with CONFIRMATION_REQUIRED and their warning; {@link #askPendingConfirmations} then shows those warnings and
     * resends with confirmation. The client never decides what counts as dangerous.
     */
    private void apply() {
        send(edits.snapshot());
    }

    private void send(Map<Identifier, String> drafts) {
        for (Map.Entry<Identifier, String> draft : drafts.entrySet()) {
            AdminSettingDescriptor setting = ClientAdminState.get(draft.getKey());
            edits.discard(draft.getKey());
            if (setting == null || setting.editable()) {
                AdminClientRequests.change(draft.getKey(), draft.getValue(), false);
            } else {
                droppedDrafts++;
                droppedAt = net.minecraft.util.Util.getMillis();
            }
        }
        onServerState();
    }

    private void revert() {
        edits.discardAll();
        onServerState();
    }

    private void confirmResetSection() {
        List<AdminSettingDescriptor> targets = panel.resettable();
        if (targets.isEmpty()) {
            return;
        }
        List<Component> body = new ArrayList<>();
        body.add(Component.translatable("admin.wizards_and_beasts.confirm.reset_section.body",
                targets.size(), Component.translatable(section.nameKey())));
        openDialog(new AdminConfirmDialog(Component.translatable("admin.wizards_and_beasts.confirm.reset_section.title"),
                body, Component.translatable("admin.wizards_and_beasts.button.reset_section"), true,
                () -> {
                    closeDialog();
                    for (AdminSettingDescriptor setting : targets) {
                        edits.discard(setting.id());
                        AdminClientRequests.reset(setting.id(), true);
                    }
                    onServerState();
                },
                this::closeDialog));
    }

    /** Shows the warnings of changes the server held back, and resends them confirmed if the admin agrees. */
    private void askPendingConfirmations() {
        List<ClientAdminState.ConfirmRequest> requests = ClientAdminState.takeConfirmRequests();
        if (requests.isEmpty()) {
            return;
        }
        List<Component> body = new ArrayList<>();
        body.add(Component.translatable("admin.wizards_and_beasts.confirm.apply.intro", requests.size()));
        for (ClientAdminState.ConfirmRequest request : requests) {
            AdminSettingDescriptor setting = ClientAdminState.get(request.settingId());
            MutableComponent line = settingLabel(request.settingId(), setting);
            String from = setting == null ? "?" : setting.value();
            String to = request.value() != null ? request.value() : setting == null ? "?" : setting.defaultValue();
            line.append(": " + from + " → " + to).append("\n").append(Component.translatable(request.warningKey()));
            body.add(line);
        }
        openDialog(new AdminConfirmDialog(Component.translatable("admin.wizards_and_beasts.confirm.apply.title"),
                body, Component.translatable("admin.wizards_and_beasts.button.apply"), true,
                () -> {
                    closeDialog();
                    for (ClientAdminState.ConfirmRequest request : requests) {
                        if (request.value() != null) {
                            AdminClientRequests.change(request.settingId(), request.value(), true);
                        } else {
                            AdminClientRequests.reset(request.settingId(), true);
                        }
                    }
                    onServerState();
                },
                this::closeDialog));
    }

    /** A setting's name as the dialog shows it; a spell's value is prefixed with the spell's own name. */
    private static MutableComponent settingLabel(Identifier id, @Nullable AdminSettingDescriptor setting) {
        MutableComponent name = setting == null ? Component.literal(id.getPath()) : Component.translatable(setting.nameKey());
        SpellSettingIds.Parsed spell = SpellSettingIds.parse(id);
        if (spell != null) {
            Spell resolved = SpellSettingIds.resolveSpell(spell.spellId());
            String spellName = resolved == null ? spell.spellId() : GuiText.resolve(resolved.getDisplayName());
            return Component.literal(spellName + " — ").append(name);
        }
        return name;
    }

    @Override
    public void openDialog(AdminConfirmDialog next) {
        dialog = next;
        rebuildWidgets();
    }

    @Override
    public void closeDialog() {
        dialog = null;
        rebuildWidgets();
    }

    @Override
    public void peek(int ticks) {
        peekTicks = Math.max(peekTicks, ticks);
    }

    @Override
    public void onClose() {
        if (peekTicks > 0) {
            peekTicks = 0;
            return;
        }
        if (dialog != null) {
            closeDialog();
            return;
        }
        if (search != null) {
            closeSearch();
            return;
        }
        if (edits.size() > 0) {
            openDialog(new AdminConfirmDialog(Component.translatable("admin.wizards_and_beasts.confirm.discard.title"),
                    List.of(Component.translatable("admin.wizards_and_beasts.confirm.discard.body", edits.size())),
                    Component.translatable("admin.wizards_and_beasts.button.discard"), false,
                    () -> {
                        edits.discardAll();
                        dialog = null;
                        super.onClose();
                    },
                    this::closeDialog));
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Every way the Control Center leaves the screen — Back, Esc, a disconnect, another screen opening over it — ends
     * here, so this is where a panel's previews are stopped.
     */
    @Override
    public void removed() {
        panel.dispose();
        // Debug tools switched on from the panel never outlive it: hitboxes go back, server leases are released.
        at.koopro.wizardsandbeasts.client.admin.ClientAdminOpsState.onControlCenterClosed();
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        at.koopro.wizardsandbeasts.client.admin.ClientAdminOpsState.tickOpen();
        if (ClientAdminState.expireStale()) {
            onServerState();
        }
        if (rebuildRequested) {
            rebuildRequested = false;
            rebuildWidgets();
        }
        if (peekTicks > 0) {
            peekTicks--;
        }
        if (dialog == null) {
            askPendingConfirmations();
        }
        if (search == null) {
            guard(panel::tick);
            if (pendingReveal != null && !rebuildRequested) {
                // The page may still be waiting for its reply (an entity page): try until its rows exist.
                if (panel.reveal(pendingReveal) || --revealTicks <= 0) {
                    pendingReveal = null;
                }
            }
        }
        updateFooter();
    }

    // ── input ──

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (peekTicks > 0) {
            peekTicks = 0;
            return true;
        }
        if (dialog == null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            AdminCategory hit = sidebarEntryAt(event.x(), event.y());
            if (hit != null) {
                if (hit != section || search != null) {
                    search = null;
                    navigate(hit);
                }
                return true;
            }
            if (super.mouseClicked(event, doubleClick)) {
                return true;
            }
            if (search != null) {
                AdminSearchIndex.Target target = search.mouseClicked(event.x(), event.y());
                if (target != null) {
                    openTarget(target);
                    return true;
                }
                return false;
            }
            return panel.mouseClicked(event.x(), event.y(), event.button());
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dialog != null) {
            return true;
        }
        if (inSidebar(mouseX, mouseY)) {
            sidebarScroll = Mth.clamp(sidebarScroll - scrollY * ENTRY_H, 0, maxSidebarScroll());
            return true;
        }
        if (search != null) {
            return search.mouseScrolled(mouseX, mouseY, scrollY);
        }
        return panel.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (peekTicks > 0) {
            peekTicks = 0;
            return true;
        }
        if (dialog == null) {
            Boolean handled = shortcut(event);
            if (handled != null) {
                return handled;
            }
        }
        return super.keyPressed(event);
    }

    /**
     * The Control Center's keys, all also reachable by mouse: Ctrl+F or / search · ↑↓ Enter in results · Ctrl+Tab and
     * Ctrl+Shift+Tab tabs · PageUp/PageDown sections · Ctrl+S apply · Alt+← back · Esc closes the topmost thing.
     *
     * @return null when the key is not a shortcut here (the focused widget gets it)
     */
    private @Nullable Boolean shortcut(KeyEvent event) {
        int key = event.key();
        boolean ctrl = event.hasControlDown();
        boolean typing = getFocused() instanceof net.minecraft.client.gui.components.EditBox;
        if (search != null) {
            if (AdminSearchOverlay.handles(key)) {
                AdminSearchIndex.Target target = search.keyPressed(key);
                if (target != null) {
                    openTarget(target);
                }
                return true;
            }
            return null;
        }
        if ((ctrl && key == GLFW.GLFW_KEY_F) || (!typing && key == GLFW.GLFW_KEY_SLASH)) {
            openSearch();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_TAB && panel.cycleTab(event.hasShiftDown() ? -1 : 1)) {
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_S) {
            if (edits.size() > 0) {
                apply();
            }
            return true;
        }
        if (event.hasAltDown() && key == GLFW.GLFW_KEY_LEFT) {
            back();
            return true;
        }
        if (!typing && (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP)) {
            List<AdminCategory> order = List.of(AdminCategory.values());
            int step = key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1;
            navigate(order.get(Math.floorMod(section.ordinal() + step, order.size())));
            return true;
        }
        return null;
    }

    // ── sidebar geometry ──

    private int sidebarTop() {
        return contentY + 4;
    }

    private int sidebarBottom() {
        return contentY + contentH - 4;
    }

    private double maxSidebarScroll() {
        return Math.max(0, AdminCategory.values().length * ENTRY_H - (sidebarBottom() - sidebarTop()));
    }

    private boolean inSidebar(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + sidebarW && mouseY >= sidebarTop() && mouseY < sidebarBottom();
    }

    private @Nullable AdminCategory sidebarEntryAt(double mouseX, double mouseY) {
        if (!inSidebar(mouseX, mouseY)) {
            return null;
        }
        int index = (int) ((mouseY - sidebarTop() + sidebarScroll) / ENTRY_H);
        AdminCategory[] all = AdminCategory.values();
        return index >= 0 && index < all.length ? all[index] : null;
    }

    // ── drawing ──

    @Override
    public void render(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (peekTicks > 0) {
            // The world is the point for these few seconds; only say how to come back.
            Component note = Component.translatable("admin.wizards_and_beasts.peek");
            int noteW = font.width(note) + 12;
            g.fill(width / 2 - noteW / 2, 6, width / 2 + noteW / 2, 20, AdminTheme.SCRIM);
            g.drawString(font, note, width / 2 - font.width(note) / 2, 9, AdminTheme.GOLD_LIGHT, false);
            return;
        }
        drawFrame(g);
        drawTopBar(g);
        drawSidebar(g, mouseX, mouseY);
        AdminSearchOverlay overlay = search;
        if (overlay != null) {
            overlay.render(g, font, mouseX, mouseY);
        } else {
            guard(() -> panel.render(g, mouseX, mouseY, partialTick));
        }
        drawFooterStatus(g);

        if (dialog == null) {
            super.render(g, mouseX, mouseY, partialTick);
            if (overlay == null) {
                guard(() -> panel.renderOverlay(g, mouseX, mouseY));
            }
            if (sidebarW == SIDEBAR_NARROW_W) {
                AdminCategory hovered = sidebarEntryAt(mouseX, mouseY);
                if (hovered != null) {
                    g.setTooltipForNextFrame(font, Component.translatable(hovered.nameKey()), mouseX, mouseY);
                }
            } else {
                AdminCategory hovered = sidebarEntryAt(mouseX, mouseY);
                if (hovered != null && !mayChange(hovered)) {
                    g.setTooltipForNextFrame(font, Component.translatable("admin.wizards_and_beasts.sidebar.read_only",
                            hovered.defaultCapability().node()), mouseX, mouseY);
                }
            }
        } else {
            g.nextStratum();
            dialog.render(g, font, width, height);
            super.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (peekTicks > 0) {
            return; // no blur, no dim: the preview must be visible
        }
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    private void drawFrame(GuiGraphics g) {
        AdminTheme.raised(g, left - 2, top - 2, frameW + 4, frameH + 4, AdminTheme.FRAME, AdminTheme.GOLD_DARK,
                AdminTheme.FRAME_HOVER);
        g.fill(contentX, contentY, contentX + contentW, contentY + contentH, AdminTheme.PAPER);
        g.fill(contentX, contentY, contentX + 1, contentY + contentH, AdminTheme.GOLD_DARK);
    }

    private void drawTopBar(GuiGraphics g) {
        g.fill(left, top + TOP_H - 1, left + frameW, top + TOP_H, AdminTheme.GOLD_DARK);
        int statusRight = left + frameW - 4 - 16 - 4 - 16 - 6;
        String status = "";
        AdminSessionInfo info = ClientAdminState.info();
        if (info != null) {
            status = Component.translatable(info.dedicated()
                            ? "admin.wizards_and_beasts.topbar.dedicated" : "admin.wizards_and_beasts.topbar.integrated").getString()
                    + " · " + info.onlinePlayers() + "/" + info.maxPlayers()
                    + " · " + String.format(Locale.ROOT, "%.1f ms", info.averageTickMillis());
        }
        // The breadcrumb wins the room; server status takes what is left.
        String crumb = breadcrumb();
        int titleX = left + 8;
        int statusW = Math.min(font.width(status), Math.max(0, statusRight - titleX - font.width(crumb) - 12));
        String crumbText = AdminText.clip(font, crumb, statusRight - titleX - statusW - (statusW > 0 ? 12 : 0));
        int sep = crumbText.indexOf(" › ");
        if (sep < 0) {
            g.drawString(font, crumbText, titleX, top + 7, AdminTheme.GOLD_LIGHT, false);
        } else {
            String head = crumbText.substring(0, sep);
            g.drawString(font, head, titleX, top + 7, AdminTheme.FRAME_TEXT_DIM, false);
            g.drawString(font, crumbText.substring(sep), titleX + font.width(head), top + 7, AdminTheme.GOLD_LIGHT, false);
        }
        if (statusW > 0) {
            String clipped = AdminText.clip(font, status, statusW);
            g.drawString(font, clipped, statusRight - font.width(clipped), top + 7, AdminTheme.FRAME_TEXT_DIM, false);
        }
    }

    /** "✦ Control Center › Magic › Spells › Stupefy", or "… › Search" while searching. */
    private String breadcrumb() {
        StringBuilder crumb = new StringBuilder("✦ ").append(getTitle().getString());
        if (search != null) {
            return crumb.append(" › ").append(Component.translatable("admin.wizards_and_beasts.search.title").getString()).toString();
        }
        crumb.append(" › ").append(Component.translatable(section.nameKey()).getString());
        Component inner = panel.crumb();
        if (inner != null) {
            crumb.append(" › ").append(inner.getString());
        }
        return crumb.toString();
    }

    /** Whether this administrator holds the authority a section's settings need by default. */
    private static boolean mayChange(AdminCategory category) {
        AdminSessionInfo info = ClientAdminState.info();
        return info == null || info.capabilities().contains(category.defaultCapability());
    }

    private void drawSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int areaTop = sidebarTop();
        int areaBottom = sidebarBottom();
        g.enableScissor(left, areaTop, left + sidebarW, areaBottom);
        AdminCategory[] all = AdminCategory.values();
        AdminCategory hovered = dialog == null ? sidebarEntryAt(mouseX, mouseY) : null;
        for (int i = 0; i < all.length; i++) {
            AdminCategory entry = all[i];
            int ey = areaTop + i * ENTRY_H - (int) sidebarScroll;
            if (ey + ENTRY_H < areaTop || ey > areaBottom) {
                continue;
            }
            boolean selected = entry == section && search == null;
            if (selected) {
                g.fill(left, ey, left + sidebarW, ey + ENTRY_H, AdminTheme.FRAME_RAISED);
                g.fill(left, ey, left + 2, ey + ENTRY_H, AdminTheme.GOLD);
            } else if (entry == hovered) {
                g.fill(left, ey, left + sidebarW, ey + ENTRY_H, AdminTheme.FRAME_HOVER);
            }
            boolean live = this.live.contains(entry);
            boolean readOnly = !mayChange(entry);
            int ink = selected ? AdminTheme.GOLD_LIGHT : live ? AdminTheme.FRAME_TEXT : AdminTheme.FRAME_TEXT_DIM;
            int textY = ey + (ENTRY_H - 8) / 2;
            if (sidebarW == SIDEBAR_NARROW_W) {
                g.drawCenteredString(font, glyph(entry), left + sidebarW / 2, textY, ink);
                continue;
            }
            g.drawString(font, glyph(entry), left + 7, textY, selected ? AdminTheme.GOLD : ink, false);
            // Not live: coming later. Live but not this administrator's to change: ⊘, with the reason as tooltip.
            String soon = !live ? Component.translatable("admin.wizards_and_beasts.sidebar.soon").getString() : readOnly ? "⊘" : "";
            int nameRoom = sidebarW - 20 - 4 - (soon.isEmpty() ? 0 : font.width(soon) + 4);
            g.drawString(font, AdminText.clip(font, Component.translatable(entry.nameKey()).getString(), nameRoom),
                    left + 20, textY, ink, false);
            if (!soon.isEmpty()) {
                g.drawString(font, soon, left + sidebarW - 4 - font.width(soon), textY, AdminTheme.FRAME_TEXT_DIM, false);
            }
        }
        g.disableScissor();
    }

    private void drawFooterStatus(GuiGraphics g) {
        int barTop = top + frameH - BOTTOM_H;
        g.fill(left, barTop, left + frameW, barTop + 1, AdminTheme.GOLD_DARK);
        Component status = footerStatus();
        int ink = footerInk;
        int from = left + 6 + 56 + 8;
        int to = (resetSection != null && resetSection.visible ? resetSection.getX() : revert != null ? revert.getX() : left + frameW) - 8;
        String text = AdminText.clip(font, status.getString(), to - from);
        g.drawString(font, text, from, barTop + (BOTTOM_H - 8) / 2, ink, false);
    }

    private int footerInk = AdminTheme.FRAME_TEXT_DIM;

    /**
     * The Control Center's one save state, most urgent first, each with a glyph so it reads without colour:
     * ⟳ saving · ● unsaved · ✖ refused · ✖ dropped (an edit lost to a permission change) · ✔ saved · up to date.
     */
    private Component footerStatus() {
        long now = net.minecraft.util.Util.getMillis();
        if (ClientAdminState.anyPending()) {
            footerInk = AdminTheme.GOLD_LIGHT;
            return Component.literal("⟳ ").append(Component.translatable("admin.wizards_and_beasts.status.saving"));
        }
        if (edits.size() > 0) {
            footerInk = AdminTheme.GOLD_LIGHT;
            return Component.literal("● ").append(Component.translatable(edits.size() == 1
                    ? "admin.wizards_and_beasts.footer.unsaved.one" : "admin.wizards_and_beasts.footer.unsaved.many", edits.size()));
        }
        int refused = ClientAdminState.recentRejections(STATUS_WINDOW_MS);
        if (refused > 0) {
            footerInk = AdminTheme.FRAME_BAD;
            return Component.literal("✖ ").append(Component.translatable("admin.wizards_and_beasts.footer.rejected", refused));
        }
        if (droppedDrafts > 0 && now - droppedAt < STATUS_WINDOW_MS) {
            footerInk = AdminTheme.FRAME_BAD;
            return Component.literal("✖ ").append(Component.translatable("admin.wizards_and_beasts.footer.dropped", droppedDrafts));
        }
        droppedDrafts = 0;
        if (now - ClientAdminState.lastAppliedAt() < SAVED_WINDOW_MS) {
            footerInk = AdminTheme.FRAME_GOOD;
            return Component.literal("✔ ").append(Component.translatable("admin.wizards_and_beasts.footer.saved"));
        }
        footerInk = AdminTheme.FRAME_TEXT_DIM;
        if (search == null && !mayChange(section)) {
            return Component.literal("⊘ ").append(Component.translatable("admin.wizards_and_beasts.sidebar.read_only",
                    section.defaultCapability().node()));
        }
        return Component.translatable("admin.wizards_and_beasts.footer.clean");
    }

    private static String glyph(AdminCategory category) {
        return switch (category) {
            case DASHBOARD -> "◈";
            case GAME_RULES -> "⚖";
            case MAGIC -> "✦";
            case DARK_ARTS -> "☠";
            case HERITAGES -> "☽";
            case CREATURES -> "❦";
            case BREWING -> "⚗";
            case WANDS -> "⚚";
            case TRAVEL -> "➶";
            case MINISTRY -> "♜";
            case ECONOMY -> "¤";
            case WORLD -> "◎";
            case MODULES -> "▦";
            case PROFILES -> "❐";
            case PLAYERS -> "☺";
            case VISUALS -> "◐";
            case PERFORMANCE -> "⚙";
            case DEBUG -> "✒";
            case ADVANCED -> "☰";
        };
    }
}
