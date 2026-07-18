package at.koopro.wizardsandbeasts.client.ability.state;

import at.koopro.wizardsandbeasts.ability.grant.AbilityGrants;
import at.koopro.wizardsandbeasts.ability.grant.AbilityKey;
import at.koopro.wizardsandbeasts.network.skill.AbilityGrantsSyncS2CPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Set;

/**
 * Client-side mirror of the server's derived {@link AbilityGrants}, populated by
 * {@link AbilityGrantsSyncS2CPayload}. There is no client consumer yet ({@code §3.3 no content}); this
 * exists so future client UI can query held abilities off synced truth rather than re-deriving.
 *
 * <p>Deliberately class-init-safe (only common-typed static state, no Minecraft references at load time)
 * so the {@code ::handle} method reference can be registered from the common
 * {@link at.koopro.wizardsandbeasts.network.skill.ModNetworkSkills} without loading a client class on a
 * dedicated server — the handler body only ever runs client-side.
 */
public final class ClientAbilityGrantState {

    private static volatile AbilityGrants current = AbilityGrants.EMPTY;
    private static volatile int lastSyncVersion = -1;

    private ClientAbilityGrantState() {}

    /** Payload entry point (registered as {@code playToClient} handler); runs on the client only. */
    public static void handle(AbilityGrantsSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> apply(payload));
    }

    private static void apply(AbilityGrantsSyncS2CPayload payload) {
        if (payload.syncVersion() < lastSyncVersion) {
            return; // stale/out-of-order packet
        }
        lastSyncVersion = payload.syncVersion();
        current = AbilityGrants.of(payload.heritage(), payload.vocation(), payload.skillNode());
    }

    public static AbilityGrants get() {
        return current;
    }

    public static boolean hasAbility(AbilityKey key) {
        return current.has(key);
    }

    public static Set<AbilityGrants.Source> sourcesOf(AbilityKey key) {
        return current.sourcesOf(key);
    }

    public static void clear() {
        current = AbilityGrants.EMPTY;
        lastSyncVersion = -1;
    }
}
