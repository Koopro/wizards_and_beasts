package at.koopro.wizardsandbeasts.skill.vocation;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Orchestrates Vocation declare / clear. Pure queries live in {@link VocationHelper}; this class
 * owns the mutations.
 *
 * <p>Web rework Phase 3: Vocation is declarative identity only. The mastery-band, opposition, and
 * secondary-slot mechanics are deleted — declaring gates nothing and refunds nothing; the web's
 * travel cost under the point cap carries the specialization tradeoff. The commitment stat profile
 * ({@code commitmentEffects}) and {@code grantedAbilities} flags are unchanged by this phase.
 */
@NullMarked
public final class VocationManager {

    /** Outcome of a declare attempt — every distinct rejection reason the command surfaces. */
    public enum CommitResult {
        OK,
        MODULE_DISABLED,
        UNKNOWN_VOCATION,
        DARK_ARTS_DISABLED
    }

    private VocationManager() {}

    public static CommitResult commit(ServerPlayer player, Identifier vocationId) {
        if (!ModuleManager.isEnabled(Module.SKILL_TREES)) {
            return CommitResult.MODULE_DISABLED;
        }
        VocationDefinition vocation = VocationRegistry.get(vocationId);
        if (vocation == null) {
            return CommitResult.UNKNOWN_VOCATION;
        }
        if (vocation.tree() == SkillTreeId.DARK_ARTS && !ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return CommitResult.DARK_ARTS_DISABLED;
        }

        PlayerVocationData updated = VocationHelper.getData(player).withPrimary(Optional.of(vocationId));
        player.setData(ModAttachments.VOCATION_DATA.get(), updated);

        // Rebuild the commitment profile from scratch to stay idempotent. grantedAbilities flags
        // are consumed live by VocationAbilityHooks.
        VocationEffectApplicator.removeAll(player);
        updated.primary().map(VocationRegistry::get)
                .ifPresent(v -> VocationEffectApplicator.apply(player, v));

        PlayerStateSyncService.syncVocations(player);
        return CommitResult.OK;
    }

    /** Admin teardown: strip the commitment profile, clear the declaration, resync. */
    public static void clear(ServerPlayer player) {
        VocationEffectApplicator.removeAll(player);
        player.setData(ModAttachments.VOCATION_DATA.get(), PlayerVocationData.EMPTY);
        PlayerStateSyncService.syncVocations(player);
    }

    /** The declared Vocation (definition), or null if none / undefined. */
    public static @Nullable VocationDefinition committed(ServerPlayer player) {
        return VocationHelper.getPrimary(player).map(VocationRegistry::get).orElse(null);
    }
}
