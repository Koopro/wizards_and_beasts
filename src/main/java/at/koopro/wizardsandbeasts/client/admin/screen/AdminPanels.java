package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Section → panel. The dashboard is its own page; any other section with settings is a {@link SettingsPanel};
 * one without any yet is a {@link ComingSoonPanel}. Magic, Dark Arts, Heritages, Creatures, Brewing, Wands, Travel and Visuals have bespoke pages. A section that needs a bespoke page (a creature preview,
 * a history browser) gets a case here and nothing else changes.
 */
@NullMarked
final class AdminPanels {

    private AdminPanels() {}

    static AdminPanel create(AdminCategory section) {
        if (section == AdminCategory.DASHBOARD) {
            return new DashboardPanel();
        }
        if (section == AdminCategory.MAGIC) {
            return new MagicPanel();
        }
        if (section == AdminCategory.DARK_ARTS) {
            return new DarkArtsPanel();
        }
        if (section == AdminCategory.HERITAGES) {
            return new HeritagePanel();
        }
        if (section == AdminCategory.CREATURES) {
            return new CreaturesPanel();
        }
        if (section == AdminCategory.BREWING) {
            return new BrewingPanel();
        }
        if (section == AdminCategory.WANDS) {
            return new WandsPanel();
        }
        if (section == AdminCategory.TRAVEL) {
            return new TravelPanel();
        }
        if (section == AdminCategory.VISUALS) {
            return new VisualsPanel();
        }
        if (section == AdminCategory.MINISTRY) {
            return new MinistryPanel();
        }
        if (section == AdminCategory.ECONOMY) {
            return new EconomyPanel();
        }
        if (section == AdminCategory.WORLD) {
            return new WorldPanel();
        }
        if (section == AdminCategory.MODULES) {
            return new ModulesPanel();
        }
        if (section == AdminCategory.PROFILES) {
            return new ProfilesPanel();
        }
        if (section == AdminCategory.PLAYERS) {
            return new PlayersPanel();
        }
        if (section == AdminCategory.PERFORMANCE) {
            return new PerformancePanel();
        }
        if (section == AdminCategory.DEBUG) {
            return new DebugPanel();
        }
        return isLive(section) ? new SettingsPanel(section) : new ComingSoonPanel(section);
    }

    /** Whether {@code panel} is still the right kind of page for its section. */
    static boolean fits(AdminPanel panel) {
        AdminCategory section = panel.section();
        if (section == AdminCategory.DASHBOARD) {
            return panel instanceof DashboardPanel;
        }
        if (section == AdminCategory.MAGIC) {
            return panel instanceof MagicPanel;
        }
        if (section == AdminCategory.DARK_ARTS) {
            return panel instanceof DarkArtsPanel;
        }
        if (section == AdminCategory.HERITAGES) {
            return panel instanceof HeritagePanel;
        }
        if (section == AdminCategory.CREATURES) {
            return panel instanceof CreaturesPanel;
        }
        if (section == AdminCategory.BREWING) {
            return panel instanceof BrewingPanel;
        }
        if (section == AdminCategory.WANDS) {
            return panel instanceof WandsPanel;
        }
        if (section == AdminCategory.TRAVEL) {
            return panel instanceof TravelPanel;
        }
        if (section == AdminCategory.VISUALS) {
            return panel instanceof VisualsPanel;
        }
        if (section == AdminCategory.MINISTRY) {
            return panel instanceof MinistryPanel;
        }
        if (section == AdminCategory.ECONOMY) {
            return panel instanceof EconomyPanel;
        }
        if (section == AdminCategory.WORLD) {
            return panel instanceof WorldPanel;
        }
        if (section == AdminCategory.MODULES) {
            return panel instanceof ModulesPanel;
        }
        if (section == AdminCategory.PROFILES) {
            return panel instanceof ProfilesPanel;
        }
        if (section == AdminCategory.PLAYERS) {
            return panel instanceof PlayersPanel;
        }
        if (section == AdminCategory.PERFORMANCE) {
            return panel instanceof PerformancePanel;
        }
        if (section == AdminCategory.DEBUG) {
            return panel instanceof DebugPanel;
        }
        return isLive(section) ? panel instanceof SettingsPanel : panel instanceof ComingSoonPanel;
    }

    static boolean isLive(AdminCategory section) {
        return section == AdminCategory.DASHBOARD || section == AdminCategory.MAGIC || section == AdminCategory.DARK_ARTS
                || section == AdminCategory.HERITAGES || section == AdminCategory.CREATURES
                || section == AdminCategory.BREWING || section == AdminCategory.WANDS
                || section == AdminCategory.TRAVEL || section == AdminCategory.VISUALS
                || section == AdminCategory.MINISTRY || section == AdminCategory.ECONOMY || section == AdminCategory.WORLD
                || section == AdminCategory.MODULES || section == AdminCategory.PROFILES
                || section == AdminCategory.PLAYERS || section == AdminCategory.PERFORMANCE
                || section == AdminCategory.DEBUG
                || !ClientAdminState.inCategory(section).isEmpty();
    }

    /** Every section that has a page now — the sidebar asks once per server state, not once per entry per frame. */
    static java.util.Set<AdminCategory> liveSet() {
        java.util.Set<AdminCategory> out = java.util.EnumSet.noneOf(AdminCategory.class);
        for (AdminCategory section : AdminCategory.values()) {
            if (isLive(section)) {
                out.add(section);
            }
        }
        return out;
    }

    /** Sections other than the dashboard that have settings, in navigation order. */
    static List<AdminCategory> liveSections() {
        List<AdminCategory> live = new ArrayList<>();
        for (AdminCategory section : AdminCategory.values()) {
            if (section != AdminCategory.DASHBOARD && isLive(section)) {
                live.add(section);
            }
        }
        return live;
    }
}
