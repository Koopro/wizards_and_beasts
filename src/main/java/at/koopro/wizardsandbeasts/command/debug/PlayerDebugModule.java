package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerDebugModule implements DebugModule {
    @Override
    public String name() {
        return "player";
    }

    @Override
    public String summary() {
        return "Cross-check player attachment state (type, form, spells, vault).";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal(name())
                .executes(ctx -> inspect(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> inspect(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))));
    }

    private int inspect(CommandSourceStack source, ServerPlayer target) {
        DebugOutput out = new DebugOutput(source);
        PlayerSpellData spellData = target.getData(ModAttachments.SPELL_DATA.get());
        PlayerHeritageData typeData = target.getData(ModAttachments.HERITAGE_DATA.get());
        PlayerVaultData vaultData = target.getData(ModAttachments.VAULT_DATA.get());

        out.header("Player Debug :: " + target.getName().getString());
        out.kv("UUID", target.getUUID());
        out.kv("Debug mode", DebugModeService.isEnabled(target) ? "ON" : "OFF");
        out.kv("Type selected", typeData.hasHeritageSelected());
        out.kv("Type API selected", HeritageAPI.hasHeritageSelected(target));
        out.kv("Active form", typeData.getActiveFormId() == null ? "(none)" : typeData.getActiveFormId());
        out.kv("Known spells", spellData.getKnownSpells().size());
        out.kv("Vault total (knuts)", vaultData.getTotalInKnuts());

        if (HeritageAPI.hasHeritageSelected(target) != typeData.hasHeritageSelected()) {
            out.warn("Validation: Type selection state mismatch between API and attachment.");
        } else {
            out.ok("Validation: Player state looks consistent.");
        }
        return 1;
    }
}
