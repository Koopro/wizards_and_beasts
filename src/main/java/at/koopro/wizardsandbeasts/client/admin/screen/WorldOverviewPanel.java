package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.module.Module;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * World → Overview: where the world's places, spawns, dimensions and events come from, and when a change to each
 * reaches the game ({@link ApplyMode}). Worldgen itself is data and code; nothing here rewrites it. The module board
 * at the end is every progression gate the game has, with its synced state and where it is edited.
 */
@NullMarked
final class WorldOverviewPanel extends AdminInfoPanel {

    private static final String KEY = "admin.wizards_and_beasts.world_info.";

    @Override
    public AdminCategory section() {
        return AdminCategory.WORLD;
    }

    @Override
    protected Component title() {
        return Component.translatable(KEY + "title");
    }

    @Override
    protected Component summary() {
        return Component.translatable(KEY + "summary");
    }

    private static Component when(ApplyMode mode) {
        return Component.translatable(mode.labelKey());
    }

    @Override
    protected List<Block> blocks() {
        List<Block> out = new ArrayList<>();
        out.add(heading(KEY + "apply_modes"));
        // Wrapped, not a row: the new-chunks explanation is a paragraph, and a row would clip it.
        for (ApplyMode mode : ApplyMode.values()) {
            out.add(new Para(Component.literal((mode.badge().isBlank() ? "·" : mode.badge().trim()) + " ")
                    .append(Component.translatable(mode.labelKey())).append(" — ")
                    .append(Component.translatable(mode.explanationKey())),
                    at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme.INK_2));
        }

        out.add(heading(KEY + "structures"));
        out.add(row(Component.translatable(KEY + "azkaban"), when(ApplyMode.NEW_CHUNKS)));
        out.add(note(KEY + "azkaban_note"));
        out.add(row(Component.translatable(KEY + "chamber"), when(ApplyMode.NEW_CHUNKS)));
        out.add(note(KEY + "chamber_note"));
        out.add(row(Component.translatable(KEY + "flora"), Component.translatable(KEY + "restart_and_new_chunks")));
        out.add(note(KEY + "flora_note"));

        out.add(heading(KEY + "spawning"));
        out.add(row(Component.translatable(KEY + "natural_spawns"),
                Component.literal(AdminFacts.setting("creature_natural_spawns", "?"))));
        out.add(note(KEY + "spawning_note"));

        out.add(heading(KEY + "dimensions"));
        out.add(row(Component.translatable(KEY + "extension_realm"), AdminFacts.moduleState(Module.POCKET_DIMENSIONS)));
        out.add(note(KEY + "dimensions_note"));

        out.add(heading(KEY + "events"));
        out.add(note(KEY + "events_note"));

        out.add(heading(KEY + "gates"));
        out.add(note(KEY + "gates_note"));
        for (Module module : Module.values()) {
            out.add(row(AdminFacts.moduleName(module), AdminFacts.moduleState(module)));
        }
        return out;
    }
}
