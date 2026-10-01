package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One section's content in the Control Center's centre area.
 *
 * <p>A panel is kept across widget rebuilds (resize, dialog open/close) and re-{@link #init}ed each time, so
 * it may hold view state such as a scroll offset; it must not hold setting values, which live in
 * {@link at.koopro.wizardsandbeasts.client.admin.ClientAdminState} and the screen's edit session.
 *
 * <p>To add a panel for a section: implement this, and return it from {@link AdminPanels#create}.
 */
@NullMarked
interface AdminPanel {

    AdminCategory section();

    /** Adds this panel's widgets through {@code host} for the area {@code (x, y, w, h)}. */
    void init(AdminPanelHost host, int x, int y, int w, int h);

    /** Draws everything that is not a widget, beneath the widgets. */
    void render(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /** Draws above the widgets: tooltips. */
    default void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {}

    default boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        return false;
    }

    /** A click on something that is not a widget (a list row). Called before the widgets see it. */
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /** Whether the footer's Reset section button applies to what this panel shows. */
    default boolean hasSectionReset() {
        return false;
    }

    /** Called every client tick while the panel is shown. */
    default void tick() {}

    /** Server state changed (snapshot or result); refresh what is shown. */
    default void onServerState() {}

    /**
     * The panel is going away: the administrator navigated to another section or tab, or the Control Center closed
     * (any way, including another screen replacing it). A panel that started something outside itself — a preview
     * beam in the world, a paused clock — stops it here. Not called on a widget rebuild; may be called twice.
     */
    default void dispose() {}

    /** The settings "Reset section" would reset; empty hides the button. */
    default List<AdminSettingDescriptor> resettable() {
        return List.of();
    }

    /** Where inside the section the administrator is (a tab, an entry), for the top bar's breadcrumb; null for nothing. */
    default @Nullable Component crumb() {
        return null;
    }

    /** Ctrl+Tab: moves to the next ({@code +1}) or previous ({@code -1}) tab. False when the page has no tabs. */
    default boolean cycleTab(int step) {
        return false;
    }

    /**
     * Search opened this page for one setting: scroll its row into view and mark it. False when the row is not on
     * this page (it may be on another tab, or the setting no longer exists).
     */
    default boolean reveal(Identifier settingId) {
        return false;
    }
}
