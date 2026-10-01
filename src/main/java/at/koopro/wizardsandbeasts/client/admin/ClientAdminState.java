package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.network.admin.AdminSessionInfo;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingResultS2CPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminSnapshotS2CPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellSummary;
import at.koopro.wizardsandbeasts.admin.spell.SpellSettingIds;
import net.minecraft.util.Util;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's cached copy of the server's admin state. A mirror, never a source: every value in here came
 * from a server packet, and nothing in the UI writes a setting's value here directly.
 *
 * <p>Descriptors arrive in a snapshot and are then kept current by result deltas, so metadata is not
 * re-sent on every change. Requests in flight are tracked by id so a row can show "saving" and so a lost
 * answer times out into a visible failure instead of leaving the row in a fake state forever.
 *
 * <p>Client thread only.
 */
@NullMarked
public final class ClientAdminState {

    /** How long a request may go unanswered before the row gives up and shows the server value again. */
    public static final long REQUEST_TIMEOUT_MS = 6_000L;

    private static @Nullable AdminSessionInfo info;
    private static final Map<Identifier, AdminSettingDescriptor> SETTINGS = new LinkedHashMap<>();
    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static final Map<Identifier, Feedback> FEEDBACK = new HashMap<>();
    /** Descriptors of the spell on the detail page. Kept apart so spell values never appear in a section list. */
    private static final Map<Identifier, AdminSettingDescriptor> SPELL_SETTINGS = new LinkedHashMap<>();
    /** Descriptors of the brew on the Brewing page, kept apart for the same reason. */
    private static final Map<Identifier, AdminSettingDescriptor> BREW_SETTINGS = new LinkedHashMap<>();
    /** Descriptors a section page received with its own reply (wands, brooms), per page, kept apart likewise. */
    private static final Map<String, Map<Identifier, AdminSettingDescriptor>> PAGE_SETTINGS = new HashMap<>();
    private static final List<ConfirmRequest> CONFIRM_QUEUE = new ArrayList<>();
    private static List<AdminSpellSummary> spellList = List.of();
    private static boolean spellListStale = true;
    private static boolean spellListReceived;
    private static AdminSpellPayloads.@Nullable DetailReply spellDetail;
    private static AdminSpellPayloads.@Nullable ActionReply lastAction;
    private static long lastActionAt;
    private static int nextRequestId = 1;
    /** Bumped whenever anything a search could find changes; lets the search index skip a rebuild. */
    private static int version;

    private ClientAdminState() {}

    /** A request sent and not yet answered. */
    /** @param requestedValue the value asked for, or null for a reset */
    public record Pending(Identifier settingId, @Nullable String requestedValue, long sentAtMillis) {}

    /**
     * A change the server held back as dangerous: the screen shows {@link #warningKey} and resends it confirmed.
     *
     * @param value the requested value, or null when the held request was a reset
     */
    public record ConfirmRequest(Identifier settingId, @Nullable String value, String warningKey) {}

    /** The last thing that happened to a setting, for the row's status line. */
    public record Feedback(Kind kind, AdminResult result, long atMillis) {
        public enum Kind {
            /** This client's own change was stored. */
            APPLIED,
            /** This client's own change was refused, or never answered. */
            REJECTED,
            /** Someone else changed it while the panel was open. */
            REMOTE
        }
    }

    // ── inbound ──

    static void accept(AdminSnapshotS2CPayload snapshot) {
        version++;
        info = snapshot.info();
        AdminCategory section = snapshot.section();
        if (section == null) {
            SETTINGS.clear();
        } else {
            SETTINGS.values().removeIf(d -> d.category() == section);
        }
        for (AdminSettingDescriptor descriptor : snapshot.settings()) {
            SETTINGS.put(descriptor.id(), descriptor);
        }
        if (snapshot.openScreen()) {
            PENDING.clear();
            FEEDBACK.clear();
        }
    }

    /**
     * A profile, snapshot or revert moved many values at once, any of them on a page of its own (spells, brews,
     * wands, brooms, beams, creatures): every page asks the server again when next shown.
     */
    static void invalidatePages() {
        spellListStale = true;
        ClientAdminCreatureState.markStale();
        ClientAdminBrewState.markStale();
        ClientAdminWandState.markStale();
        ClientAdminBroomState.markStale();
        ClientAdminVisualState.markStale();
    }

