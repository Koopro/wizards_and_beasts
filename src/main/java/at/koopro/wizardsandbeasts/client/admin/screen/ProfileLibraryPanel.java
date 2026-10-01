package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.admin.profile.ProfileDocument;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminProfileState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminConfirmDialog;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTextField;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.Op;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.ProfileRow;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Profiles → Profiles (shipped presets and saved profiles) or Profiles → Snapshots. The first entry is always the
 * current configuration, whose page saves it as a profile or takes a snapshot. A profile's page shows the server's
 * preview — how many settings would change, each one "from → to", what needs a restart or new chunks, every error
 * and warning — and Apply asks for confirmation with that count before anything is sent. Nothing is applied when
 * the preview has errors; the server validates again on apply and refuses the whole profile if anything fails.
 */
@NullMarked
final class ProfileLibraryPanel extends AdminBrowserPanel<ProfileRow> {

    private static final String KEY = ProfileText.KEY;
    private static final String CURRENT = "@current";
    private static final int BUTTON_H = 16;
    /** Change lines on the page before "…and N more". */
    private static final int MAX_PAGE_CHANGES = 60;
    /** Change lines in the confirmation dialog. */
    private static final int MAX_DIALOG_CHANGES = 8;
    private static final Set<Op> OWN_OPS = EnumSet.of(Op.APPLY, Op.SAVE_AS, Op.SNAPSHOT, Op.DUPLICATE, Op.RENAME,
            Op.DELETE, Op.EXPORT);

    static @Nullable String selectedProfile = CURRENT;
    static @Nullable String selectedSnapshot = CURRENT;

    private final boolean snapshots;
    private String name = "";
    private @Nullable String previewAskedFor;
    private long previewAskedAt = -1;
    private int builtVersion = -1;
    private long followedAction = ClientAdminProfileState.lastActionAt();
    private final ProfileRow currentRow;

    ProfileLibraryPanel(boolean snapshots) {
        this.snapshots = snapshots;
        this.currentRow = new ProfileRow(CURRENT, Component.translatable(KEY + "current").getString(), "", "",
                snapshots ? ProfileDocument.Kind.SNAPSHOT : ProfileDocument.Kind.CUSTOM, 0, 0);
    }

    @Override
    public AdminCategory section() {
        return AdminCategory.PROFILES;
    }

    @Override
    protected List<ProfileRow> entries() {
        List<ProfileRow> out = new ArrayList<>();
        if (!ClientAdminProfileState.loaded()) {
            return out;
        }
        out.add(currentRow);
        for (ProfileRow row : ClientAdminProfileState.profiles()) {
            if ((row.kind() == ProfileDocument.Kind.SNAPSHOT) == snapshots) {
                out.add(row);
            }
        }
        if (snapshots) {
            // Newest snapshot first, under the current-configuration entry.
            out.subList(1, out.size()).sort((a, b) -> Long.compare(b.createdMillis(), a.createdMillis()));
        }
        return out;
    }

    @Override
    protected String idOf(ProfileRow entry) {
        return entry.id();
    }

    @Override
    protected Component nameOf(ProfileRow entry) {
        return Component.literal(entry.name());
    }

    @Override
    protected boolean enabledOf(ProfileRow entry) {
        return true;
    }

    @Override
    protected String badgesOf(ProfileRow entry) {
        if (entry.id().equals(CURRENT)) {
            return "●";
        }
        return entry.kind() == ProfileDocument.Kind.PRESET ? "★" : "";
    }

    @Override
    protected @Nullable String selected() {
        return snapshots ? selectedSnapshot : selectedProfile;
    }

    @Override
    protected void setSelected(String id) {
        if (snapshots) {
            selectedSnapshot = id;
        } else {
            selectedProfile = id;
        }
    }

    @Override
    protected boolean stale() {
        return ClientAdminProfileState.stale();
    }

    @Override
    protected void request() {
        AdminClientRequests.profileList();
    }

