package at.koopro.wizardsandbeasts.spell.protego;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ProtegoWardManager {
    private static final Map<UUID, Integer> CASTER_TO_ENTITY = new ConcurrentHashMap<>();

    private ProtegoWardManager() {}

    public static void register(@NonNull UUID casterUUID, int entityId) {
        CASTER_TO_ENTITY.put(casterUUID, entityId);
    }

    public static int getEntityId(@NonNull UUID casterUUID) {
        return CASTER_TO_ENTITY.getOrDefault(casterUUID, -1);
    }

    public static void remove(@NonNull UUID casterUUID) {
        CASTER_TO_ENTITY.remove(casterUUID);
    }

    public static void shatterExistingIfPresent(@NonNull ServerPlayer caster) {
        int existingId = getEntityId(caster.getUUID());
        if (existingId < 0) {
            return;
        }
        Entity entity = caster.level().getEntity(existingId);
        if (entity instanceof ProtegoShieldEntity shield) {
            shield.beginShatter();
        } else {
            remove(caster.getUUID());
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getEffectInstance().is(ModEffects.PROTEGO_SHIELD)) {
            return;
        }
        int entityId = getEntityId(player.getUUID());
        if (entityId < 0) {
            return;
        }
        Entity entity = player.level().getEntity(entityId);
        if (entity instanceof ProtegoShieldEntity shield && !shield.isShattering()) {
            shield.beginShatter();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        CASTER_TO_ENTITY.clear();
    }
}
