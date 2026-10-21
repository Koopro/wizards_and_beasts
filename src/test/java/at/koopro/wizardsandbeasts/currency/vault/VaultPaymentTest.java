package at.koopro.wizardsandbeasts.currency.vault;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transaction safety of the vault: a payment is all or nothing, value is conserved, change comes in the largest
 * coins, nothing goes negative, and nothing overflows.
 *
 * <p>The property tests run thousands of random vaults and prices; the fixed cases name the bugs they pin.
 */
class VaultPaymentTest {

    private static PlayerVaultData vault(long galleons, long sickles, long knuts) {
        PlayerVaultData vault = new PlayerVaultData();
        vault.depositGalleons(galleons);
        vault.depositSickles(sickles);
        vault.depositKnuts(knuts);
        return vault;
    }

    private static long[] coins(PlayerVaultData vault) {
        return new long[]{vault.getGalleons(), vault.getSickles(), vault.getKnuts()};
    }

    @Test
    void aRefusedPaymentLeavesEveryCoinWhereItWas() {
        // The bug this replaces: "withdraw what you can, deposit the partial back as Knuts" kept the value of a
        // refused customer's vault but turned their three Galleons into 1479 Knuts.
        PlayerVaultData vault = vault(3, 5, 7);
        long[] before = coins(vault);
        assertFalse(vault.pay(CurrencyHelper.toKnuts(7, 0, 0)));
        assertEquals(before[0], vault.getGalleons());
        assertEquals(before[1], vault.getSickles());
        assertEquals(before[2], vault.getKnuts());
    }

    @Test
    void paymentIsAllOrNothingOnAnyVault() {
        Random random = new Random(29L * 17L);
        for (int i = 0; i < 20_000; i++) {
            PlayerVaultData vault = vault(random.nextInt(40), random.nextInt(60), random.nextInt(100));
            long total = vault.getTotalInKnuts();
            long[] before = coins(vault);
            long price = random.nextInt(25_000);
            boolean paid = vault.pay(price);

            assertEquals(price <= total, paid, () -> "affordable " + price + " of " + total);
            if (paid) {
                assertEquals(total - price, vault.getTotalInKnuts(), "exactly the price is taken");
            } else {
                assertEquals(before[0], vault.getGalleons(), "refused: galleons untouched");
                assertEquals(before[1], vault.getSickles(), "refused: sickles untouched");
                assertEquals(before[2], vault.getKnuts(), "refused: knuts untouched");
            }
            assertTrue(vault.getGalleons() >= 0 && vault.getSickles() >= 0 && vault.getKnuts() >= 0,
                    "no denomination goes negative");
            if (paid && before[0] > vault.getGalleons()) {
                // A Galleon was broken: its change is in the largest coins, never a pile of Knuts. (Breaking one
                // means every loose Knut and Sickle was spent first, so whatever Knuts remain are change.)
                assertTrue(vault.getKnuts() < CurrencyHelper.KNUTS_PER_SICKLE,
                        () -> "change came back as Knuts: " + vault.getKnuts());
            }
        }
    }

    @Test
    void changeFromABrokenGalleonComesBackInSicklesAndKnuts() {
        PlayerVaultData vault = vault(1, 0, 0);
        assertTrue(vault.pay(30));
        assertEquals(0, vault.getGalleons());
        assertEquals(15, vault.getSickles());
        assertEquals(28, vault.getKnuts());
        assertEquals(463, vault.getTotalInKnuts());
    }

    @Test
    void creditPaysInTheLargestCoins() {
        PlayerVaultData vault = new PlayerVaultData();
        assertTrue(vault.credit(500));
        assertEquals(1, vault.getGalleons());
        assertEquals(0, vault.getSickles());
        assertEquals(7, vault.getKnuts());
    }

    @Test
    void creditStopsAtTheCeilingAndLeavesTheVaultAlone() {
        PlayerVaultData vault = new PlayerVaultData();
        assertTrue(vault.credit(CurrencyHelper.MAX_VAULT_KNUTS - 10));
        long[] before = coins(vault);
        assertFalse(vault.canCredit(11));
        assertFalse(vault.credit(11));
        assertEquals(before[0], vault.getGalleons());
        assertEquals(before[2], vault.getKnuts());
        assertTrue(vault.credit(10));
        assertEquals(CurrencyHelper.MAX_VAULT_KNUTS, vault.getTotalInKnuts());
        assertFalse(vault.credit(Long.MAX_VALUE), "a huge credit cannot overflow past the check");
    }

    @Test
    void negativeAmountsNeverMintMoney() {
        PlayerVaultData vault = vault(1, 1, 1);
        long total = vault.getTotalInKnuts();
        assertThrows(IllegalArgumentException.class, () -> vault.pay(-5));
        assertThrows(IllegalArgumentException.class, () -> vault.credit(-5));
        assertThrows(IllegalArgumentException.class, () -> vault.depositKnuts(-5));
        assertFalse(vault.withdrawKnuts(-5));
        assertFalse(vault.withdrawSickles(-5));
        assertFalse(vault.withdrawGalleons(-5));
        assertEquals(0, vault.withdrawSmartKnuts(-5));
        assertEquals(0, vault.withdrawSmartSickles(-5));
        assertEquals(total, vault.getTotalInKnuts());
    }

    @Test
    void zeroIsAlwaysPaid() {
        PlayerVaultData vault = new PlayerVaultData();
        assertTrue(vault.pay(0));
        assertTrue(vault.credit(0));
        assertEquals(0, vault.getTotalInKnuts());
    }

    @Test
    void aCorruptNegativeSaveLoadsAsNoCoins() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(PlayerVaultData.VERSION_KEY, PlayerVaultData.CURRENT_VERSION);
        tag.putLong("Knuts", -50L);
        tag.putLong("Sickles", 3L);
        tag.putLong("Galleons", -1L);
        PlayerVaultData vault = new PlayerVaultData();
        vault.load(tag);
        assertEquals(0, vault.getKnuts());
        assertEquals(3, vault.getSickles());
        assertEquals(0, vault.getGalleons());
        assertFalse(vault.pay(88), "a negative coin count can never make a price look affordable");
        assertTrue(vault.pay(87));
    }

    @Test
    void saveAndLoadKeepEveryCoin() {
        PlayerVaultData vault = vault(12, 16, 28);
        PlayerVaultData copy = new PlayerVaultData();
        copy.load(vault.save());
        assertEquals(vault.getGalleons(), copy.getGalleons());
        assertEquals(vault.getSickles(), copy.getSickles());
        assertEquals(vault.getKnuts(), copy.getKnuts());
    }
}
