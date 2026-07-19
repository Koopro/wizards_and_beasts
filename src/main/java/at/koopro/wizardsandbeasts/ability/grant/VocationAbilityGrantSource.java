package at.koopro.wizardsandbeasts.ability.grant;

import at.koopro.wizardsandbeasts.skill.vocation.VocationDefinition;
import at.koopro.wizardsandbeasts.skill.vocation.VocationHelper;
import at.koopro.wizardsandbeasts.skill.vocation.VocationRegistry;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/** Vocation grant source: the declared primary vocation's {@code grantedAbilities}. Read-only. */
@NullMarked
public final class VocationAbilityGrantSource implements AbilityGrantSource {

    @Override
    public AbilityGrants.Source source() {
        return AbilityGrants.Source.VOCATION;
    }

    @Override
    public List<String> grantsFor(ServerPlayer player) {
        VocationDefinition vocation = VocationHelper.getPrimary(player)
                .map(VocationRegistry::get)
                .orElse(null);
        return vocation == null ? List.of() : new ArrayList<>(vocation.grantedAbilities());
    }
}
