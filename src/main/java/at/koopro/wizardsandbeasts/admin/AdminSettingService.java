package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry;
import at.koopro.wizardsandbeasts.admin.history.AdminChangeHistory;
import at.koopro.wizardsandbeasts.admin.history.AdminChangeRecord;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * The one place an administrative setting changes.
 *
 * <p>The command tree and the network handler are two doors into these methods, exactly as
 * {@link at.koopro.wizardsandbeasts.module.ModuleStateService} is for module state. Every request is judged
 * here, in this order, no matter which door it came through:
 * <ol>
 *   <li>the actor is an administrator at all — checked <em>before</em> the id is looked up, so an outsider
 *       cannot probe which ids exist;</li>
 *   <li>the setting exists;</li>
 *   <li>the actor holds the setting's capability;</li>
 *   <li>the setting is server-authoritative ({@link at.koopro.wizardsandbeasts.admin.config.SettingScope});</li>
 *   <li>the backing store is available;</li>
 *   <li>the text parses as the setting's type, lies in bounds, and satisfies every cross-setting rule;</li>
 * </ol>
 * then the value is stored, read back, recorded and announced. Whatever a client's widget allowed, it is
 * re-decided here.
 *
 * <p>Server thread only. Instances are cheap and self-contained so a unit test can build one over an
 * in-memory registry; the live instance is {@link AdminSettings#service()}.
 */
@NullMarked
public final class AdminSettingService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Stand-in id for results that concern no particular setting (an empty undo). */
    public static final Identifier NO_SETTING = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "none");

    /** A rejected request's text is kept in history, but a hostile one is not kept whole. */
    private static final int MAX_RECORDED_TEXT = 64;
    private static final String UNDO_STALE = "admin.wizards_and_beasts.conflict.undo_stale";

    private final AdminSettingRegistry registry;
    private final AdminChangeHistory history;
    private final LongSupplier clock;
    private final List<BiConsumer<AdminContext, AdminResult>> observers = new CopyOnWriteArrayList<>();
    /** The batch every record made right now belongs to; set only inside {@link #withGroup}. Server thread only. */
    private @Nullable String group;

    public AdminSettingService(AdminSettingRegistry registry, AdminChangeHistory history, LongSupplier clock) {
        this.registry = registry;
        this.history = history;
        this.clock = clock;
    }

    public AdminSettingRegistry registry() {
        return registry;
    }

    public AdminChangeHistory history() {
        return history;
    }

    /**
     * Called after every <em>applied</em> change, whichever door it came through. This is how an open panel
     * on another administrator's screen hears about a change made by command, without the service knowing
     * the network exists.
     */
    public void addObserver(BiConsumer<AdminContext, AdminResult> observer) {
        observers.add(observer);
    }

    // ── mutations ──

    /** Sets one setting from text, unconfirmed: a dangerous change comes back as CONFIRMATION_REQUIRED. */
    public AdminResult change(AdminContext actor, Identifier settingId, String rawValue) {
        return change(actor, settingId, rawValue, false);
    }

    /**
     * Sets one setting from text — the command argument or the packet field, unchanged.
     *
     * @param confirmed the actor has seen and accepted the setting's danger warning; without it a change the
     *                  setting's danger rule flags is not applied and answered with
     *                  {@link AdminRejection#CONFIRMATION_REQUIRED}
     */
    public AdminResult change(AdminContext actor, Identifier settingId, String rawValue, boolean confirmed) {
        return attempt(actor, settingId, rawValue, AdminChangeRecord.Kind.CHANGE, confirmed);
    }

    /** Puts one setting back to its default, unconfirmed. */
    public AdminResult reset(AdminContext actor, Identifier settingId) {
        return reset(actor, settingId, false);
    }

    /** Puts one setting back to its default. A default can be dangerous too (an Unforgivable enabled again). */
    public AdminResult reset(AdminContext actor, Identifier settingId, boolean confirmed) {
        if (!actor.canRead()) {
            return refuse(actor, settingId, AdminChangeRecord.Kind.RESET, "", "", AdminRejection.UNAUTHORIZED, null);
        }
        AdminSetting<?> setting = registry.get(settingId);
        if (setting == null) {
            return refuse(actor, settingId, AdminChangeRecord.Kind.RESET, "", "", AdminRejection.UNKNOWN_SETTING, null);
        }
        return attemptTyped(actor, setting, setting.defaultText(), AdminChangeRecord.Kind.RESET, confirmed);
    }

    /**
     * Resets every server-authoritative setting in {@code category} that is not already at its default.
     * One result per setting touched; a single UNAUTHORIZED result when the actor is not an administrator.
     */
    public List<AdminResult> resetSection(AdminContext actor, AdminCategory category) {
        return resetEach(actor, registry.inCategory(category));
    }

    /** {@link #resetSection} over every section. */
    public List<AdminResult> resetAll(AdminContext actor) {
        return resetEach(actor, List.copyOf(registry.all()));
    }

    /**
     * Reverts the most recent applied change that has not been undone, server-wide.
     *
     * <p>Refuses with CONFLICT when the setting no longer holds the value that change produced — it was
     * edited since (in the config file, say), and replaying the old value would silently discard that edit.
     */
    public AdminResult undoLast(AdminContext actor) {
        if (!actor.canRead()) {
            return refuse(actor, NO_SETTING, AdminChangeRecord.Kind.UNDO, "", "", AdminRejection.UNAUTHORIZED, null);
        }
        Optional<AdminChangeRecord> last = history.lastUndoable();
        if (last.isEmpty()) {
            return AdminResult.rejected(NO_SETTING, AdminRejection.NOTHING_TO_UNDO, null, "");
        }
        AdminChangeRecord record = last.get();
        AdminSetting<?> setting = registry.get(record.settingId());
        if (setting == null) {
            return refuse(actor, record.settingId(), AdminChangeRecord.Kind.UNDO, "", record.oldValue(),
                    AdminRejection.UNKNOWN_SETTING, null);
        }
        if (!actor.canModify(setting.capability())) {
            return refuse(actor, setting.id(), AdminChangeRecord.Kind.UNDO, "", record.oldValue(),
                    AdminRejection.UNAUTHORIZED, null);
        }
        if (setting.binding().available() && !setting.currentText().equals(record.newValue())) {
            return refuse(actor, setting.id(), AdminChangeRecord.Kind.UNDO, setting.currentText(),
                    record.oldValue(), AdminRejection.CONFLICT, UNDO_STALE);
        }
        // Undo is itself the explicit step: the change it reverts was confirmed when it was made.
        AdminResult result = attemptTyped(actor, setting, record.oldValue(), AdminChangeRecord.Kind.UNDO, true);
        if (result.applied() || result.status() == AdminResult.Status.UNCHANGED) {
            history.markUndone(record.sequence());
        }
        return result;
    }

    /**
     * Runs {@code body} with every history record it makes tagged {@code group} — a profile applied, a snapshot
     * restored, a group reverted — so the batch can be inspected and reverted as one. Not re-entrant.
     */
    public <T> T withGroup(String batch, java.util.function.Supplier<T> body) {
        String previous = group;
        group = batch;
        try {
            return body.get();
        } finally {
            group = previous;
        }
    }

    /**
     * One change made by a batch on the administrator's behalf: already confirmed (the batch was), recorded as
     * {@code kind} in the current group. Same checks as every change — authority, parse, bounds, rules.
     */
    public AdminResult changeAs(AdminContext actor, Identifier settingId, String rawValue, AdminChangeRecord.Kind kind) {
        return attempt(actor, settingId, rawValue, kind, true);
    }

    /**
     * Reverts one recorded change: puts back the value it replaced. Refused like {@link #undoLast} when the setting has
     * moved on since (another change would be silently discarded) or the record is not an applied, not-undone change.
     */
    public AdminResult revert(AdminContext actor, long sequence) {
        if (!actor.canRead()) {
            return refuse(actor, NO_SETTING, AdminChangeRecord.Kind.REVERT, "", "", AdminRejection.UNAUTHORIZED, null);
        }
        Optional<AdminChangeRecord> found = history.find(sequence);
        if (found.isEmpty() || !found.get().applied() || found.get().undone()) {
            return AdminResult.rejected(NO_SETTING, AdminRejection.NOTHING_TO_UNDO, null, "");
        }
        AdminChangeRecord record = found.get();
        AdminSetting<?> setting = registry.get(record.settingId());
        if (setting == null) {
            return refuse(actor, record.settingId(), AdminChangeRecord.Kind.REVERT, "", record.oldValue(),
                    AdminRejection.UNKNOWN_SETTING, null);
        }
        if (setting.binding().available() && !setting.currentText().equals(record.newValue())) {
            return refuse(actor, setting.id(), AdminChangeRecord.Kind.REVERT, setting.currentText(),
                    record.oldValue(), AdminRejection.CONFLICT, UNDO_STALE);
        }
        AdminResult result = attemptTyped(actor, setting, record.oldValue(), AdminChangeRecord.Kind.REVERT, true);
        if (result.applied() || result.status() == AdminResult.Status.UNCHANGED) {
            history.markUndone(record.sequence());
        }
        return result;
    }

    /** Whether an applied, not-undone change is waiting for a restart to take effect. */
    public boolean restartPending() {
        return history.anyApplied(id -> {
            AdminSetting<?> setting = registry.get(id);
            return setting != null && setting.restartRequired();
        });
    }

    // ── the pipeline ──

    private List<AdminResult> resetEach(AdminContext actor, List<AdminSetting<?>> settings) {
        if (!actor.canRead()) {
            return List.of(refuse(actor, NO_SETTING, AdminChangeRecord.Kind.RESET, "", "",
                    AdminRejection.UNAUTHORIZED, null));
        }
        List<AdminResult> results = new ArrayList<>();
        for (AdminSetting<?> setting : settings) {
            // Client-scoped settings are not ours to reset, and one already at its default has nothing
            // to undo — skipping both keeps a section reset from filling the history with no-ops.
            if (!setting.writable() || !setting.binding().available() || setting.isDefault()) {
                continue;
            }
            // Section and full resets are confirmed by the caller (the dialog, `reset_all confirm`).
            results.add(attemptTyped(actor, setting, setting.defaultText(), AdminChangeRecord.Kind.RESET, true));
        }
        return results;
    }

    private AdminResult attempt(AdminContext actor, Identifier settingId, String rawValue,
                                AdminChangeRecord.Kind kind, boolean confirmed) {
        if (!actor.canRead()) {
            return refuse(actor, settingId, kind, "", rawValue, AdminRejection.UNAUTHORIZED, null);
        }
        AdminSetting<?> setting = registry.get(settingId);
        if (setting == null) {
            return refuse(actor, settingId, kind, "", rawValue, AdminRejection.UNKNOWN_SETTING, null);
        }
        return attemptTyped(actor, setting, rawValue, kind, confirmed);
    }

    /** Generic so {@code T} stays captured from parse to store without a cast. */
    private <T> AdminResult attemptTyped(AdminContext actor, AdminSetting<T> setting, String rawValue,
                                         AdminChangeRecord.Kind kind, boolean confirmed) {
        Identifier id = setting.id();
        if (!actor.canModify(setting.capability())) {
            // The value is withheld: an actor may be allowed to open the panel without this capability.
            return refuse(actor, id, kind, "", rawValue, AdminRejection.UNAUTHORIZED, null);
        }
        if (!setting.binding().available()) {
            return refuse(actor, id, kind, "", rawValue, AdminRejection.UNAVAILABLE, null);
        }
        String current = setting.currentText();
        if (!setting.writable()) {
            return refuse(actor, id, kind, current, rawValue, AdminRejection.CLIENT_ONLY, null);
        }
        T parsed = setting.type().parse(rawValue);
        if (parsed == null) {
            return refuse(actor, id, kind, current, rawValue, AdminRejection.INVALID_VALUE, null);
        }
        if (!setting.type().inBounds(parsed)) {
            return refuse(actor, id, kind, current, rawValue, AdminRejection.OUT_OF_RANGE, null);
        }
        String objection = setting.firstObjection(parsed, registry);
        if (objection != null) {
            return refuse(actor, id, kind, current, rawValue, AdminRejection.CONFLICT, objection);
        }

        T previous = setting.current();
        if (Objects.equals(previous, parsed)) {
            return AdminResult.unchanged(id, current, setting.restartRequired());
        }
        if (!confirmed) {
            String warning = setting.confirmationFor(previous, parsed);
            if (warning != null) {
                // A question, not a refusal: not recorded in history, nothing stored.
                return AdminResult.rejected(id, AdminRejection.CONFIRMATION_REQUIRED, warning, current);
            }
        }
        try {
            setting.binding().set(parsed);
        } catch (RuntimeException writeFailed) {
            LOGGER.error("[Admin] Could not store {} = {}", id, rawValue, writeFailed);
            return refuse(actor, id, kind, setting.currentText(), rawValue, AdminRejection.UNAVAILABLE, null);
        }

        // Read back rather than echo: the store is the authority on what it now holds.
        T now = setting.current();
        String nowText = setting.type().format(now);
        history.record(id, kind, current, nowText, actor.actorId(), actor.actorName(), clock.getAsLong(),
                true, null, group);
        LOGGER.info("[Admin] {} {} {}: {} -> {}", actor.actorName(), verb(kind), id, current, nowText);
        try {
            setting.notifyChanged(previous, now, actor);
        } catch (RuntimeException listenerFailed) {
            // The value is stored and recorded; a broken listener must not turn that into a reported failure.
            LOGGER.error("[Admin] Change listener for {} failed", id, listenerFailed);
        }
        AdminResult applied = AdminResult.applied(id, current, nowText, setting.restartRequired());
        for (BiConsumer<AdminContext, AdminResult> observer : observers) {
            try {
                observer.accept(actor, applied);
            } catch (RuntimeException observerFailed) {
                LOGGER.error("[Admin] Observer failed for {}", id, observerFailed);
            }
        }
        return applied;
    }

    private AdminResult refuse(AdminContext actor, Identifier id, AdminChangeRecord.Kind kind, String current,
                               String requested, AdminRejection why, @Nullable String detailKey) {
        history.record(id, kind, current, clip(requested), actor.actorId(), actor.actorName(), clock.getAsLong(),
                false, why, group);
        if (why == AdminRejection.UNAUTHORIZED) {
            LOGGER.warn("[Admin] Refused {} of {} from unauthorised {} ({})",
                    verb(kind), id, actor.actorName(), actor.actorId());
        } else {
            LOGGER.debug("[Admin] Refused {} of {} from {}: {}", verb(kind), id, actor.actorName(), why);
        }
        return AdminResult.rejected(id, why, detailKey, current);
    }

    private static String clip(String text) {
        return text.length() <= MAX_RECORDED_TEXT ? text : text.substring(0, MAX_RECORDED_TEXT) + "…";
    }

    private static String verb(AdminChangeRecord.Kind kind) {
        return switch (kind) {
            case CHANGE -> "set";
            case RESET -> "reset";
            case UNDO -> "undid";
            case PROFILE -> "set (profile)";
            case REVERT -> "reverted";
        };
    }
}
