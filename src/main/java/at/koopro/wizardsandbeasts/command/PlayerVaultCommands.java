package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.command.debug.DebugModule;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.network.currency.VaultSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import at.koopro.wizardsandbeasts.util.ChatReport;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /wandb player vault} — put money in a vault, take it out, read it back.
 *
 * <h2>Why this was missing</h2>
 *
 * <p>The vault had a debug node that printed the balance and nothing anywhere that could change it.
 * Every price in the mod — a respec fee, a fine, a vendor, a Dragot exchange — bills the vault, so
 * "does this cost the right amount" could be observed and never arranged. Earning the money the
 * intended way first is not a test of the thing being tested.
 *
 * <p>Deposits and withdrawals are separate verbs rather than a signed {@code add}, because
 * withdrawal can <em>fail</em> — there is a smart-withdraw path that breaks a galleon into sickles
 * and it either finds the money or does not. A signed add would hide that answer behind a sign.
 */
@NullMarked
public final class PlayerVaultCommands {

    private PlayerVaultCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("vault")
                .executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))))
                .then(coinNode("deposit", true))
                .then(coinNode("withdraw", false))
                .then(DebugModule.onSelfOrTarget("clear", PlayerVaultCommands::clear));
    }

    /** {@code deposit|withdraw <galleons|sickles|knuts> <amount> [player]}. */
    private static LiteralArgumentBuilder<CommandSourceStack> coinNode(String verb, boolean deposit) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(verb);
        for (Coin coin : Coin.values()) {
            node.then(Commands.literal(coin.literal)
                    .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(ctx -> move(ctx.getSource(),
                                    ctx.getSource().getPlayerOrException(), coin,
                                    LongArgumentType.getLong(ctx, "amount"), deposit))
                            .then(Commands.argument("target", EntityArgument.player())
                                    .executes(ctx -> move(ctx.getSource(),
                                            EntityArgument.getPlayer(ctx, "target"), coin,
                                            LongArgumentType.getLong(ctx, "amount"), deposit)))));
        }
        return node;
    }

    private static int move(CommandSourceStack source, ServerPlayer target, Coin coin,
                            long amount, boolean deposit) {
        PlayerVaultData vault = target.getData(ModAttachments.VAULT_DATA.get());
        boolean ok = deposit ? coin.deposit(vault, amount) : coin.withdraw(vault, amount);
        VaultSyncS2CPayload.syncToPlayer(target);
        if (!ok) {
            source.sendFailure(Component.literal("Not enough " + coin.literal + " in "
                    + target.getName().getString() + "'s vault."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                        (deposit ? "Deposited " : "Withdrew ") + amount + " " + coin.literal
                                + " · now " + describe(vault))
                .withStyle(ChatPalette.color(ChatPalette.OK)), true);
        return 1;
    }

    private static int clear(CommandSourceStack source, ServerPlayer target) {
        PlayerVaultData vault = target.getData(ModAttachments.VAULT_DATA.get());
        long had = vault.getTotalInKnuts();
        vault.resetAll();
        VaultSyncS2CPayload.syncToPlayer(target);
        source.sendSuccess(() -> Component.literal("Emptied " + target.getName().getString()
                        + "'s vault (" + had + " knuts)")
                .withStyle(ChatPalette.color(ChatPalette.OK)), true);
        return 1;
    }

    private static int show(CommandSourceStack source, ServerPlayer target) {
        PlayerVaultData vault = target.getData(ModAttachments.VAULT_DATA.get());
        ChatReport.of("Vault · " + target.getName().getString())
                .row("galleons", String.valueOf(vault.getGalleons()))
                .row("sickles", String.valueOf(vault.getSickles()))
                .row("knuts", String.valueOf(vault.getKnuts()))
                .row("total (knuts)", String.valueOf(vault.getTotalInKnuts()))
                .send(source);
        return 1;
    }

    private static String describe(PlayerVaultData vault) {
        return vault.getGalleons() + "g " + vault.getSickles() + "s " + vault.getKnuts() + "k";
    }

    /**
     * The three denominations, each knowing how to move itself.
     *
     * <p>Withdrawals use the plain per-coin path rather than the smart one that breaks larger coins
     * down. A command that says "withdraw 5 sickles" and silently breaks a galleon to do it is
     * reporting a different operation from the one it performed, and the breaking behaviour has its
     * own exchange verbs to be tested through.
     */
    private enum Coin {
        GALLEONS("galleons"),
        SICKLES("sickles"),
        KNUTS("knuts");

        private final String literal;

        Coin(String literal) {
            this.literal = literal;
        }

        boolean deposit(PlayerVaultData vault, long amount) {
            switch (this) {
                case GALLEONS -> vault.depositGalleons(amount);
                case SICKLES -> vault.depositSickles(amount);
                case KNUTS -> vault.depositKnuts(amount);
            }
            return true;
        }

        boolean withdraw(PlayerVaultData vault, long amount) {
            return switch (this) {
                case GALLEONS -> vault.withdrawGalleons(amount);
                case SICKLES -> vault.withdrawSickles(amount);
                case KNUTS -> vault.withdrawKnuts(amount);
            };
        }
    }
}
