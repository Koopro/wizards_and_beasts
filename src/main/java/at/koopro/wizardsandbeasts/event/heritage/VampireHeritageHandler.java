package at.koopro.wizardsandbeasts.event.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The daylight half of being a vampire.
 *
 * <p>Two things live here, and only one of them is new machinery:
 * <ul>
 *   <li><b>{@code sunlight_weakness}</b> — a variant tag that was declared on Turned and Born vampires
 *       and <b>read by nothing</b>. Direct sun now burns. {@code blood_hunger} has since been picked up
 *       too, by {@code heritage.nutrition.NutritionPolicyResolver}; {@code water_breathing} is still
 *       declared and still unread.</li>
 *   <li><b>The bat form is voluntary</b>, so it is not triggered here at all — it lives on the ability
 *       wheel ({@code VampireFormAbilityBehavior} → {@code HeritageTransformService.toggle}). This class
 *       only ever pushes a vampire <em>out</em> of the sky, never into it.</li>
 * </ul>
 *
 * <p>The burn deliberately mirrors {@code ObscurialRules.shouldApplyDaylightVulnerability}, which solves
 * the identical problem for an obscurus: the mod should not grow two different answers to "is this player
 * standing in the sun".
 *
 * <p>The <em>other</em> half of being a vampire — thirst, feeding, and the fact that food does nothing —
 * lives in {@code heritage.vampire}, keyed on {@code blood_hunger} rather than on this heritage, so a
 * Dhampir gets the hunger without the burn and a future lineage could get either alone.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class VampireHeritageHandler {

    /** The tag the mod already uses to record "daylight hurts this one". */
    public static final String TAG_SUNLIGHT_WEAKNESS = "sunlight_weakness";

    /** Seconds between burns. A vampire caught out should have time to reach shade. */
    private static final int BURN_INTERVAL_TICKS = 40;

    /** Half a heart per burn: attritional and survivable, not an execution. */
    private static final float BURN_DAMAGE = 1.0f;

    /** Vanilla's own "is it bright out" boundary — day runs 0..12300, matching the werewolf's night. */
    private static final long DAY_END = 12300L;

    private VampireHeritageHandler() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)) {
            return;
        }
        if (event.getServer().getTickCount() % BURN_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
            if (data.getSelectedHeritage() != Heritage.VAMPIRE || !hasSunlightWeakness(data)) {
                continue;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            if (isBurning(player, level)) {
                burn(player, level);
            }
        }
    }

    /** True for a variant the mod has marked as harmed by daylight — Turned and Born, not Dhampir. */
    public static boolean hasSunlightWeakness(PlayerHeritageData data) {
        HeritageVariant variant = data.getSelectedHeritageVariant();
        return variant != null && variant.hasTag(TAG_SUNLIGHT_WEAKNESS);
    }

    /**
     * Standing in real daylight: a sky overhead, the sun up, and nothing dimming it.
     *
     * <p>Rain and thunder spare them, which is both the folklore and the same allowance vanilla makes for
     * burning undead. Creative and spectator are exempt for the obvious reason.
     */
    public static boolean isBurning(ServerPlayer player, ServerLevel level) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        var dimension = level.dimensionType();
        if (!dimension.hasSkyLight() || dimension.hasFixedTime()) {
            return false;
        }
        if (level.isRaining() || level.isThundering()) {
            return false;
        }
        if (Math.floorMod(level.getDayTime(), 24000L) >= DAY_END) {
            return false;
        }
        return level.canSeeSky(player.blockPosition());
    }

    private static void burn(ServerPlayer player, ServerLevel level) {
        player.hurt(level.damageSources().onFire(), BURN_DAMAGE);
        level.sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(),
                8, 0.3, 0.4, 0.3, 0.01);
        PlayerFeedback.actionBar(player,
                Component.translatable("message.wizards_and_beasts.vampire.sunlight"));
    }
}
