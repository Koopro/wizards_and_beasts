package at.koopro.wizardsandbeasts.client.atmosphere;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Gives each recognised place its air: runs {@link PlaceSense}, then for every place with any strength spends its
 * emitters' particles, plays its sparse one-shot sounds and keeps its room tone at the right volume.
 *
 * <h2>Budget</h2>
 * <ul>
 *   <li>Nothing at all while paused, with no level, or with {@code Config.atmosphere} OFF.</li>
 *   <li>Every emitter is bounded by its own reach (at most 24 blocks) and by loaded chunks; an anchor emitter only
 *       starts at blocks the sampler has just seen.</li>
 *   <li>At most {@link #TICK_CAP} particles a tick across every place; the vanilla Particles option thins those
 *       further ({@code ClientLevel.addParticle} does that for every non-forced particle).</li>
 *   <li>Sounds: one-shots on intervals of seconds; one room-tone instance per place, capped at 0.25 volume.</li>
 * </ul>
 *
 * <p>Client-side and cosmetic: nothing is sent and nothing the server knows changes.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
@NullMarked
public final class AtmosphereDirector {

    /** Particles per tick across every place, at FULL. */
    static final int TICK_CAP = 16;
    /** Tries to find a spot per particle before giving up on it this tick. */
    private static final int TRIES = 4;
    /** A place this faint makes no sound yet. */
    private static final float SOUND_FLOOR = 0.25f;

    private static @Nullable ClientLevel lastLevel;
    private static AtmosphereProfile.Ambience ambience = new AtmosphereProfile.Ambience(false, false, false, true);

    private AtmosphereDirector() {}

    /** What the conditions saw last tick; the fog uses it too. */
    static AtmosphereProfile.Ambience ambience() {
        return ambience;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Entity camera = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        if (level != lastLevel) {
            lastLevel = level;
            PlaceSense.reset();
        }
        if (level == null || camera == null || mc.isPaused()) {
            return;
        }
        float scale = Config.atmosphere.scale;
        if (scale <= 0f) {
            PlaceSense.reset();
            fadeBeds();
            return;
        }
        RandomSource random = level.random;
        BlockPos eye = BlockPos.containing(camera.getEyePosition());
        ambience = new AtmosphereProfile.Ambience(
                level.dimensionType().hasSkyLight() && level.getSkyDarken() >= 4,
                level.isRaining(), level.isThundering(), level.canSeeSky(eye));
        PlaceSense.tick(level, camera, ambience, random);

        int budget = Math.max(1, Math.round(TICK_CAP * scale));
        for (PlaceSense.Place place : PlaceSense.places()) {
            float strength = place.strength();
            if (strength <= 0.01f) {
                bed(mc, place, 0f, random);
                continue;
            }
            budget = emit(level, camera, place, strength * scale, budget, random);
            sounds(level, camera, place, strength, random);
            AtmosphereProfile.Bed bed = place.profile.bed().orElse(null);
            float target = bed != null && bed.when().test(ambience) && place.gated
                    ? bed.volume() * strength * Config.ambientSoundVolume : 0f;
            bed(mc, place, target, random);
        }
    }

    private static void fadeBeds() {
        for (PlaceSense.Place place : PlaceSense.places()) {
            if (place.bed != null) {
                place.bed.release();
            }
        }
    }

    // ---- particles -----------------------------------------------------------------------------------------------

    private static int emit(ClientLevel level, Entity camera, PlaceSense.Place place, float amount, int budget,
                            RandomSource random) {
        var emitters = place.profile.emitters();
        for (int e = 0; e < emitters.size() && budget > 0; e++) {
            AtmosphereProfile.Emitter emitter = emitters.get(e);
            if (!emitter.when().test(ambience)) {
                place.carry[e] = 0f;
                continue;
            }
            place.carry[e] += emitter.rate() * amount;
            int n = (int) place.carry[e];
            place.carry[e] -= n;
            SpellTintParticleOptions options = AtmosphereParticles.options(emitter.particle(), emitter.colour());
            if (options == null) {
                continue;
            }
            for (int i = 0; i < n && budget > 0; i++) {
                Vec3 at = spot(level, camera, place, emitter, random);
                if (at != null) {
                    level.addParticle(options, at.x, at.y, at.z, 0, 0, 0);
                    budget--;
                }
            }
        }
        return budget;
    }

    /** Somewhere this emitter may put a particle now, or {@code null} after a few failed tries. */
    private static @Nullable Vec3 spot(ClientLevel level, Entity camera, PlaceSense.Place place,
                                       AtmosphereProfile.Emitter emitter, RandomSource random) {
        int reach = emitter.reach();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int t = 0; t < TRIES; t++) {
            switch (emitter.where()) {
                case ANCHOR -> {
                    BlockPos anchor = place.anyAnchor(random);
                    if (anchor == null || anchor.distSqr(camera.blockPosition()) > (double) reach * reach
                            || !level.isLoaded(anchor) || !stillMatches(level.getBlockState(anchor), place)) {
                        continue;
                    }
                    var off = emitter.offset();
                    var spread = emitter.spread();
                    double x = anchor.getX() + off.x() + (random.nextDouble() * 2 - 1) * spread.x();
                    double y = anchor.getY() + off.y() + (random.nextDouble() * 2 - 1) * spread.y();
                    double z = anchor.getZ() + off.z() + (random.nextDouble() * 2 - 1) * spread.z();
                    pos.set(x, y, z);
                    if (open(level, pos, emitter)) {
                        return new Vec3(x, y, z);
                    }
                }
                case AIR -> {
                    pos.set(camera.getBlockX() + random.nextInt(reach * 2 + 1) - reach,
                            camera.getBlockY() + random.nextInt(reach + 1) - reach / 2 + 1,
                            camera.getBlockZ() + random.nextInt(reach * 2 + 1) - reach);
                    // Not right at the camera: a speck a hand from the eye is drawn as a blot.
                    if (pos.distToCenterSqr(camera.getEyePosition()) < 4.0) {
                        continue;
                    }
                    if (level.isLoaded(pos) && level.getBlockState(pos).isAir() && lit(level, pos, emitter)) {
                        return new Vec3(pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                                pos.getZ() + random.nextDouble());
                    }
                }
                case GROUND -> {
                    int x = camera.getBlockX() + random.nextInt(reach * 2 + 1) - reach;
                    int z = camera.getBlockZ() + random.nextInt(reach * 2 + 1) - reach;
                    pos.set(x, camera.getBlockY(), z);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (Math.abs(top - camera.getY()) > reach / 2.0) {
                        continue;
                    }
                    pos.set(x, top, z);
                    if (!lit(level, pos, emitter)) {
                        continue;
                    }
                    // Big soft things (mist) only at a distance: one sheet close to the eye reads as a card.
                    if (emitter.particle().getPath().equals("mist")
                            && pos.distToCenterSqr(camera.getEyePosition()) < 16.0) {
                        continue;
                    }
                    return new Vec3(x + random.nextDouble(), top + emitter.height().pick(random),
                            z + random.nextDouble());
                }
            }
        }
        return null;
    }

    private static boolean stillMatches(BlockState state, PlaceSense.Place place) {
        for (AtmosphereProfile.BlockRule rule : place.profile.blocks()) {
            if (state.is(rule.tag())) {
                return true;
            }
        }
        return false;
    }

    private static boolean open(ClientLevel level, BlockPos pos, AtmosphereProfile.Emitter emitter) {
        return level.isLoaded(pos) && !level.getBlockState(pos).isSolidRender() && lit(level, pos, emitter);
    }

    private static boolean lit(ClientLevel level, BlockPos pos, AtmosphereProfile.Emitter emitter) {
        if (emitter.minLight() <= 0 && emitter.maxLight() >= 15) {
            return true;
        }
        int light = level.getMaxLocalRawBrightness(pos);
        return light >= emitter.minLight() && light <= emitter.maxLight();
    }

    // ---- sound ---------------------------------------------------------------------------------------------------

    private static void sounds(ClientLevel level, Entity camera, PlaceSense.Place place, float strength,
                               RandomSource random) {
        var cues = place.profile.sounds();
        float volumeScale = Config.ambientSoundVolume;
        for (int c = 0; c < cues.size(); c++) {
            AtmosphereProfile.SoundCue cue = cues.get(c);
            if (place.soundIn[c] < 0) {
                // First schedule lands anywhere in the interval, so a place does not open on a sound.
                place.soundIn[c] = Math.round(cue.interval().pick(random) * 20f);
                continue;
            }
            if (--place.soundIn[c] > 0) {
                continue;
            }
            place.soundIn[c] = Math.round(cue.interval().pick(random) * 20f);
            if (strength < SOUND_FLOOR || volumeScale <= 0f || !cue.when().test(ambience)) {
                continue;
            }
            Vec3 at = switch (cue.at()) {
                case ANCHOR -> {
                    BlockPos anchor = place.anyAnchor(random);
                    yield anchor == null ? null : Vec3.atCenterOf(anchor);
                }
                case AROUND -> {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = 4 + random.nextDouble() * 6;
                    yield camera.getEyePosition().add(Math.cos(angle) * dist, random.nextDouble() * 3 - 1,
                            Math.sin(angle) * dist);
                }
            };
            if (at == null) {
                continue;
            }
            level.playLocalSound(at.x, at.y, at.z, event(cue.sound()), SoundSource.AMBIENT,
                    cue.volume() * strength * volumeScale, cue.pitch().pick(random), false);
        }
    }

    private static void bed(Minecraft mc, PlaceSense.Place place, float target, RandomSource random) {
        AmbientBed bed = place.bed;
        if (bed != null && bed.isStopped()) {
            place.bed = bed = null;
        }
        if (bed == null) {
            if (target <= 0.001f) {
                return;
            }
            AtmosphereProfile.Bed def = place.profile.bed().orElse(null);
            if (def == null) {
                return;
            }
            bed = new AmbientBed(event(def.sound()), random);
            place.bed = bed;
            mc.getSoundManager().play(bed);
        }
        bed.target(target);
    }

    static SoundEvent event(Identifier id) {
        return BuiltInRegistries.SOUND_EVENT.getOptional(id).orElseGet(() -> SoundEvent.createVariableRangeEvent(id));
    }
}
