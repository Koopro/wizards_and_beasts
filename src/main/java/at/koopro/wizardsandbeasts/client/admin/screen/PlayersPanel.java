package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Facet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Players: the online players and one facet of the selected one per tab — Overview, Heritage, Skills, Spells,
 * Effects, Ministry, Economy, Debug — plus that player's administrative action log. The selection is shared, so
 * switching tabs keeps the player.
 */
@NullMarked
final class PlayersPanel extends TabbedPanel {

    static int tab;

    PlayersPanel() {
        super(AdminCategory.PLAYERS, tabs(), () -> tab, index -> tab = index);
    }

    private static List<Tab> tabs() {
        List<Tab> tabs = new ArrayList<>();
        for (Facet facet : Facet.values()) {
            tabs.add(new Tab("admin.wizards_and_beasts.players.tab." + facet.id(), () -> new PlayerFacetPanel(facet)));
        }
        tabs.add(new Tab("admin.wizards_and_beasts.players.tab.log", () -> new PlayerFacetPanel(null)));
        return tabs;
    }

    /** The tab index for a facet id, or the log for {@code log}. */
    static int tabIndex(String name) {
        Facet facet = Facet.byId(name);
        return facet == null ? Facet.values().length : facet.ordinal();
    }

    /** Selects a player (by UUID text) for every tab. */
    static void select(@Nullable String player) {
        if (player != null) {
            PlayerFacetPanel.selected = player;
        }
    }
}
