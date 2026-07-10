package at.koopro.wizardsandbeasts.skill.vocation;

import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.Skill;
import net.minecraft.resources.Identifier;
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

    /** True if the declared Vocation grants the named ability flag (see VocationAbilityHooks). */
    public static boolean hasGrantedAbility(Player player, String flag) {
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
