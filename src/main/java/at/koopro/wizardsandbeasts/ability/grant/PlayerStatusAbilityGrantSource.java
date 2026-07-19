package at.koopro.wizardsandbeasts.ability.grant;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.ability.AnimagusAbilityService;
import at.koopro.wizardsandbeasts.ability.AnimagusTransformService;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialServerLogic;
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
        List<String> out = new ArrayList<>(8);
        if (ApparitionServerLogic.canApparate(player)) {
            out.add(AbilityIds.APPARITION.toString());
        }
        if (LegilimencyServerLogic.canLegilimise(player)) {
            out.add(AbilityIds.LEGILIMENCY.toString());
        }
        if (AnimagusTransformService.canTransform(player)) {
            out.add(AbilityIds.ANIMAGUS_FORM.toString());
        }
        // Form-scoped entries: they appear in the wheel only while the form is held, mirroring the
        // dedicated binds, which silently did nothing outside it.
        if (AnimagusAbilityService.canUseActive(player)) {
            out.add(AbilityIds.ANIMAGUS_BEAST_ABILITY.toString());
        }
        if (ObscurialServerLogic.canToggleForm(player)) {
            out.add(AbilityIds.OBSCURIAL_FORM.toString());
        }
        if (ObscurialServerLogic.canStressVent(player)) {
            out.add(AbilityIds.OBSCURIAL_STRESS_VENT.toString());
        }
        if (ObscurialServerLogic.canUseAbilities(player)) {
            out.add(AbilityIds.OBSCURUS_SURGE.toString());
            out.add(AbilityIds.OBSCURUS_GRASP.toString());
        }
        return out;
    }
}
