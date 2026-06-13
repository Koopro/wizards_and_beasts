package at.koopro.wizardsandbeasts.event.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SpellCombatControlHandler {
    private static final Map<UUID, Long> DISARM_LOCKOUT_UNTIL = new ConcurrentHashMap<>();

    private static final int DISARM_LOCKOUT_TICKS = 30;

    private SpellCombatControlHandler() {}

    public static void applyExpelliarmusDisarm(ServerLevel level, LivingEntity target, ServerPlayer attacker) {
        if (target.getMainHandItem().isEmpty()) return;
        ItemStack dropped = target.getMainHandItem().copy();
        target.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ItemEntity itemEntity = new ItemEntity(level,
                target.getX(), target.getEyeY() - 0.15, target.getZ(), dropped);
        Vec3 throwDir = target.getLookAngle().scale(0.35).add(0, 0.2, 0);
        itemEntity.setDeltaMovement(throwDir);
        level.addFreshEntity(itemEntity);

        if (target instanceof ServerPlayer player) {
            DISARM_LOCKOUT_UNTIL.put(player.getUUID(), level.getGameTime() + DISARM_LOCKOUT_TICKS);
        }
    }

    public static void applyStupefyStun(LivingEntity target) {
        target.stopUsingItem();
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        DISARM_LOCKOUT_UNTIL.entrySet().removeIf(e -> now >= e.getValue());

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (isStunned(player)) {
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;
                player.stopUsingItem();
            }
        }
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        if (isDisarmed(player, now) || isStunned(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        if (isDisarmed(player, now) || isStunned(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        if (isDisarmed(player, now) || isStunned(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        if (isDisarmed(player, now) || isStunned(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        if (isDisarmed(player, now) || isStunned(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        if (isDisarmed(player, now) || isStunned(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DISARM_LOCKOUT_UNTIL.remove(player.getUUID());
    }

    public static boolean isStunned(Entity entity) {
        return entity instanceof LivingEntity living && living.hasEffect(ModEffects.STUPEFY);
    }

    private static boolean isDisarmed(ServerPlayer player, long gameTime) {
        Long until = DISARM_LOCKOUT_UNTIL.get(player.getUUID());
        return until != null && gameTime < until;
    }
}
