package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.currency.dragot.DragotRates;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.module.Module;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Economy → Gringotts: the coinage, the Dragot exchange, every price and fee as it stands, where money comes from,
 * and the integrity rules. The prices quoted are the live settings (edited on the Rules tab); the coinage and the
 * exchange terms are fixed in code and shown as such.
 *
 * <p>No control on this page or the Rules tab can put money into anyone's vault. Moving a player's money is an
 * administrator command ({@code /wandb player vault …}), permission-gated and logged, kept apart from settings.
 */
@NullMarked
final class GringottsInfoPanel extends AdminInfoPanel {

    private static final String KEY = "admin.wizards_and_beasts.gringotts_info.";

    @Override
    public AdminCategory section() {
        return AdminCategory.ECONOMY;
    }

    @Override
    protected Component title() {
        return Component.translatable(KEY + "title");
    }

    @Override
    protected Component summary() {
        return Component.translatable(KEY + "summary");
    }

    @Override
    protected List<Block> blocks() {
        List<Block> out = new ArrayList<>();
        out.add(heading(KEY + "status"));
        out.add(row(Component.translatable(KEY + "module"), AdminFacts.moduleState(Module.GRINGOTTS)));
        out.add(note(KEY + "status_note"));

        out.add(heading(KEY + "coinage"));
        out.add(row(Component.translatable(KEY + "knut"), Component.translatable(KEY + "knut_value")));
        out.add(row(Component.translatable(KEY + "sickle"),
                Component.translatable(KEY + "sickle_value", CurrencyHelper.KNUTS_PER_SICKLE)));
        out.add(row(Component.translatable(KEY + "galleon"),
                Component.translatable(KEY + "galleon_value", CurrencyHelper.SICKLES_PER_GALLEON, CurrencyHelper.KNUTS_PER_GALLEON)));
        out.add(note(KEY + "coinage_note"));

        out.add(heading(KEY + "dragots"));
        out.add(row(Component.translatable(KEY + "dragot_rate"),
                Component.translatable(KEY + "dragot_rate_value", AdminFacts.setting("dragot_galleon_rate", "0.8"))));
        out.add(row(Component.translatable(KEY + "dragot_fee"), AdminFacts.percent(DragotRates.GRINGOTTS_FEE)));
        out.add(row(Component.translatable(KEY + "dragot_variance"), "±" + AdminFacts.percent(DragotRates.VARIANCE)));
        out.add(row(Component.translatable(KEY + "dragot_quote"), AdminFacts.seconds(DragotRates.QUOTE_LIFETIME_TICKS)));
        out.add(row(Component.translatable(KEY + "dragot_forged"), AdminFacts.percent(DragotRates.DEVALUED_LOOT_CHANCE)));
        out.add(row(Component.translatable(KEY + "dragot_notice"), AdminFacts.percent(DragotRates.DEVALUED_NOTICE_CHANCE)));
        out.add(note(KEY + "dragot_note"));

        out.add(heading(KEY + "prices"));
        out.add(row(Component.translatable(KEY + "price.wand"),
                AdminFacts.money(AdminFacts.settingLong("ollivander_wand_price_knuts", 3451))));
        out.add(row(Component.translatable(KEY + "price.respec"),
                AdminFacts.money(AdminFacts.settingLong("skill_respec_cost_knuts", 493))));
        out.add(row(Component.translatable(KEY + "price.floo"),
                AdminFacts.money(AdminFacts.settingLong("floo_registration_fee_knuts", 493))));
        out.add(row(Component.translatable(KEY + "price.fines"),
                AdminFacts.setting("ministry_fine_scale_percent", "100") + "%"));
        out.add(note(KEY + "prices_note"));

        out.add(heading(KEY + "sources"));
        out.add(note(KEY + "sources_note"));
        out.add(heading(KEY + "integrity"));
        out.add(note(KEY + "integrity_note"));
        return out;
    }
}