    @Override
    protected Component pickPrompt() {
        return Component.translatable(KEY + (snapshots ? "pick_snapshot" : "pick_profile"));
    }

    @Override
    protected String subtitleOf(ProfileRow entry) {
        if (entry.id().equals(CURRENT)) {
            return Component.translatable(KEY + "current_subtitle").getString();
        }
        String who = entry.author().isEmpty() ? "" : " · " + entry.author();
        String when = entry.createdMillis() > 0 ? " · " + ProfileText.time(entry.createdMillis()) : "";
        return ProfileText.kind(entry.kind()).getString() + " · "
                + Component.translatable(KEY + "values", entry.size()).getString() + who + when;
    }

    @Override
    public void onServerState() {
        AdminProfilePayloads.ActionReply last = ClientAdminProfileState.lastAction();
        if (last != null && ClientAdminProfileState.lastActionAt() != followedAction && last.ok()
                && (last.op() == Op.SAVE_AS || last.op() == Op.SNAPSHOT || last.op() == Op.DUPLICATE)
                && (last.op() == Op.SNAPSHOT) == snapshots) {
            // Show what was just made.
            followedAction = ClientAdminProfileState.lastActionAt();
            setSelected(last.id());
            name = "";
        }
        super.onServerState();
        if (host != null && builtVersion != ClientAdminProfileState.version()) {
            host.requestRebuild();
        }
    }

    // ── the page ──

    @Override
    protected int buildPage(AdminPanelHost host, Font font, ProfileRow entry, int doc, int rowW) {
        builtVersion = ClientAdminProfileState.version();
        if (entry.id().equals(CURRENT)) {
            return currentPage(host, font, doc, rowW);
        }
        if (!entry.description().isEmpty()) {
            doc = wrap(font, doc, rowW, Component.literal(entry.description()), AdminTheme.INK_2) + 4;
        }
        AdminProfilePayloads.PreviewReply preview = ClientAdminProfileState.preview(entry.id());
        if (preview == null && (!entry.id().equals(previewAskedFor)
                || previewAskedAt != ClientAdminProfileState.lastActionAt())) {
            previewAskedFor = entry.id();
            previewAskedAt = ClientAdminProfileState.lastActionAt();
            AdminClientRequests.profilePreview(entry.id());
        }

        doc = header(doc, Component.translatable(KEY + "actions"));
        int third = Math.max(40, (rowW - 2 * AdminTheme.GAP) / 3);
        boolean applicable = preview != null && preview.applicable() && !preview.changes().isEmpty();
        boolean builtin = entry.kind() == ProfileDocument.Kind.PRESET;
        int bx = button(snapshots ? "restore" : "apply", third, 0, doc, AdminButton.Tone.PRIMARY, applicable,
                () -> confirmApply(host, entry));
        bx = button("export", third, bx, doc, AdminButton.Tone.NEUTRAL, true,
                () -> AdminClientRequests.profileAction(Op.EXPORT, entry.id(), ""));
        button("delete", third, bx, doc, AdminButton.Tone.DANGER, !builtin, () -> confirmDelete(host, entry));
        doc += BUTTON_H + AdminTheme.GAP;
        if (!snapshots) {
            AdminTextField field = new AdminTextField(font, 0, 0, third, BUTTON_H, SettingKind.STRING, 48, name,
                    text -> name = text);
            field.inkHint(Component.translatable(KEY + "name_hint"));
            place(field, 0, doc);
            boolean nameOk = !name.trim().isEmpty();
            bx = third + AdminTheme.GAP;
            bx = button("duplicate", third, bx, doc, AdminButton.Tone.NEUTRAL, true,
                    () -> AdminClientRequests.profileAction(Op.DUPLICATE, entry.id(),
                            nameOk ? name.trim() : entry.name() + " copy"));
            button("rename", third, bx, doc, AdminButton.Tone.NEUTRAL, !builtin && nameOk,
                    () -> AdminClientRequests.profileAction(Op.RENAME, entry.id(), name.trim()));
            doc += BUTTON_H + AdminTheme.GAP;
        }
        doc = status(font, doc, rowW);

        doc = header(doc + 4, Component.translatable(KEY + "preview"));
        if (preview == null) {
            return wrap(font, doc, rowW, Component.translatable("admin.wizards_and_beasts.status.loading"), AdminTheme.INK_3);
        }
        doc = previewBody(font, doc, rowW, preview);
        if (builtin) {
            doc = wrap(font, doc + 4, rowW, Component.translatable(KEY + "preset_note"), AdminTheme.INK_3);
        }
        return doc;
    }

