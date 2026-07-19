package at.koopro.wizardsandbeasts.ability.grant;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * A pluggable contributor to a player's {@linkplain AbilityGrants derived grant snapshot}. Framework
 * extension point (§2.2): future grantors (Animagus status, werewolf status, item, ritual) register a
 * source without touching {@link AbilityGrantService}. Each source reads only already-persistent player
 * state and returns raw ability-key strings for its {@link AbilityGrants.Source} bucket; the resolver
 * normalizes and merges them. Sources must be pure reads — no mutation, no side effects.
 */
@NullMarked
public interface AbilityGrantSource {

    /** The bucket these grants are attributed to in the merged snapshot. */
    AbilityGrants.Source source();

    /** Raw ability-key strings this source currently grants {@code player}; empty if none. */
    List<String> grantsFor(ServerPlayer player);
}
