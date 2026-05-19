package at.koopro.wizardsandbeasts.event.cloak;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.cloak.CloakItem;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public class CloakEffectsHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> DEATHLY_HIDDEN_PLAYERS = new HashSet<>();
    private static final Set<UUID> CLOAK_INVISIBLE_PLAYERS = new HashSet<>();

    private CloakEffectsHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Set<UUID> currentlyHidden = new HashSet<>();
        Set<UUID> currentlyCloakInvisible = new HashSet<>();
        List<ServerPlayer> allPlayers = event.getServer().getPlayerList().getPlayers();

        for (ServerPlayer player : allPlayers) {
            boolean cloaked = isCloakEquipped(player);
            boolean deathlyCloaked = isDeathlyCloaked(player);
            UUID playerId = player.getUUID();

            if (!cloaked) {
                if (DEATHLY_HIDDEN_PLAYERS.contains(player.getUUID())) {
                    broadcastEquipment(allPlayers, player, false);
                    logCloak("restore equipment visibility", player);
                }
                if (CLOAK_INVISIBLE_PLAYERS.contains(playerId)) {
                    player.setInvisible(false);
                    logCloak("restore entity visibility", player);
                }
                continue;
            }

            player.setInvisible(true);
            currentlyCloakInvisible.add(playerId);
            if (!CLOAK_INVISIBLE_PLAYERS.contains(playerId)) {
                logCloak("apply cloak invisibility (no potion effect)", player);
                clearMobTargets(player);
            }

            if (deathlyCloaked) {
                currentlyHidden.add(playerId);
                broadcastEquipment(allPlayers, player, true);
                if (!DEATHLY_HIDDEN_PLAYERS.contains(playerId)) {
                    logCloak("deathly cloak: mask all equipment slots for observers", player);
                }
            } else if (DEATHLY_HIDDEN_PLAYERS.contains(playerId)) {
                broadcastEquipment(allPlayers, player, false);
                logCloak("normal cloak: unmask equipment slots", player);
            }
        }

        DEATHLY_HIDDEN_PLAYERS.clear();
        DEATHLY_HIDDEN_PLAYERS.addAll(currentlyHidden);
        CLOAK_INVISIBLE_PLAYERS.clear();
        CLOAK_INVISIBLE_PLAYERS.addAll(currentlyCloakInvisible);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)) {
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }
        syncTargetForObserver(observer, target);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) observer.level();
        for (ServerPlayer target : serverLevel.getServer().getPlayerList().getPlayers()) {
            syncTargetForObserver(observer, target);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        resyncTargetToAllObservers(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        resyncTargetToAllObservers(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DEATHLY_HIDDEN_PLAYERS.remove(player.getUUID());
        CLOAK_INVISIBLE_PLAYERS.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onLivingEquipmentChanged(LivingEquipmentChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.CHEST) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer) {
            return;
        }

        LivingEntity entity = event.getEntity();
        boolean wasCloak = event.getFrom().getItem() instanceof CloakItem;
        boolean isCloak = event.getTo().getItem() instanceof CloakItem;
        if (!wasCloak && !isCloak) {
            return;
        }
        entity.setInvisible(isCloak);
    }

    private static void broadcastEquipment(List<ServerPlayer> allPlayers, ServerPlayer target, boolean hideAllEquipment) {
        List<Pair<EquipmentSlot, ItemStack>> equipment = createEquipmentPacketData(target, hideAllEquipment);
        ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(target.getId(), equipment);

        for (ServerPlayer observer : allPlayers) {
            if (observer != target && observer.level() == target.level()) {
                observer.connection.send(packet);
            }
        }
    }

    private static List<Pair<EquipmentSlot, ItemStack>> createEquipmentPacketData(ServerPlayer target, boolean hidden) {
        List<Pair<EquipmentSlot, ItemStack>> list = new ArrayList<>(6);
        list.add(Pair.of(EquipmentSlot.MAINHAND, hidden ? ItemStack.EMPTY : target.getMainHandItem().copy()));
        list.add(Pair.of(EquipmentSlot.OFFHAND, hidden ? ItemStack.EMPTY : target.getOffhandItem().copy()));
        list.add(Pair.of(EquipmentSlot.HEAD, hidden ? ItemStack.EMPTY : target.getItemBySlot(EquipmentSlot.HEAD).copy()));
        list.add(Pair.of(EquipmentSlot.CHEST, hidden ? ItemStack.EMPTY : target.getItemBySlot(EquipmentSlot.CHEST).copy()));
        list.add(Pair.of(EquipmentSlot.LEGS, hidden ? ItemStack.EMPTY : target.getItemBySlot(EquipmentSlot.LEGS).copy()));
        list.add(Pair.of(EquipmentSlot.FEET, hidden ? ItemStack.EMPTY : target.getItemBySlot(EquipmentSlot.FEET).copy()));
        return list;
    }

    private static boolean isCloakEquipped(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof CloakItem;
    }

    private static boolean isDeathlyCloaked(Player player) {
        ItemStack chestItem = player.getItemBySlot(EquipmentSlot.CHEST);
        return chestItem.getItem() instanceof CloakItem cloakItem && cloakItem.isDeathlyHallow();
    }

    private static void syncTargetForObserver(ServerPlayer observer, ServerPlayer target) {
        if (observer == target || observer.level() != target.level()) {
            return;
        }
        boolean deathly = isDeathlyCloaked(target);
        List<Pair<EquipmentSlot, ItemStack>> equipment = createEquipmentPacketData(target, deathly);
        observer.connection.send(new ClientboundSetEquipmentPacket(target.getId(), equipment));
        if (deathly) {
            logCloak("resync deathly cloak concealment for new observer", target);
        }
    }

    private static void resyncTargetToAllObservers(ServerPlayer target) {
        ServerLevel serverLevel = (ServerLevel) target.level();
        boolean deathly = isDeathlyCloaked(target);
        broadcastEquipment(serverLevel.getServer().getPlayerList().getPlayers(), target, deathly);
        if (deathly) {
            logCloak("resync deathly cloak concealment after lifecycle event", target);
        }
    }

    @SubscribeEvent
    public static void onMobChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget instanceof Player player && CLOAK_INVISIBLE_PLAYERS.contains(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    private static void clearMobTargets(Player player) {
        AABB searchBox = player.getBoundingBox().inflate(64.0);
        player.level().getEntitiesOfClass(Mob.class, searchBox, mob -> mob.getTarget() == player)
                .forEach(mob -> mob.setTarget(null));
    }

    private static void logCloak(String action, ServerPlayer player) {
        if (!Config.debugLogCloakVisibility) {
            return;
        }
        LOGGER.info("[CloakDebug] {} | player={} uuid={}", action, player.getName().getString(), player.getUUID());
    }
}
