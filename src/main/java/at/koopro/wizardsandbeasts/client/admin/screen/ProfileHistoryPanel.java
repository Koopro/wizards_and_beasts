package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminProfileState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminConfirmDialog;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.HistoryRow;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.Op;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Profiles → History: the world's configuration history, newest first — every change from any door (panel,
 * command, profile, revert) with when, who, the old and the new value. A change's page reverts that one change, or
 * the whole group it belongs to (a profile applied, a snapshot restored). The server refuses a revert when the
 * value has been changed since, so a revert never silently undoes someone else's later change.
 */
@NullMarked
final class ProfileHistoryPanel extends AdminBrowserPanel<HistoryRow> {

    private static final String KEY = ProfileText.KEY;
    private static final int BUTTON_H = 16;

    static @Nullable String selected;
    private int builtVersion = -1;

    @Override
    public AdminCategory section() {
        return AdminCategory.PROFILES;
    }

    @Override
    protected List<HistoryRow> entries() {
        return ClientAdminProfileState.history();
    }

    @Override
    protected String idOf(HistoryRow entry) {
        return Long.toString(entry.sequence());
    }

    @Override
    protected Component nameOf(HistoryRow entry) {
        return ProfileText.settingName(entry.nameKey(), entry.settingId());
    }

    @Override
    protected boolean enabledOf(HistoryRow entry) {
        return entry.applied() && !entry.undone();
    }

    @Override
    protected String badgesOf(HistoryRow entry) {
        if (!entry.applied()) {
            return "✖";
        }
        String marks = entry.undone() ? "↶" : "";
        return entry.group().isEmpty() ? marks : marks + "▣";
    }

    @Override
    protected @Nullable String selected() {
        return selected;
    }

    @Override
    protected void setSelected(String id) {
        selected = id;
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
        return Component.translatable(KEY + "pick_change");
    }

    @Override
    protected String subtitleOf(HistoryRow entry) {
        return ProfileText.time(entry.timestampMillis()) + " · " + entry.actor() + " · "
                + Component.translatable(KEY + "record." + entry.kind().name().toLowerCase(Locale.ROOT)).getString();
    }

    @Override
    protected List<Component> tooltipOf(HistoryRow entry) {
        return List.of(nameOf(entry), Component.literal(ProfileText.shown(entry.oldValue()) + " → "
                + ProfileText.shown(entry.newValue())), Component.literal(subtitleOf(entry)));
    }

    @Override
    public void onServerState() {
        super.onServerState();
        if (host != null && builtVersion != ClientAdminProfileState.version()) {
            host.requestRebuild();
        }
    }

    @Override
    protected int buildPage(AdminPanelHost host, Font font, HistoryRow entry, int doc, int rowW) {
        builtVersion = ClientAdminProfileState.version();
        doc = header(doc, Component.translatable(KEY + "change"));
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "change.setting", entry.settingId()), AdminTheme.INK_3);
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "change.from", ProfileText.shown(entry.oldValue())),
                AdminTheme.INK_2);
        doc = wrap(font, doc, rowW, Component.translatable(KEY + (entry.applied() ? "change.to" : "change.requested"),
                ProfileText.shown(entry.newValue())), AdminTheme.INK);
        if (!entry.applied()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "change.refused", entry.rejection()), AdminTheme.BAD);
        } else if (entry.undone()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "change.undone"), AdminTheme.INK_3);
        }
        if (!entry.group().isEmpty()) {
            doc = wrap(font, doc, rowW, Component.translatable(KEY + "change.group", entry.group()), AdminTheme.INK_3);
        }

        doc = header(doc + 4, Component.translatable(KEY + "revert"));
        int half = (rowW - AdminTheme.GAP) / 2;
        AdminButton one = new AdminButton(0, 0, half, BUTTON_H, Component.translatable(KEY + "button.revert_one"),
                AdminButton.Tone.NEUTRAL, () -> confirmRevertOne(host, entry));
        one.active = entry.revertible();
        place(one, 0, doc);
        AdminButton group = new AdminButton(0, 0, half, BUTTON_H, Component.translatable(KEY + "button.revert_group"),
                AdminButton.Tone.NEUTRAL, () -> confirmRevertGroup(host, entry));
        group.active = !entry.group().isEmpty() && groupRevertible(entry.group());
        place(group, half + AdminTheme.GAP, doc);
        doc += BUTTON_H + AdminTheme.GAP;

        AdminProfilePayloads.ActionReply last = ClientAdminProfileState.lastAction();
        if (last != null && (last.op() == Op.REVERT_ONE || last.op() == Op.REVERT_GROUP)) {
            doc = wrap(font, doc, rowW, ProfileText.outcome(last), last.ok() ? AdminTheme.GOOD : AdminTheme.BAD);
            for (AdminProfilePayloads.IssueRow issue : last.issues()) {
                doc = wrap(font, doc, rowW, Component.literal("✖ ").append(ProfileText.issue(issue)), AdminTheme.BAD);
            }
        }
        return wrap(font, doc + 4, rowW, Component.translatable(KEY + "revert_note"), AdminTheme.INK_3);
    }

    private static boolean groupRevertible(String group) {
        for (HistoryRow row : ClientAdminProfileState.history()) {
            if (group.equals(row.group()) && row.revertible()) {
                return true;
            }
        }
        return false;
    }

    private static int groupSize(String group) {
        int size = 0;
        for (HistoryRow row : ClientAdminProfileState.history()) {
            if (group.equals(row.group()) && row.revertible()) {
                size++;
            }
        }
        return size;
    }

    private void confirmRevertOne(AdminPanelHost host, HistoryRow entry) {
        host.openDialog(new AdminConfirmDialog(Component.translatable(KEY + "confirm.revert_one.title"),
                List.of(Component.translatable(KEY + "confirm.revert_one.body", nameOf(entry),
                        ProfileText.shown(entry.newValue()), ProfileText.shown(entry.oldValue()))),
                Component.translatable(KEY + "button.revert"), false,
                () -> {
                    host.closeDialog();
                    AdminClientRequests.profileAction(Op.REVERT_ONE, Long.toString(entry.sequence()), "");
                },
                host::closeDialog));
    }

    private void confirmRevertGroup(AdminPanelHost host, HistoryRow entry) {
        host.openDialog(new AdminConfirmDialog(Component.translatable(KEY + "confirm.revert_group.title"),
                List.of(Component.translatable(KEY + "confirm.revert_group.body", groupSize(entry.group()), entry.group())),
                Component.translatable(KEY + "button.revert"), false,
                () -> {
                    host.closeDialog();
                    AdminClientRequests.profileAction(Op.REVERT_GROUP, entry.group(), "");
                },
                host::closeDialog));
    }

    @Override
    protected boolean loaded() {
        return ClientAdminProfileState.loaded();
    }

    @Override
    protected Component emptyMessage() {
        return Component.translatable("admin.wizards_and_beasts.empty.history");
    }
}
