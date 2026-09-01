package at.koopro.wizardsandbeasts.shadow;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.demiguise.CamouflageHandler;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.beast.HidebehindEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pools of a Hidebehind's own concealment, thrown and left lying in the world.
 *
 * <p>In-memory and unsaved, on the same pattern as {@code CauldronBrewing}'s brew queue: a zone lasts
 * twelve seconds, so persisting it across a restart would be more machinery than the thing is worth,
 * and a server that goes down mid-fight taking the shadows with it is the correct outcome anyway.
 *
 * <h2>What "a zone of darkness" actually does</h2>
 * <ul>
 *   <li><b>Camouflage</b> for everything standing in it — the same effect a Demiguise hair grants, so
 *       "invisible to mobs" means one thing in this mod and not two. It is refreshed each tick and
 *       runs out shortly after you step out, which is what makes the edge of the pool matter.</li>
 *   <li><b>Darkness</b>, which is the honest form of "reduced light level". Minecraft has a block
 *       that <em>adds</em> light and none that subtracts it, so a real light change would mean
 *       rewriting the lightmap for a sphere of positions and fighting every neighbouring source. The
 *       effect gets the same idea across and costs nothing to clean up.</li>
 *   <li><b>Hidebehinds are drawn in.</b> They are made of this stuff, and a pool of it is the one
 *       thing that will pull one off a player it is already stalking.</li>
 * </ul>
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ShadowZones {

    /** Radius of a thrown pool, in blocks. */
    public static final double RADIUS = 4.0;
    /** How long a pool lasts. */
    public static final int LIFETIME_TICKS = 240;   // 12s
    /**
     * How far out a Hidebehind notices a pool.
     *
     * <p>Wider than the pool itself and wider than {@code HidebehindStalkGoal}'s sixteen-block stalk
     * range, so throwing one actually pulls a stalker off you rather than merely competing with you
     * for its attention.
     */
    public static final double ATTRACTION_RADIUS = 24.0;

    /** Ticks a standing occupant's camouflage is topped up to. Short, so leaving the pool ends it. */
    private static final int OCCUPANT_REFRESH_TICKS = 40;
    /** Cap on simultaneous pools, so a stack of essence cannot fill a server's tick with AABB sweeps. */
    private static final int MAX_ZONES = 128;

    private record Zone(ResourceKey<Level> dimension, Vec3 centre, int remainingTicks) {}

    private static final List<Zone> ZONES = new ArrayList<>();

    private ShadowZones() {}

    /** Opens a pool at {@code centre}. Silently ignored past {@link #MAX_ZONES}. */
    public static void create(Level level, Vec3 centre) {
        if (level.isClientSide() || ZONES.size() >= MAX_ZONES) {
            return;
        }
        ZONES.add(new Zone(level.dimension(), centre, LIFETIME_TICKS));
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 0.4f);
        pullHidebehinds(level, centre);
    }

    /** Whether {@code pos} is inside any live pool. Used by the Shadow Form brew's own concealment. */
    public static boolean isInside(Level level, Vec3 pos) {
        for (Zone zone : ZONES) {
            if (zone.dimension().equals(level.dimension())
                    && zone.centre().distanceToSqr(pos) <= RADIUS * RADIUS) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        ZONES.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ZONES.isEmpty()) {
            return;
        }
        Iterator<Zone> iterator = ZONES.iterator();
        List<Zone> replacements = new ArrayList<>();
        while (iterator.hasNext()) {
            Zone zone = iterator.next();
            ServerLevel level = event.getServer().getLevel(zone.dimension());
            if (level == null || zone.remainingTicks() <= 1) {
                iterator.remove();
                continue;
            }
            steep(level, zone);
            iterator.remove();
            replacements.add(new Zone(zone.dimension(), zone.centre(), zone.remainingTicks() - 1));
        }
        ZONES.addAll(replacements);
    }

    /** One tick of a pool: conceal whoever is standing in it, and keep it visible. */
    private static void steep(ServerLevel level, Zone zone) {
        Vec3 centre = zone.centre();
        AABB box = new AABB(centre, centre).inflate(RADIUS);
        for (LivingEntity inside : level.getEntitiesOfClass(LivingEntity.class, box,
                candidate -> candidate.isAlive() && candidate.distanceToSqr(centre) <= RADIUS * RADIUS)) {
            inside.addEffect(new MobEffectInstance(
                    ModEffects.CAMOUFLAGE, OCCUPANT_REFRESH_TICKS, 0, true, false, true));
            inside.addEffect(new MobEffectInstance(
                    MobEffects.DARKNESS, OCCUPANT_REFRESH_TICKS, 0, true, false, true));
            CamouflageHandler.forgetTargets(inside);
        }

        // Particles every few ticks rather than every tick: a four-block sphere refilled twenty times
        // a second is a lot of packets for something already opaque.
        if (zone.remainingTicks() % 4 == 0) {
            level.sendParticles(ParticleTypes.SQUID_INK,
                    centre.x, centre.y + 1.0, centre.z, 24, RADIUS * 0.5, 1.0, RADIUS * 0.5, 0.01);
            level.sendParticles(ParticleTypes.SMOKE,
                    centre.x, centre.y + 0.5, centre.z, 12, RADIUS * 0.4, 0.6, RADIUS * 0.4, 0.005);
        }
        // Re-attract on a slow cadence, so a Hidebehind that wandered off comes back while it lasts.
        if (zone.remainingTicks() % 40 == 0) {
            pullHidebehinds(level, centre);
        }
    }

    /** Sends every nearby Hidebehind toward the pool and makes it forget what it was stalking. */
    private static void pullHidebehinds(Level level, Vec3 centre) {
        AABB range = new AABB(centre, centre).inflate(ATTRACTION_RADIUS);
        for (HidebehindEntity beast : level.getEntitiesOfClass(HidebehindEntity.class, range)) {
            beast.setTarget(null);
            beast.getNavigation().moveTo(centre.x, centre.y, centre.z, 1.1);
        }
    }
}
