package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.broom.BroomSettingProvider;
import at.koopro.wizardsandbeasts.broom.rules.BroomStat;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminBroomState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminBroomPayloads.BroomSummary;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Travel → Brooms: every broom definition on the left; on the right whether it may be deployed, a preview — the
 * broom's own item model and a bar per flight stat (authored mark, bar as it flies, draft marker while editing) — the
 * nine flight stats as sliders with the stat's own bounds and step, and the handling profile it keeps.
 *
 * <p>Brooms fly on the rider's client from the definitions the server syncs; every value here changes those synced
 * definitions, never a client's own copy. Wind and FOV are each player's own preference (Visuals), not broom data.
 */
@NullMarked
final class BroomBrowserPanel extends AdminBrowserPanel<BroomSummary> {

    private static final String KEY = "admin.wizards_and_beasts.broom.";
    private static final int BAR_H = 11;

    static @Nullable String selected;

    private int previewTop = -1;

    @Override
    public AdminCategory section() {
        return AdminCategory.TRAVEL;
    }

    @Override
    protected List<BroomSummary> entries() {
        return ClientAdminBroomState.brooms();
    }

    @Override
    protected String idOf(BroomSummary entry) {
        return entry.id();
    }

    @Override
    protected Component nameOf(BroomSummary entry) {
        return Component.translatable(entry.nameKey());
    }

    @Override
    protected boolean enabledOf(BroomSummary entry) {
        return entry.enabled();
    }

    @Override
    protected int swatchOf(BroomSummary entry) {
        return entry.tint();
    }

    @Override
    protected boolean hasSwatches() {
        return true;
    }

    @Override
    protected String badgesOf(BroomSummary entry) {
        return (entry.enabled() ? "" : "✕") + (entry.overridden() ? "•" : "");
    }

    @Override
    protected @Nullable String selected() {
        return selected;
    }

    @Override
    protected void setSelected(String id) {
        selected = id;
    }

    @Override
    protected boolean stale() {
        return ClientAdminBroomState.stale();
    }

    @Override
    protected void request() {
        AdminClientRequests.broomList();
    }

    @Override
    protected Component pickPrompt() {
        return Component.translatable(KEY + "pick");
    }

    @Override
    protected String subtitleOf(BroomSummary entry) {
        String tier = Component.translatable("broom_tier.wizards_and_beasts." + entry.tier()).getString();
        String speed = Component.translatable(KEY + "top_speed", format(entry.effective().get(BroomStat.MAX_SPEED.ordinal())))
                .getString();
        String state = entry.enabled() ? "" : " · " + Component.translatable(KEY + "withdrawn").getString();
        return tier + " · " + speed + state;
    }

    @Override
    protected int buildPage(AdminPanelHost host, Font font, BroomSummary entry, int doc, int rowW) {
        Identifier broom = Identifier.tryParse(entry.id());
        if (broom == null) {
            return doc;
        }
        doc = header(doc, Component.translatable(KEY + "broom"));
        doc = row(host, font, BroomSettingProvider.id(broom, BroomSettingProvider.ENABLED), doc, rowW);

        doc = header(doc + 4, Component.translatable(KEY + "preview"));
        previewTop = doc;
        doc += 70 + BroomStat.values().length * BAR_H + 4;
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "preview_note"), AdminTheme.INK_3);

        doc = header(doc + 4, Component.translatable(KEY + "flight"));
        for (BroomStat stat : BroomStat.values()) {
            doc = row(host, font, BroomSettingProvider.id(broom, stat.id()), doc, rowW);
        }
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "flight_note"), AdminTheme.INK_3);

        doc = header(doc + 4, Component.translatable(KEY + "handling"));
        doc = facts(font, doc, rowW, entry.facts());
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "client_prefs_note"), AdminTheme.INK_3);
        return doc;
    }

    /** The value a stat row shows right now: the draft while editing, else the server's. */
    private float shownValue(BroomSummary entry, BroomStat stat) {
        Identifier broom = Identifier.tryParse(entry.id());
        AdminSettingDescriptor setting = broom == null ? null : ClientAdminState.get(BroomSettingProvider.id(broom, stat.id()));
        if (setting == null || host == null) {
            return entry.effective().get(stat.ordinal());
        }
        try {
            return Float.parseFloat(host.edits().shown(setting));
        } catch (NumberFormatException e) {
            return entry.effective().get(stat.ordinal());
        }
    }

    @Override
    protected void renderPage(GuiGraphics g, Font font, BroomSummary entry, int top, int width, int mouseX, int mouseY) {
        if (previewTop < 0) {
            return;
        }
        int py = top + previewTop;
        AdminItemPreview.draw(g, AdminItemPreview.broom(entry.id()), detailX, py, 4.0f);
        int tx = detailX + 72;
        g.drawString(font, AdminText.clip(font, nameOf(entry).getString(), width - 72), tx, py + 4, AdminTheme.INK, false);
        g.drawString(font, AdminText.clip(font, subtitleOf(entry), width - 72), tx, py + 16, AdminTheme.INK_2, false);
        g.drawString(font, AdminText.clip(font, Component.translatable(KEY + "legend").getString(), width - 72), tx, py + 28,
                AdminTheme.INK_3, false);

        int labelW = 78;
        int barX = detailX + labelW;
        int barW = Math.max(40, width - labelW - 44);
        int by = py + 70;
        for (BroomStat stat : BroomStat.values()) {
            float authored = entry.authored().get(stat.ordinal());
            float flies = entry.effective().get(stat.ordinal());
            float draft = shownValue(entry, stat);
            g.drawString(font, AdminText.clip(font, Component.translatable(
                    "admin.wizards_and_beasts.broom_property." + stat.id()).getString(), labelW - 4), detailX, by + 1, AdminTheme.INK_2, false);
            g.fill(barX, by + 2, barX + barW, by + BAR_H - 2, AdminTheme.PAPER_SHADE);
            g.fill(barX, by + 2, barX + fraction(stat, flies, barW), by + BAR_H - 2, AdminTheme.GOLD);
            int mark = barX + fraction(stat, authored, barW);
            g.fill(mark, by, mark + 1, by + BAR_H, AdminTheme.INK);
            if (Math.abs(draft - flies) > 1e-4) {
                int d = barX + fraction(stat, draft, barW);
                g.fill(d - 1, by + 1, d + 1, by + BAR_H - 1, AdminTheme.WAX);
            }
            g.drawString(font, format(flies), barX + barW + 4, by + 1, AdminTheme.INK, false);
            by += BAR_H;
        }
    }

    private static int fraction(BroomStat stat, float value, int width) {
        float t = (value - stat.min()) / (stat.max() - stat.min());
        return Math.round(Math.max(0f, Math.min(1f, t)) * width);
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    protected boolean loaded() {
        return ClientAdminBroomState.received();
    }

    @Override
    protected Component emptyMessage() {
        return Component.translatable("admin.wizards_and_beasts.empty.brooms");
    }
}