    static void accept(AdminSettingResultS2CPayload payload) {
        AdminResult result = payload.result();
        Pending pending = PENDING.remove(payload.requestId());
        version++;
        // An empty value means the server withheld it (unauthorised, unknown); keep what we had rather than
        // blanking the row. Otherwise the server's value replaces ours, whatever the status.
        if (!result.value().isEmpty()) {
            AdminSettingDescriptor known = SETTINGS.get(result.settingId());
            if (known != null) {
                SETTINGS.put(known.id(), known.withValue(result.value()));
            }
            AdminSettingDescriptor spell = SPELL_SETTINGS.get(result.settingId());
            if (spell != null) {
                SPELL_SETTINGS.put(spell.id(), spell.withValue(result.value()));
            }
            AdminSettingDescriptor brew = BREW_SETTINGS.get(result.settingId());
            if (brew != null) {
                BREW_SETTINGS.put(brew.id(), brew.withValue(result.value()));
            }
            for (Map<Identifier, AdminSettingDescriptor> page : PAGE_SETTINGS.values()) {
                AdminSettingDescriptor onPage = page.get(result.settingId());
                if (onPage != null) {
                    page.put(onPage.id(), onPage.withValue(result.value()));
                }
            }
        }
        if (result.applied()) {
            // Every applied change is a new history record.
            ClientAdminProfileState.markStale();
        }
        if (result.applied() && SpellSettingIds.isSpellSetting(result.settingId())) {
            spellListStale = true;
        }
        if (result.applied() && result.settingId().getPath().startsWith(
                at.koopro.wizardsandbeasts.admin.creature.CreatureRuleSettings.PREFIX)) {
            // Creature rows and pages summarise their rules (natural spawning, variant weights).
            ClientAdminCreatureState.markStale();
        }
        if (result.applied() && at.koopro.wizardsandbeasts.admin.brew.BrewSettingIds.isBrewSetting(result.settingId())) {
            ClientAdminBrewState.markStale();
        }
        if (result.applied() && at.koopro.wizardsandbeasts.admin.wand.WandSettingProvider.isWandSetting(result.settingId())) {
            ClientAdminWandState.markStale();
        }
        if (result.applied() && (at.koopro.wizardsandbeasts.admin.broom.BroomSettingProvider.isBroomSetting(result.settingId())
                || result.settingId().getPath().equals("broom_server_speed_scale"))) {
            // Rows show each broom as it flies, which the speed scale moves too.
            ClientAdminBroomState.markStale();
        }
        if (result.applied() && at.koopro.wizardsandbeasts.admin.visual.BeamVisualSettingProvider.isBeamSetting(result.settingId())) {
            // Beam rows show the effective look and which preset it matches.
            ClientAdminVisualState.markStale();
        }
        boolean own = payload.requestId() == AdminSettingResultS2CPayload.BATCH || pending != null;
        if (result.needsConfirmation()) {
            // Not a refusal: the server wants the administrator to read a warning first. The screen asks, and
            // resends with confirmation; until then nothing changed, so there is no status to show.
            if (own && result.detailKey() != null) {
                CONFIRM_QUEUE.add(new ConfirmRequest(result.settingId(),
                        pending == null ? null : pending.requestedValue(),
                        result.detailKey()));
            }
            FEEDBACK.remove(result.settingId());
            return;
        }
        Feedback.Kind kind;
        if (payload.requestId() == AdminSettingResultS2CPayload.BROADCAST) {
            kind = Feedback.Kind.REMOTE;
        } else if (!own) {
            return; // an answer to a request from before the last snapshot; the snapshot already superseded it
        } else {
            kind = result.rejected() ? Feedback.Kind.REJECTED : Feedback.Kind.APPLIED;
        }
        FEEDBACK.put(result.settingId(), new Feedback(kind, result, Util.getMillis()));
    }

    // ── outbound bookkeeping ──

    static int track(Identifier settingId, @Nullable String requestedValue) {
        int id = nextRequestId++;
        if (nextRequestId == Integer.MAX_VALUE) {
            nextRequestId = 1; // never hand out BROADCAST (-1) or wrap negative
        }
        PENDING.put(id, new Pending(settingId, requestedValue, Util.getMillis()));
        return id;
    }

    /**
     * Expires requests the server never answered. Each becomes a REJECTED feedback with the server value
     * as last known, so the row stops claiming to be saving.
     *
     * @return true when anything expired (the screen should refresh its rows)
     */
    public static boolean expireStale() {
        long now = Util.getMillis();
        boolean expired = false;
        for (Iterator<Map.Entry<Integer, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Pending pending = it.next().getValue();
            if (now - pending.sentAtMillis() < REQUEST_TIMEOUT_MS) {
                continue;
            }
            it.remove();
            AdminSettingDescriptor known = SETTINGS.get(pending.settingId());
            String value = known == null ? "" : known.value();
            FEEDBACK.put(pending.settingId(), new Feedback(Feedback.Kind.REJECTED,
                    new AdminResult(pending.settingId(), AdminResult.Status.REJECTED, null,
                            "admin.wizards_and_beasts.status.timeout", value, value, false), now));
            expired = true;
        }
        return expired;
    }

    // ── reads ──

    public static @Nullable AdminSessionInfo info() {
        return info;
    }

