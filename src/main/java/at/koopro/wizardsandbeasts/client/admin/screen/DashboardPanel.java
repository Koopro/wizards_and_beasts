package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.config.SettingScope;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchIndex;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminConfirmDialog;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTooltip;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminWarning;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSessionInfo;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The landing page: a row of tiles with the server at a glance (each opens its section), then the latest
 * configuration changes — named, with old and new value, who and when, and Undo where the change can still be taken
 * back. Numbers come from the last snapshot and are worked out when it arrives, not every frame.
 */
@NullMarked
final class DashboardPanel implements AdminPanel {

    private static final String KEY = "admin.wizards_and_beasts.dashboard.";
    private static final int TILE_H = 28;
    private static final int TILE_GAP = 4;
    private static final int MIN_TILE_W = 74;
    private static final int RECENT_H = 22;
    private static final int UNDO_W = 40;

    /** One tile: what it counts, what it says, its ink, and the section it opens. */
    private record Tile(Component label, String value, int ink, AdminCategory target, @Nullable String tab,
                        AdminButton button) {}

    private @Nullable AdminPanelHost host;
    private int x;
    private int y;
    private int w;
    private int h;
    private final List<Tile> tiles = new ArrayList<>();
    private int recentTop;
    /** The snapshot the page was built from; a new one rebuilds it (tiles and Undo buttons follow the data). */
    private @Nullable AdminSessionInfo builtFrom;
    private long changedFromDefault;

    @Override
    public AdminCategory section() {
        return AdminCategory.DASHBOARD;
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        tiles.clear();
        AdminSessionInfo info = ClientAdminState.info();
        builtFrom = info;
        if (info == null) {
            return;
        }
        countSettings();
        Font font = host.font();
        int top = y + 32;
        if (info.restartPending()) {
            top += AdminWarning.measure(font, w, Component.translatable(KEY + "restart_pending")) + AdminTheme.GAP;
        }

        List<TileSpec> specs = tileSpecs(info);
        int cols = Math.max(1, Math.min(specs.size(), (w + TILE_GAP) / (MIN_TILE_W + TILE_GAP)));
        int tileW = (w - (cols - 1) * TILE_GAP) / cols;
        for (int i = 0; i < specs.size(); i++) {
            TileSpec spec = specs.get(i);
            int tx = x + (i % cols) * (tileW + TILE_GAP);
            int ty = top + (i / cols) * (TILE_H + TILE_GAP);
            AdminButton button = host.addPanelWidget(new AdminButton(tx, ty, tileW, TILE_H, Component.empty(),
                    AdminButton.Tone.NEUTRAL, () -> open(spec.target(), spec.tab())));
            button.setTooltip(Tooltip.create(Component.translatable(KEY + "open", Component.translatable(spec.target().nameKey()))));
            tiles.add(new Tile(spec.label(), spec.value(), spec.ink(), spec.target(), spec.tab(), button));
        }
        int rows = (specs.size() + cols - 1) / cols;
        recentTop = top + rows * (TILE_H + TILE_GAP) + AdminTheme.GAP;

        // Undo, one per revertible change that fits.
        boolean mayUndo = info.capabilities().contains(AdminCapability.CONFIG);
        int listTop = recentTop + font.lineHeight + 4;
        List<AdminSessionInfo.RecentChange> recent = info.recentChanges();
        for (int i = 0; i < recent.size(); i++) {
            int rowY = listTop + i * RECENT_H;
            if (rowY + RECENT_H > y + h) {
                break;
            }
            AdminSessionInfo.RecentChange change = recent.get(i);
            if (mayUndo && change.revertible()) {
                AdminButton undo = host.addPanelWidget(new AdminButton(x + w - UNDO_W, rowY + 3, UNDO_W, 14,
                        Component.translatable(KEY + "undo"), AdminButton.Tone.QUIET, () -> confirmUndo(change)));
                undo.setTooltip(Tooltip.create(Component.translatable(KEY + "undo.tooltip", value(change, change.oldValue()))));
            }
        }
    }

    private record TileSpec(Component label, String value, int ink, AdminCategory target, @Nullable String tab) {}

