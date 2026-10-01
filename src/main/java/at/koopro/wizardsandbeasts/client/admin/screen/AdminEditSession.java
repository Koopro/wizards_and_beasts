package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Edits the administrator has made but not yet applied — the only client-side values in the Control Center.
 *
 * <p>A draft is never mistaken for the server's value: rows show it with an "edited" status, Apply turns each
 * draft into a request and drops it, and from then on the row shows the pending request and then whatever the
 * server answers. Setting a draft back to the current value removes it, so "edited" always means "different".
 */
@NullMarked
final class AdminEditSession {

    private final Map<Identifier, String> drafts = new LinkedHashMap<>();

    /** What a row should display: the draft, else the value a request in flight asked for, else the server's. */
    String shown(AdminSettingDescriptor setting) {
        String draft = drafts.get(setting.id());
        if (draft != null) {
            return draft;
        }
        String pending = ClientAdminState.pendingValue(setting.id());
        return pending != null ? pending : setting.value();
    }

    boolean isEdited(Identifier id) {
        return drafts.containsKey(id);
    }

    @Nullable String draft(Identifier id) {
        return drafts.get(id);
    }

    void edit(AdminSettingDescriptor setting, String value) {
        ClientAdminState.clearFeedback(setting.id());
        if (value.equals(setting.value())) {
            drafts.remove(setting.id());
        } else {
            drafts.put(setting.id(), value);
        }
    }

    void discard(Identifier id) {
        drafts.remove(id);
    }

    void discardAll() {
        drafts.clear();
    }

    /** Drafts in edit order. A copy: Apply removes entries while iterating. */
    Map<Identifier, String> snapshot() {
        return new LinkedHashMap<>(drafts);
    }

    int size() {
        return drafts.size();
    }

    /**
     * Drops drafts that no longer mean anything: the server now holds exactly that value (another admin set
     * it), or the setting is no longer editable. A draft whose setting is not currently loaded — a value of a
     * spell the admin navigated away from — is kept; Apply sends it and the server decides.
     *
     * @return how many drafts were dropped because their setting became read-only (never silent: the screen says so)
     */
    int reconcile() {
        int[] lost = {0};
        drafts.entrySet().removeIf(entry -> {
            AdminSettingDescriptor setting = ClientAdminState.get(entry.getKey());
            if (setting == null) {
                return false;
            }
            if (!setting.editable()) {
                lost[0]++; // the administrator's edit is gone: the caller must say so
                return true;
            }
            return setting.value().equals(entry.getValue());
        });
        return lost[0];
    }
}
