package at.koopro.wizardsandbeasts.ability.grant;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of {@link AbilityGrantSource}s that {@link AbilityGrantService} iterates. Built-ins (heritage,
 * vocation, skill-node, debug) are registered on class-init; future grantors call {@link #register}. Sources
 * are pure reads, so registration order only affects nothing but the (already source-bucketed) merge order.
 */
@NullMarked
public final class AbilityGrantSources {

    private static final List<AbilityGrantSource> SOURCES = new CopyOnWriteArrayList<>();

    static {
        register(new HeritageAbilityGrantSource());
        register(new VocationAbilityGrantSource());
        register(new SkillNodeAbilityGrantSource());
        register(DebugAbilityGrantSource.INSTANCE);
    }

    private AbilityGrantSources() {}

    public static void register(AbilityGrantSource source) {
        SOURCES.add(source);
    }

    public static List<AbilityGrantSource> all() {
        return List.copyOf(SOURCES);
    }
}