    private List<TileSpec> tileSpecs(AdminSessionInfo info) {
        AdminSessionInfo.Counts counts = info.counts();
        float tick = info.averageTickMillis();
        String tickGlyph = tick < 40f ? "✔ " : tick < 50f ? "⚠ " : "✖ ";
        int tickInk = tick < 40f ? AdminTheme.GOOD : tick < 50f ? AdminTheme.GOLD_DARK : AdminTheme.BAD;
        List<TileSpec> out = new ArrayList<>();
        out.add(new TileSpec(Component.translatable(KEY + "tile.server"), Component.translatable(info.dedicated()
                ? KEY + "tile.dedicated" : KEY + "tile.integrated").getString(), AdminTheme.INK, AdminCategory.PERFORMANCE, "live"));
        out.add(new TileSpec(Component.translatable(KEY + "tile.players"), info.onlinePlayers() + " / " + info.maxPlayers(),
                AdminTheme.INK, AdminCategory.PLAYERS, null));
        out.add(new TileSpec(Component.translatable(KEY + "tile.performance"),
                tickGlyph + String.format(Locale.ROOT, "%.1f ms", tick), tickInk, AdminCategory.PERFORMANCE, "live"));
        out.add(new TileSpec(Component.translatable(KEY + "tile.modules"), info.modulesEnabled() + " / " + info.modulesTotal(),
                AdminTheme.INK, AdminCategory.MODULES, "modules"));
        out.add(new TileSpec(Component.translatable(KEY + "tile.spells"), Integer.toString(counts.spells()), AdminTheme.INK,
                AdminCategory.MAGIC, "spells"));
        out.add(new TileSpec(Component.translatable(KEY + "tile.brews"), Integer.toString(counts.brews()), AdminTheme.INK,
                AdminCategory.BREWING, null));
        out.add(new TileSpec(Component.translatable(KEY + "tile.creatures"), Integer.toString(counts.creatures()), AdminTheme.INK,
                AdminCategory.CREATURES, null));
        out.add(new TileSpec(Component.translatable(KEY + "tile.heritages"), Integer.toString(counts.heritages()), AdminTheme.INK,
                AdminCategory.HERITAGES, null));
        out.add(new TileSpec(Component.translatable(KEY + "tile.debug"), counts.debugTools() == 0
                ? Component.translatable(KEY + "tile.debug.off").getString()
                : "⚠ " + Component.translatable(KEY + "tile.debug.on", counts.debugTools(), counts.debugHolders()).getString(),
                counts.debugTools() == 0 ? AdminTheme.INK_2 : AdminTheme.GOLD_DARK, AdminCategory.DEBUG, "tools"));
        out.add(new TileSpec(Component.translatable(KEY + "tile.configuration"),
                Component.translatable(KEY + "tile.changed", changedFromDefault).getString(),
                changedFromDefault == 0 ? AdminTheme.INK_2 : AdminTheme.GOLD_DARK, AdminCategory.PROFILES, "history"));
        return out;
    }

    private void countSettings() {
        long changed = 0;
        for (AdminSettingDescriptor setting : ClientAdminState.all()) {
            if (setting.scope() == SettingScope.SERVER && !setting.isDefault()) {
                changed++;
            }
        }
        changedFromDefault = changed;
    }

    private void open(AdminCategory target, @Nullable String tab) {
        if (host instanceof AdminControlCenterScreen screen && tab != null) {
            switch (target) {
                case MAGIC -> {
                    MagicPanel.tab = MagicPanel.Tab.SPELLS;
                    screen.navigate(target);
                }
                default -> screen.showTab(target, tab);
            }
        } else if (host != null) {
            host.navigate(target);
        }
    }

    private void confirmUndo(AdminSessionInfo.RecentChange change) {
        if (host == null) {
            return;
        }
        AdminPanelHost h = host;
        h.openDialog(new AdminConfirmDialog(Component.translatable(KEY + "undo.title"),
                List.of(Component.translatable(KEY + "undo.body", name(change), value(change, change.newValue()),
                        value(change, change.oldValue()))),
                Component.translatable(KEY + "undo"), AdminWarning.Severity.WARNING,
                () -> {
                    h.closeDialog();
                    AdminClientRequests.profileAction(AdminProfilePayloads.Op.REVERT_ONE, Long.toString(change.sequence()), "");
                    // Answered after the revert on the same connection, so the list shows the undo.
                    AdminClientRequests.refresh();
                },
                h::closeDialog));
    }

