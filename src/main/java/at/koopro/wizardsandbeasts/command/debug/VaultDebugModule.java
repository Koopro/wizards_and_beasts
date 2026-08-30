package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class VaultDebugModule implements DebugModule {
    @Override
    public String name() {
        return "vault";
    }

    @Override
    public String summary() {
        return "Gringotts balance snapshot (knuts/sickles/galleons).";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> register() {
        return DebugModule.onSelfOrTarget(name(), this::inspect);
    }

    private int inspect(CommandSourceStack source, ServerPlayer target) {
        DebugOutput out = new DebugOutput(source);
        PlayerVaultData data = target.getData(ModAttachments.VAULT_DATA.get());
        out.header("Vault Debug :: " + target.getName().getString());
        out.kv("Knuts", data.getKnuts());
        out.kv("Sickles", data.getSickles());
        out.kv("Galleons", data.getGalleons());
        out.kv("Total (knuts)", data.getTotalInKnuts());
        if (data.getKnuts() < 0 || data.getSickles() < 0 || data.getGalleons() < 0) {
            out.warn("Validation: Negative vault balance detected.");
        } else {
            out.ok("Validation: Vault balances are valid.");
        }
        return 1;
    }
}
