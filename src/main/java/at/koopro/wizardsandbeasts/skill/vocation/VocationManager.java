package at.koopro.wizardsandbeasts.skill.vocation;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.build.Archetype;
import at.koopro.wizardsandbeasts.skill.build.BuildRules;
import at.koopro.wizardsandbeasts.skill.build.Builds;
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
        DARK_ARTS_DISABLED,
        /** The build's mechanics live in a module that is switched off. */
        BUILD_SEALED,
        /** The player holds another build's capstone; their vocation follows their mastery. */
        MASTERY_HELD
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
        if (vocation.archetype().filter(a -> !BuildRules.modulesOn(a, ModuleManager::isEnabled)).isPresent()) {
            return CommitResult.BUILD_SEALED;
        }
        // Identity follows mastery. A declaration used to be free to swap at will, which made a vocation's
        // label say nothing about the wizard wearing it. Once a capstone is held the declaration is that build's
        // until the capstone is unlearned — and allocating the capstone declares it (see followMastery).
        var held = BuildRules.heldCapstone(SkillSystemAPI.getSkillData(player).getUnlockedSkills(), Builds.all());
        if (held.isPresent() && !held.get().id().equals(vocationId)) {
            return CommitResult.MASTERY_HELD;
        }

        PlayerVocationData updated = VocationHelper.getData(player).withPrimary(Optional.of(vocationId));
        player.setData(ModAttachments.VOCATION_DATA.get(), updated);

        // Rebuild the commitment profile from scratch to stay idempotent. grantedAbilities flags
        // are consumed live by VocationAbilityHooks.
        VocationEffectApplicator.removeAll(player);
        updated.primary().map(VocationRegistry::get)
                .ifPresent(v -> VocationEffectApplicator.apply(player, v));

        PlayerStateSyncService.syncVocations(player);
        PlayerStateSyncService.syncAbilityGrants(player); // declaration adds VOCATION-source grants
        return CommitResult.OK;
    }

    /**
     * Declares the build whose capstone was just allocated, so the sheet, the chat card and the vocation agree. A
     * refusal here (module sealed between the check and now) leaves the old declaration alone.
     */
    public static void followMastery(ServerPlayer player, Archetype archetype) {
        if (!VocationHelper.getPrimary(player).filter(archetype.id()::equals).isPresent()) {
            commit(player, archetype.id());
        }
    }

    /** Admin teardown: strip the commitment profile, clear the declaration, resync. */
    public static void clear(ServerPlayer player) {
        VocationEffectApplicator.removeAll(player);
        player.setData(ModAttachments.VOCATION_DATA.get(), PlayerVocationData.EMPTY);
        PlayerStateSyncService.syncVocations(player);
        PlayerStateSyncService.syncAbilityGrants(player); // clear strips exactly the VOCATION-source grants
    }

    /** The declared Vocation (definition), or null if none / undefined. */
    public static @Nullable VocationDefinition committed(ServerPlayer player) {
        return VocationHelper.getPrimary(player).map(VocationRegistry::get).orElse(null);
    }
}
