package at.koopro.wizardsandbeasts.admin.config;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A family of settings resolved on demand rather than registered up front.
 *
 * <p>For things whose members change with datapacks — one setting per spell and property — registering every
 * member at startup would freeze a list that {@code /reload} can invalidate. A provider answers "is this id one
 * of mine, and what is it right now?" each time the service asks, so the answer always reflects the live
 * registry. The returned setting goes through exactly the same authorisation, parsing, bounds, rules,
 * confirmation and history as a static one.
 */
@NullMarked
public interface AdminSettingProvider {

    /** The setting with this id, built from live state, or null when the id is not this provider's. */
    @Nullable AdminSetting<?> resolve(Identifier id);

    /**
     * Every id this provider can resolve right now — for profiles and snapshots, which need the whole server
     * configuration rather than one panel's slice. An id listed here that no longer resolves is skipped by the
     * caller. {@code server} is null outside a running server (a unit test); providers that need it return nothing.
     */
    default java.util.Collection<Identifier> enumerate(net.minecraft.server.@Nullable MinecraftServer server) {
        return java.util.List.of();
    }
}
