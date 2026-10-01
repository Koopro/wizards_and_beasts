package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A section page made of tabs, each its own panel — every tabbed section in the Control Center is one of these, so
 * the tab bar looks, sizes and behaves the same everywhere. The selected tab lives with the caller (a static, so
 * reopening lands where the administrator left off); a tab left behind is disposed so its previews stop.
 *
 * <p>Tabs share the width they are given (never wider than {@link #MAX_TAB_W}, never narrower than
 * {@link #MIN_TAB_W}; a label that does not fit is clipped and the full name is the tooltip), Ctrl+Tab cycles them,
 * and the selected tab's name is the page's breadcrumb.
 */
@NullMarked
class TabbedPanel implements AdminPanel {

    static final int TAB_H = 16;
    private static final int TAB_GAP = 2;
    private static final int MIN_TAB_W = 36;
    private static final int MAX_TAB_W = 90;

    /** One tab: its label key and how to build its page. */
    record Tab(String labelKey, Supplier<AdminPanel> page) {}

    private final AdminCategory section;
    private final List<Tab> tabs;
    private final IntSupplier selected;
    private final IntConsumer select;
    private AdminPanel active;
    private int activeIndex;
    private @Nullable AdminPanelHost host;

    TabbedPanel(AdminCategory section, List<Tab> tabs, IntSupplier selected, IntConsumer select) {
        this.section = section;
        this.tabs = List.copyOf(tabs);
        this.selected = selected;
        this.select = select;
        this.activeIndex = clamp(selected.getAsInt());
        this.active = this.tabs.get(activeIndex).page().get();
    }

    private int clamp(int index) {
        return Math.max(0, Math.min(tabs.size() - 1, index));
    }

    /** The tab's width for a bar {@code w} wide holding {@code count} tabs. */
    static int tabWidth(int w, int count) {
        return Math.max(MIN_TAB_W, Math.min(MAX_TAB_W, (w - (count - 1) * TAB_GAP) / Math.max(1, count)));
    }

    @Override
    public AdminCategory section() {
        return section;
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        int wanted = clamp(selected.getAsInt());
        if (wanted != activeIndex) {
            active.dispose();
            activeIndex = wanted;
            active = tabs.get(activeIndex).page().get();
        }
        int tabW = tabWidth(w, tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            int index = i;
            Component label = Component.translatable(tabs.get(i).labelKey());
            AdminButton button = host.addPanelWidget(new AdminButton(x + i * (tabW + TAB_GAP), y, tabW, TAB_H, label,
                    i == activeIndex ? AdminButton.Tone.PRIMARY : AdminButton.Tone.QUIET, () -> show(index)));
            if (host.font().width(label) > tabW - 8) {
                button.setTooltip(Tooltip.create(label));
            }
        }
        active.init(host, x, y + TAB_H + 6, w, h - TAB_H - 6);
    }

    private void show(int index) {
        if (index != activeIndex && host != null) {
            select.accept(index);
            host.requestRebuild();
        }
    }

    @Override
    public boolean cycleTab(int step) {
        if (tabs.size() < 2) {
            return false;
        }
        show(Math.floorMod(activeIndex + step, tabs.size()));
        return true;
    }

    @Override
    public @Nullable Component crumb() {
        Component tab = Component.translatable(tabs.get(activeIndex).labelKey());
        Component inner = active.crumb();
        // "Creatures › Creatures › Phoenix" says nothing twice: a tab named like its section is left out.
        if (tab.getString().equals(Component.translatable(section.nameKey()).getString())) {
            return inner;
        }
        return inner == null ? tab : tab.copy().append(" › ").append(inner);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        active.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        active.renderOverlay(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        return active.mouseScrolled(mouseX, mouseY, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return active.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onServerState() {
        active.onServerState();
    }

    @Override
    public void tick() {
        active.tick();
    }

    @Override
    public void dispose() {
        active.dispose();
    }

    @Override
    public boolean hasSectionReset() {
        return active.hasSectionReset();
    }

    @Override
    public List<AdminSettingDescriptor> resettable() {
        return active.resettable();
    }

    @Override
    public boolean reveal(net.minecraft.resources.Identifier settingId) {
        return active.reveal(settingId);
    }
}