    /** A section setting, or a setting of the spell on the detail page. */
    public static @Nullable AdminSettingDescriptor get(Identifier id) {
        AdminSettingDescriptor setting = SETTINGS.get(id);
        if (setting != null) {
            return setting;
        }
        AdminSettingDescriptor spell = SPELL_SETTINGS.get(id);
        if (spell != null) {
            return spell;
        }
        AdminSettingDescriptor brew = BREW_SETTINGS.get(id);
        if (brew != null) {
            return brew;
        }
        for (Map<Identifier, AdminSettingDescriptor> page : PAGE_SETTINGS.values()) {
            AdminSettingDescriptor found = page.get(id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ── magic section ──

    static void accept(AdminSpellPayloads.ListReply reply) {
        version++;
        spellList = reply.spells();
        spellListStale = false;
        spellListReceived = true;
    }

    static void accept(AdminSpellPayloads.DetailReply reply) {
        spellDetail = reply;
        SPELL_SETTINGS.clear();
        for (AdminSettingDescriptor descriptor : reply.settings()) {
            SPELL_SETTINGS.put(descriptor.id(), descriptor);
        }
    }

    static void accept(AdminSpellPayloads.ActionReply reply) {
        lastAction = reply;
        lastActionAt = Util.getMillis();
    }

    public static List<AdminSpellSummary> spellList() {
        return spellList;
    }

    /** True until the first list arrives, and again after any spell value changes. */
    public static boolean spellListStale() {
        return spellListStale;
    }

    /** Whether the server has answered with the spell list since joining. */
    public static boolean spellListReceived() {
        return spellListReceived;
    }

    public static void markSpellListRequested() {
        spellListStale = false;
    }

    public static AdminSpellPayloads.@Nullable DetailReply spellDetail() {
        return spellDetail;
    }

    /** The editable values of the spell on the detail page, with the latest server values. */
    public static List<AdminSettingDescriptor> spellSettings() {
        return List.copyOf(SPELL_SETTINGS.values());
    }

    public static AdminSpellPayloads.@Nullable ActionReply lastAction() {
        return lastAction;
    }

    public static long lastActionAt() {
        return lastActionAt;
    }

    /** The editable values of the brew on the Brewing page, replacing the previous brew's. */
    static void acceptBrewSettings(List<AdminSettingDescriptor> settings) {
        BREW_SETTINGS.clear();
        for (AdminSettingDescriptor descriptor : settings) {
            BREW_SETTINGS.put(descriptor.id(), descriptor);
        }
    }

    /** Held-back dangerous changes waiting for the screen to ask. Drains the queue. */
    public static List<ConfirmRequest> takeConfirmRequests() {
        List<ConfirmRequest> out = List.copyOf(CONFIRM_QUEUE);
        CONFIRM_QUEUE.clear();
        return out;
    }

    public static List<AdminSettingDescriptor> inCategory(AdminCategory category) {
        List<AdminSettingDescriptor> out = new ArrayList<>();
        for (AdminSettingDescriptor descriptor : SETTINGS.values()) {
            if (descriptor.category() == category) {
                out.add(descriptor);
            }
        }
        return out;
    }

    public static List<AdminSettingDescriptor> all() {
        return List.copyOf(SETTINGS.values());
    }

    /** Changes whenever settings, results or the spell list arrive. */
    public static int version() {
        return version;
    }

    public static boolean isPending(Identifier id) {
        for (Pending pending : PENDING.values()) {
            if (pending.settingId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The value a request in flight asked for, so a row can keep showing it while "saving". */
    public static @Nullable String pendingValue(Identifier id) {
        for (Pending pending : PENDING.values()) {
            if (pending.settingId().equals(id) && pending.requestedValue() != null) {
                return pending.requestedValue();
            }
        }
        return null;
    }

    public static boolean anyPending() {
        return !PENDING.isEmpty();
    }

    public static @Nullable Feedback feedback(Identifier id) {
        return FEEDBACK.get(id);
    }

    /** How many of this client's own changes the server refused (or never answered) in the last {@code windowMs}. */
    public static int recentRejections(long windowMs) {
        long since = Util.getMillis() - windowMs;
        int count = 0;
        for (Feedback feedback : FEEDBACK.values()) {
            if (feedback.kind() == Feedback.Kind.REJECTED && feedback.atMillis() >= since) {
                count++;
            }
        }
        return count;
    }

    /** When this client's newest own change was stored, or 0. */
    public static long lastAppliedAt() {
        long newest = 0;
        for (Feedback feedback : FEEDBACK.values()) {
            if (feedback.kind() == Feedback.Kind.APPLIED) {
                newest = Math.max(newest, feedback.atMillis());
            }
        }
        return newest;
    }

    /** Forgets a row's status once the admin edits it again. */
    public static void clearFeedback(Identifier id) {
        FEEDBACK.remove(id);
    }

    /** The editable values a section page received with its own reply, replacing that page's previous ones. */
    static void acceptPageSettings(String page, List<AdminSettingDescriptor> settings) {
        Map<Identifier, AdminSettingDescriptor> map = new LinkedHashMap<>();
        for (AdminSettingDescriptor descriptor : settings) {
            map.put(descriptor.id(), descriptor);
        }
        PAGE_SETTINGS.put(page, map);
    }

    /** Leaving the server: nothing cached may leak into the next one. */
    public static void clear() {
        info = null;
        SETTINGS.clear();
        PENDING.clear();
        FEEDBACK.clear();
        SPELL_SETTINGS.clear();
        BREW_SETTINGS.clear();
        PAGE_SETTINGS.clear();
        CONFIRM_QUEUE.clear();
        spellList = List.of();
        spellListStale = true;
        spellListReceived = false;
        spellDetail = null;
        lastAction = null;
    }
}
