package at.koopro.wizardsandbeasts.skill.vocation;

import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.Skill;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Pure query helpers over {@link PlayerVocationData} + {@link VocationRegistry}. {@link #unlockState} is the
 * single source of truth for the Mastery-cap / opposition lockout (the §5 mechanic); it is the only thing the
 * {@code SkillSystemAPI.evaluateUnlock} gate consults.
 */
@NullMarked
public final class VocationHelper {

    /** Whether a node sits in the open Foundation band or the commitment-gated Mastery band. */
    public enum Band { FOUNDATION, MASTERY }

    /** Outcome of evaluating a node against the player's committed Vocations. */
    public enum UnlockState {
        ALLOWED,
        LOCKED_OPPOSED,
        LOCKED_NOT_COMMITTED,
        LOCKED_CAPPED_SECONDARY,
        LOCKED_CAPSTONE
    }

    private VocationHelper() {}

    public static PlayerVocationData getData(Player player) {
        return player.getData(ModAttachments.VOCATION_DATA.get());
    }

    public static Optional<Identifier> getPrimary(Player player) {
        return getData(player).primary();
    }

    public static Optional<Identifier> getSecondary(Player player) {
        return getData(player).secondary();
    }

    public static boolean isCommitted(Player player, Identifier vocationId) {
        PlayerVocationData data = getData(player);
        return data.primary().filter(vocationId::equals).isPresent()
                || data.secondary().filter(vocationId::equals).isPresent();
    }

    /** True if either committed Vocation grants the named ability flag (see VocationAbilityHooks). */
    public static boolean hasGrantedAbility(Player player, String flag) {
        PlayerVocationData data = getData(player);
        return grantsFlag(data.primary(), flag) || grantsFlag(data.secondary(), flag);
    }

    private static boolean grantsFlag(Optional<Identifier> vocationId, String flag) {
        return vocationId.map(VocationRegistry::get)
                .map(v -> v.grantedAbilities().contains(flag))
                .orElse(false);
    }

    /** The Vocation owning this node's tree, or null if no Vocation owns it (e.g. goblin / elf trees). */
    public static @Nullable VocationDefinition vocationOf(Skill node) {
        return VocationRegistry.forTree(node.getTree());
    }

    /** Foundation when no Vocation owns the tree, or when {@code tier <= foundationMaxTier}. */
    public static Band band(Skill node) {
        VocationDefinition vocation = vocationOf(node);
        if (vocation == null) {
            return Band.FOUNDATION;
        }
        return node.getTier() <= vocation.foundationMaxTier() ? Band.FOUNDATION : Band.MASTERY;
    }

    private static boolean isCapstone(VocationDefinition vocation, Skill node) {
        return vocation.capstoneNodeId()
                .map(id -> id.getPath().equals(node.getId()))
                .orElse(false);
    }

    private static boolean isSlot(Optional<Identifier> slot, Identifier vocationId) {
        return slot.filter(vocationId::equals).isPresent();
    }

    private static boolean opposedToSlot(Identifier vocationId, Optional<Identifier> slot) {
        return slot.map(committed -> VocationRegistry.areOpposed(vocationId, committed)).orElse(false);
    }

    /**
     * The §5 ruleset, evaluated in order for node {@code N} with owning Vocation {@code V}:
     * opposition (hard lock incl. Foundation) → Foundation open → capstone (primary only) →
     * Mastery (primary full / secondary first-Mastery-tier-only / else not committed).
     */
    public static UnlockState unlockState(Player player, Skill node) {
        PlayerVocationData data = getData(player);
        return unlockState(data.primary(), data.secondary(), node);
    }

    /**
     * Pure core of the §5 ruleset, independent of {@link Player} — committed slots are passed directly so the
     * mechanic is unit-testable. {@link #unlockState(Player, Skill)} is the live wrapper.
     */
    public static UnlockState unlockState(Optional<Identifier> primary, Optional<Identifier> secondary, Skill node) {
        VocationDefinition vocation = vocationOf(node);
        if (vocation == null) {
            return UnlockState.ALLOWED; // tree not owned by any Vocation — ungated
        }

        Identifier vocationId = vocation.id();

        // 1. Opposition — hard lock, Foundation included.
        if (opposedToSlot(vocationId, primary) || opposedToSlot(vocationId, secondary)) {
            return UnlockState.LOCKED_OPPOSED;
        }

        // 2. Foundation — open to all.
        if (band(node) == Band.FOUNDATION) {
            return UnlockState.ALLOWED;
        }

        // 3. Capstone — only the committed primary may take it.
        if (isCapstone(vocation, node)) {
            return isSlot(primary, vocationId) ? UnlockState.ALLOWED : UnlockState.LOCKED_CAPSTONE;
        }

        // 4. Mastery.
        if (isSlot(primary, vocationId)) {
            return UnlockState.ALLOWED;
        }
        if (isSlot(secondary, vocationId)) {
            return node.getTier() == vocation.foundationMaxTier() + 1
                    ? UnlockState.ALLOWED
                    : UnlockState.LOCKED_CAPPED_SECONDARY;
        }
        return UnlockState.LOCKED_NOT_COMMITTED;
    }
}
