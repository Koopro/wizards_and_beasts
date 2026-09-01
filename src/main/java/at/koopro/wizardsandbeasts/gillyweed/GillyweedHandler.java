package at.koopro.wizardsandbeasts.gillyweed;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Living with gills, and losing them.
 *
 * <h2>The three seconds that matter</h2>
 * Gillyweed running out underwater is the moment the item is remembered by, and an effect that simply
 * stopped would be a drowning with no warning. The last {@link Gillyweed#WARNING_TICKS} are loud:
 * bubbles tearing off the neck, a rising note, and a line telling the diver to go up. After that
 * vanilla's ordinary drowning takes over — the warning buys attention, not air.
 *
 * <h2>Replacing Water Breathing, not stacking with it</h2>
 * Vanilla Water Breathing is refused outright while the gills are in
 * ({@link MobEffectEvent.Applicable}), and any already running is removed when they grow. A wizard
 * who drinks a potion and then chews Gillyweed has wasted a potion, which is the honest outcome —
 * the alternative is ninety seconds of underwater for the price of forty-five.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class GillyweedHandler {

    /** Ticks between gill flutters while submerged. */
    private static final int GILL_INTERVAL = 6;
    /** Ticks between warning bursts as the gills fade. */
    private static final int WARNING_INTERVAL = 10;

    private GillyweedHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobEffectInstance gills = player.getEffect(ModEffects.GILLS);
        if (gills == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();

        if (Gillyweed.isFading(gills.getDuration())) {
            fadeWarning(player, level, gills.getDuration());
            return;
        }
        if (player.isUnderWater() && player.tickCount % GILL_INTERVAL == 0) {
            gillFlutter(player, level);
        }
    }

    /**
     * Bubbles streaming off the sides of the neck.
     *
     * <p>Placed at the throat and offset to either side rather than in a cloud around the player, so
     * it reads as gills working rather than as a generic water effect. Spawned server-side so other
     * divers see them — half the point of the transformation is being seen to have changed.
     */
    private static void gillFlutter(ServerPlayer player, ServerLevel level) {
        double neckY = player.getY() + player.getEyeHeight() - 0.22;
        // Perpendicular to the look direction: the gills sit on the sides of the neck.
        double yaw = Math.toRadians(player.getYRot());
        double sideX = Math.cos(yaw) * 0.22;
        double sideZ = Math.sin(yaw) * 0.22;
        for (int side = -1; side <= 1; side += 2) {
            level.sendParticles(ParticleTypes.BUBBLE,
                    player.getX() + sideX * side, neckY, player.getZ() + sideZ * side,
                    2, 0.02, 0.04, 0.02, 0.005);
        }
    }

    /** The last three seconds: unmistakable, and only underwater is it urgent. */
    private static void fadeWarning(ServerPlayer player, ServerLevel level, int remaining) {
        if (player.tickCount % WARNING_INTERVAL != 0) {
            return;
        }
        level.sendParticles(ParticleTypes.BUBBLE_POP,
                player.getX(), player.getY() + player.getEyeHeight() - 0.2, player.getZ(),
                8, 0.25, 0.25, 0.25, 0.06);
        // Pitch climbs as the seconds run out, so the warning gets more insistent rather than just
        // repeating.
        float pitch = 1.2f + (1.0f - remaining / (float) Gillyweed.WARNING_TICKS) * 0.6f;
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5f, pitch);

        if (player.isUnderWater()) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("item.wizards_and_beasts.gillyweed.fading",
                            Math.max(1, remaining / 20)).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Vanilla Water Breathing will not land on a wizard who already has gills. */
    @SubscribeEvent
    public static void onApplicable(MobEffectEvent.Applicable event) {
        if (!event.getEntity().hasEffect(ModEffects.GILLS)) {
            return;
        }
        if (event.getEffectInstance().getEffect().is(MobEffects.WATER_BREATHING)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /**
     * When the gills go, so does the refresh flag — and a wizard who topped up pays for it now.
     *
     * <p>The cooldown is applied on <em>expiry</em> rather than on eating, which is what makes
     * refreshing a real choice: chewing a second sprig costs you nothing at the time and everything
     * ten seconds after you surface.
     */
    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        endGills(event.getEntity(), event.getEffectInstance());
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        endGills(event.getEntity(), event.getEffectInstance());
    }

    private static void endGills(LivingEntity entity, @org.jspecify.annotations.Nullable MobEffectInstance instance) {
        if (instance == null || !instance.getEffect().is(ModEffects.GILLS)) {
            return;
        }
        if (entity.level().isClientSide() || !(entity instanceof Player player)) {
            return;
        }
        if (Gillyweed.wasRefreshed(player)) {
            player.getCooldowns().addCooldown(
                    ConsumableItemRegistry.GILLYWEED.get().getDefaultInstance(),
                    Gillyweed.REFRESH_COOLDOWN_TICKS);
        }
        Gillyweed.clearRefresh(player);

        if (player instanceof ServerPlayer served && served.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.BUBBLE_POP,
                    served.getX(), served.getY() + served.getEyeHeight() - 0.2, served.getZ(),
                    16, 0.3, 0.3, 0.3, 0.08);
            level.playSound(null, served.getX(), served.getY(), served.getZ(),
                    SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 0.7f, 1.1f);
        }
    }
}
