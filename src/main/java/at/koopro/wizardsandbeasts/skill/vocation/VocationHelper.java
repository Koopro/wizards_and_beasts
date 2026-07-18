package at.koopro.wizardsandbeasts.skill.vocation;

import at.koopro.wizardsandbeasts.ability.grant.AbilityGrantService;
import at.koopro.wizardsandbeasts.ability.grant.AbilityGrants;
import at.koopro.wizardsandbeasts.ability.grant.AbilityKey;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.Skill;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Pure query helpers over {@link PlayerVocationData} + {@link VocationRegistry}.
 *
 * <p>Since the web rework Phase 3, Vocation is declarative identity only — the mastery-band /
 * opposition enforcement that used to live here (and gate {@code SkillSystemAPI.evaluateUnlock})
 * is deleted; the web's travel cost under the point cap does that differentiation. What remains:
 * declaration queries, the {@link #vocationOf} home-region mapping (the hook the future
 * in-region-bonus prompt consumes), and the granted-ability flag query.
 */
@NullMarked
public final class VocationHelper {

    private VocationHelper() {}

    public static PlayerVocationData getData(Player player) {
        return player.getData(ModAttachments.VOCATION_DATA.get());
    }

    public static Optional<Identifier> getPrimary(Player player) {
        return getData(player).primary();
    }

    public static boolean isCommitted(Player player, Identifier vocationId) {
        return getData(player).primary().filter(vocationId::equals).isPresent();
    }

    /**
     * True if the declared Vocation grants the named ability flag (see VocationAbilityHooks).
     *
     * <p>Re-plumbed onto the source-tracked {@link AbilityGrants} layer (proof migration #1): the server
     * path now resolves through {@link AbilityGrantService} as a {@code VOCATION}-source query. Behavior is
     * identical — the layer computes vocation grants from the same primary vocation's {@code grantedAbilities}
     * — but the grant is now revoke-safe by derivation (clearing the vocation drops exactly its grants). The
     * logical/client side keeps the unchanged direct read (the live readers are all server-side).
     */
    public static boolean hasGrantedAbility(Player player, String flag) {
        if (player instanceof ServerPlayer serverPlayer) {
            return AbilityGrantService.hasFromSource(serverPlayer, AbilityKey.of(flag), AbilityGrants.Source.VOCATION);
        }
        return grantsFlag(getData(player).primary(), flag);
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
}