    @Override
    public void onServerState() {
        if (host != null && ClientAdminState.info() != builtFrom) {
            host.requestRebuild();
        }
    }

    // ── drawing ──

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        AdminSectionHeader.render(g, font, x, y, w, Component.translatable(AdminCategory.DASHBOARD.nameKey()),
                Component.translatable(AdminCategory.DASHBOARD.summaryKey()));
        AdminSessionInfo info = ClientAdminState.info();
        if (info == null) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.status.loading"), x, y + 32, AdminTheme.INK_3, false);
            return;
        }
        if (info.restartPending()) {
            AdminWarning.render(g, font, x, y + 32, w, Component.translatable(KEY + "restart_pending"));
        }

        AdminSectionHeader.renderSub(g, font, x, recentTop, w, Component.translatable(KEY + "recent"));
        int listTop = recentTop + font.lineHeight + 4;
        if (info.recentChanges().isEmpty()) {
            g.drawString(font, Component.translatable(KEY + "recent.none"), x, listTop + 2, AdminTheme.INK_3, false);
        }
        long now = System.currentTimeMillis();
        List<AdminSessionInfo.RecentChange> recent = info.recentChanges();
        for (int i = 0; i < recent.size(); i++) {
            int rowY = listTop + i * RECENT_H;
            if (rowY + RECENT_H > y + h) {
                break;
            }
            AdminSessionInfo.RecentChange change = recent.get(i);
            int textW = w - UNDO_W - 6;
            String head = (change.applied() ? "" : "✖ ") + name(change);
            g.drawString(font, AdminText.clip(font, head, textW), x + 2, rowY + 2,
                    change.applied() ? AdminTheme.INK : AdminTheme.BAD, false);
            String detail = value(change, change.oldValue()) + " → " + value(change, change.newValue())
                    + "  ·  " + change.actorName() + "  ·  " + ago(now - change.timestampMillis())
                    + (change.applied() ? "" : "  ·  " + Component.translatable(KEY + "refused").getString());
            g.drawString(font, AdminText.clip(font, detail, textW), x + 2, rowY + 11, AdminTheme.INK_3, false);
            g.fill(x, rowY + RECENT_H - 1, x + w, rowY + RECENT_H, AdminTheme.PAPER_SHADE);
        }
    }

    /** Tile text above the tile buttons (which only give the tiles their face, focus and click). */
    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        for (Tile tile : tiles) {
            AdminButton b = tile.button();
            int innerW = b.getWidth() - 8;
            g.drawString(font, AdminText.clip(font, tile.label().getString(), innerW), b.getX() + 4, b.getY() + 5,
                    AdminTheme.INK_3, false);
            g.drawString(font, AdminText.clip(font, tile.value(), innerW), b.getX() + 4, b.getY() + 16, tile.ink(), false);
        }
    }

    private static String name(AdminSessionInfo.RecentChange change) {
        if (!change.nameKey().isEmpty()) {
            return Component.translatable(change.nameKey()).getString();
        }
        Identifier id = Identifier.tryParse(change.settingId());
        String path = id == null ? change.settingId() : id.getPath();
        return Component.translatable(KEY + "removed", AdminSearchIndex.humanise(path)).getString();
    }

    /** A value as the setting's own row would show it (On/Off, an option's name), else as stored. */
    private static String value(AdminSessionInfo.RecentChange change, String raw) {
        Identifier id = Identifier.tryParse(change.settingId());
        AdminSettingDescriptor setting = id == null ? null : ClientAdminState.get(id);
        return setting == null ? raw : AdminTooltip.display(setting, raw).getString();
    }

    /** "just now", "4 min ago", "2 h ago", "3 d ago". Server and client clocks may differ; never in the future. */
    static String ago(long millis) {
        long minutes = Math.max(0, millis) / 60_000L;
        if (minutes < 1) {
            return Component.translatable(KEY + "ago.now").getString();
        }
        if (minutes < 60) {
            return Component.translatable(KEY + "ago.minutes", minutes).getString();
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return Component.translatable(KEY + "ago.hours", hours).getString();
        }
        return Component.translatable(KEY + "ago.days", hours / 24).getString();
    }
}
