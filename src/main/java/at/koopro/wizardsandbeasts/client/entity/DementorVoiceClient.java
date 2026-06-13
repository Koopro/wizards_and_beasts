package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.NonNull;

/**
 * Client-only Dementor ambience. Holds the {@code Minecraft} access that must not
 * live in the common {@link DementorEntity} class; only ever invoked from the
 * client tick path, so this class is never loaded on a dedicated server.
 */
public final class DementorVoiceClient {

    private DementorVoiceClient() {
    }

    /**
     * Memory-voice tick (§6.11). Plays a faint whisper near the local player while
     * they are under the Dementor chill effect.
     *
     * @param dementor the ticking dementor on the client side
     * @param cooldown ticks remaining until the next whisper
     * @return the updated cooldown value to store on the entity
     */
    public static int tickVoice(@NonNull DementorEntity dementor, int cooldown) {
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null || !localPlayer.hasEffect(ModEffects.DEMENTOR_CHILL)) return cooldown;
        if (cooldown > 0) return cooldown - 1;
        var random = dementor.getRandom();
        // TODO: replace with custom whisper sounds in future audio prompt
        dementor.level().playLocalSound(localPlayer.getX(), localPlayer.getY(), localPlayer.getZ(),
                SoundEvents.AMBIENT_CAVE.value(), dementor.getSoundSource(), 0.15f,
                0.4f + random.nextFloat() * 0.2f, false);
        return 80 + random.nextInt(120);
    }
}
