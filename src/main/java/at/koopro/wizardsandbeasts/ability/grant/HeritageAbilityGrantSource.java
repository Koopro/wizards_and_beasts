package at.koopro.wizardsandbeasts.ability.grant;

import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Heritage grant source (§2.2 mandates this one). Exposes the committed heritage variant's tags as ability
 * grants — reusing the existing tag data, so no new {@code List<Identifier> abilities} field is added to the
 * heritage definition (see MIGRATION_DELTAS.md). Read-only over already-persistent heritage state.
 */
@NullMarked
public final class HeritageAbilityGrantSource implements AbilityGrantSource {

    @Override
    public AbilityGrants.Source source() {
        return AbilityGrants.Source.HERITAGE;
    }

    @Override
    public List<String> grantsFor(ServerPlayer player) {
        HeritageVariant variant = HeritageAPI.getPlayerHeritageVariant(player);
        return variant == null ? List.of() : new ArrayList<>(variant.getTags());
    }
}
