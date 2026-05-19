package at.koopro.wizardsandbeasts.item.bloodpact;

import at.koopro.wizardsandbeasts.bloodpact.BloodPactRecord;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.bloodpact.SBreakPactPayload;
import at.koopro.wizardsandbeasts.network.bloodpact.SUpdateVialPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class BloodPactVialItem extends Item {

    public static final Map<UUID, PendingPactProposal> PENDING_PROPOSALS = new HashMap<>();

    public record PendingPactProposal(UUID initiatorUUID, UUID targetUUID, long expiryGameTick) {}

    public BloodPactVialItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !stack.getOrDefault(ModDataComponents.BLOOD_PACT_BROKEN.get(), false);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = player.getItemInHand(hand);
        boolean broken = stack.getOrDefault(ModDataComponents.BLOOD_PACT_BROKEN.get(), false);
        Optional<UUID> pactUuidOpt = stack.getOrDefault(ModDataComponents.PACT_UUID.get(), Optional.empty());
        long currentTick = level.getGameTime();

        // 4b — Sneaking: break own pact
        if (player.isShiftKeyDown()) {
            if (broken) {
                player.displayClientMessage(
                        Component.literal("The vial is already broken.").withStyle(ChatFormatting.DARK_GRAY),
                        true);
                return InteractionResult.PASS;
            }
            if (pactUuidOpt.isEmpty()) {
                return InteractionResult.PASS;
            }
            UUID pactUUID = pactUuidOpt.get();

            List<BloodPactRecord> myPacts = new ArrayList<>(player.getData(ModAttachments.ACTIVE_BLOOD_PACTS.get()));
            BloodPactRecord myRecord = myPacts.stream()
                    .filter(r -> r.pactUUID().equals(pactUUID))
                    .findFirst()
                    .orElse(null);

            myPacts.removeIf(r -> r.pactUUID().equals(pactUUID));
            player.setData(ModAttachments.ACTIVE_BLOOD_PACTS.get(), myPacts);
            stack.set(ModDataComponents.BLOOD_PACT_BROKEN.get(), true);

            if (myRecord != null && level instanceof ServerLevel serverLevel) {
                ServerPlayer partner = serverLevel.getServer().getPlayerList().getPlayer(myRecord.partnerUUID());
                if (partner != null) {
                    List<BloodPactRecord> partnerPacts = new ArrayList<>(partner.getData(ModAttachments.ACTIVE_BLOOD_PACTS.get()));
                    partnerPacts.removeIf(r -> r.pactUUID().equals(pactUUID));
                    partner.setData(ModAttachments.ACTIVE_BLOOD_PACTS.get(), partnerPacts);
                    markPartnerVialBroken(partner, pactUUID);
                    SBreakPactPayload.sendToPlayer(partner, pactUUID);
                }
            }

            player.displayClientMessage(
                    Component.literal("You shatter the blood pact. The covenant is broken.")
                            .withStyle(ChatFormatting.DARK_RED),
                    false);
            level.playSound(null, player.blockPosition(),
                    ModSounds.BLOOD_PACT_SHATTER.get(), SoundSource.PLAYERS, 1.2f, 0.8f);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        16, 0.3, 0.3, 0.3, 0.05);
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        8, 0.3, 0.3, 0.3, 0.1);
            }
            return InteractionResult.SUCCESS;
        }

        // Broken vial is inert when not sneaking
        if (broken) {
            return InteractionResult.PASS;
        }

        // 4e — Already sealed, not sneaking
        if (pactUuidOpt.isPresent()) {
            player.displayClientMessage(
                    Component.literal("The pact is already sealed.").withStyle(ChatFormatting.GOLD),
                    true);
            return InteractionResult.PASS;
        }

        // Find nearest player within 3 blocks
        List<Player> nearby = level.getEntitiesOfClass(Player.class,
                new AABB(player.getX() - 3, player.getY() - 3, player.getZ() - 3,
                        player.getX() + 3, player.getY() + 3, player.getZ() + 3),
                p -> !p.getUUID().equals(player.getUUID()));

        if (nearby.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("No one is close enough. A blood pact requires two willing parties.")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    false);
            return InteractionResult.PASS;
        }

        Player targetPlayer = nearby.get(0);

        // 4d — Accept a pending proposal targeting this player
        PendingPactProposal proposal = PENDING_PROPOSALS.get(player.getUUID());
        if (proposal != null && proposal.initiatorUUID().equals(targetPlayer.getUUID())) {
            if (currentTick > proposal.expiryGameTick()) {
                PENDING_PROPOSALS.remove(player.getUUID());
                player.displayClientMessage(
                        Component.literal("The offer has expired.").withStyle(ChatFormatting.DARK_GRAY),
                        true);
                return InteractionResult.PASS;
            }
            PENDING_PROPOSALS.remove(player.getUUID());
            sealPact(player, targetPlayer, stack, level);
            return InteractionResult.SUCCESS;
        }

        // 4c — Propose pact
        List<BloodPactRecord> targetPacts = targetPlayer.getData(ModAttachments.ACTIVE_BLOOD_PACTS.get());
        boolean alreadyBound = targetPacts.stream().anyMatch(r -> r.partnerUUID().equals(player.getUUID()));
        if (alreadyBound) {
            player.displayClientMessage(
                    Component.literal("You are already bound to this person.").withStyle(ChatFormatting.GOLD),
                    true);
            return InteractionResult.PASS;
        }

        PENDING_PROPOSALS.put(targetPlayer.getUUID(),
                new PendingPactProposal(player.getUUID(), targetPlayer.getUUID(), currentTick + 200));

        player.displayClientMessage(
                Component.literal("You offer the blood pact to " + targetPlayer.getScoreboardName()
                        + ". They must right-click you within 10 seconds to seal it.")
                        .withStyle(ChatFormatting.GOLD),
                false);
        targetPlayer.displayClientMessage(
                Component.literal(player.getScoreboardName()
                        + " offers you a blood pact. Right-click them within 10 seconds to seal it.")
                        .withStyle(ChatFormatting.DARK_RED),
                false);
        level.playSound(null, player.blockPosition(),
                ModSounds.BLOOD_PACT_OFFER.get(), SoundSource.PLAYERS, 1.0f, 1.1f);
        return InteractionResult.SUCCESS;
    }

    private static void sealPact(Player acceptor, Player initiator, ItemStack acceptorStack, Level level) {
        UUID pactUUID = UUID.randomUUID();
        UUID initiatorUUID = initiator.getUUID();
        UUID acceptorUUID = acceptor.getUUID();

        BloodPactRecord initiatorRecord = new BloodPactRecord(pactUUID, acceptorUUID);
        BloodPactRecord acceptorRecord = new BloodPactRecord(pactUUID, initiatorUUID);

        List<BloodPactRecord> initiatorPacts = new ArrayList<>(initiator.getData(ModAttachments.ACTIVE_BLOOD_PACTS.get()));
        initiatorPacts.add(initiatorRecord);
        initiator.setData(ModAttachments.ACTIVE_BLOOD_PACTS.get(), initiatorPacts);

        List<BloodPactRecord> acceptorPacts = new ArrayList<>(acceptor.getData(ModAttachments.ACTIVE_BLOOD_PACTS.get()));
        acceptorPacts.add(acceptorRecord);
        acceptor.setData(ModAttachments.ACTIVE_BLOOD_PACTS.get(), acceptorPacts);

        // Update acceptor's held vial
        acceptorStack.set(ModDataComponents.PACT_UUID.get(), Optional.of(pactUUID));
        acceptorStack.set(ModDataComponents.PACT_PARTNER_UUID.get(), Optional.of(initiatorUUID));
        acceptorStack.set(ModDataComponents.BLOOD_PACT_BROKEN.get(), false);

        // Update initiator's held vial server-side and sync to client
        for (InteractionHand ihand : InteractionHand.values()) {
            ItemStack initiatorStack = initiator.getItemInHand(ihand);
            if (initiatorStack.getItem() instanceof BloodPactVialItem
                    && initiatorStack.getOrDefault(ModDataComponents.PACT_UUID.get(), Optional.empty()).isEmpty()) {
                initiatorStack.set(ModDataComponents.PACT_UUID.get(), Optional.of(pactUUID));
                initiatorStack.set(ModDataComponents.PACT_PARTNER_UUID.get(), Optional.of(acceptorUUID));
                initiatorStack.set(ModDataComponents.BLOOD_PACT_BROKEN.get(), false);
                break;
            }
        }
        if (initiator instanceof ServerPlayer serverInitiator) {
            SUpdateVialPayload.sendToPlayer(serverInitiator, pactUUID, acceptorUUID);
        }

        Component sealMsg = Component.literal(
                        "The blood pact is sealed. You may not harm each other while this covenant holds.")
                .withStyle(ChatFormatting.DARK_PURPLE);
        acceptor.displayClientMessage(sealMsg, false);
        initiator.displayClientMessage(sealMsg, false);

        level.playSound(null, acceptor.blockPosition(),
                ModSounds.BLOOD_PACT_SEAL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        level.playSound(null, initiator.blockPosition(),
                ModSounds.BLOOD_PACT_SEAL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    acceptor.getX(), acceptor.getY() + 1.0, acceptor.getZ(),
                    24, 0.5, 0.5, 0.5, 0.1);
            serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE,
                    acceptor.getX(), acceptor.getY() + 1.0, acceptor.getZ(),
                    12, 0.5, 0.5, 0.5, 0.05);
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    initiator.getX(), initiator.getY() + 1.0, initiator.getZ(),
                    24, 0.5, 0.5, 0.5, 0.1);
            serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE,
                    initiator.getX(), initiator.getY() + 1.0, initiator.getZ(),
                    12, 0.5, 0.5, 0.5, 0.05);
        }
    }

    private static void markPartnerVialBroken(ServerPlayer partner, UUID pactUUID) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = partner.getItemInHand(hand);
            if (!(s.getItem() instanceof BloodPactVialItem)) continue;
            Optional<UUID> itemPact = s.getOrDefault(ModDataComponents.PACT_UUID.get(), Optional.empty());
            if (itemPact.isPresent() && itemPact.get().equals(pactUUID)) {
                s.set(ModDataComponents.BLOOD_PACT_BROKEN.get(), true);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("A binding covenant in blood.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("Made in 1899, Godric's Hollow.")
                .withStyle(ChatFormatting.DARK_GRAY));

        Optional<UUID> pactUuidOpt = stack.getOrDefault(ModDataComponents.PACT_UUID.get(), Optional.empty());
        boolean broken = stack.getOrDefault(ModDataComponents.BLOOD_PACT_BROKEN.get(), false);

        if (pactUuidOpt.isEmpty()) {
            tooltipAdder.accept(Component.literal("[Unsealed — find a willing party]")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (!broken) {
            tooltipAdder.accept(Component.literal("[Binding — Pact active]")
                    .withStyle(ChatFormatting.DARK_PURPLE));
            Optional<UUID> partnerOpt = stack.getOrDefault(ModDataComponents.PACT_PARTNER_UUID.get(), Optional.empty());
            String partnerStr = partnerOpt.map(UUID::toString).orElse("Unknown");
            tooltipAdder.accept(Component.literal("Bound to: " + partnerStr)
                    .withStyle(ChatFormatting.GOLD));
            tooltipAdder.accept(Component.literal("Sneak + right-click to shatter the covenant.")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else {
            tooltipAdder.accept(Component.literal("[Shattered]")
                    .withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.DARK_RED));
        }
    }
}
