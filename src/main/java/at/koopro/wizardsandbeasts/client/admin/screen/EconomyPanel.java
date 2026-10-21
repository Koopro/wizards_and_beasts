package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Economy, as three tabs: <b>Gringotts</b> (coinage, exchange, prices as they stand, money sources, integrity —
 * read-only), <b>Markets</b> (the ledger's flows and every trader's book, live — read-only) and <b>Rules</b> (the bank
 * switch and every price, fee and market scale the game charges). No control here creates money.
 */
@NullMarked
final class EconomyPanel extends TabbedPanel {

    static int tab;

    /** Tab indices, for navigation (a search result opens Rules, the capture harness any tab). */
    static final int MARKETS_TAB = 1;
    static final int RULES_TAB = 2;

    EconomyPanel() {
        super(AdminCategory.ECONOMY, List.of(
                new Tab("admin.wizards_and_beasts.tab.gringotts", GringottsInfoPanel::new),
                new Tab("admin.wizards_and_beasts.tab.markets", EconomyLivePanel::new),
                new Tab("admin.wizards_and_beasts.tab.rules", () -> new SettingsPanel(AdminCategory.ECONOMY, id -> true,
                        null, null).note(Component.translatable("admin.wizards_and_beasts.economy_rules.note"))),
                new Tab("admin.wizards_and_beasts.tab.economy_markets",
                        () -> new AdminReportPanel(AdminCategory.ECONOMY, "economy_markets")),
                new Tab("admin.wizards_and_beasts.tab.economy_audit",
                        () -> new AdminReportPanel(AdminCategory.ECONOMY, "audit", 100, "money"))),
                () -> tab, index -> tab = index);
    }
}
