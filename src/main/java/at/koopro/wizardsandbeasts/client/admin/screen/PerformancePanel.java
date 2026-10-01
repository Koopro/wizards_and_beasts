package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.perf.PerformancePresets;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Performance, as three tabs: <b>Live</b> (measured metrics, refreshed while shown), <b>Presets</b> (LOW / MEDIUM / HIGH
 * and the classification of every performance-relevant setting) and <b>Settings</b> (those settings, from whatever
 * section they are filed under).
 */
@NullMarked
final class PerformancePanel extends TabbedPanel {

    static int tab;

    PerformancePanel() {
        super(AdminCategory.PERFORMANCE, List.of(
                new Tab("admin.wizards_and_beasts.tab.perf_live", PerfLivePanel::new),
                new Tab("admin.wizards_and_beasts.tab.perf_presets", PerfPresetsPanel::new),
                new Tab("admin.wizards_and_beasts.tab.perf_settings", () -> new SettingsPanel(AdminCategory.PERFORMANCE,
                        id -> PerformancePresets.CLASSIFICATION.containsKey(id.getPath()), null, null)
                        .anySection()
                        .note(Component.translatable("admin.wizards_and_beasts.perf.settings_note")))),
                () -> tab, index -> tab = index);
    }

    static int tabIndex(String name) {
        return switch (name) {
            case "presets" -> 1;
            case "settings" -> 2;
            default -> 0;
        };
    }
}
