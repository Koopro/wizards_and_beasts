package at.koopro.wizardsandbeasts.wand.rules;

import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondState;
import at.koopro.wizardsandbeasts.wand.registry.WandTemperament;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wandmaking withdrawals and the allegiance/affinity globals, and that shipped values change nothing. */
class WandRulesTest {

    private static final Identifier HOLLY = Identifier.fromNamespaceAndPath("wizards_and_beasts", "holly");
    private static final Identifier YEW = Identifier.fromNamespaceAndPath("wizards_and_beasts", "yew");
    private static final Identifier PHOENIX = Identifier.fromNamespaceAndPath("wizards_and_beasts", "phoenix_feather");
    private static final Identifier DRAGON = Identifier.fromNamespaceAndPath("wizards_and_beasts", "dragon_heartstring");

    @AfterEach
    void reset() {
        WandRules.publish(Set.of(), Set.of(), Set.of());
        WandGlobals.publishLocal(WandGlobals.Values.SHIPPED);
        WandGlobals.clearRemote();
    }

    @Test
    void nothingIsWithdrawnByDefault() {
        assertTrue(WandRules.mayMake(HOLLY, PHOENIX));
        assertFalse(WandRules.mayMake(null, PHOENIX));
        assertFalse(WandRules.mayMake(HOLLY, null));
    }

    @Test
    void aWithdrawnWoodCoreOrPairingStopsExactlyThoseMakes() {
        WandRules.publish(Set.of(YEW.toString()), Set.of(), Set.of());
        assertFalse(WandRules.mayMake(YEW, PHOENIX));
        assertTrue(WandRules.mayMake(HOLLY, PHOENIX));

        WandRules.publish(Set.of(), Set.of(DRAGON.toString()), Set.of());
        assertFalse(WandRules.mayMake(HOLLY, DRAGON));
        assertTrue(WandRules.mayMake(HOLLY, PHOENIX));

        WandRules.publish(Set.of(), Set.of(), Set.of(WandRules.pairKey(HOLLY, PHOENIX)));
        assertFalse(WandRules.mayMake(HOLLY, PHOENIX));
        assertTrue(WandRules.mayMake(HOLLY, DRAGON), "only the named pairing is withdrawn");
        assertTrue(WandRules.mayMake(YEW, PHOENIX));
    }

    @Test
    void affinityStrengthScalesAroundNeutral() {
        assertEquals(1.2f, WandGlobals.scaled(1.2f, 1.0f), 1e-6f, "shipped strength is the identity");
        WandGlobals.publishLocal(new WandGlobals.Values(0.0f, 1.0f, 1, 1.0f, true));
        assertEquals(1.0f, WandGlobals.scaled(1.2f, 1.0f), 1e-6f, "strength 0 makes every wand neutral");
        WandGlobals.publishLocal(new WandGlobals.Values(2.0f, 1.0f, 1, 1.0f, true));
        assertEquals(1.4f, WandGlobals.scaled(1.2f, 1.0f), 1e-6f);
        assertEquals(-0.1f, WandGlobals.scaled(-0.05f, 0.0f), 1e-6f, "fizzle is neutral at zero");
    }

    @Test
    void aRemoteServersGlobalsWinOverTheLocalConfig() {
        WandGlobals.publishLocal(new WandGlobals.Values(2.0f, 1.0f, 1, 1.0f, true));
        WandGlobals.acceptRemote(new WandGlobals.Values(0.5f, 1.0f, 1, 1.0f, true));
        assertEquals(0.5f, WandGlobals.current().affinityStrength(), 1e-6f);
        WandGlobals.clearRemote();
        assertEquals(2.0f, WandGlobals.current().affinityStrength(), 1e-6f);
    }

    @Test
    void allegianceReadsTheGlobalsAndShippedValuesKeepItsRules() {
        WandTemperament neutral = WandTemperament.NEUTRAL;
        assertEquals(1, WandAllegianceRules.winsToTransfer(neutral, false), "canon: one defeat wins a wand");
        assertEquals(1, WandAllegianceRules.winsToTransfer(neutral, true));

        WandGlobals.publishLocal(new WandGlobals.Values(1.0f, 1.0f, 3, 1.0f, true));
        assertEquals(3, WandAllegianceRules.winsToTransfer(neutral, false));
        assertEquals(1, WandAllegianceRules.winsToTransfer(neutral, true), "the Elder Wand always answers to one defeat");

        WandTemperament backfiring = new WandTemperament(1.0f, 0, 0.0f, 0.0f, false, true, 1.0f, 1.0f, false, 1.0f);
        WandGlobals.publishLocal(WandGlobals.Values.SHIPPED);
        assertTrue(WandAllegianceRules.backfires(WandBondState.UNFAMILIAR, backfiring, 1.0f));
        WandGlobals.publishLocal(new WandGlobals.Values(1.0f, 1.0f, 1, 1.0f, false));
        assertFalse(WandAllegianceRules.backfires(WandBondState.UNFAMILIAR, backfiring, 1.0f));
    }
}
