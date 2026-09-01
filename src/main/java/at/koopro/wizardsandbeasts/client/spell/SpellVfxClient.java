package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The client-side sink for spell VFX, and the map of where to plug a new one in.
 *
 * <h2>Where per-spell look and sound come from</h2>
 * All four are already data-driven. A new spell that wants its own presentation almost never needs
 * Java — check this list before writing any:
 *
 * <table border="1">
 *   <caption>Plug points</caption>
 *   <tr><th>What</th><th>Where you author it</th><th>What reads it</th></tr>
 *   <tr>
 *     <td><b>Cast sound</b></td>
 *     <td>{@code "sound": { "id": …, "volume": …, "pitch": … }} in the spell JSON
 *         ({@code SpellDefinition.SoundDef}); Java spells set it on {@code SpellProperties.Builder.sound}</td>
 *     <td>{@code Spell.playSound}, called from {@code SpellExecutor.dispatchGeneric} — except for the
 *         two beam cast types, which own their audio for the length of the channel</td>
 *   </tr>
 *   <tr>
 *     <td><b>Trail particles</b></td>
 *     <td>{@code spellFamily} + {@code color} in the spell JSON</td>
 *     <td>{@code SpellProjectileEntity.tick}, tinting {@code ModParticles.tinted(family, argb)}.
 *         Emission rate is throttled by {@code Config.perfProfile}, so do not add an unthrottled
 *         per-tick emitter of your own</td>
 *   </tr>
 *   <tr>
 *     <td><b>Impact burst</b></td>
 *     <td>the same {@code spellFamily} + {@code color}</td>
 *     <td>server sends {@code SpellImpactBurstS2CPayload.sendToTracking(...)}; the client handler
 *         lands on {@link #spawnTintBurst}</td>
 *   </tr>
 *   <tr>
 *     <td><b>Beam look</b></td>
 *     <td>{@code BeamStyles} / {@code BeamSettings} under {@code client.wand}</td>
 *     <td>{@code client.beam}, the only beam renderer</td>
 *   </tr>
 * </table>
 *
 * <h2>When Java really is needed</h2>
 * Write it here, not at the call site. Everything visible or audible about a spell reaches the client
 * through this class or {@code client.beam}, which is what keeps the client-only render types out of
 * {@code SpellClientPayloadHandlers} — that class is loaded <em>server</em>-side when the payload
 * registrar resolves its method references, so a client-only type named in its signatures would fail
 * verification on a dedicated server. Add a static method here and call it from a handler lambda.
 *
 * <p>A genuinely bespoke effect still needs a payload: put a bounded one in {@code network/spell},
 * register it in {@code ModNetworkSpells}, and keep the decision of <em>whether</em> it fires on the
 * server. The client is told what happened; it does not decide that anything did.
 */
public final class SpellVfxClient {

    private SpellVfxClient() {}

    /** Low-pitched bass note — audible cast-denied feedback, no world position. */
    public static void playDeniedFeedback() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f));
    }

    public static void spawnTintBurst(Vec3 pos, SpellFamily family, int argb, int count, float spread) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        RandomSource rand = mc.level.random;
        var opts = ModParticles.tinted(family, argb);
        for (int i = 0; i < count; i++) {
            double ox = (rand.nextDouble() - 0.5) * spread * 2.0;
            double oy = (rand.nextDouble() - 0.5) * spread * 2.0;
            double oz = (rand.nextDouble() - 0.5) * spread * 2.0;
            mc.level.addParticle(opts, pos.x + ox, pos.y + oy, pos.z + oz, 0.0, 0.02, 0.0);
        }
    }

    public static void spawnTintBeam(Vec3 from, Vec3 to, SpellFamily family, int argb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        var opts = ModParticles.tinted(family, argb);
        double dist = from.distanceTo(to);
        if (dist < 0.02) {
            return;
        }
        int steps = Math.max(10, (int) (dist * 14.0));
        RandomSource rand = mc.level.random;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 p = from.lerp(to, t);
            double jitter = 0.025 + rand.nextDouble() * 0.03;
            double dx = (rand.nextDouble() - 0.5) * jitter;
            double dy = (rand.nextDouble() - 0.5) * jitter;
            double dz = (rand.nextDouble() - 0.5) * jitter;
            mc.level.addParticle(opts, p.x, p.y, p.z, dx, dy, dz);
        }
    }
}
