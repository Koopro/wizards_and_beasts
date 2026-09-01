package at.koopro.wizardsandbeasts.demiguise;

import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cloak's new mortality.
 *
 * <p>Two of these guard decisions that would be silent, world-breaking regressions: the Hallow must
 * never acquire a charge, and a cloak that predates the mechanic must not load as spent.
 */
class CloakChargesTest {

    private static ItemStack ordinaryCloak() {
        return new ItemStack(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get());
    }

    @Test
    void anOrdinaryCloakWearsOutAndTheHallowDoesNot() {
        assertTrue(CloakCharges.isChargeable(ordinaryCloak()));

        ItemStack hallow = new ItemStack(DarkArtefactItemRegistry.DEATHLY_HALLOW_CLOAK.get());
        assertFalse(CloakCharges.isChargeable(hallow),
                "being the one cloak that never fails is what makes it a Hallow");
    }

    @Test
    void theHallowIsNeverWrittenToAndAlwaysHides() {
        ItemStack hallow = new ItemStack(DarkArtefactItemRegistry.DEATHLY_HALLOW_CLOAK.get());

        for (int second = 0; second < 5_000; second++) {
            assertTrue(CloakCharges.spendSecond(hallow), "the Hallow ran out");
        }
        assertTrue(CloakCharges.hasCharge(hallow));
        assertFalse(hallow.has(ModDataComponents.CLOAK_CHARGES.get()),
                "the Hallow must not carry a charge component at all");
    }

    @Test
    void aCloakThatPredatesChargesLoadsUsableRatherThanSpent() {
        // No component at all is what every already-existing cloak in every already-existing world
        // looks like, and what a bare /give produces. Reading that as zero would brick all of them.
        ItemStack legacy = ordinaryCloak();
        assertFalse(legacy.has(ModDataComponents.CLOAK_CHARGES.get()));
        assertEquals(CloakCharges.STARTING_CHARGES, CloakCharges.remaining(legacy));
        assertTrue(CloakCharges.hasCharge(legacy));
    }

    @Test
    void spendingRunsDownToZeroAndStops() {
        ItemStack cloak = ordinaryCloak();
        cloak.set(ModDataComponents.CLOAK_CHARGES.get(), 3);

        assertTrue(CloakCharges.spendSecond(cloak));
        assertEquals(2, CloakCharges.remaining(cloak));
        assertTrue(CloakCharges.spendSecond(cloak));
        assertFalse(CloakCharges.spendSecond(cloak), "the last second should report empty");

        assertEquals(0, CloakCharges.remaining(cloak));
        assertFalse(CloakCharges.hasCharge(cloak));
        // Spending past empty must not go negative or wrap.
        assertFalse(CloakCharges.spendSecond(cloak));
        assertEquals(0, CloakCharges.remaining(cloak));
    }

    @Test
    void aSpentCloakIsRewovenRatherThanReplaced() {
        ItemStack cloak = ordinaryCloak();
        cloak.set(ModDataComponents.CLOAK_CHARGES.get(), 0);

        int after = CloakCharges.recharge(cloak, 1);
        assertEquals(CloakCharges.SECONDS_PER_HAIR, after);
        assertTrue(CloakCharges.hasCharge(cloak));
    }

    @Test
    void rechargingCannotExceedTheCap() {
        // The cap is what stops a stack of hairs turning an ordinary cloak into a Hallow.
        ItemStack cloak = ordinaryCloak();
        assertEquals(CloakCharges.MAX_CHARGES, CloakCharges.recharge(cloak, 9_999));
        assertEquals(CloakCharges.MAX_CHARGES, CloakCharges.remaining(cloak));
    }

    @Test
    void oneHairIsWorthAMeaningfulButNotEndlessAmountOfHiding() {
        assertTrue(CloakCharges.SECONDS_PER_HAIR > 0);
        assertTrue(CloakCharges.MAX_CHARGES / CloakCharges.SECONDS_PER_HAIR >= 4,
                "the cap should hold several hairs, or reweaving is pointless");
        assertTrue(CloakCharges.STARTING_CHARGES < CloakCharges.MAX_CHARGES,
                "a found cloak must have room to be improved");
    }

    @Test
    void nothingThatIsNotACloakIsChargeable() {
        assertFalse(CloakCharges.isChargeable(new ItemStack(Items.STICK)));
        assertFalse(CloakCharges.isChargeable(ItemStack.EMPTY));
        // Non-cloaks report full so a caller that forgets to check cannot accidentally block on them.
        assertTrue(CloakCharges.hasCharge(new ItemStack(Items.STICK)));
    }
}
