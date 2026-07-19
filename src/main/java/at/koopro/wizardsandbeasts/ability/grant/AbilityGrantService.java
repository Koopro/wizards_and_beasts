package at.koopro.wizardsandbeasts.ability.grant;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side query surface for the {@linkplain AbilityGrants source-tracked grant layer}. Grants are
 * <b>derived, not stored</b> (§3.1): every query recomputes {@link AbilityGrants} from the player's three
 * persistent grantors, so there is no cache to persist, migrate, or let drift. Recompute is cheap (a few
 * map lookups over allocated nodes); the only thing that "invalidates" is the client mirror, which is
 * re-pushed on the mutation seams via {@link at.koopro.wizardsandbeasts.network.skill.AbilityGrantsSyncS2CPayload}.
 *
 * <p>Grantors:
 * <ul>
 *   <li>{@code HERITAGE} — the committed variant's tags, exposed read-only (dead tags stay dead: exposed
 *       but nothing consumes them; heritage internals are untouched).</li>
 *   <li>{@code VOCATION} — the declared vocation's {@code grantedAbilities}.</li>
 *   <li>{@code SKILL_NODE} — allocated nodes' {@link SkillEffect.GrantAbility} (new) and the legacy
 *       {@link SkillEffect.UnlockAbility} strings, bridged so existing node abilities are source-tracked.</li>
 * </ul>
 */
@NullMarked
public final class AbilityGrantService {

    private AbilityGrantService() {}

    /**
     * Recomputes the full grant snapshot by iterating every registered {@link AbilityGrantSource}. Sources
     * are pluggable (§2.2): the resolver no longer hardcodes the grantor list — it merges whatever
     * {@link AbilityGrantSources#all()} exposes.
     */
    public static AbilityGrants compute(ServerPlayer player) {
        Map<AbilityGrants.Source, List<String>> bySource = new EnumMap<>(AbilityGrants.Source.class);
        for (AbilityGrantSource src : AbilityGrantSources.all()) {
            List<String> grants = src.grantsFor(player);
            if (grants.isEmpty()) {
                continue;
            }
            bySource.computeIfAbsent(src.source(), s -> new ArrayList<>()).addAll(grants);
        }
        return AbilityGrants.ofSources(bySource);
    }

    public static boolean hasAbility(ServerPlayer player, AbilityKey key) {
        return compute(player).has(key);
    }

    public static Set<AbilityGrants.Source> sourcesOf(ServerPlayer player, AbilityKey key) {
        return compute(player).sourcesOf(key);
    }

    public static boolean hasFromSource(ServerPlayer player, AbilityKey key, AbilityGrants.Source source) {
        return compute(player).hasFrom(key, source);
    }
}
