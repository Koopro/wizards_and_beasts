package at.koopro.wizardsandbeasts.currency.dragot;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.Merchant;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What happens when a wizard offers French money across a counter.
 *
 * <p>Offering is a gesture that already exists: right-clicking a vendor with a Dragot in hand. That
 * is what this listens for, and it is the moment all three of the Dragot's shop-side rules resolve:
 *
 * <ol>
 *   <li><b>Acceptance.</b> {@link DragotAcceptance} says whether this vendor deals in Dragots. An
 *       untagged British shopkeeper turns them away; a Continental one will not take anything
 *       else.</li>
 *   <li><b>Detection.</b> A vendor who <em>does</em> take them counts them, and
 *       {@link DragotRates#DEVALUED_NOTICE_CHANCE} of the time finds a bad one. The sale is off.</li>
 *   <li><b>Reputation.</b> Being caught files the offence — which reaches the standing system through
 *       the existing {@code OFFENCE} deed trigger — and, when the vendor is a villager, is remembered
 *       personally as {@link GossipType#MINOR_NEGATIVE}. Villagers already have a reputation system;
 *       inventing a second one for this would have been a second thing to keep in sync.</li>
 * </ol>
 *
 * <p>Nothing here touches a vendor who was never offered a Dragot, so a player carrying only British
 * coin never notices this class exists.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class DragotVendorEvents {

    private DragotVendorEvents() {}

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity vendor = event.getTarget();
        if (!(vendor instanceof Merchant)) {
            return;
        }

        DragotAcceptance policy = DragotAcceptance.policyFor(vendor);
        boolean offering = DragotPurse.isOffering(player);

        // A vendor nobody is offering foreign coin to, who does not demand it, is not this class's
        // business — leave the interaction entirely alone.
        if (!offering && policy != DragotAcceptance.ONLY) {
            return;
        }

        if (policy.refuses(offering)) {
            PlayerFeedback.actionBar(player,
                    policy.refusalFor(offering).copy().withStyle(ChatFormatting.RED));
            event.setCanceled(true);
            return;
        }

        if (!offering) {
            return;
        }

        if (DragotExchange.noticedByVendor(player)) {
            refuseAndRemember(player, vendor);
            event.setCanceled(true);
            return;
        }

        if (policy == DragotAcceptance.PENALTY) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("currency.wizards_and_beasts.dragot.penalty",
                                    Math.round(DragotRates.PENALTY_MARKUP * 100.0f))
                            .withStyle(ChatFormatting.GOLD));
        }
    }

    /** The sale is off, it is on your file, and this particular shopkeeper will not forget it. */
    private static void refuseAndRemember(ServerPlayer player, Entity vendor) {
        PlayerFeedback.refuse(player,
                Component.translatable("currency.wizards_and_beasts.dragot.caught"),
                Component.translatable("currency.wizards_and_beasts.dragot.caught.detail"));

        if (vendor instanceof Villager villager) {
            villager.getGossips().add(player.getUUID(), GossipType.MINOR_NEGATIVE,
                    GossipType.REPUTATION_CHANGE_PER_EVENT);
        }
        vendor.level().playSound(null, vendor.getX(), vendor.getY(), vendor.getZ(),
                SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 0.9f);
    }
}
