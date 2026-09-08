package at.koopro.wizardsandbeasts.ability.debug;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.def.AbilityDefinitionRegistry;
import at.koopro.wizardsandbeasts.ability.grant.DebugAbilityGrantSource;
import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Every registered ability, granted from the source that exists to be taken away again.
 *
 * <p>Grants come from five sources — heritage, vocation, skill node, status effect and
 * {@code DEBUG} — and only the last is revocable by hand. Granting through
 * {@link DebugAbilityGrantSource} therefore means {@code reset} can undo exactly what {@code open}
 * did, and nothing else: a player who had Apparition from their heritage keeps it, because this kit
 * never touched that grant.
 *
 * <p>That is also why the read-only section beside this one prints the source set per ability. After
 * running this, "why can they do that" has a debug answer and a real one, and they are
 * distinguishable.
 */
@NullMarked
public final class AbilityDevKit implements FeatureDevKit {

    @Override
    public String id() {
        return "abilities";
    }

    @Override
    public String title() {
        return "Abilities";
    }

    @Override
    public String summary() {
        return "Grant every registered ability from the DEBUG source, so reset can take them back.";
    }

    @Override
    public void open(ServerPlayer target, DevLog log) {
        if (AbilityDefinitionRegistry.size() == 0) {
            log.warn("no ability definitions loaded - the datapack listener has not run");
            return;
        }
        int granted = 0;
        for (AbilityDefinition definition : AbilityDefinitionRegistry.getAll()) {
            if (DebugAbilityGrantSource.INSTANCE.grant(target, definition.id().toString())) {
                granted++;
            }
        }
        resync(target);
        if (granted == 0) {
            log.skip("all " + AbilityDefinitionRegistry.size() + " abilities already granted");
        } else {
            log.changed("abilities granted", granted + " of " + AbilityDefinitionRegistry.size());
        }
    }

    @Override
    public void reset(ServerPlayer target, DevLog log) {
        int revoked = 0;
        for (AbilityDefinition definition : AbilityDefinitionRegistry.getAll()) {
            if (DebugAbilityGrantSource.INSTANCE.revoke(target, definition.id().toString())) {
                revoked++;
            }
        }
        resync(target);
        if (revoked == 0) {
            log.skip("no DEBUG-sourced grants to revoke");
        } else {
            log.changed("debug grants revoked", revoked);
        }
    }

    /**
     * Grants and selection both, because they are two attachments describing one thing.
     *
     * <p>Syncing grants alone leaves a client whose wheel still lists an ability it no longer has —
     * the same pairing {@code /wandb player ability grant} does.
     */
    private static void resync(ServerPlayer target) {
        PlayerStateSyncService.syncAbilityGrants(target);
        PlayerStateSyncService.syncAbilities(target);
    }
}
