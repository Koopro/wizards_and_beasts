package at.koopro.wizardsandbeasts.ability.grant;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Auto-grants the three test abilities to every player while {@code Config.enableDebugTools} is on — the
 * "debug flag" that gates the verification abilities (§2.5). Off by default, so the test abilities never
 * surface in normal play even though their definitions always load.
 */
@NullMarked
public final class DebugAutoGrantSource implements AbilityGrantSource {

    private static final List<String> DEBUG_ABILITIES = List.of(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "debug_active").toString(),
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "debug_toggle").toString(),
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "debug_passive").toString());

    @Override
    public AbilityGrants.Source source() {
        return AbilityGrants.Source.DEBUG;
    }

    @Override
    public List<String> grantsFor(ServerPlayer player) {
        return Config.enableDebugTools ? DEBUG_ABILITIES : List.of();
    }
}
