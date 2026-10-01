package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminLangKeys;
import at.koopro.wizardsandbeasts.admin.perf.PerformancePresets;
import at.koopro.wizardsandbeasts.admin.perf.PerformancePresets.Kind;
import at.koopro.wizardsandbeasts.admin.perf.PerformancePresets.Preset;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminOpsState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminConfirmDialog;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminOpsPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Performance → Presets: LOW, MEDIUM, HIGH as the values each sets, which one the settings match now (CUSTOM when
 * none), and every performance-relevant setting with what it affects — Gameplay, Server, Client, Visual — and its
 * value. Applying asks first and changes the settings through the server like any edit (one history group).
 */
@NullMarked
final class PerfPresetsPanel extends OpsDocPanel {

    private static final String KEY = PerfLivePanel.KEY;
    private static final int BUTTON_H = 16;

    @Override
    public AdminCategory section() {
        return AdminCategory.PERFORMANCE;
    }

    @Override
    protected Component title() {
        return Component.translatable(KEY + "presets.title");
    }

    @Override
    protected Component summary() {
        return Component.translatable(KEY + "presets.summary");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    private static String value(String path) {
        AdminSettingDescriptor setting = ClientAdminState.get(id(path));
        return setting == null ? "?" : setting.value();
    }

    /** The preset the cached setting values match, or null for CUSTOM. Read from what the server last sent. */
    static @Nullable Preset current() {
        for (Preset preset : Preset.values()) {
            if (preset.settings().entrySet().stream().allMatch(e -> e.getValue().equals(value(e.getKey())))) {
                return preset;
            }
        }
        return null;
    }

    @Override
    protected int controls(AdminPanelHost host, Font font, int left, int top, int width) {
        int bw = Math.max(50, (width - 2 * AdminTheme.GAP) / 3);
        Preset current = current();
        int bx = left;
        for (Preset preset : Preset.values()) {
            AdminButton button = new AdminButton(bx, top, bw, BUTTON_H,
                    Component.translatable(KEY + "preset." + preset.id()).append(preset == current ? " ✔" : ""),
                    preset == current ? AdminButton.Tone.PRIMARY : AdminButton.Tone.NEUTRAL, () -> confirm(host, preset));
            host.addPanelWidget(button);
            bx += bw + AdminTheme.GAP;
        }
        return BUTTON_H;
    }

    @Override
    protected boolean rebuildsControls() {
        return true;
    }

    @Override
    public void onServerState() {
        // Setting values arrive as setting results, not ops answers: rebuild whenever the match may have changed.
        if (host != null) {
            host.requestRebuild();
        }
    }

    private void confirm(AdminPanelHost host, Preset preset) {
        List<Component> body = new ArrayList<>();
        body.add(Component.translatable(KEY + "confirm.body", Component.translatable(KEY + "preset." + preset.id())));
        preset.settings().forEach((path, to) -> body.add(Component.translatable(AdminLangKeys.settingName(path))
                .append(": " + value(path) + " → " + to)));
        body.add(Component.translatable(KEY + "confirm.history"));
        host.openDialog(new AdminConfirmDialog(Component.translatable(KEY + "confirm.title",
                Component.translatable(KEY + "preset." + preset.id())), body,
                Component.translatable(KEY + "confirm.apply"), false,
                () -> {
                    host.closeDialog();
                    ClientAdminOpsState.applyPreset(preset);
                },
                host::closeDialog));
    }

    @Override
    protected List<Line> lines() {
        List<Line> out = new ArrayList<>();
        Preset current = current();
        out.add(current == null
                ? Line.text(Component.translatable(KEY + "current_custom"), AdminTheme.GOLD_DARK)
                : Line.text(Component.translatable(KEY + "current", Component.translatable(KEY + "preset." + current.id())),
                AdminTheme.GOOD));
        AdminOpsPayloads.PresetReply last = ClientAdminOpsState.lastPreset();
        if (last != null) {
            out.add(last.applied()
                    ? Line.text(Component.translatable(KEY + "applied", Component.translatable(KEY + "preset." + last.preset().id()),
                    last.changed()), AdminTheme.GOOD)
                    : Line.text(Component.translatable(KEY + "refused", last.detail()), AdminTheme.BAD));
        }
        for (Preset preset : Preset.values()) {
            out.add(heading(KEY + "preset." + preset.id()));
            out.add(note(KEY + "preset." + preset.id() + ".desc"));
            preset.settings().forEach((path, to) -> out.add(plain("• " + Component.translatable(
                    AdminLangKeys.settingName(path)).getString() + ": " + to, AdminTheme.INK_2)));
        }
        out.add(heading(KEY + "classification"));
        out.add(note(KEY + "classification_note"));
        for (Map.Entry<String, java.util.Set<Kind>> entry : PerformancePresets.CLASSIFICATION.entrySet()) {
            String kinds = entry.getValue().stream().sorted()
                    .map(kind -> Component.translatable(KEY + "kind." + kind.name().toLowerCase(Locale.ROOT)).getString())
                    .collect(Collectors.joining(" · "));
            out.add(plain("• " + Component.translatable(AdminLangKeys.settingName(entry.getKey())).getString() + " = "
                    + value(entry.getKey()) + "   [" + kinds + "]", AdminTheme.INK_2));
        }
        out.add(plain("• " + Component.translatable(KEY + "beam_quality").getString() + "   ["
                + Component.translatable(KEY + "kind.client").getString() + " · "
                + Component.translatable(KEY + "kind.visual").getString() + "]", AdminTheme.INK_2));
        out.add(note(KEY + "not_exposed"));
        return out;
    }
}
