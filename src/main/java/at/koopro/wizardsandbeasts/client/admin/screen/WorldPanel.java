package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * World, as two tabs: <b>Overview</b> (structures, spawning, dimensions, events and every progression gate, each
 * with when a change reaches the game — read-only) and <b>Rules</b> (the world's module switches and spawn
 * suppression; the structure switches are marked NEW CHUNKS ONLY).
 */
@NullMarked
final class WorldPanel extends TabbedPanel {

    static int tab;

    WorldPanel() {
        super(AdminCategory.WORLD, List.of(
                new Tab("admin.wizards_and_beasts.tab.world_overview", WorldOverviewPanel::new),
                new Tab("admin.wizards_and_beasts.tab.rules", () -> new SettingsPanel(AdminCategory.WORLD, id -> true,
                        null, null).note(Component.translatable("admin.wizards_and_beasts.world_rules.note")))),
                () -> tab, index -> tab = index);
    }
}
