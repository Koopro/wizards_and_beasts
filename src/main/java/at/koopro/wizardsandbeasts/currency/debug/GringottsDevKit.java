package at.koopro.wizardsandbeasts.currency.debug;

import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.network.currency.VaultSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Money in the vault and coins in the pocket, because they are not the same thing.
 *
 * <p>Vendors, fines and the respec fee bill the <em>vault</em>. Physical coins are what you hand
 * over at a counter and what a Niffler steals. A kit that gave only one of them would leave half the
 * currency layer untestable, and which half is not obvious until you are standing in front of the
 * thing that refuses you.
 */
@NullMarked
public final class GringottsDevKit implements FeatureDevKit {

    /** Comfortably past every price in the mod without being a number that hides an overflow. */
    private static final long GALLEONS = 500L;
    private static final long SICKLES = 500L;
    private static final long KNUTS = 500L;
    private static final int POCKET_COINS = 64;

    @Override
    public String id() {
        return "gringotts";
    }

    @Override
    public String title() {
        return "Gringotts";
    }

    @Override
    public String summary() {
        return "Fill the vault, and hand over a stack of each coin for over-the-counter tests.";
    }

    @Override
    public void open(ServerPlayer target, DevLog log) {
        PlayerVaultData vault = target.getData(ModAttachments.VAULT_DATA.get());
        vault.depositGalleons(GALLEONS);
        vault.depositSickles(SICKLES);
        vault.depositKnuts(KNUTS);
        VaultSyncS2CPayload.syncToPlayer(target);
        log.changed("vault", vault.getGalleons() + "g " + vault.getSickles()
                + "s " + vault.getKnuts() + "k");
    }

    @Override
    public void kit(ServerPlayer target, DevLog log) {
        target.getInventory().add(new ItemStack(CurrencyItemRegistry.GALLEON.get(), POCKET_COINS));
        target.getInventory().add(new ItemStack(CurrencyItemRegistry.SICKLE.get(), POCKET_COINS));
        target.getInventory().add(new ItemStack(CurrencyItemRegistry.KNUT.get(), POCKET_COINS));
        // A Dragot is foreign money on a moving rate with a forgery chance, which is a different
        // test from "can I afford this" -- so it comes along, and separately.
        target.getInventory().add(new ItemStack(CurrencyItemRegistry.DRAGOT.get(), POCKET_COINS));
        log.changed("coins", POCKET_COINS + " each of galleon, sickle, knut and dragot");
    }

    @Override
    public void reset(ServerPlayer target, DevLog log) {
        PlayerVaultData vault = target.getData(ModAttachments.VAULT_DATA.get());
        long had = vault.getTotalInKnuts();
        vault.resetAll();
        VaultSyncS2CPayload.syncToPlayer(target);
        log.changed("vault emptied", had + " knuts removed");
    }
}
