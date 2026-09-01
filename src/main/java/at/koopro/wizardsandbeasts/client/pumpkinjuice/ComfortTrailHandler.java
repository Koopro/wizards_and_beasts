package at.koopro.wizardsandbeasts.client.pumpkinjuice;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.pumpkinjuice.PumpkinJuice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The orange trail a freshly drunk glass of pumpkin juice leaves behind you.
 *
 * <p><b>Entirely client-side and entirely free.</b> The trail runs for the first
 * {@link PumpkinJuice#TRAIL_TICKS} of a sixty-second effect, and the client works out which part of
 * the effect it is in from the duration it already replicates — so a decoration lasting a sixth as
 * long as the buff costs no extra state, no packet and no server tick.
 *
 * <p>Only while walking, as briefed: standing still leaves nothing. Drawn for every player rather
 * than only the local one, because the whole point of a trail is that other people see it.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ComfortTrailHandler {

    /** Pumpkin orange, as dust. 1.21.11 takes a packed RGB int, not a Vector3f. */
    private static final DustParticleOptions ORANGE = new DustParticleOptions(0xE07A22, 1.0f);

    /** Ticks between motes. Every third: a trail, not a smokescreen. */
    private static final int INTERVAL = 3;

    /** Squared blocks per tick above which the player counts as walking. */
    private static final double WALKING_THRESHOLD = 0.0016;

    private ComfortTrailHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || level.getGameTime() % INTERVAL != 0) {
            return;
        }
        for (Player player : level.players()) {
            if (isTrailing(player) && isWalking(player)) {
                level.addParticle(ORANGE,
                        player.getX(), player.getY() + 0.1, player.getZ(), 0.0, 0.01, 0.0);
            }
        }
    }

    /** Whether this player is inside the first {@link PumpkinJuice#TRAIL_TICKS} of the effect. */
    private static boolean isTrailing(Player player) {
        MobEffectInstance comfort = player.getEffect(ModEffects.HOGWARTS_COMFORT);
        return comfort != null
                && comfort.getDuration() > PumpkinJuice.COMFORT_TICKS - PumpkinJuice.TRAIL_TICKS;
    }

    private static boolean isWalking(Player player) {
        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        return dx * dx + dz * dz > WALKING_THRESHOLD;
    }
}
