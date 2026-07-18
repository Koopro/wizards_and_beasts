package at.koopro.wizardsandbeasts.ability.grant;

import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillEffect;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.skill.vocation.VocationDefinition;
import at.koopro.wizardsandbeasts.skill.vocation.VocationHelper;
import at.koopro.wizardsandbeasts.skill.vocation.VocationRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
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

    /** Recomputes the full grant snapshot from the player's persisted inputs. */
    public static AbilityGrants compute(ServerPlayer player) {
        return AbilityGrants.of(heritageTags(player), vocationFlags(player), skillNodeAbilities(player));
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

    // ── Per-source input readers (each reads only already-persistent state) ──

    private static List<String> heritageTags(ServerPlayer player) {
        HeritageVariant variant = HeritageAPI.getPlayerHeritageVariant(player);
        return variant == null ? List.of() : new ArrayList<>(variant.getTags());
    }

    private static List<String> vocationFlags(ServerPlayer player) {
        VocationDefinition vocation = VocationHelper.getPrimary(player)
                .map(VocationRegistry::get)
                .orElse(null);
        return vocation == null ? List.of() : new ArrayList<>(vocation.grantedAbilities());
    }

    private static List<String> skillNodeAbilities(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        PlayerSkillData data = SkillSystemAPI.getSkillData(player);
        for (String nodeId : data.getUnlockedSkills().keySet()) {
            Skill node = SkillTrees.byId(nodeId);
            if (node == null) {
                continue;
            }
            for (SkillEffect effect : node.getEffects()) {
                if (effect instanceof SkillEffect.GrantAbility grant) {
                    out.add(grant.ability().id());
                } else if (effect instanceof SkillEffect.UnlockAbility unlock) {
                    out.add(unlock.abilityId());
                }
            }
        }
        return out;
    }
}