    private int currentPage(AdminPanelHost host, Font font, int doc, int rowW) {
        doc = wrap(font, doc, rowW, Component.translatable(KEY + (snapshots ? "current_snapshot_note" : "current_note")),
                AdminTheme.INK_2) + 4;
        doc = header(doc, Component.translatable(KEY + (snapshots ? "create_snapshot" : "save_as")));
        int half = (rowW - AdminTheme.GAP) / 2;
        AdminTextField field = new AdminTextField(font, 0, 0, half, BUTTON_H, SettingKind.STRING, 48, name, text -> name = text);
        field.inkHint(Component.translatable(KEY + (snapshots ? "label_hint" : "name_hint")));
        place(field, 0, doc);
        if (snapshots) {
            button("create_snapshot", half, half + AdminTheme.GAP, doc, AdminButton.Tone.PRIMARY, true,
                    () -> AdminClientRequests.profileAction(Op.SNAPSHOT, "", name.trim()));
        } else {
            button("save_as", half, half + AdminTheme.GAP, doc, AdminButton.Tone.PRIMARY, !name.trim().isEmpty(),
                    () -> AdminClientRequests.profileAction(Op.SAVE_AS, "", name.trim()));
        }
        doc += BUTTON_H + AdminTheme.GAP;
        doc = status(font, doc, rowW);
        return wrap(font, doc + 4, rowW, Component.translatable(KEY + "capture_note"), AdminTheme.INK_3);
    }

