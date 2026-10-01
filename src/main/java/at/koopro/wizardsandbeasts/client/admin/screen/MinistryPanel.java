package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Ministry, as two tabs: <b>Rules</b> (the Ministry switch, fines, ageing, notoriety cooling — every setting the law
 * actually reads) and <b>Law</b> (the offences, wanted bands, Trace channels, case ladder and Azkaban as enforced,
 * read-only).
 */
@NullMarked
final class MinistryPanel extends TabbedPanel {

    static int tab;

    MinistryPanel() {
        super(AdminCategory.MINISTRY, List.of(
                new Tab("admin.wizards_and_beasts.tab.rules", () -> new SettingsPanel(AdminCategory.MINISTRY, id -> true,
                        null, null).note(Component.translatable("admin.wizards_and_beasts.ministry_rules.note"))),
                new Tab("admin.wizards_and_beasts.tab.ministry_law", MinistryLawPanel::new)),
                () -> tab, index -> tab = index);
    }
}
