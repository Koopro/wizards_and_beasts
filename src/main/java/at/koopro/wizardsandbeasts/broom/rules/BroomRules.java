package at.koopro.wizardsandbeasts.broom.rules;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The effective broom definitions: what the datapacks define, with the administrator's overrides and the server's
 * speed scale on top.
 *
 * <p><b>An overlay, not a second path</b> — the same shape as brewing. The loader hands the authored definitions here
 * ({@link #acceptAuthored}); this class writes the effective ones into {@link BroomDefinitionRegistry}, whose existing
 * sync sends them to every client. Flight runs on the rider's client from those synced definitions, so what the
 * server decides here is what every rider flies, and a broom in the world picks it up at once (its cached definition
 * is keyed by the registry's generation). Server state.
 */
@NullMarked
public final class BroomRules {

    private static Map<Identifier, BroomDefinition> authored = Map.of();
    private static Map<String, Map<BroomStat, Float>> overrides = Map.of();
    private static Set<String> disabled = Set.of();
    private static float speedScale = 1.0f;

    private BroomRules() {}

    public static synchronized void acceptAuthored(Map<Identifier, BroomDefinition> loaded) {
        authored = new LinkedHashMap<>(loaded);
        rebuild();
    }

    public static synchronized void publish(Map<String, Map<BroomStat, Float>> stats, Set<String> off, float scale) {
        Map<String, Map<BroomStat, Float>> copy = new HashMap<>();
        stats.forEach((id, values) -> copy.put(id, Map.copyOf(values)));
        overrides = copy;
        disabled = Set.copyOf(off);
        speedScale = scale;
        rebuild();
    }

    private static void rebuild() {
        Map<Identifier, BroomDefinition> effective = new HashMap<>();
        authored.forEach((key, definition) -> effective.put(key, effective(definition)));
        BroomDefinitionRegistry.replaceAll(effective);
    }

    /** One broom as it flies: its overrides applied, then the server's speed scale on its top speed. */
    public static BroomDefinition effective(BroomDefinition definition) {
        Map<BroomStat, Float> values = new EnumMap<>(BroomStat.class);
        values.putAll(overrides.getOrDefault(definition.id().toString(), Map.of()));
        if (speedScale != 1.0f) {
            float base = values.getOrDefault(BroomStat.MAX_SPEED, definition.maxSpeed());
            values.put(BroomStat.MAX_SPEED, base * speedScale);
        }
        return BroomStat.apply(definition, values);
    }

    public static @Nullable BroomDefinition authored(Identifier id) {
        for (BroomDefinition definition : authored.values()) {
            if (definition.id().equals(id)) {
                return definition;
            }
        }
        return null;
    }

    public static java.util.Collection<BroomDefinition> authoredAll() {
        return java.util.List.copyOf(authored.values());
    }

    public static Map<BroomStat, Float> overridesFor(String id) {
        return overrides.getOrDefault(id, Map.of());
    }

    /** Whether this broom may be deployed or mounted. On unless an administrator withdrew it. */
    public static boolean enabled(@Nullable Identifier id) {
        return id == null || !disabled.contains(id.toString());
    }

    public static float speedScale() {
        return speedScale;
    }

    /** Set on a client connected to someone else's server (see {@code BroomRulesClient}). */
    private static volatile boolean remoteServer;

    public static void setRemoteServer(boolean remote) {
        remoteServer = remote;
    }

    /**
     * The rider's own broom-speed preference, as flight may use it: freely on their own world, but on a remote
     * server only to slow a broom down — never above 1.
     */
    public static float personalSpeedMultiplier(float configured) {
        return remoteServer ? Math.min(1.0f, configured) : configured;
    }
}
