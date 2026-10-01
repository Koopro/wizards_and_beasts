package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService.BeamSummary;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService.PresetOp;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualSettingProvider;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminVisualState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminCheckbox;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminConfirmDialog;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTextField;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.beam.BeamAppearance;
import at.koopro.wizardsandbeasts.client.beam.BeamChannelClient;
import at.koopro.wizardsandbeasts.client.beam.BeamPreviewViewport;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminVisualPayloads;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.visual.beam.BeamFadeCycle;
import at.koopro.wizardsandbeasts.visual.beam.BeamPreset;
import at.koopro.wizardsandbeasts.visual.beam.BeamShapeKind;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisual;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualDefaults;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualProperty;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Visuals → Spell Beams: every beam spell on the left; on the right its live preview, the preset library and the look
 * itself as ordinary setting rows.
 *
 * <p><b>Live preview.</b> The viewport draws the rows as they stand <em>now</em> — drafts included — through the
 * production beam renderer, every frame, so a slider drag shows at once and nothing needs a restart or a recast.
 * Pause freezes the preview's own clock (crackle, spin, sparks, fades); Compare stacks the authored default above the
 * edited look; "Watch in world" runs the same look from the administrator's own wand for a few seconds. Nothing here
 * is sent anywhere until Apply, and then only as validated settings.
 *
 * <p><b>Presets</b> are templates: Load copies one into the rows as drafts; Save / Save as new / Duplicate / Rename /
 * Delete run on the server. Built-in presets (each spell's default) cannot be overwritten, renamed or deleted, and
 * "Reset to default" only drafts the authored values back — the default itself is code, never data.
 *
 * <p><b>Cleanup.</b> The viewport is drawn from a local render state each frame and keeps nothing. The in-world preview
 * is stopped by {@link #dispose} (tab switch, section switch, screen closed by any path) and would expire on its own
 * after {@link BeamChannelClient#PREVIEW_TTL_TICKS} if even that were missed.
 */
@NullMarked
final class BeamBrowserPanel extends AdminBrowserPanel<BeamSummary> {

    private static final String KEY = "admin.wizards_and_beasts.beam.";
    private static final int BUTTON_H = 16;
    private static final int VIEW_H = 46;
    private static final int WORLD_PREVIEW_TICKS = 100;
    private static final float WORLD_PREVIEW_RANGE = 24f;
    private static final long STATUS_MS = 8_000L;

    private static final List<BeamVisualProperty> LOOK = List.of(
            BeamVisualProperty.ENABLED, BeamVisualProperty.SHAPE, BeamVisualProperty.CORE_COLOR,
            BeamVisualProperty.GLOW_COLOR, BeamVisualProperty.CORE_WIDTH, BeamVisualProperty.CORE_HEIGHT,
            BeamVisualProperty.CORE_BRIGHTNESS, BeamVisualProperty.GLOW_BRIGHTNESS, BeamVisualProperty.GLOW_SHELLS,
            BeamVisualProperty.SPARK_DENSITY, BeamVisualProperty.SPIN, BeamVisualProperty.ADDITIVE);
    private static final List<BeamVisualProperty> LIGHTNING = List.of(
            BeamVisualProperty.SEGMENTS, BeamVisualProperty.JITTER, BeamVisualProperty.CRACKLE_TICKS);
    private static final List<BeamVisualProperty> FADE = List.of(
            BeamVisualProperty.FADE_IN_TICKS, BeamVisualProperty.FADE_OUT_TICKS);

    static @Nullable String selected;
    /** View state that survives a rebuild, like the other browsers' selection. */
    static boolean paused;
    static boolean compare = true;
    static String presetChoice = "";
    /** The spell {@link #presetChoice} was picked on; another spell's page starts on its own default. */
    private static String presetChoiceFor = "";
    static String presetName = "";

    private int previewDoc = -1;
    private int statusDoc = -1;
    private int clock;
    private boolean worldPreview;

    @Override
    public AdminCategory section() {
        return AdminCategory.VISUALS;
    }

    // ── list ──

    @Override
    protected List<BeamSummary> entries() {
        return ClientAdminVisualState.beams();
    }

    @Override
    protected String idOf(BeamSummary entry) {
        return entry.spell();
    }

    @Override
    protected Component nameOf(BeamSummary entry) {
        return Component.literal(spellName(entry.spell()));
    }

    static String spellName(String spellKey) {
        Spell spell = Spells.byId(spellKey);
        return spell == null ? spellKey : GuiText.resolve(spell.getDisplayName());
    }

    @Override
    protected boolean enabledOf(BeamSummary entry) {
        return entry.enabled();
    }

    @Override
    protected int swatchOf(BeamSummary entry) {
        return entry.color() & 0xFFFFFF;
    }

    @Override
    protected boolean hasSwatches() {
        return true;
    }

    @Override
    protected String badgesOf(BeamSummary entry) {
        return (entry.enabled() ? "" : "✕") + (entry.overridden() ? "•" : "");
    }

    @Override
    protected @Nullable String selected() {
        return selected;
    }

    @Override
    protected void setSelected(String id) {
        stopWorldPreview();
        selected = id;
    }

    @Override
    protected boolean stale() {
        return ClientAdminVisualState.stale();
    }

    @Override
    protected void request() {
        AdminClientRequests.beamList();
    }

    @Override
    protected Component pickPrompt() {
        return Component.translatable(KEY + "pick");
    }

    @Override
    protected String subtitleOf(BeamSummary entry) {
        String state = entry.enabled() ? "" : " · " + Component.translatable(KEY + "off").getString();
        return Component.translatable(KEY + "preset_label", presetLabel(entry.preset())).getString() + state;
    }

    private static String presetLabel(String preset) {
        if ("default".equals(preset)) {
            return Component.translatable(KEY + "preset.default").getString();
        }
        if ("custom".equals(preset)) {
            return Component.translatable(KEY + "preset.custom").getString();
        }
        BeamPreset known = ClientAdminVisualState.preset(preset);
        return known == null ? preset : presetName(known).getString();
    }

    private static Component presetName(BeamPreset preset) {
        return preset.builtin()
                ? Component.translatable(KEY + "preset.builtin", spellName(preset.name()))
                : Component.literal(preset.name());
    }

    // ── the look as it stands ──

    private static @Nullable BeamVisual authored(BeamSummary entry) {
        BeamVisual code = BeamVisualDefaults.forSpell(entry.spell(), entry.color());
        return code == null ? null : BeamVisuals.apply(code, entry.authored());
    }

    /** The rows as shown — drafts, pending requests, else the server's values — as one look. */
    private @Nullable BeamVisual shown(BeamSummary entry) {
        BeamVisual base = authored(entry);
        if (base == null) {
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>(entry.effective());
        for (BeamVisualProperty property : BeamVisualProperty.values()) {
            AdminSettingDescriptor setting = ClientAdminState.get(BeamVisualSettingProvider.id(entry.spell(), property));
            if (setting != null && host != null) {
                values.put(property.id(), host.edits().shown(setting));
            }
        }
        return BeamVisuals.apply(base, values);
    }

    private @Nullable BeamSummary current() {
        return find(selected);
    }

    // ── page ──

    @Override
    protected int buildPage(AdminPanelHost host, Font font, BeamSummary entry, int doc, int rowW) {
        doc = header(doc, Component.translatable(KEY + "preview"));
        int bw = Math.max(40, (rowW - 3 * AdminTheme.GAP) / 4);
        place(new AdminButton(0, 0, bw, BUTTON_H, Component.translatable(KEY + (paused ? "play" : "pause")),
                AdminButton.Tone.NEUTRAL, () -> {
                    paused = !paused;
                    host.requestRebuild();
                }), 0, doc);
        place(new AdminButton(0, 0, bw, BUTTON_H, Component.translatable(KEY + "replay"), AdminButton.Tone.NEUTRAL,
                () -> clock = 0), bw + AdminTheme.GAP, doc);
        place(new AdminButton(0, 0, bw, BUTTON_H, Component.translatable(KEY + "watch"), AdminButton.Tone.PRIMARY,
                this::watchInWorld), 2 * (bw + AdminTheme.GAP), doc);
        place(new AdminCheckbox(0, 0, Component.translatable(KEY + "compare"), compare, checked -> {
            compare = checked;
            host.requestRebuild();
        }), 3 * (bw + AdminTheme.GAP), doc + 2);
        doc += BUTTON_H + AdminTheme.GAP;
        previewDoc = doc;
        doc += (compare ? 2 * VIEW_H + AdminTheme.GAP : VIEW_H) + AdminTheme.GAP;
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "preview_note"), AdminTheme.INK_3);

        doc = presets(host, font, entry, doc + 4, rowW);

        doc = header(doc + 4, Component.translatable(KEY + "look"));
        for (BeamVisualProperty property : LOOK) {
            doc = row(host, font, BeamVisualSettingProvider.id(entry.spell(), property), doc, rowW);
        }
        doc = header(doc + 4, Component.translatable(KEY + "lightning"));
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "lightning_note"), AdminTheme.INK_3);
        for (BeamVisualProperty property : LIGHTNING) {
            doc = row(host, font, BeamVisualSettingProvider.id(entry.spell(), property), doc, rowW);
        }
        doc = header(doc + 4, Component.translatable(KEY + "fade"));
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "fade_note"), AdminTheme.INK_3);
        for (BeamVisualProperty property : FADE) {
            doc = row(host, font, BeamVisualSettingProvider.id(entry.spell(), property), doc, rowW);
        }
        doc = header(doc + 4, Component.translatable(KEY + "impact"));
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "impact_note"), AdminTheme.INK_3);
        doc = row(host, font, BeamVisualSettingProvider.id(entry.spell(), BeamVisualProperty.IMPACT_INTENSITY), doc, rowW);

        doc = header(doc + 4, Component.translatable(KEY + "reset"));
        place(new AdminButton(0, 0, Math.min(rowW, 160), BUTTON_H, Component.translatable(KEY + "reset_to_default"),
                AdminButton.Tone.NEUTRAL, () -> resetToDefault(entry)), 0, doc);
        doc += BUTTON_H + AdminTheme.GAP;
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "reset_note"), AdminTheme.INK_3);
        doc = wrap(font, doc + 4, rowW, Component.translatable(KEY + "unsupported_note"), AdminTheme.INK_3);
        return doc;
    }

    private int presets(AdminPanelHost host, Font font, BeamSummary entry, int doc, int rowW) {
        doc = header(doc, Component.translatable(KEY + "presets"));
        List<BeamPreset> library = ClientAdminVisualState.presets();
        List<String> ids = new ArrayList<>();
        library.forEach(p -> ids.add(p.id()));
        if (ids.isEmpty()) {
            doc = wrap(font, doc, rowW, Component.translatable("admin.wizards_and_beasts.status.loading"), AdminTheme.INK_3);
            return doc;
        }
        // Each spell's page opens on its own default preset, however it was reached, not on the last page's pick.
        if (!entry.spell().equals(presetChoiceFor)) {
            presetChoiceFor = entry.spell();
            presetChoice = "";
        }
        if (!ids.contains(presetChoice)) {
            String own = BeamPreset.builtinId(entry.spell());
            presetChoice = ids.contains(own) ? own : ids.get(0);
        }
        BeamPreset chosen = ClientAdminVisualState.preset(presetChoice);
        boolean builtin = chosen == null || chosen.builtin();

        int half = (rowW - AdminTheme.GAP) / 2;
        place(new AdminEnumSelector(0, 0, half, BUTTON_H, ids, presetChoice, value -> {
            presetChoice = value;
            host.requestRebuild();
        }, id -> {
            BeamPreset preset = ClientAdminVisualState.preset(id);
            return preset == null ? Component.literal(id) : presetName(preset);
        }), 0, doc);
        AdminTextField nameField = new AdminTextField(font, 0, 0, half, BUTTON_H, SettingKind.STRING, BeamPreset.MAX_NAME,
                presetName, text -> presetName = text);
        nameField.inkHint(Component.translatable(KEY + "name_hint"));
        place(nameField, half + AdminTheme.GAP, doc);
        doc += BUTTON_H + AdminTheme.GAP;

        boolean nameOk = BeamPreset.validName(presetName.trim());
        // Two rows of three: six in one row squeezed "Save as new" below readable size.
        int bw = Math.max(40, (rowW - 2 * AdminTheme.GAP) / 3);
        int bx = 0;
        bx = presetButton("load", bw, bx, doc, AdminButton.Tone.PRIMARY, chosen != null, () -> loadPreset(entry, chosen));
        bx = presetButton("save", bw, bx, doc, AdminButton.Tone.NEUTRAL, !builtin, () -> send(PresetOp.SAVE, presetChoice, entry));
        presetButton("save_new", bw, bx, doc, AdminButton.Tone.NEUTRAL, nameOk, () -> send(PresetOp.CREATE, "", entry));
        doc += BUTTON_H + AdminTheme.GAP;
        bx = 0;
        bx = presetButton("duplicate", bw, bx, doc, AdminButton.Tone.NEUTRAL, chosen != null && nameOk,
                () -> send(PresetOp.DUPLICATE, presetChoice, entry));
        bx = presetButton("rename", bw, bx, doc, AdminButton.Tone.NEUTRAL, !builtin && nameOk,
                () -> send(PresetOp.RENAME, presetChoice, entry));
        presetButton("delete", bw, bx, doc, AdminButton.Tone.DANGER, !builtin, () -> confirmDelete(host, chosen));
        doc += BUTTON_H + 2;
        statusDoc = doc;
        doc += LINE;
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "presets_note"), AdminTheme.INK_3);
        return doc;
    }

    private int presetButton(String key, int w, int x, int doc, AdminButton.Tone tone, boolean active, Runnable action) {
        AdminButton button = new AdminButton(0, 0, w, BUTTON_H, Component.translatable(KEY + "preset_op." + key), tone, action);
        button.active = active;
        place(button, x, doc);
        return x + w + AdminTheme.GAP;
    }

    // ── actions ──

    /** Copies a preset into this spell's rows as drafts; Apply sends them like any edit. */
    private void loadPreset(BeamSummary entry, @Nullable BeamPreset preset) {
        if (preset == null || host == null) {
            return;
        }
        for (BeamVisualProperty property : BeamVisualProperty.values()) {
            String value = preset.values().get(property.id());
            AdminSettingDescriptor setting = ClientAdminState.get(BeamVisualSettingProvider.id(entry.spell(), property));
            if (value != null && setting != null && setting.editable()) {
                host.edits().edit(setting, value);
            }
        }
        refreshRows();
    }

    /** Drafts every row back to the authored look. The default is code, so this is always available. */
    private void resetToDefault(BeamSummary entry) {
        if (host == null) {
            return;
        }
        for (BeamVisualProperty property : BeamVisualProperty.values()) {
            AdminSettingDescriptor setting = ClientAdminState.get(BeamVisualSettingProvider.id(entry.spell(), property));
            if (setting != null && setting.editable()) {
                host.edits().edit(setting, setting.defaultValue());
            }
        }
        refreshRows();
    }

    private void send(PresetOp op, String presetId, BeamSummary entry) {
        BeamVisual look = shown(entry);
        Map<String, String> values = look == null ? Map.of() : BeamVisuals.toText(look);
        String name = presetName.trim();
        if (op == PresetOp.DUPLICATE && name.isEmpty()) {
            name = presetLabel(presetId) + " copy";
        }
        AdminClientRequests.beamPreset(op, presetId, name, values);
    }

    private void confirmDelete(AdminPanelHost host, @Nullable BeamPreset preset) {
        if (preset == null || preset.builtin()) {
            return;
        }
        host.openDialog(new AdminConfirmDialog(Component.translatable(KEY + "delete.title"),
                List.of(Component.translatable(KEY + "delete.body", preset.name())),
                Component.translatable(KEY + "preset_op.delete"), true,
                () -> {
                    host.closeDialog();
                    AdminClientRequests.beamPreset(PresetOp.DELETE, preset.id(), "", Map.of());
                },
                host::closeDialog));
    }

    /** Runs the edited look from the administrator's own wand for a few seconds, the panel hidden meanwhile. */
    private void watchInWorld() {
        if (host == null) {
            return;
        }
        String spell = selected;
        BeamSummary entry = current();
        BeamVisual first = entry == null ? null : shown(entry);
        if (spell == null || first == null) {
            return;
        }
        BeamChannelClient.startPreview(() -> {
            BeamSummary now = find(spell);
            BeamVisual look = now == null ? null : shown(now);
            return BeamAppearance.of(look == null ? first : look);
        }, WORLD_PREVIEW_RANGE);
        worldPreview = true;
        host.peek(WORLD_PREVIEW_TICKS);
    }

    private void stopWorldPreview() {
        if (worldPreview) {
            BeamChannelClient.stopPreview();
            worldPreview = false;
        }
    }

    @Override
    public void tick() {
        if (!paused) {
            clock++;
        }
        if (worldPreview) {
            if (BeamChannelClient.previewRunning()) {
                BeamChannelClient.keepPreviewAlive();
            } else {
                worldPreview = false;
            }
        }
    }

    @Override
    public void dispose() {
        stopWorldPreview();
    }

    // ── drawing ──

    @Override
    protected void renderPage(GuiGraphics g, Font font, BeamSummary entry, int top, int width, int mouseX, int mouseY) {
        if (previewDoc >= 0) {
            renderPreview(g, font, entry, top + previewDoc, width);
        }
        if (statusDoc >= 0) {
            AdminVisualPayloads.ActionReply last = ClientAdminVisualState.lastAction();
            int sy = top + statusDoc;
            if (last != null && Util.getMillis() - ClientAdminVisualState.lastActionAt() < STATUS_MS && sy >= y && sy + LINE <= y + h) {
                boolean ok = "ok".equals(last.outcome());
                Component text = Component.translatable(KEY + "outcome." + last.outcome(),
                        Component.translatable(KEY + "preset_op." + opKey(last.op())));
                g.drawString(font, AdminText.clip(font, text.getString(), width), detailX, sy,
                        ok ? AdminTheme.GOOD : AdminTheme.BAD, false);
            }
        }
    }

    private static String opKey(PresetOp op) {
        return switch (op) {
            case CREATE -> "save_new";
            case SAVE -> "save";
            case DUPLICATE -> "duplicate";
            case RENAME -> "rename";
            case DELETE -> "delete";
        };
    }

    private void renderPreview(GuiGraphics g, Font font, BeamSummary entry, int py, int width) {
        int total = compare ? 2 * VIEW_H + AdminTheme.GAP : VIEW_H;
        // Widgets and pictures are not clipped the way text is; draw the viewport only while wholly on the page.
        if (py < y || py + total > y + h) {
            return;
        }
        BeamVisual edited = shown(entry);
        BeamVisual authored = authored(entry);
        if (edited == null || authored == null) {
            return;
        }
        float partial = paused ? 0f : Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int vy = py;
        if (compare) {
            viewport(g, font, authored, Component.translatable(KEY + "view.default"), vy, width, partial);
            vy += VIEW_H + AdminTheme.GAP;
        }
        viewport(g, font, edited, Component.translatable(KEY + "view.edited"), vy, width, partial);
        if (!edited.enabled()) {
            Component off = Component.translatable(KEY + "view.off");
            g.drawString(font, AdminText.clip(font, off.getString(), width - 8), detailX + 4, vy + VIEW_H - 11,
                    AdminTheme.GOLD_LIGHT, false);
        }
    }

    private void viewport(GuiGraphics g, Font font, BeamVisual visual, Component label, int vy, int width, float partial) {
        g.fill(detailX, vy, detailX + width, vy + VIEW_H, AdminTheme.FRAME);
        float alpha = BeamFadeCycle.alpha(clock + partial, visual.fadeInTicks(), visual.fadeOutTicks());
        BeamPreviewViewport.draw(g, visual, alpha, clock, partial, detailX + 1, vy + 1, detailX + width - 1, vy + VIEW_H - 1);
        g.drawString(font, label, detailX + 4, vy + 3, AdminTheme.FRAME_TEXT, false);
        String shape = visual.shape() == BeamShapeKind.LIGHTNING ? "ϟ" : "—";
        g.drawString(font, shape + String.format(Locale.ROOT, " %d×%d px", visual.coreWidth(), visual.coreHeight()),
                detailX + width - 4 - font.width(shape + " 00×00 px"), vy + 3, AdminTheme.FRAME_TEXT_DIM, false);
    }

    @Override
    protected boolean loaded() {
        return ClientAdminVisualState.received();
    }

    @Override
    protected Component emptyMessage() {
        return Component.translatable("admin.wizards_and_beasts.empty.beams");
    }
}
