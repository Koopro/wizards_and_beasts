package at.koopro.wizardsandbeasts.heritage.elf;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * The world's side of a house-elf's service ({@link HouseElfBond}).
 *
 * <p>Why events rather than a mixin: every hook here is an existing NeoForge event — a player using another player,
 * damage arriving, a login — so nothing vanilla needs changing.
 *
 * <ul>
 *   <li>Sneak-use, empty hand: an elf pledges service, or a player accepts the elf's pledge.</li>
 *   <li>Sneak-use with a garment: a master frees their elf.</li>
 *   <li>A bound elf's blow at its master never lands; the elf takes the hurt instead.</li>
 *   <li>An elf logging in or out refreshes their master's wheel (the call appears and goes with them).</li>
 * </ul>
 *
 * Runs at high priority and cancels what it handles, so a vampire's empty-hand feed never fires on the same click.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class HouseElfEvents {

    /** What a bound elf's raised hand costs itself. */
    public static final float SELF_PUNISHMENT = 2.0f;

    private HouseElfEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !HouseElfBond.enabled()
                || !(event.getEntity() instanceof ServerPlayer actor) || !actor.isShiftKeyDown()
                || !(event.getTarget() instanceof ServerPlayer other)) {
            return;
        }
        ItemStack held = actor.getMainHandItem();
        boolean handled;
        if (held.isEmpty()) {
            handled = HouseElfBond.isElf(actor) ? HouseElfBond.pledge(actor, other) : HouseElfBond.accept(actor, other);
        } else {
            handled = HouseElfBond.present(actor, other, held);
        }
        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer master)
                || !(event.getSource().getEntity() instanceof ServerPlayer elf) || elf == master
                || !HouseElfBond.serves(elf, master)) {
            return;
        }
        event.setCanceled(true);
        PlayerFeedback.actionBar(elf, Component.translatable("heritage.wizards_and_beasts.elf.punished")
                .withStyle(ChatFormatting.RED));
        elf.hurtServer(elf.level(), elf.damageSources().magic(), SELF_PUNISHMENT);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        refreshMaster(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        refreshMaster(event.getEntity());
    }

    private static void refreshMaster(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer elf) || !HouseElfBond.isElf(elf) || elf.level().getServer() == null) {
            return;
        }
        UUID master = HouseElfBond.bondOf(elf).master().orElse(null);
        ServerPlayer online = master == null ? null : elf.level().getServer().getPlayerList().getPlayer(master);
        if (online != null) {
            PlayerStateSyncService.syncAbilityGrants(online);
        }
    }
}
