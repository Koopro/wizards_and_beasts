package at.koopro.wizardsandbeasts.admin;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dangerous changes a command asked for and the server held back, waiting for {@code /wandb admin config confirm}.
 *
 * <p>The panel confirms in a dialog and resends; a command cannot, because its value argument is greedy. So
 * the held request is kept here, per actor, for a short while, and replayed with confirmation by the confirm
 * command — through the same {@link AdminSettingService} call, so nothing about it is re-decided differently.
 * One pending batch per actor; a newer one replaces it. Server thread only.
 */
@NullMarked
public final class AdminConfirmations {

    public static final long WINDOW_MILLIS = 60_000L;

    /** One held request: a change to {@code value}, or a reset when {@code value} is null. */
    public record Action(Identifier settingId, @Nullable String value) {}

    private record Pending(List<Action> actions, long expiresAt) {}

    private static final Map<String, Pending> PENDING = new HashMap<>();

    private AdminConfirmations() {}

    public static void hold(AdminContext actor, List<Action> actions, long nowMillis) {
        if (!actions.isEmpty()) {
            PENDING.put(key(actor), new Pending(List.copyOf(actions), nowMillis + WINDOW_MILLIS));
        }
    }

    /** Replays the actor's held batch with confirmation, or returns an empty list when there is none (or it expired). */
    public static List<AdminResult> confirm(AdminContext actor, AdminSettingService service, long nowMillis) {
        Pending pending = PENDING.remove(key(actor));
        if (pending == null || pending.expiresAt() < nowMillis) {
            return List.of();
        }
        List<AdminResult> results = new ArrayList<>();
        for (Action action : pending.actions()) {
            results.add(action.value() == null
                    ? service.reset(actor, action.settingId(), true)
                    : service.change(actor, action.settingId(), action.value(), true));
        }
        return results;
    }

    public static void clear() {
        PENDING.clear();
    }

    private static String key(AdminContext actor) {
        return actor.actorId() != null ? actor.actorId().toString() : "console:" + actor.actorName();
    }
}
