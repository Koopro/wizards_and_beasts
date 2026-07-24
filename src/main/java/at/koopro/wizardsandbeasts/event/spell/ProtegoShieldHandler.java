package at.koopro.wizardsandbeasts.event.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.cast.SpellProtegoRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ProtegoShieldHandler {
    public static final String PROTEGO_ACTIVE_TAG = "neo_protego_active";
    private static final Map<UUID, Long> PROTEGO_EXPIRY_TICKS = new ConcurrentHashMap<>();

    private ProtegoShieldHandler() {}

    public static void activate(ServerPlayer player, long expiryTick) {
        PROTEGO_EXPIRY_TICKS.put(player.getUUID(), expiryTick);
        player.addTag(PROTEGO_ACTIVE_TAG);
    }

    /** Ends the incoming-damage ward early — called when the shield entity shatters before expiry. */
    public static void deactivate(ServerPlayer player) {
        PROTEGO_EXPIRY_TICKS.remove(player.getUUID());
        player.removeTag(PROTEGO_ACTIVE_TAG);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getTags().contains(PROTEGO_ACTIVE_TAG)) return;
        if (SpellProtegoRules.bypassesProtego(event)) {
            return;
        }
        event.setCanceled(true);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(), ModSounds.PROTEGO_BLOCK.get(),
                    SoundSource.PLAYERS, 0.72f, 1.03f + serverLevel.random.nextFloat() * 0.09f);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Long expiry = PROTEGO_EXPIRY_TICKS.get(player.getUUID());
            if (expiry == null) continue;
            if (now >= expiry) {
                PROTEGO_EXPIRY_TICKS.remove(player.getUUID());
                player.removeTag(PROTEGO_ACTIVE_TAG);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PROTEGO_EXPIRY_TICKS.remove(player.getUUID());
        player.removeTag(PROTEGO_ACTIVE_TAG);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PROTEGO_EXPIRY_TICKS.remove(player.getUUID());
        player.removeTag(PROTEGO_ACTIVE_TAG);
    }
}
