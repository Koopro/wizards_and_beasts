package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminWarning;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What a section shows when its page failed — a setting vanished mid-draw, a datapack reload left a page with data
 * it did not expect, a resource is missing. The Control Center stays open, says what happened in plain words, and
 * offers Retry (build the page again, after asking the server for fresh state). The cause goes to the log.
 */
@NullMarked
final class PanelErrorPanel implements AdminPanel {

    private final AdminCategory section;
    private final String cause;
    private final Runnable retry;
    private @Nullable AdminPanelHost host;
    private int x;
    private int y;
    private int w;

    PanelErrorPanel(AdminCategory section, String cause, Runnable retry) {
        this.section = section;
        this.cause = cause;
        this.retry = retry;
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
        host.addPanelWidget(new AdminButton(x, y + 90, 80, 18,
                Component.translatable("admin.wizards_and_beasts.error.retry"), AdminButton.Tone.PRIMARY, retry));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        int used = AdminSectionHeader.render(g, host.font(), x, y, w, Component.translatable(section.nameKey()),
                Component.translatable(section.summaryKey()));
        AdminWarning.render(g, host.font(), x, y + used + 4, w,
                Component.translatable("admin.wizards_and_beasts.error.panel", cause), AdminWarning.Severity.DANGER);
    }
}
