package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A section with nothing registered in it yet. Says so plainly, says what the section will hold, and points
 * at the command that covers it today where one exists — an empty page with no explanation reads as broken.
 */
@NullMarked
final class ComingSoonPanel implements AdminPanel {

    private final AdminCategory section;
    private @Nullable AdminPanelHost host;
    private int x;
    private int y;
    private int w;
    private int h;

    ComingSoonPanel(AdminCategory section) {
        this.section = section;
    }

    @Override
    public AdminCategory section() {
        return section;
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    @Override
    public void onServerState() {
        // The server may just have told us this section has settings after all.
        if (host != null && AdminPanels.liveSections().contains(section)) {
            host.requestRebuild();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        AdminSectionHeader.render(g, font, x, y, w, Component.translatable(section.nameKey()),
                Component.translatable(section.summaryKey()));
        int centreX = x + w / 2;
        int top = y + h / 2 - 28;
        AdminText.centred(g, font, Component.literal("✦"), centreX, top, AdminTheme.GOLD);
        AdminText.centred(g, font, Component.translatable("admin.wizards_and_beasts.coming_soon.title"),
                centreX, top + 14, AdminTheme.RUBRIC);
        int lineY = top + 28;
        for (FormattedCharSequence line : font.split(Component.translatable("admin.wizards_and_beasts.coming_soon.body"), w - 40)) {
            AdminText.centred(g, font, line, centreX, lineY, AdminTheme.INK_2);
            lineY += font.lineHeight + 1;
        }
        if (section == AdminCategory.MODULES) {
            AdminText.centred(g, font, Component.translatable("admin.wizards_and_beasts.coming_soon.modules_hint"),
                    centreX, lineY + 6, AdminTheme.INK_3);
        }
    }
}
