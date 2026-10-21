package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.creature.CreatureRuleSettings;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminCreatureState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminMultiSelect;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creatures → Spawning: every creature's natural-spawn rule in one checklist. Test spawns and the Creatures module
 * are unaffected; a creature's own page edits the same setting.
 */
@NullMarked
final class CreatureSpawningPanel extends BulkTogglePanel {

    @Override
    public AdminCategory section() {
        return AdminCategory.CREATURES;
    }

    @Override
    protected String key() {
        return "admin.wizards_and_beasts.bulk.creatures.";
    }

    @Override
    protected @Nullable Rows rows() {
        if (!ClientAdminCreatureState.received()) {
            return null;
        }
        List<AdminCreaturePayloads.CreatureSummary> creatures = ClientAdminCreatureState.creatures();
        List<AdminMultiSelect.Item> items = new ArrayList<>(creatures.size());
        Set<String> on = new HashSet<>();
        for (AdminCreaturePayloads.CreatureSummary creature : creatures) {
            // A creature with no natural spawn entries cannot spawn whatever its rule says: shown, but noted.
            String note = creature.spawnEntries() == 0
                    ? Component.translatable("admin.wizards_and_beasts.bulk.creatures.no_entries").getString()
                    : words(creature.category());
            items.add(new AdminMultiSelect.Item(creature.id(), Component.translatable(creature.nameKey()), note));
            if (creature.naturalSpawnRule()) {
                on.add(creature.id());
            }
        }
        return new Rows(items, on, creatures);
    }

    private static String words(String constant) {
        String spaced = constant.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return spaced.isEmpty() ? "" : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    @Override
    protected Identifier settingId(String rowId) {
        return CreatureRuleSettings.naturalSpawnId(rowId);
    }

    @Override
    protected void requestRows() {
        AdminClientRequests.creatureList();
    }
}
