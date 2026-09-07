package at.koopro.wizardsandbeasts.heritage.vampire;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionEnforcer;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicyResolver;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * When the blood economy runs. Every rule it enforces lives in {@link VampireBloodHandler}.
 *
 * <p>Registration only, deliberately thin — the same division {@code VampireHeritageHandler} keeps
 * between "the server ticked" and "here is what daylight does".
 *
 * <p>Every listener is gated on {@link Module#HERITAGE}. With the module off a vampire is an ordinary
 * hungry human: the bar comes back (the client's policy never leaves VANILLA because nothing syncs a
 * pool), food feeds them again, and nothing drains. That is the correct off state for a module switch —
 * not "the heritage half-works".
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class VampireBloodEvents {

    private VampireBloodEvents() {}

    /** The drain clock. One pass per {@link VampireBloodHandler#TICK_INTERVAL} ticks, over every player. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)) {
            return;
        }
        long tick = event.getServer().getTickCount();
        if (tick % VampireBloodHandler.TICK_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.level() instanceof ServerLevel level) {
                VampireBloodHandler.tick(player, level, tick);
            }
        }
    }

    /**
     * A bare-handed right-click: the feed.
     *
     * <p>Server-side only, and cancelled there rather than on both sides. The client cannot know the feed
     * cooldown, the drained window or whether the pool is full, so a client-side cancel would be a guess
     * — and a wrong guess costs the player an arm swing on an interaction that did not happen. Letting
     * the client predict normally and the server overrule it is how every other authoritative
     * interaction in this mod behaves.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.getMainHandItem().isEmpty()) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (VampireBloodHandler.attemptFeed(player, target, level)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
        }
    }

    /**
     * Photographs the food bar before a non-vanilla eater takes a bite.
     *
     * <p>Taken for every use-item action rather than only for food, because the cheap test here is
     * "which policy is this player on" and the expensive one is enumerating everything that might turn
     * out to grant nutrition. For a bow or a shield the snapshot restores identical values, which costs
     * two field writes and cannot be wrong.
     */
    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player
                && !NutritionPolicyResolver.resolve(player).feedsOnFood()) {
            NutritionEnforcer.rememberBeforeMeal(player);
        }
    }

    /**
     * Puts the food bar back where it was, so the meal was worth nothing.
     *
     * <p>{@code Finish} fires <em>after</em> the item has been consumed and its nutrition applied — the
     * event's own javadoc says so — which is exactly why this is a restore and not a cancel. Cancelling
     * {@code Start} instead would have taken the eating animation away too, and a vampire who cannot even
     * raise bread to their mouth reads as a bug rather than as a body that gets nothing from it.
     */
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player
                && !NutritionPolicyResolver.resolve(player).feedsOnFood()) {
            VampireBloodHandler.denyNutrition(player, event.getItem().has(DataComponents.FOOD));
        }
    }

    /** A meal abandoned half-way. Drops the snapshot rather than applying it — nothing was eaten. */
    @SubscribeEvent
    public static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NutritionEnforcer.forgetMeal(player);
        }
    }

    /**
     * Gives a pool to a vampire who predates the blood economy.
     *
     * <p>The attachment defaults to an <em>empty</em> pool, which is correct for a player who has drunk
     * their way down to nothing and catastrophic for one whose save file was written before this system
     * existed: they would log in STARVING, take damage, and have no idea why. The seeded flag tells the
     * two apart, and this is the only place that reads it.
     *
     * <p>Runs before {@code PlayerStateSyncService.syncFullLoginState} sends the blood payload only by
     * luck of listener order, which is why {@link VampireBloodAPI#seed} syncs on its own rather than
     * relying on the login sweep to notice.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !VampireBloodAPI.drinksBlood(player)) {
            return;
        }
        if (!VampireBloodAPI.getData(player).isSeeded()) {
            VampireBloodAPI.seed(player);
        }
    }

    /**
     * What dying costs a blood-drinker.
     *
     * <p>The pool is {@code copyOnDeath}, so it arrives at the respawn point unchanged; this settles it
     * to {@code vampireBloodRespawnPercent}. Settles rather than tops up: waking with more blood than you
     * died with would make dying a meal, and waking with less than the floor would make a death spiral
     * unrecoverable for a player who respawns far from anything with a pulse.
     *
     * <p>Skipped for the return trip from the End, which is a teleport wearing a respawn's clothes.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)) {
            return;
        }
        if (event.isEndConquered()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !VampireBloodAPI.drinksBlood(player)) {
            return;
        }
        VampireBloodData data = VampireBloodAPI.getData(player);
        VampireBloodAPI.setBlood(player, data.getMaxBlood() * VampireBloodConfig.respawnPercent);
        data.setLastNotifiedStage(null);
    }
}
