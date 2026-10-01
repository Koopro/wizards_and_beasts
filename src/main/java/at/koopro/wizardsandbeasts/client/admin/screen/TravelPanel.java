package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Travel, as four tabs: <b>Brooms</b> (the {@link BroomBrowserPanel} — each broom, its preview and flight sliders),
 * <b>Floo</b> (the network switch, cooldown, misfire chance, wind-up, hearth timings, spoken-address matching, and the
 * registration fee filed under Economy), <b>Apparition</b> (the switch and the world's Apparition rules) and
 * <b>Rules</b> (broom flight: the switch, the server speed scale, the speed guard and the riders' own preferences).
 */
@NullMarked
final class TravelPanel extends TabbedPanel {

    enum Tab { BROOMS, FLOO, APPARITION, RULES }

    static Tab tab = Tab.BROOMS;

    TravelPanel() {
        super(AdminCategory.TRAVEL, List.of(
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.brooms", BroomBrowserPanel::new),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.floo", () -> new SettingsPanel(AdminCategory.TRAVEL,
                        TravelPanel::isFloo,
                        Component.translatable("admin.wizards_and_beasts.travel.floo.title"),
                        Component.translatable("admin.wizards_and_beasts.travel.floo.summary"))
                        .note(Component.translatable("admin.wizards_and_beasts.travel.floo.note"))
                        .anySection()),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.apparition", () -> new SettingsPanel(AdminCategory.TRAVEL,
                        TravelPanel::isApparition,
                        Component.translatable("admin.wizards_and_beasts.travel.apparition.title"),
                        Component.translatable("admin.wizards_and_beasts.travel.apparition.summary"))
                        .note(Component.translatable("admin.wizards_and_beasts.travel.apparition.note"))),
                new TabbedPanel.Tab("admin.wizards_and_beasts.tab.rules", () -> new SettingsPanel(AdminCategory.TRAVEL,
                        id -> !isFloo(id) && !isApparition(id), null, null))),
                () -> tab.ordinal(), index -> tab = Tab.values()[index]);
    }

    static boolean isFloo(Identifier id) {
        String path = id.getPath();
        return path.startsWith("floo_") || path.equals("module_floo_network");
    }

    static boolean isApparition(Identifier id) {
        String path = id.getPath();
        return path.startsWith("apparition_") || path.equals("module_apparition");
    }
}
