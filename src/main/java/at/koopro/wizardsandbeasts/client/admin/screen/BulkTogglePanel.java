package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminConfirmDialog;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminMultiSelect;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTextField;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Many on/off settings of one kind, edited together: every spell's "enabled", every creature's "spawns naturally".
 *
 * <p>Nothing here is new business logic. Each row is an existing per-entity setting the entity's own page already
 * edits; this page is a {@link AdminMultiSelect} over them, so turning off every Dark Arts spell is a filter and two
 * clicks instead of forty pages. Apply asks first (naming every change), then sends one ordinary change request per
 * row. The server checks each one against the sender's authority, holds any dangerous one for the screen's usual
 * confirmation, records it in history and answers with the stored value; the list is then fetched again, so what the
 * page shows is always the server's state, never a guess about it.
 */
@NullMarked
abstract class BulkTogglePanel implements AdminPanel {

    private static final int BUTTON_H = 16;
    /** Changes named one by one in the confirmation; the rest are counted. */
    private static final int LISTED = 12;

    protected @Nullable AdminPanelHost host;
    private int x;
    private int y;
    private int w;
    private int listTop;
    private @Nullable AdminMultiSelect list;
    private @Nullable AdminButton applyButton;
    private @Nullable Object lastSource;
    private long seenApplied;
    private String filter = "";
    private @Nullable Component status;

    /** Lang prefix for this page's title, summary and status lines. */
    protected abstract String key();

    /** The rows and which of them the server says are on; null until the server's list has arrived. */
    protected abstract @Nullable Rows rows();

    /** The setting a row's tick writes. */
    protected abstract Identifier settingId(String rowId);

    /** Asks the server for the list again. */
    protected abstract void requestRows();

    /** The rows and the server's on-set, plus the object the client holds them in (to notice a fresh reply). */
    protected record Rows(List<AdminMultiSelect.Item> items, Set<String> on, Object source) {}

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        Font font = host.font();
        int top = y + 32 + font.split(Component.translatable(key() + "note"), w).size() * 10 + 4;
        AdminTextField field = new AdminTextField(font, x, top, Math.min(160, w / 2), BUTTON_H, SettingKind.STRING, 32,
                filter, text -> {
                    filter = text;
                    if (list != null) {
                        list.setFilter(text);
                    }
                });
        field.inkHint(Component.translatable("admin.wizards_and_beasts.bulk.filter_hint"));
        host.addPanelWidget(field);
        int bw = Math.max(40, (w - Math.min(160, w / 2) - 4 * AdminTheme.GAP) / 4);
        int bx = x + Math.min(160, w / 2) + AdminTheme.GAP;
        host.addPanelWidget(new AdminButton(bx, top, bw, BUTTON_H, Component.translatable("admin.wizards_and_beasts.bulk.all"),
                AdminButton.Tone.NEUTRAL, () -> shown(true)));
        bx += bw + AdminTheme.GAP;
        host.addPanelWidget(new AdminButton(bx, top, bw, BUTTON_H, Component.translatable("admin.wizards_and_beasts.bulk.none"),
                AdminButton.Tone.NEUTRAL, () -> shown(false)));
        bx += bw + AdminTheme.GAP;
        host.addPanelWidget(new AdminButton(bx, top, bw, BUTTON_H, Component.translatable("admin.wizards_and_beasts.button.revert"),
                AdminButton.Tone.QUIET, () -> {
                    if (list != null) {
                        list.revert();
                    }
                }));
        bx += bw + AdminTheme.GAP;
        applyButton = host.addPanelWidget(new AdminButton(bx, top, bw, BUTTON_H, applyLabel(0), AdminButton.Tone.PRIMARY,
                this::confirmApply));
        applyButton.active = false;
        listTop = top + BUTTON_H + AdminTheme.GAP;
        list = host.addPanelWidget(new AdminMultiSelect(x, listTop, w, Math.max(40, y + h - listTop - 14), this::onEdited));
        list.setFilter(filter);
        lastSource = null;
        seenApplied = ClientAdminState.lastAppliedAt();
        syncRows();
        if (rows() == null) {
            requestRows();
        }
    }

    private Component applyLabel(int count) {
        return Component.translatable("admin.wizards_and_beasts.bulk.apply", count);
    }

    private void onEdited(int count) {
        if (applyButton != null) {
            applyButton.setMessage(applyLabel(count));
            applyButton.active = count > 0;
        }
    }

    private void shown(boolean on) {
        if (list != null) {
            list.setShown(on);
        }
    }

    private void syncRows() {
        Rows rows = rows();
        if (list == null || rows == null || rows.source() == lastSource) {
            return;
        }
        lastSource = rows.source();
        list.setItems(rows.items(), rows.on());
        onEdited(list.changes().size());
    }

    @Override
    public void tick() {
        syncRows();
        // Any setting applied anywhere (here, on an entity page, by another administrator) may change a row: ask again.
        long applied = ClientAdminState.lastAppliedAt();
        if (applied > seenApplied) {
            seenApplied = applied;
            requestRows();
        }
    }

    private void confirmApply() {
        if (host == null || list == null) {
            return;
        }
        Map<String, Boolean> changes = list.changes();
        if (changes.isEmpty()) {
            return;
        }
        List<Component> body = new ArrayList<>();
        body.add(Component.translatable(key() + "confirm.body", changes.size()));
        // The changes as one block of lines, not one warning box each: the exact objects, compact enough to fit.
        MutableComponent listed = Component.empty();
        int shown = 0;
        for (Map.Entry<String, Boolean> change : changes.entrySet()) {
            if (shown > 0) {
                listed.append("\n");
            }
            if (shown++ == LISTED) {
                listed.append(Component.translatable("admin.wizards_and_beasts.bulk.more", changes.size() - LISTED));
                break;
            }
            listed.append("• ").append(list.labelOf(change.getKey())).append(": ")
                    .append(Component.translatable(change.getValue() ? "options.on" : "options.off"));
        }
        body.add(listed);
        body.add(Component.translatable("admin.wizards_and_beasts.bulk.server_note"));
        AdminPanelHost h = host;
        h.openDialog(new AdminConfirmDialog(Component.translatable(key() + "confirm.title"), body,
                applyLabel(changes.size()), false, () -> {
                    h.closeDialog();
                    send(changes);
                }, h::closeDialog));
    }

    private void send(Map<String, Boolean> changes) {
        for (Map.Entry<String, Boolean> change : changes.entrySet()) {
            AdminClientRequests.change(settingId(change.getKey()), String.valueOf(change.getValue()), false);
        }
        // The page drops its drafts at once and shows the server's list when it answers: no optimistic state.
        if (list != null) {
            list.revert();
        }
        status = Component.translatable("admin.wizards_and_beasts.bulk.sent", changes.size());
        requestRows();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        AdminSectionHeader.render(g, font, x, y, w, Component.translatable(key() + "title"),
                Component.translatable(key() + "summary"));
        int ny = y + 32;
        for (var line : font.split(Component.translatable(key() + "note"), w)) {
            g.drawString(font, line, x, ny, AdminTheme.INK_3, false);
            ny += 10;
        }
        if (rows() == null && list != null) {
            g.drawString(font, Component.translatable("admin.wizards_and_beasts.bulk.loading"), x + 6, listTop + 6,
                    AdminTheme.INK_3, false);
        }
        if (status != null && list != null) {
            g.drawString(font, status, x, list.getY() + list.getHeight() + 3, AdminTheme.INK_2, false);
        }
    }
}
