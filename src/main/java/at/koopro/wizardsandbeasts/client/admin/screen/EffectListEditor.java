package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.brew.tuning.BrewEffectText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminCheckbox;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTextField;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Rows for editing one mob-effect list setting — effect, duration, amplifier, enabled, remove — plus "Add effect".
 * Reusable by any setting whose value is a {@link BrewEffectText} list.
 *
 * <p>The rows are a view of the setting's <em>draft</em>: every edit writes the whole list back as one text value
 * into the screen's edit session, and Apply sends it as an ordinary change. The server parses and validates it
 * (known effect ids, bounds, duplicates) exactly as it would a typed command, so nothing the editor produces is
 * trusted. Effects offered are the client's mob-effect registry, the same registry the server checks against.
 */
@NullMarked
final class EffectListEditor {

    /** A widget placed at a document position by the owning panel. */
    interface Placer {
        void place(AbstractWidget widget, int docX, int docY);
    }

    /** One editable row; numbers are kept as typed so a half-typed value is not rewritten under the cursor. */
    static final class Row {
        String effect;
        String duration;
        String amplifier;
        boolean ambient;
        boolean enabled;

        Row(String effect, String duration, String amplifier, boolean ambient, boolean enabled) {
            this.effect = effect;
            this.duration = duration;
            this.amplifier = amplifier;
            this.ambient = ambient;
            this.enabled = enabled;
        }

        /** The row as a validated line, or null while it is not a valid entry yet. */
        BrewEffectText.@Nullable Line line() {
            List<BrewEffectText.Line> parsed = BrewEffectText.parse(text(), BuiltInRegistries.MOB_EFFECT::containsKey);
            return parsed == null || parsed.isEmpty() ? null : parsed.get(0);
        }

        String text() {
            return (enabled ? "" : "-") + effect + " " + duration.trim() + " " + amplifier.trim() + (ambient ? " ambient" : "");
        }
    }

    static final int ROW_H = 20;
    private static final String DEFAULT_EFFECT = "minecraft:luck";

    private final AdminPanelHost host;
    private final AdminSettingDescriptor setting;
    private final List<Row> rows = new ArrayList<>();

    EffectListEditor(AdminPanelHost host, AdminSettingDescriptor setting) {
        this.host = host;
        this.setting = setting;
        for (String entry : host.edits().shown(setting).split(";")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean enabled = !trimmed.startsWith("-");
            String[] parts = (enabled ? trimmed : trimmed.substring(1).trim()).split("\\s+");
            rows.add(new Row(parts[0], parts.length > 1 ? parts[1] : "600", parts.length > 2 ? parts[2] : "0",
                    parts.length > 3 && parts[3].equalsIgnoreCase("ambient"), enabled));
        }
    }

    List<Row> rows() {
        return rows;
    }

    AdminSettingDescriptor setting() {
        return setting;
    }

    /** Every effect the client knows, sorted by id, for the effect selector. */
    private static List<String> effectOptions() {
        List<String> out = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.MOB_EFFECT.keySet()) {
            out.add(id.toString());
        }
        out.sort(String::compareTo);
        return out;
    }

    private static Component effectLabel(String id) {
        Identifier parsed = Identifier.tryParse(id);
        MobEffect effect = parsed == null ? null : BuiltInRegistries.MOB_EFFECT.getValue(parsed);
        return effect == null ? Component.literal(id) : effect.getDisplayName();
    }

    /** Places the rows from {@code docY}; returns the document height used. */
    int build(int docY, int width, Placer placer) {
        List<String> options = effectOptions();
        boolean editable = setting.editable();
        int y = docY;
        int selectorW = Math.max(90, width - 50 - 30 - 58 - 22 - 16);
        for (Row row : rows) {
            if (!options.contains(row.effect)) {
                options = new ArrayList<>(options);
                options.add(row.effect);
            }
            AdminEnumSelector effect = new AdminEnumSelector(0, 0, selectorW, 16, options, row.effect, value -> {
                row.effect = value;
                commit(true);
            }, EffectListEditor::effectLabel);
            effect.active = editable;
            placer.place(effect, 0, y);
            AdminTextField duration = new AdminTextField(host.font(), 0, 0, 50, 16, SettingKind.INTEGER, 6, row.duration, value -> {
                row.duration = value;
                commit(false);
            });
            duration.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.effect_editor.duration")));
            duration.active = editable;
            placer.place(duration, selectorW + 4, y);
            AdminTextField amplifier = new AdminTextField(host.font(), 0, 0, 30, 16, SettingKind.INTEGER, 1, row.amplifier, value -> {
                row.amplifier = value;
                commit(false);
            });
            amplifier.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.effect_editor.amplifier")));
            amplifier.active = editable;
            placer.place(amplifier, selectorW + 58, y);
            AdminCheckbox enabled = new AdminCheckbox(0, 0, Component.empty(), row.enabled, checked -> {
                row.enabled = checked;
                commit(false);
            });
            enabled.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.effect_editor.enabled")));
            enabled.active = editable;
            placer.place(enabled, selectorW + 92, y + 2);
            AdminButton remove = new AdminButton(0, 0, 16, 16, Component.literal("✕"), AdminButton.Tone.QUIET, () -> {
                rows.remove(row);
                commit(true);
            });
            remove.setTooltip(Tooltip.create(Component.translatable("admin.wizards_and_beasts.effect_editor.remove")));
            remove.active = editable;
            placer.place(remove, selectorW + 110, y);
            y += ROW_H;
        }
        AdminButton add = new AdminButton(0, 0, 100, 16, Component.translatable("admin.wizards_and_beasts.effect_editor.add"),
                AdminButton.Tone.NEUTRAL, () -> {
                    rows.add(new Row(unused(), "600", "0", false, true));
                    commit(true);
                });
        add.active = editable && rows.size() < BrewEffectText.MAX_EFFECTS;
        placer.place(add, 0, y);
        return y + ROW_H - docY;
    }

    /** The first effect not already in the list, so "Add effect" never produces a duplicate the server refuses. */
    private String unused() {
        List<String> taken = rows.stream().map(r -> r.effect).toList();
        if (!taken.contains(DEFAULT_EFFECT)) {
            return DEFAULT_EFFECT;
        }
        for (String option : effectOptions()) {
            if (!taken.contains(option)) {
                return option;
            }
        }
        return DEFAULT_EFFECT;
    }

    /** Writes the rows back as the setting's draft; structural changes rebuild the rows, typing does not. */
    private void commit(boolean rebuild) {
        List<String> entries = new ArrayList<>();
        for (Row row : rows) {
            entries.add(row.text());
        }
        host.edits().edit(setting, String.join("; ", entries));
        if (rebuild) {
            host.requestRebuild();
        }
    }
}
