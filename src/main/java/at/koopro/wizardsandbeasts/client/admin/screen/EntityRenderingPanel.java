package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminCreatureState;
import at.koopro.wizardsandbeasts.client.admin.viewer.EntityViewerScreen;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads.CreatureSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Visuals → Entities: the Phase 4 entity viewer reached from the Visuals section. Every creature in the roster; a click
 * opens the full-screen viewer on the production renderer — model, variant, any animation clip at any speed, zoom,
 * hitbox and light level. The viewer spawns nothing into the world and drops its preview instance when closed.
 */
@NullMarked
final class EntityRenderingPanel implements AdminPanel {

    private static final String KEY = "admin.wizards_and_beasts.entities.";
    private static final int ROW_H = 13;

    private @Nullable AdminPanelHost host;
    private int x;
    private int y;
    private int w;
    private int h;
    private int listTop;
    private double scroll;

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
        if (ClientAdminCreatureState.listStale()) {
            AdminClientRequests.creatureList();
        }
        int noteLines = host.font().split(Component.translatable(KEY + "note"), w).size();
        listTop = y + 32 + noteLines * 10 + 4;
    }

    private List<CreatureSummary> creatures() {
        return ClientAdminCreatureState.creatures();
    }

    private double maxScroll() {
        return Math.max(0, creatures().size() * ROW_H - (y + h - listTop));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (mouseX < x || mouseX >= x + w || mouseY < listTop || mouseY >= y + h) {
            return false;
        }
        scroll = Mth.clamp(scroll - deltaY * ROW_H * 3, 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < x || mouseX >= x + w || mouseY < listTop || mouseY >= y + h) {
            return false;
        }
        int index = (int) ((mouseY - listTop + scroll) / ROW_H);
        List<CreatureSummary> all = creatures();
        if (index < 0 || index >= all.size()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            minecraft.setScreen(new EntityViewerScreen(minecraft.screen, all.get(index).id(), ""));
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        AdminSectionHeader.render(g, font, x, y, w, Component.translatable(KEY + "title"), Component.translatable(KEY + "summary"));
        int ny = y + 32;
        for (FormattedCharSequence line : font.split(Component.translatable(KEY + "note"), w)) {
            g.drawString(font, line, x, ny, AdminTheme.INK_3, false);
            ny += 10;
        }
        List<CreatureSummary> all = creatures();
        if (all.isEmpty()) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.status.loading"), x, listTop, AdminTheme.INK_3, false);
            return;
        }
        g.enableScissor(x, listTop, x + w, y + h);
        for (int i = 0; i < all.size(); i++) {
            int rowY = listTop + i * ROW_H - (int) scroll;
            if (rowY + ROW_H < listTop || rowY > y + h) {
                continue;
            }
            CreatureSummary creature = all.get(i);
            if (mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW_H) {
                g.fill(x, rowY, x + w, rowY + ROW_H, AdminTheme.ROW_HOVER);
            }
            String open = Component.translatable(KEY + "open").getString();
            g.drawString(font, AdminText.clip(font, Component.translatable(creature.nameKey()).getString(),
                    w - font.width(open) - 12), x + 4, rowY + 3, AdminTheme.INK, false);
            g.drawString(font, open, x + w - font.width(open) - 4, rowY + 3, AdminTheme.GOLD_DARK, false);
        }
        g.disableScissor();
    }
}
