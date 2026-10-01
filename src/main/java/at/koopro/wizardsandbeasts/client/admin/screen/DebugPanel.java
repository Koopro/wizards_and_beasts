package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Debug, as three tabs: <b>Tools</b> (leased switches: debug mode, spell logging, log level, hitboxes), <b>Live</b>
 * (beams, cast sessions, recent casts and refusals, entities, modules, network) and <b>Settings</b> (the persistent
 * debug settings).
 */
@NullMarked
final class DebugPanel extends TabbedPanel {

    static int tab;

    DebugPanel() {
        super(AdminCategory.DEBUG, List.of(
                new Tab("admin.wizards_and_beasts.tab.debug_tools", DebugToolsPanel::new),
                new Tab("admin.wizards_and_beasts.tab.debug_live", DebugLivePanel::new),
                new Tab("admin.wizards_and_beasts.tab.debug_settings", () -> new SettingsPanel(AdminCategory.DEBUG,
                        id -> true, null, null)
                        .note(Component.translatable("admin.wizards_and_beasts.debug_tools.settings_note")))),
                () -> tab, index -> tab = index);
    }

    static int tabIndex(String name) {
        return switch (name) {
            case "live" -> 1;
            case "settings" -> 2;
            default -> 0;
        };
    }
}
