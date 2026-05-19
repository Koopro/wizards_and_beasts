package at.koopro.wizardsandbeasts.bloodpact.command;

import at.koopro.wizardsandbeasts.bloodpact.BloodPactRecord;
import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.item.bloodpact.BloodPactVialItem;
import at.koopro.wizardsandbeasts.network.bloodpact.SBreakPactPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PactCommands {

    private PactCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("pact")
                .then(Commands.literal("break")
                        .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> breakPact(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))));
    }

    private static int breakPact(CommandSourceStack source, ServerPlayer target) {
        List<BloodPactRecord> pacts = new ArrayList<>(target.getData(ModAttachments.ACTIVE_BLOOD_PACTS.get()));
        if (pacts.isEmpty()) {
            source.sendFailure(Component.literal(target.getScoreboardName() + " has no active blood pacts."));
            return 0;
        }

        for (BloodPactRecord record : pacts) {
            UUID pactUUID = record.pactUUID();
            UUID partnerUUID = record.partnerUUID();
            ServerPlayer partner = source.getServer().getPlayerList().getPlayer(partnerUUID);
            if (partner != null) {
                List<BloodPactRecord> partnerPacts = new ArrayList<>(partner.getData(ModAttachments.ACTIVE_BLOOD_PACTS.get()));
                partnerPacts.removeIf(r -> r.pactUUID().equals(pactUUID));
                partner.setData(ModAttachments.ACTIVE_BLOOD_PACTS.get(), partnerPacts);
                markVialBroken(partner, pactUUID);
                SBreakPactPayload.sendToPlayer(partner, pactUUID);
            }
        }

        markVialBroken(target, null);
        target.setData(ModAttachments.ACTIVE_BLOOD_PACTS.get(), new ArrayList<>());

        source.sendSuccess(() -> Component.literal("Removed all blood pacts from " + target.getScoreboardName() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return pacts.size();
    }

    private static void markVialBroken(ServerPlayer player, UUID pactUUID) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof BloodPactVialItem)) continue;
            if (pactUUID != null) {
                Optional<UUID> itemPact = stack.getOrDefault(ModDataComponents.PACT_UUID.get(), Optional.empty());
                if (itemPact.isEmpty() || !itemPact.get().equals(pactUUID)) continue;
            }
            stack.set(ModDataComponents.BLOOD_PACT_BROKEN.get(), true);
        }
    }
}
