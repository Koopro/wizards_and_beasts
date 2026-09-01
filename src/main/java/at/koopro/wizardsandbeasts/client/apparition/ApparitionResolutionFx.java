package at.koopro.wizardsandbeasts.client.apparition;

import at.koopro.wizardsandbeasts.apparition.ApparitionCrackVariant;
import at.koopro.wizardsandbeasts.apparition.ApparitionPhase;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPresentationState;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Everything an Apparition sounds and looks like at the two ends of it: the gathering, the crack, the twist
 * and the soot left hanging afterwards.
 *
 * <p>This is the consumer {@link ClientApparitionPresentationState#drainResolutions()} never had. The server
 * has always resolved which crack an observer hears and how far it carries, serialised both, and sent them to
 * everyone at <i>either</i> end of the jump — and nothing on the client ever read the queue. The audible
 * Apparition was a hardcoded {@code entity.enderman.teleport} played server-side at a fixed volume, so
 * {@link ApparitionCrackVariant} and the whole proficiency-scaled stealth curve behind it were inaudible.
 *
 * <h2>Nothing here is local to the caster</h2>
 *
 * <p>Every cue is driven by a packet about a player, not by the acting client's own knowledge of what it just
 * did, so a bystander sees and hears the same Apparition the caster does. The single exception is the FOV
 * punch, which is a camera effect and belongs to exactly one pair of eyes.
 *
 * <h2>Radius is the volume</h2>
 *
 * <p>Vanilla attenuates a sound to silence at {@code 16 * volume} blocks, so the server's audible radius maps
 * onto a volume by dividing by sixteen. That is not a fudge factor: it is the inverse of the attenuation
 * vanilla already applies, which is what makes a practised wizard's eight-block crack actually inaudible at
 * nine blocks rather than merely quiet.
 */
@NullMarked
public final class ApparitionResolutionFx {

    /** Blocks per unit volume in vanilla's attenuation. */
    private static final float BLOCKS_PER_VOLUME = 16.0f;

    /** Points on one spiral. Enough to read as a line, few enough to cost nothing. */
    private static final int SPIRAL_POINTS = 40;
    /** How many times the twist wraps the body. */
    private static final double SPIRAL_TURNS = 2.5;
    /** Roughly a player's height. */
    private static final double SPIRAL_HEIGHT = 1.9;
    private static final double SPIRAL_RADIUS = 0.6;

    /**
     * Soot and deep purple, and pointedly nothing green.
     *
     * <p>The Floo owns emerald: a flare, a column and a whoosh. Apparition has to be legible as a different
     * kind of travel from across a room, so it is ash and a crack, in a palette the Network never uses.
     */
    private static final int COLOUR_SOOT = 0x241E28;
    private static final int COLOUR_VIOLET = 0x5B2E7A;
    /** What a tear looks like. Used at the origin of a splinch, never at an arrival. */
    private static final int COLOUR_SPLINCH = 0x9E2B2B;

    /** Departure sits a touch below arrival, so leaving and landing are not the same event by ear. */
    private static final float PITCH_DEPART = 0.96f;
    private static final float PITCH_ARRIVE = 1.04f;
    /** The gathering, low as it begins and higher as the window opens. */
    private static final float PITCH_WINDUP_BEGIN = 0.85f;
    private static final float PITCH_WINDUP_OPEN = 1.25f;
    /**
     * The wind-up carries about eight blocks. Short on purpose: it is the tell that lets somebody standing
     * next to you interrupt an anchored jump, not an announcement to the whole valley.
     */
    private static final float WINDUP_VOLUME = 0.5f;

    /** How long soot hangs after an Apparition. One second, as the pillar asks. */
    private static final int SOOT_TICKS = 20;
    private static final int SOOT_ASH_PER_TICK = 2;

    /** Ticks the local caster's view stays punched after their own vanish. */
    static final int FOV_PUNCH_TICKS = 6;
    /** Peak widening. Same order as the broom's, which is the most this mod ever moves a camera. */
    static final float FOV_PUNCH_STRENGTH = 0.05f;

    /** Soot still hanging where somebody left or arrived. */
    private record Soot(Vec3 at, int expiresAtTick, int colour) {}

    private static final List<Soot> SOOT = new ArrayList<>();
    /**
     * Which wind-up cues each in-flight charge has already been given, keyed by caster entity id.
     *
     * <p>Needed because the charge packet arrives every tick and carries no "this is new" bit: without a
     * memory of what has already fired, the gathering would restart on every one of them.
     */
    private static final Map<Integer, ApparitionPhase> WINDUP_CUED = new HashMap<>();

    private static int clientTick;
    private static int fovPunchTicksLeft;

    private ApparitionResolutionFx() {}

    /**
     * Plays everything that resolved since the last client tick.
     *
     * <p>Drains rather than peeks: each resolution is one event and is rendered exactly once. Anything that
     * arrives while the client has no level is dropped with it, which is the correct outcome — there is
     * nowhere to draw it.
     */
    public static void drainAndPlay(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            ClientApparitionPresentationState.drainResolutions();
            return;
        }
        for (ClientApparitionPresentationState.Resolution event
                : ClientApparitionPresentationState.drainResolutions()) {
            playDeparture(level, event);
            if (event.arrived()) {
                playArrival(level, event);
            }
            if (mc.player != null && mc.player.getId() == event.casterId()) {
                fovPunchTicksLeft = FOV_PUNCH_TICKS;
            }
        }
    }

    /**
     * One client tick of the ambient half: the gathering cues for charges in flight, and the soot still
     * settling from ones that finished.
     */
    public static void tick(Minecraft mc) {
        clientTick++;
        cueWindUps(mc);
        settleSoot(mc);
        if (fovPunchTicksLeft > 0) {
            fovPunchTicksLeft--;
        }
    }

    /** Drops everything. Called on disconnect, so no soot or half-cued charge survives into the next world. */
    public static void clear() {
        SOOT.clear();
        WINDUP_CUED.clear();
        fovPunchTicksLeft = 0;
        clientTick = 0;
    }

    /**
     * How much wider the local player's view is right now, decaying to nothing.
     *
     * <p>Read by {@link ApparitionFovHandler} rather than applied here: the FOV event fires per frame and
     * this state advances per tick, and mixing the two clocks is how a camera effect starts stuttering.
     */
    static float fovPunch() {
        if (fovPunchTicksLeft <= 0) {
            return 0.0f;
        }
        return FOV_PUNCH_STRENGTH * (fovPunchTicksLeft / (float) FOV_PUNCH_TICKS);
    }

    // ── the gathering ─────────────────────────────────────────────────────────

    /**
     * The buildup, twice: once as a charge appears and once as its window opens.
     *
     * <p>Both are played on the caster's own position rather than the destination, because this is the sound
     * of a body being gathered up, and it is the only cue an observer standing behind a wizard gets.
     */
    private static void cueWindUps(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            WINDUP_CUED.clear();
            return;
        }
        Map<Integer, ClientApparitionPresentationState.Charge> charges =
                ClientApparitionPresentationState.charges();

        // Anything no longer charging is forgotten, so the next attempt by the same wizard cues again.
        WINDUP_CUED.keySet().removeIf(id -> !charges.containsKey(id));

        for (Map.Entry<Integer, ClientApparitionPresentationState.Charge> entry : charges.entrySet()) {
            ClientApparitionPresentationState.Charge charge = entry.getValue();
            // Until a destination resolves, the clock has not started and there is nothing to gather toward.
            if (charge.destination() == null || charge.elapsed() <= 0) {
                continue;
            }
            Entity caster = level.getEntity(entry.getKey());
            if (caster == null) {
                continue;
            }
            ApparitionPhase cued = WINDUP_CUED.get(entry.getKey());
            if (cued == null) {
                whoosh(level, caster, PITCH_WINDUP_BEGIN);
                WINDUP_CUED.put(entry.getKey(), ApparitionPhase.DETERMINATION);
            } else if (cued == ApparitionPhase.DETERMINATION && charge.isWindowOpen()) {
                whoosh(level, caster, PITCH_WINDUP_OPEN);
                WINDUP_CUED.put(entry.getKey(), ApparitionPhase.DELIBERATION);
            }
        }
    }

    private static void whoosh(ClientLevel level, Entity caster, float pitch) {
        level.playLocalSound(caster.getX(), caster.getY() + 1.0, caster.getZ(),
                ModSounds.APPARITION_WINDUP.get(), SoundSource.PLAYERS, WINDUP_VOLUME, pitch, false);
    }

    // ── the crack ─────────────────────────────────────────────────────────────

    private static void playDeparture(ClientLevel level, ClientApparitionPresentationState.Resolution event) {
        boolean torn = event.splinchTier().isSplinch();
        Vec3 origin = event.origin();

        crack(level, origin, event, PITCH_DEPART);
        if (torn) {
            // The tear is its own sound at the origin only, because the origin is where the part that did
            // not travel stays. A catastrophe never arrives anywhere, so this is the whole of what is heard.
            level.playLocalSound(origin.x, origin.y, origin.z, ModSounds.APPARITION_SPLINCH.get(),
                    SoundSource.PLAYERS, volumeFor(event.radius()), 1.0f, false);
        }
        spiral(level, origin, true, torn ? COLOUR_SPLINCH : COLOUR_VIOLET);
        SOOT.add(new Soot(origin, clientTick + SOOT_TICKS, torn ? COLOUR_SPLINCH : COLOUR_SOOT));
    }

    private static void playArrival(ClientLevel level, ClientApparitionPresentationState.Resolution event) {
        Vec3 destination = event.destination();
        if (destination == null) {
            return;
        }
        crack(level, destination, event, PITCH_ARRIVE);
        spiral(level, destination, false, COLOUR_VIOLET);
        // One puff at the feet. Vanilla's POOF rather than PORTAL: a wizard steps out of the air, and the
        // portal mote is the one particle in the game that means "the End".
        level.addParticle(ParticleTypes.POOF, destination.x, destination.y + 0.2, destination.z, 0.0, 0.0, 0.0);
        SOOT.add(new Soot(destination, clientTick + SOOT_TICKS, COLOUR_SOOT));
    }

    private static void crack(ClientLevel level, Vec3 at,
                              ClientApparitionPresentationState.Resolution event, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, soundFor(event.crackVariant()), SoundSource.PLAYERS,
                volumeFor(event.radius()), pitch, false);
    }

    /** The three cracks. Which one this is was decided on the server; the client only plays it. */
    private static SoundEvent soundFor(ApparitionCrackVariant variant) {
        return switch (variant) {
            case WIZARD -> ModSounds.APPARITION_CRACK_WIZARD.get();
            case ELF -> ModSounds.APPARITION_CRACK_ELF.get();
            case MUFFLED -> ModSounds.APPARITION_CRACK_MUFFLED.get();
        };
    }

    /** Inverts vanilla's attenuation, so the server's radius in blocks is the distance it actually carries. */
    private static float volumeFor(int radius) {
        return Math.max(0.05f, radius / BLOCKS_PER_VOLUME);
    }

    // ── what is left behind ───────────────────────────────────────────────────

    /**
     * Ash and smoke drifting for a second after the crack, at both ends.
     *
     * <p>The residue is the part a bystander actually catches: a crack is over in a frame, and somebody who
     * turned toward the noise a moment too late would otherwise find an empty room and no evidence anybody
     * had been in it.
     */
    private static void settleSoot(Minecraft mc) {
        if (SOOT.isEmpty()) {
            return;
        }
        ClientLevel level = mc.level;
        if (level == null) {
            SOOT.clear();
            return;
        }
        for (Iterator<Soot> it = SOOT.iterator(); it.hasNext(); ) {
            Soot soot = it.next();
            if (clientTick >= soot.expiresAtTick()) {
                it.remove();
                continue;
            }
            DustParticleOptions dust = new DustParticleOptions(soot.colour(), 0.8f);
            for (int i = 0; i < SOOT_ASH_PER_TICK; i++) {
                double dx = (level.random.nextDouble() - 0.5) * 0.8;
                double dz = (level.random.nextDouble() - 0.5) * 0.8;
                double dy = level.random.nextDouble() * SPIRAL_HEIGHT;
                level.addParticle(ParticleTypes.ASH,
                        soot.at().x + dx, soot.at().y + dy, soot.at().z + dz, 0.0, -0.01, 0.0);
                level.addParticle(dust,
                        soot.at().x + dx, soot.at().y + dy * 0.5, soot.at().z + dz, 0.0, 0.015, 0.0);
            }
            level.addParticle(ParticleTypes.SMOKE,
                    soot.at().x, soot.at().y + 0.6, soot.at().z, 0.0, 0.02, 0.0);
        }
    }

    /**
     * The twist, drawn as a helix around where a body was or will be.
     *
     * <p>Departing, it tightens as it climbs — the wizard screwing themselves out of the world. Arriving, it
     * runs the other way and opens out. Same curve, read in reverse, so the two are recognisably one motion.
     * Ash is threaded through it so the shape survives against a bright sky, where dust alone washes out.
     */
    private static void spiral(ClientLevel level, Vec3 centre, boolean departing, int colour) {
        DustParticleOptions dust = new DustParticleOptions(colour, 1.0f);
        DustParticleOptions soot = new DustParticleOptions(COLOUR_SOOT, 1.2f);
        for (int i = 0; i < SPIRAL_POINTS; i++) {
            double t = i / (double) (SPIRAL_POINTS - 1);
            double along = departing ? t : 1.0 - t;
            double angle = Math.PI * 2.0 * SPIRAL_TURNS * along * (departing ? 1.0 : -1.0);
            double radius = SPIRAL_RADIUS * (1.0 - along);
            double y = centre.y + SPIRAL_HEIGHT * along;
            double x = centre.x + Math.cos(angle) * radius;
            double z = centre.z + Math.sin(angle) * radius;
            double rise = departing ? 0.03 : -0.03;

            level.addParticle(i % 3 == 0 ? soot : dust, x, y, z, 0.0, rise, 0.0);
            if (i % 5 == 0) {
                level.addParticle(ParticleTypes.ASH, x, y, z, 0.0, rise * 0.5, 0.0);
            }
        }
    }
}
