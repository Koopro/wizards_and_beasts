package at.koopro.wizardsandbeasts.wand.ollivander;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * What a wand from Ollivander costs: "Seven Galleons" (<i>Philosopher's Stone</i> ch. 5).
 *
 * <p>The first wand is free. A new character has no money and cannot cast without a wand, so charging for the first
 * would lock them out of the game; that is the Minecraft compromise, and it is the only one. Every wand after it is
 * paid for from the vault, all or nothing, while the Gringotts module is on — so losing a wand to a rival, or
 * snapping one, costs something again (documentation/CANON_AUDIT.md C-8). {@code ollivanderWandPriceKnuts} = 0
 * makes every wand free.
 */
@NullMarked
public final class OllivanderPrice {

    /** Set once a player has taken a wand from the trial; the next one is paid for. */
    public static final String FIRST_WAND_FLAG = "ollivander_first_wand";

    private OllivanderPrice() {}

    /** What this player's next wand costs, in Knuts: 0 for the first, or while there is no vault to bill. */
    public static int priceFor(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.GRINGOTTS) || Config.ollivanderWandPriceKnuts <= 0
                || !PlayerAbilityHelper.hasAbilityFlag(player, FIRST_WAND_FLAG)) {
            return 0;
        }
        return Config.ollivanderWandPriceKnuts;
    }

    /**
     * Takes the price from the vault, all or nothing. Returns whether the wand may be handed over; when the vault is
     * short, says so and takes nothing.
     */
    public static boolean pay(ServerPlayer player) {
        int price = priceFor(player);
        if (price <= 0) {
            return true;
        }
        PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
        long withdrawn = vault.withdrawSmartKnuts(price);
        if (withdrawn < price) {
            if (withdrawn > 0) {
                vault.depositKnuts(withdrawn); // never pocket a partial payment
            }
            PlayerFeedback.actionBar(player, Component.translatable("wandcraft.trial.price.short",
                    CurrencyHelper.formatFromKnuts(price)).withStyle(ChatFormatting.RED));
            return false;
        }
        PlayerFeedback.actionBar(player, Component.translatable("wandcraft.trial.price.paid",
                CurrencyHelper.formatFromKnuts(price)).withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** The trial handed this player a wand: any later one is paid for. */
    public static void recordSale(ServerPlayer player) {
        PlayerAbilityHelper.addAbilityFlag(player, FIRST_WAND_FLAG);
    }
}
