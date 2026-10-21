package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The world's side of Auror casework: culprits taken alive or lost, a witness questioned by sneaking and using
 * them, and the posted cases moved once a second. Events rather than mixins: every hook here is one NeoForge
 * already fires.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MinistryCaseworkEvents {

    private static final int INTERVAL_TICKS = 20;

    private MinistryCaseworkEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL_TICKS == 0 && TraceService.isActive()) {
            MinistryCasework.tick(event.getServer(), MinistryTrace.now(event.getServer()));
        }
    }

    /** Late, so armour and every other modifier has had its say before "would this bring them down" is asked. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (MinistryCasework.onCulpritHurt(entity, event.getAmount(), event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            MinistryCasework.onCulpritDied(event.getEntity());
        }
    }

    /**
     * Sneak and use a witness to question them. Only consumes the click when the interview happened: a villager
     * who saw nothing an Auror is working still trades as usual.
     */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer auror) || !auror.isShiftKeyDown()
                || !(event.getTarget() instanceof LivingEntity witness) || !MinistryCasework.isAuror(auror)) {
            return;
        }
        if (MinistryCasework.interview(auror, witness).ok()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
