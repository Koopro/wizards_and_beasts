package at.koopro.wizardsandbeasts.disguise;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Turning a typed name into somebody to look like.
 *
 * <h2>Why a name is enough</h2>
 * <p>The whole point of a disguise is to wear a face that is not in the room. Requiring an online
 * player — which {@code EntityArgument.player()} does, and which is how the Polyjuice sample command
 * used to work — makes the feature useless for exactly the case it exists for. So the input here is a
 * bare string, and this class is what makes that survivable.
 *
 * <h2>Three ways a name resolves, in order</h2>
 * <ol>
 *   <li><b>Online here.</b> Answered on the server thread with no lookup at all. This is the common
 *       case and it must not pay for the uncommon one.</li>
 *   <li><b>A real Mojang account.</b> {@link net.minecraft.server.players.ProfileResolver} answers,
 *       and its answer is cached for ten minutes and persisted in {@code usercache.json}. Note that
 *       {@code GameProfileCache} no longer exists on 1.21.11 — {@code ProfileResolver} replaced it.</li>
 *   <li><b>Nothing.</b> An invented name, a typo, or an offline-mode server with no internet. The
 *       name still resolves, to {@link UUIDUtil#createOfflinePlayerUUID} — the same derivation an
 *       offline-mode server uses. The nameplate is then correct and the body falls back to the
 *       default skin that UUID hashes to, which is the honest rendering of "there is no such
 *       account" and is a great deal better than refusing.</li>
 * </ol>
 *
 * <h2>Threading</h2>
 * <p>{@code ProfileResolver.Cached.fetchByName} is a blocking HTTP call behind a Guava
 * {@code LoadingCache}: on a cold cache it goes to Mojang, and calling it from the server thread
 * would stall every player on the server for the length of that round trip. It runs on
 * {@link Util#backgroundExecutor()}, and the callback is handed back through
 * {@link MinecraftServer#execute} so callers never have to think about which thread they are on —
 * {@code onResolved} always runs on the server thread.
 */
@NullMarked
public final class DisguiseTargetResolver {

    /**
     * Somebody to look like.
     *
     * @param authentic whether a real account was found. Callers use it only to phrase their feedback
     *                  — a disguise works either way, and the renderer neither receives this nor
     *                  needs to
     */
    public record Target(java.util.UUID id, String name, boolean authentic) {}

    private DisguiseTargetResolver() {}

    /**
     * Resolve a typed name, then run {@code onResolved} on the server thread.
     *
     * <p>Never fails: a name that resolves to no account still produces a {@link Target}. An
     * <em>invalid</em> name — over sixteen characters, or containing a space or a control character —
     * is the one case that does not call back, because it can never be anybody and asking Mojang
     * about it would be a wasted round trip. Callers validate before calling; {@link #isUsableName}
     * is the same check.
     */
    public static void resolve(MinecraftServer server, String rawName, Consumer<Target> onResolved) {
        String name = rawName.strip();
        if (!isUsableName(name)) {
            return;
        }

        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            // The name as the account spells it, not as the operator typed it: getPlayerByName is
            // case-insensitive, so "notch" would otherwise hang the wrong capitalisation on a nameplate.
            onResolved.accept(new Target(online.getUUID(), online.getGameProfile().name(), true));
            return;
        }

        Util.backgroundExecutor().forName("wandbDisguiseProfileLookup").execute(() -> {
            Target target = lookup(server, name);
            // Back onto the server thread before anything touches the world. Also re-entrant-safe:
            // if the server is shutting down, execute() drops the task rather than running it on a
            // dead level.
            server.execute(() -> onResolved.accept(target));
        });
    }

    /** Whether a string could name any player at all. */
    public static boolean isUsableName(String name) {
        return !name.isEmpty() && StringUtil.isValidPlayerName(name);
    }

    private static Target lookup(MinecraftServer server, String name) {
        Optional<GameProfile> profile;
        try {
            profile = server.services().profileResolver().fetchByName(name);
        } catch (RuntimeException offlineOrRateLimited) {
            // Guava's LoadingCache wraps a loader failure in an UncheckedExecutionException, and the
            // loader here is a network call. A server with no internet must still be able to disguise
            // somebody — falling through to the offline UUID is what makes that true.
            profile = Optional.empty();
        }
        return profile
                .map(found -> new Target(found.id(), found.name(), true))
                .orElseGet(() -> new Target(UUIDUtil.createOfflinePlayerUUID(name), name, false));
    }
}
