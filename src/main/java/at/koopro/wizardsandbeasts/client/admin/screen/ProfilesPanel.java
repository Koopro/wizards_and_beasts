package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Profiles, as four tabs: <b>Profiles</b> (shipped presets and saved profiles: preview, apply, save as, duplicate,
 * rename, delete, export), <b>Snapshots</b> (take one of the current configuration, restore one), <b>History</b>
 * (every change, revert one or a whole group) and <b>Import</b> (validate a file into a profile).
 */
@NullMarked
final class ProfilesPanel extends TabbedPanel {

    static int tab;

    ProfilesPanel() {
        super(AdminCategory.PROFILES, List.of(
                new Tab("admin.wizards_and_beasts.tab.profile_library", () -> new ProfileLibraryPanel(false)),
                new Tab("admin.wizards_and_beasts.tab.snapshots", () -> new ProfileLibraryPanel(true)),
                new Tab("admin.wizards_and_beasts.tab.history", ProfileHistoryPanel::new),
                new Tab("admin.wizards_and_beasts.tab.import", ProfileImportPanel::new)),
                () -> tab, index -> tab = index);
    }

    /** Selects {@code entry} on the named tab; {@code @newest} on History picks the latest change. */
    static void select(String tabName, @org.jspecify.annotations.Nullable String entry) {
        if (entry == null) {
            return;
        }
        switch (tabIndex(tabName)) {
            case 1 -> ProfileLibraryPanel.selectedSnapshot = entry;
            case 2 -> {
                var history = at.koopro.wizardsandbeasts.client.admin.ClientAdminProfileState.history();
                ProfileHistoryPanel.selected = "@newest".equals(entry)
                        ? (history.isEmpty() ? null : Long.toString(history.get(0).sequence())) : entry;
            }
            case 3 -> ProfileImportPanel.selected = entry;
            default -> ProfileLibraryPanel.selectedProfile = entry;
        }
    }

    static int tabIndex(String name) {
        return switch (name) {
            case "snapshots" -> 1;
            case "history" -> 2;
            case "import" -> 3;
            default -> 0;
        };
    }
}
