package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualSettingProvider;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminVisualState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualDefaults;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualProperty;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Visuals → Impacts: every beam's impact intensity side by side — the one per-spell impact value the game has. It
 * scales the particles and camera kick of that beam's impact bursts on the server where they are sent, and nothing the
 * beam does. The same values appear on each beam's page under Spell Beams. Projectile and targeted-spell impacts are
 * sized by the spell's family and colour and are not adjustable (the burst carries no spell to look up).
 */
@NullMarked
final class ImpactsPanel implements AdminPanel {

    private static final String KEY = "admin.wizards_and_beasts.impacts.";

    private final PlacedRows rows = new PlacedRows();
    private final List<Label> labels = new ArrayList<>();
    private @Nullable AdminPanelHost host;
    private int x;
    private int y;
    private int w;
    private int h;
    private int noteTop;

    private record Label(String text, int y) {}

    @Override
    public AdminCategory section() {
        return AdminCategory.VISUALS;
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        rows.clear();
        labels.clear();
        if (ClientAdminVisualState.stale()) {
            AdminClientRequests.beamList();
        }
        Font font = host.font();
        int next = y + 32;
        for (String spell : BeamVisualDefaults.SPELLS) {
            if (next + 10 + AdminTheme.ROW_H > y + h - 30) {
                break;
            }
            int after = rows.add(host, font, BeamVisualSettingProvider.id(spell, BeamVisualProperty.IMPACT_INTENSITY),
                    x, next + 10, w);
            if (after != next + 10) {
                labels.add(new Label(BeamBrowserPanel.spellName(spell), next));
                next = after + 2;
            }
        }
        noteTop = next + 4;
    }

    @Override
    public void onServerState() {
        if (host == null) {
            return;
        }
        if (rows.isEmpty() && !ClientAdminVisualState.beams().isEmpty()) {
            host.requestRebuild();
        } else {
            rows.refresh(host);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        AdminSectionHeader.render(g, font, x, y, w, Component.translatable(KEY + "title"), Component.translatable(KEY + "summary"));
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.status.loading"), x, y + 34, AdminTheme.INK_3, false);
            return;
        }
        for (Label label : labels) {
            g.drawString(font, label.text(), x, label.y(), AdminTheme.RUBRIC, false);
        }
        rows.render(host, g, font, mouseX, mouseY);
        int ny = noteTop;
        for (FormattedCharSequence line : font.split(Component.translatable(KEY + "note"), w)) {
            if (ny + 10 > y + h) {
                break;
            }
            g.drawString(font, line, x, ny, AdminTheme.INK_3, false);
            ny += 10;
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        if (host != null) {
            rows.renderTooltip(g, host.font(), mouseX, mouseY);
        }
    }

    @Override
    public boolean hasSectionReset() {
        return true;
    }

    @Override
    public List<AdminSettingDescriptor> resettable() {
        return rows.settings().stream().filter(s -> s.editable() && !s.isDefault()).toList();
    }
}
