package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.spell.SpellSettingIds;
import at.koopro.wizardsandbeasts.admin.spell.SpellProperty;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminMultiSelect;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellSummary;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Magic → Availability: every spell's "enabled" setting in one checklist. A spell's own page edits the same setting.
 */
@NullMarked
final class SpellAvailabilityPanel extends BulkTogglePanel {

    @Override
    public AdminCategory section() {
        return AdminCategory.MAGIC;
    }

    @Override
    protected String key() {
        return "admin.wizards_and_beasts.bulk.spells.";
    }

    @Override
    protected @Nullable Rows rows() {
        if (!ClientAdminState.spellListReceived()) {
            return null;
        }
        List<AdminSpellSummary> spells = ClientAdminState.spellList();
        List<AdminMultiSelect.Item> items = new ArrayList<>(spells.size());
        Set<String> on = new HashSet<>();
        for (AdminSpellSummary spell : spells) {
            items.add(new AdminMultiSelect.Item(spell.id(), Component.literal(GuiText.resolve(spell.displayName())),
                    Component.translatable("spell_category.wizards_and_beasts." + spell.category().toLowerCase(Locale.ROOT))
                            .getString() + (spell.implemented() ? "" : " · " + Component.translatable(
                            "gui.wizards_and_beasts.spell.coming_soon_short").getString())));
            if (spell.enabled()) {
                on.add(spell.id());
            }
        }
        return new Rows(items, on, spells);
    }

    @Override
    protected Identifier settingId(String rowId) {
        return SpellSettingIds.of(rowId, SpellProperty.ENABLED);
    }

    @Override
    protected void requestRows() {
        AdminClientRequests.spellList();
    }
}
