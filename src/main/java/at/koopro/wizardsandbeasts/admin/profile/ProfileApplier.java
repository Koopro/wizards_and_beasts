package at.koopro.wizardsandbeasts.admin.profile;

import at.koopro.wizardsandbeasts.admin.AdminRejection;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.history.AdminChangeRecord;
import at.koopro.wizardsandbeasts.admin.profile.ProfileCodec.Issue;
import at.koopro.wizardsandbeasts.admin.profile.ProfileValidator.Change;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.ModuleStateService;
import at.koopro.wizardsandbeasts.module.profile.ModuleProfilePlanner;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a list of changes all-or-nothing, through the ordinary {@link AdminSettingService} door — every change gets
 * the same authority, parse, bounds and rule checks as one typed by hand, and lands in the history under one group.
 *
 * <ol>
 *   <li>Stale check: every setting must still hold the value the plan was made from; otherwise nothing is touched.</li>
 *   <li>Order: module switches first, in the order {@link ModuleProfilePlanner} gives (dependants close before their
 *       base, bases open before their dependants), then everything else.</li>
 *   <li>Passes: a change refused by a cross-setting rule (CONFLICT — "the last selectable heritage") waits for the next
 *       pass, because another change in the batch may be what makes it legal. Any other refusal is final.</li>
 *   <li>If anything is still refused, every change already made is put back (also in passes), and the result says
 *       so. The datapack reload module switches need runs once, at the end.</li>
 * </ol>
 */
@NullMarked
public final class ProfileApplier {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_PASSES = 6;

    /**
     * @param applied        whether the whole batch is now in place
     * @param changed        how many values changed (0 when rolled back)
     * @param failures       why it was refused (stale values, final refusals)
     * @param rollbackFailed values that could not be put back — should never happen; logged at ERROR if it does
     */
    public record Outcome(boolean applied, int changed, List<Issue> failures, List<Issue> rollbackFailed, String group) {}

    private ProfileApplier() {}

    public static Outcome apply(AdminSettingService service, AdminContext actor, List<Change> changes, String group,
                                AdminChangeRecord.Kind kind, @Nullable MinecraftServer server) {
        List<Issue> stale = new ArrayList<>();
        for (Change change : changes) {
            AdminSetting<?> setting = service.registry().get(change.id());
            if (setting == null) {
                stale.add(new Issue("unknown_setting", change.id().toString(), ""));
            } else if (setting.binding().available() && !setting.currentText().equals(change.from())) {
                stale.add(new Issue("stale", change.id().toString(), setting.currentText()));
            }
        }
        if (!stale.isEmpty()) {
            return new Outcome(false, 0, stale, List.of(), group);
        }

        List<Change> ordered = order(changes);
        boolean touchesModules = ordered.stream().anyMatch(c -> c.module() != null);
        Outcome outcome = ModuleStateService.withoutDatapackReload(() -> service.withGroup(group, () -> {
            List<Change> done = new ArrayList<>();
            List<Issue> failures = run(service, actor, ordered, kind, done, false);
            if (failures.isEmpty()) {
                return new Outcome(true, done.size(), List.of(), List.of(), group);
            }
            List<Change> undo = new ArrayList<>();
            for (int i = done.size() - 1; i >= 0; i--) {
                Change c = done.get(i);
                undo.add(new Change(c.id(), c.to(), c.from(), c.applyMode(), c.module()));
            }
            List<Issue> rollbackFailed = run(service, actor, order(undo), AdminChangeRecord.Kind.REVERT, new ArrayList<>(), true);
            if (!rollbackFailed.isEmpty()) {
                LOGGER.error("[Admin] Rolling back {} left {} value(s) changed: {}", group, rollbackFailed.size(), rollbackFailed);
            }
            // The batch did not happen: its records are consumed so neither undo nor a group revert replays them.
            for (AdminChangeRecord record : service.history().inGroup(group)) {
                if (record.undoable() || record.kind() == AdminChangeRecord.Kind.PROFILE) {
                    service.history().markUndone(record.sequence());
                }
            }
            return new Outcome(false, 0, failures, rollbackFailed, group);
        }));
        if (touchesModules && server != null) {
            ModuleStateService.reloadAfterBatch(server);
        }
        LOGGER.info("[Admin] {} {} by {}: {} change(s){}", outcome.applied() ? "Applied" : "Refused", group,
                actor.actorName(), outcome.changed(), outcome.failures().isEmpty() ? "" : " — " + outcome.failures());
        return outcome;
    }

    /**
     * Applies in passes; {@code done} collects what changed. Returns the refusals left at the end (empty = all in
     * place). Applying stops at the first final refusal; rolling back keeps going so as much as possible is restored.
     */
    private static List<Issue> run(AdminSettingService service, AdminContext actor, List<Change> changes,
                                   AdminChangeRecord.Kind kind, List<Change> done, boolean rollingBack) {
        List<Change> pending = new ArrayList<>(changes);
        List<Issue> hard = new ArrayList<>();
        List<Issue> conflicts = new ArrayList<>();
        for (int pass = 0; pass < MAX_PASSES && !pending.isEmpty(); pass++) {
            List<Change> retry = new ArrayList<>();
            conflicts.clear();
            boolean progress = false;
            for (Change change : pending) {
                AdminResult result = service.changeAs(actor, change.id(), change.to(), kind);
                if (result.applied()) {
                    done.add(change);
                    progress = true;
                } else if (result.status() == AdminResult.Status.UNCHANGED) {
                    progress = true;
                } else if (result.rejection() == AdminRejection.CONFLICT) {
                    retry.add(change);
                    conflicts.add(new Issue("conflict", change.id().toString(),
                            result.detailKey() == null ? "" : result.detailKey()));
                } else {
                    hard.add(new Issue(result.rejection() == null ? "refused"
                            : result.rejection().name().toLowerCase(java.util.Locale.ROOT), change.id().toString(), change.to()));
                    if (!rollingBack) {
                        return List.copyOf(hard);
                    }
                }
            }
            pending = retry;
            if (!progress) {
                break;
            }
        }
        List<Issue> left = new ArrayList<>(hard);
        if (!pending.isEmpty()) {
            left.addAll(conflicts);
        }
        return List.copyOf(left);
    }

    /** Module switches first in dependency-safe order, then the rest in the order given. */
    static List<Change> order(List<Change> changes) {
        Map<Module, ModuleState> current = new EnumMap<>(ModuleManager.snapshot());
        Map<Module, ModuleState> target = new EnumMap<>(Module.class);
        Map<Module, Change> byModule = new EnumMap<>(Module.class);
        List<Change> others = new ArrayList<>();
        for (Change change : changes) {
            ModuleState state = change.module() == null ? null : ModuleState.parse(change.to());
            if (change.module() != null && state != null) {
                target.put(change.module(), state);
                byModule.put(change.module(), change);
            } else {
                others.add(change);
            }
        }
        List<Change> out = new ArrayList<>();
        for (ModuleProfilePlanner.Step step : ModuleProfilePlanner.plan(current, target).steps()) {
            Change change = byModule.remove(step.module());
            if (change != null) {
                out.add(change);
            }
        }
        out.addAll(byModule.values());
        out.addAll(others);
        return out;
    }
}
