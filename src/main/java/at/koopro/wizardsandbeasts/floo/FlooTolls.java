package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * Tolls on privately kept public hearths: a keeper may ask up to {@link FlooRegistryEntry#MAX_TOLL_KNUTS} of each
 * traveller, and the money goes to them.
 *
 * <p>Money moves between two vaults; none is made or destroyed ({@code MoneyFlow.FLOO_TOLL} out,
 * {@code FLOO_TOLL_INCOME} in). A keeper who is offline is paid at their next login from the network's own ledger
 * ({@link FlooNetworkManager#holdToll}), because a vault is a player attachment and cannot be credited in absentia.
 *
 * <p><b>Who pays.</b> Not the keeper, not an administrator, not a traveller sent there by a misfire (they did not
 * choose the hearth), and nobody while the Gringotts module is off — without the bank, a service is a favour, the
 * rule every location service already follows.
 */
@NullMarked
public final class FlooTolls {

    private FlooTolls() {}

    /** Whether {@code traveller} owes a toll to reach {@code entry} on purpose. */
    public static boolean owes(ServerPlayer traveller, FlooRegistryEntry entry) {
        return owes(entry.tollKnuts(), entry.isOwnedBy(traveller.getUUID()), FlooAccess.isAdmin(traveller),
                ModuleManager.isEnabled(Module.GRINGOTTS));
    }

    /** The rule as arithmetic, for the tests. */
    public static boolean owes(int toll, boolean isKeeper, boolean isAdmin, boolean billed) {
        return toll > 0 && !isKeeper && !isAdmin && billed;
    }

    /**
     * Takes the toll for {@code entry} from {@code traveller}'s vault.
     *
     * @return the Knuts taken (0 when none is owed), or -1 when it is owed and cannot be paid — the hop is refused
     */
    public static long charge(ServerPlayer traveller, FlooRegistryEntry entry) {
        if (!owes(traveller, entry)) {
            return 0L;
        }
        long toll = entry.tollKnuts();
        if (!GringottsBilling.charge(traveller, MoneyFlow.FLOO_TOLL, toll)) {
            PlayerFeedback.refuse(traveller, Component.translatable("floo.wizards_and_beasts.toll.title"),
                    Component.translatable("floo.wizards_and_beasts.toll.cannot_afford",
                            CurrencyHelper.formatFromKnuts(toll)));
            return -1L;
        }
        return toll;
    }

    /** Gives back a toll taken for a hop that then failed. */
    public static void refund(ServerPlayer traveller, long knuts) {
        if (knuts > 0) {
            GringottsBilling.refund(traveller, MoneyFlow.FLOO_TOLL, knuts);
        }
    }

    /** Pays a toll taken from a traveller to the hearth's keeper, now or at their next login. */
    public static void payKeeper(ServerLevel level, FlooRegistryEntry entry, long knuts) {
        if (knuts <= 0 || entry.owner().isEmpty()) {
            return;
        }
        UUID keeper = entry.owner().get();
        MinecraftServer server = level.getServer();
        ServerPlayer online = server.getPlayerList().getPlayer(keeper);
        if (online != null && GringottsBilling.credit(online, MoneyFlow.FLOO_TOLL_INCOME, knuts)) {
            return;
        }
        FlooNetworkManager.get(level).holdToll(keeper, knuts);
    }

    /** Pays a keeper the tolls that came in while they were away. Called at login. */
    public static void collect(ServerPlayer keeper) {
        if (!(keeper.level() instanceof ServerLevel level)) {
            return;
        }
        FlooNetworkManager manager = FlooNetworkManager.get(level);
        long held = manager.takeHeldTolls(keeper.getUUID());
        if (held <= 0) {
            return;
        }
        if (!GringottsBilling.credit(keeper, MoneyFlow.FLOO_TOLL_INCOME, held)) {
            manager.returnHeldTolls(keeper.getUUID(), held);
            return;
        }
        PlayerFeedback.toast(keeper, NoticeKind.SUCCESS, Component.translatable("floo.wizards_and_beasts.toll.collected"),
                Component.translatable("floo.wizards_and_beasts.toll.collected.body",
                        CurrencyHelper.formatFromKnuts(held)));
    }
}