    private int previewBody(Font font, int doc, int rowW, AdminProfilePayloads.PreviewReply preview) {
        int count = preview.changes().size();
        doc = wrap(font, doc, rowW, count == 0 && preview.applicable()
                        ? Component.translatable(KEY + "no_change")
                        : Component.translatable(KEY + "will_change", count),
                count == 0 ? AdminTheme.GOOD : AdminTheme.INK);
        if (preview.resetToDefault() > 0) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "reset_count", preview.resetToDefault()), AdminTheme.INK_3);
        }
        if (preview.needsRestart()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "needs_restart"), AdminTheme.BAD);
        }
        if (preview.touchesWorldgen()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "new_chunks"), AdminTheme.GOLD_DARK);
        }
        if (!preview.errors().isEmpty()) {
            doc = wrap(font, doc + 2, rowW, Component.translatable(KEY + "errors", preview.errors().size()), AdminTheme.BAD);
            for (AdminProfilePayloads.IssueRow issue : preview.errors()) {
                doc = wrap(font, doc, rowW, Component.literal("✖ ").append(ProfileText.issue(issue)), AdminTheme.BAD);
            }
        }
        for (AdminProfilePayloads.IssueRow issue : preview.warnings()) {
            doc = wrap(font, doc, rowW, Component.literal("⚠ ").append(ProfileText.issue(issue)), AdminTheme.GOLD_DARK);
        }
        doc += 2;
        int shown = 0;
        for (AdminProfilePayloads.ChangeRow change : preview.changes()) {
            if (shown++ >= MAX_PAGE_CHANGES) {
                doc = wrap(font, doc, rowW, Component.translatable(KEY + "more", count - MAX_PAGE_CHANGES), AdminTheme.INK_3);
                break;
            }
            String mark = switch (change.applyMode()) {
                case "RESTART" -> " ⟳";
                case "NEW_CHUNKS" -> " ◎";
                case "RELOAD" -> " ↻";
                default -> "";
            };
            doc = wrap(font, doc, rowW, Component.literal("• ").append(ProfileText.change(change)).append(mark), AdminTheme.INK_2);
        }
        return doc;
    }

    /** The last action of this page's kind, with every issue the server named. */
    private int status(Font font, int doc, int rowW) {
        AdminProfilePayloads.ActionReply last = ClientAdminProfileState.lastAction();
        if (last == null || !OWN_OPS.contains(last.op())) {
            return doc;
        }
        doc = wrap(font, doc, rowW, ProfileText.outcome(last), last.ok() ? AdminTheme.GOOD : AdminTheme.BAD);
        for (AdminProfilePayloads.IssueRow issue : last.issues()) {
            doc = wrap(font, doc, rowW, Component.literal(last.ok() ? "⚠ " : "✖ ").append(ProfileText.issue(issue)),
                    last.ok() ? AdminTheme.GOLD_DARK : AdminTheme.BAD);
        }
        return doc;
    }

    private int button(String key, int w, int x, int doc, AdminButton.Tone tone, boolean active, Runnable action) {
        AdminButton button = new AdminButton(0, 0, w, BUTTON_H, Component.translatable(KEY + "button." + key), tone, action);
        button.active = active;
        place(button, x, doc);
        return x + w + AdminTheme.GAP;
    }

    // ── confirmations ──

    /** "12 settings will change", the first few "from → to", restart notes; [Cancel] [Apply]. */
    private void confirmApply(AdminPanelHost host, ProfileRow entry) {
        AdminProfilePayloads.PreviewReply preview = ClientAdminProfileState.preview(entry.id());
        if (preview == null || !preview.applicable()) {
            return;
        }
        List<Component> body = new ArrayList<>();
        body.add(Component.translatable(KEY + "will_change", preview.changes().size()));
        int shown = 0;
        for (AdminProfilePayloads.ChangeRow change : preview.changes()) {
            if (shown++ >= MAX_DIALOG_CHANGES) {
                body.add(Component.translatable(KEY + "more", preview.changes().size() - MAX_DIALOG_CHANGES));
                break;
            }
            body.add(ProfileText.change(change));
        }
        if (preview.needsRestart()) {
            body.add(Component.translatable(KEY + "needs_restart"));
        }
        if (preview.touchesWorldgen()) {
            body.add(Component.translatable(KEY + "new_chunks"));
        }
        body.add(Component.translatable(KEY + "confirm.apply.revert_note"));
        host.openDialog(new AdminConfirmDialog(
                Component.translatable(KEY + (snapshots ? "confirm.restore.title" : "confirm.apply.title"), entry.name()),
                body, Component.translatable(KEY + "button." + (snapshots ? "restore_confirm" : "apply_confirm")), false,
                () -> {
                    host.closeDialog();
                    AdminClientRequests.profileAction(Op.APPLY, entry.id(), "");
                },
                host::closeDialog));
    }

    private void confirmDelete(AdminPanelHost host, ProfileRow entry) {
        host.openDialog(new AdminConfirmDialog(Component.translatable(KEY + "confirm.delete.title"),
                List.of(Component.translatable(KEY + "confirm.delete.body", entry.name())),
                Component.translatable(KEY + "button.delete"), true,
                () -> {
                    host.closeDialog();
                    setSelected(CURRENT);
                    AdminClientRequests.profileAction(Op.DELETE, entry.id(), "");
                },
                host::closeDialog));
    }

    @Override
    protected boolean loaded() {
        return ClientAdminProfileState.loaded();
    }

    @Override
    protected Component emptyMessage() {
        return Component.translatable(snapshots ? "admin.wizards_and_beasts.empty.snapshots" : "admin.wizards_and_beasts.empty.profiles");
    }
}
