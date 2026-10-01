package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.hud.WandHudSprites;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellSummary;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Dark Arts: the rules that govern dark magic server-wide (the generic {@link SettingsPanel} over the DARK_ARTS
 * category — whether Unforgivables may be cast, the Dark Arts module), and every spell the spell law or its
 * category marks as dark, with its state and prerequisite. Clicking a spell opens it in Magic → Spells, where
 * its values live; the list here is an overview, not a second editor.
 */
@NullMarked
final class DarkArtsPanel implements AdminPanel {

    private static final int ROW_H = 13;

    private final SettingsPanel rules = new SettingsPanel(AdminCategory.DARK_ARTS);
    private @Nullable AdminPanelHost host;
    private int x;
    private int y;
    private int w;
    private int listTop;
    private int bottom;
    private double scroll;

    @Override
    public AdminCategory section() {
        return AdminCategory.DARK_ARTS;
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.bottom = y + h;
        int rulesH = 32 + ClientAdminState.inCategory(AdminCategory.DARK_ARTS).size() * AdminTheme.ROW_H + 4;
        rules.init(host, x, y, w, Math.min(rulesH, h / 2));
        this.listTop = y + Math.min(rulesH, h / 2) + 16;
        if (ClientAdminState.spellListStale()) {
            AdminClientRequests.spellList();
        }
    }

    private List<AdminSpellSummary> darkSpells() {
        List<AdminSpellSummary> out = new ArrayList<>();
        for (AdminSpellSummary row : ClientAdminState.spellList()) {
            if (row.dark()) {
                out.add(row);
            }
        }
        // Unforgivables first: they are what this page is most often opened for.
        out.sort((a, b) -> Boolean.compare(b.unforgivable(), a.unforgivable()));
        return out;
    }

    @Override
    public void onServerState() {
        if (ClientAdminState.spellListStale()) {
            AdminClientRequests.spellList();
        }
        rules.onServerState();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        rules.render(g, mouseX, mouseY, partialTick);
        Font font = host.font();
        AdminSectionHeader.renderSub(g, font, x, listTop - 14, w, Component.translatable("admin.wizards_and_beasts.dark_arts.spells"));
        List<AdminSpellSummary> spells = darkSpells();
        if (spells.isEmpty()) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.status.loading"), x, listTop + 2, AdminTheme.INK_3, false);
            return;
        }
        g.enableScissor(x, listTop, x + w, bottom);
        int stateX = x + w * 45 / 100;
        int reqX = x + w * 68 / 100;
        for (int i = 0; i < spells.size(); i++) {
            int rowY = listTop + i * ROW_H - (int) scroll;
            if (rowY + ROW_H < listTop || rowY > bottom) {
                continue;
            }
            AdminSpellSummary row = spells.get(i);
            if (mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW_H && mouseY >= listTop) {
                g.fill(x, rowY, x + w, rowY + ROW_H, AdminTheme.ROW_HOVER);
            }
            g.blitSprite(RenderPipelines.GUI_TEXTURED, WandHudSprites.spellIcon(row.id()), x + 2, rowY + 1, 11, 11);
            g.drawString(font, AdminText.clip(font, GuiText.resolve(row.displayName()), stateX - x - 20), x + 16, rowY + 3,
                    row.unforgivable() ? AdminTheme.RUBRIC : AdminTheme.INK, false);
            String state = Component.translatable(row.castAllowed() ? "admin.wizards_and_beasts.badge.castable"
                    : row.enabled() ? "admin.wizards_and_beasts.badge.refused" : "admin.wizards_and_beasts.badge.disabled").getString()
                    + (row.unforgivable() ? " · " + Component.translatable("admin.wizards_and_beasts.badge.unforgivable").getString() : "");
            g.drawString(font, AdminText.clip(font, state, reqX - stateX - 4), stateX, rowY + 3,
                    row.castAllowed() ? (row.unforgivable() ? AdminTheme.WAX : AdminTheme.INK_2) : AdminTheme.GOOD, false);
            g.drawString(font, AdminText.clip(font, row.requirement(), x + w - reqX), reqX, rowY + 3, AdminTheme.INK_3, false);
        }
        g.disableScissor();
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        rules.renderOverlay(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (host == null || mouseX < x || mouseX >= x + w || mouseY < listTop || mouseY >= bottom) {
            return false;
        }
        List<AdminSpellSummary> spells = darkSpells();
        int index = (int) ((mouseY - listTop + scroll) / ROW_H);
        if (index < 0 || index >= spells.size()) {
            return false;
        }
        // Open it where its values are edited.
        host.showSpell(spells.get(index).id());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (mouseY >= listTop && mouseY < bottom) {
            double max = Math.max(0, darkSpells().size() * ROW_H - (bottom - listTop));
            scroll = Mth.clamp(scroll - deltaY * ROW_H * 3, 0, max);
            return true;
        }
        return rules.mouseScrolled(mouseX, mouseY, deltaY);
    }

    @Override
    public boolean hasSectionReset() {
        return true;
    }

    @Override
    public List<AdminSettingDescriptor> resettable() {
        return rules.resettable();
    }
}
