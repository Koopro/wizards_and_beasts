package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Economy, as two tabs: <b>Gringotts</b> (coinage, exchange, prices as they stand, money sources, integrity —
 * read-only) and <b>Rules</b> (the bank switch and every price and fee the game charges). No control here creates
 * money.
 */
@NullMarked
final class EconomyPanel extends TabbedPanel {

    static int tab;

    EconomyPanel() {
        super(AdminCategory.ECONOMY, List.of(
                new Tab("admin.wizards_and_beasts.tab.gringotts", GringottsInfoPanel::new),
                new Tab("admin.wizards_and_beasts.tab.rules", () -> new SettingsPanel(AdminCategory.ECONOMY, id -> true,
                        null, null).note(Component.translatable("admin.wizards_and_beasts.economy_rules.note")))),
                () -> tab, index -> tab = index);
    }
}
