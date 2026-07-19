package at.koopro.wizardsandbeasts.ability.grant;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.ability.AnimagusTransformService;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.legilimency.LegilimencyServerLogic;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Grants the abilities whose eligibility is earned player <b>status</b> rather than a declared list: a
 * passed Apparition test plus a licence, a wizard bloodline, a completed Animagus ritual. Each entry
 * delegates to the owning system's existing predicate — this source duplicates no rule and decides nothing;
 * it only makes the wheel show what the server would already have allowed.
 *
 * <p>Replaces the heritage-tag grant path, which never matched an ability id (heritage tags are bare
 * strings, ability keys are namespaced) and so granted nothing. Tags are tags again.
 *
 * <p>Read-only and cheap — a few attachment reads — matching the derived, never-stored grant contract.
 */
@NullMarked
public final class PlayerStatusAbilityGrantSource implements AbilityGrantSource {

    @Override
    public AbilityGrants.Source source() {
        return AbilityGrants.Source.STATUS;
    }

    @Override
    public List<String> grantsFor(ServerPlayer player) {
        List<String> out = new ArrayList<>(3);
        if (ApparitionServerLogic.canApparate(player)) {
            out.add(AbilityIds.APPARITION.toString());
        }
        if (LegilimencyServerLogic.canLegilimise(player)) {
            out.add(AbilityIds.LEGILIMENCY.toString());
        }
        if (AnimagusTransformService.canTransform(player)) {
            out.add(AbilityIds.ANIMAGUS_FORM.toString());
        }
        return out;
    }
}
