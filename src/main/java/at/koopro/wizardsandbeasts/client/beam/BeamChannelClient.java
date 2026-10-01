package at.koopro.wizardsandbeasts.client.beam;

import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the live {@link BeamEntity} per caster and drives it from the server's channel packets.
 *
 * <p>The beams are client-only entities: the server never spawns one, it only says <em>who</em> is
 * channelling <em>what</em>. Everything else — where the beam starts, where it ends, what it looks
 * like — is re-derived here every frame, which is why none of it is on the wire.
 */
public final class BeamChannelClient {

    /** Caster entity id -> its beam. */
    private static final Map<Integer, BeamEntity> BEAMS = new ConcurrentHashMap<>();

    /** The editor's stand-in beam, kept out of {@link #BEAMS} so no channel packet can evict it. */
    private static BeamEntity PREVIEW;

    private BeamChannelClient() {}

    /**
     * A channel started (or is still running). Idempotent: a repeat announcement for a caster that
     * already has a live beam of the same spell is ignored, which is what lets the server re-send
     * on an interval for the benefit of players who only just started tracking the caster.
     */
    public static void start(int casterId, String spellId, float range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        BeamEntity existing = BEAMS.get(casterId);
        if (existing != null) {
            if (!existing.isRemoved() && spellId.equals(existing.getSpellId())) {
                existing.setRange(range);
                return;
            }
            existing.discard();
            BEAMS.remove(casterId);
        }

        Entity caster = mc.level.getEntity(casterId);
        if (caster == null) {
            return;
        }
        Spell spell = Spells.byId(spellId);
        Optional<BeamAppearance.Appearance> look = BeamAppearance.forSpell(spell);
        if (look.isEmpty()) {
            return; // not a beam spell, or one that deliberately draws nothing (Leviosa)
        }

        BeamEntity beam = new BeamEntity(ModEntities.BEAM.get(), mc.level);
        beam.setId(BeamEntity.nextClientId());
        beam.setPos(caster.getX(), caster.getY(), caster.getZ());
        // extendSpeed 0: the beam's length comes from the server's reach ramp, re-resolved per
        // frame in BeamEntityRenderer. A second growth animation on top would fight it.
        beam.configure(casterId, look.get().style(), look.get().shape(), 0f);
        beam.restyle(look.get());
        beam.setSpellId(spellId);
        beam.setRange(range);
        mc.level.addEntity(beam);
        BEAMS.put(casterId, beam);
    }

    /**
     * How long a preview beam lives without being kept alive. An editor that is open keeps calling
     * {@link #keepPreviewAlive}; one that went away without cleaning up (a crash in a screen, a lost
     * close event) cannot leave a beam running for more than this.
     */
    public static final int PREVIEW_TTL_TICKS = 100;

    /**
     * Spawns a beam on the local player that is not tied to a channel, so an editor has something to
     * look at in the world. It draws whatever {@code look} supplies each frame and reaches
     * {@code range} blocks — there is no server session behind it to ramp against. Nothing is sent to
     * the server and no other player sees it.
     */
    public static void startPreview(java.util.function.Supplier<BeamAppearance.Appearance> look, float range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        stopPreview();
        BeamAppearance.Appearance first = look.get();
        BeamEntity beam = new BeamEntity(ModEntities.BEAM.get(), mc.level);
        beam.setId(BeamEntity.nextClientId());
        beam.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        beam.configure(mc.player.getId(), first.style(), first.shape(), 0f);
        beam.setLiveLook(look);
        beam.setRange(range);
        beam.keepAliveFor(PREVIEW_TTL_TICKS);
        mc.level.addEntity(beam);
        PREVIEW = beam;
    }

    /** The debug editor's preview: the {@link BeamStyleEditor} working copy. */
    public static void startPreview() {
        startPreview(() -> new BeamAppearance.Appearance(BeamStyleEditor.style(), BeamStyleEditor.shape()),
                BeamStyleEditor.previewRange);
    }

    public static boolean previewRunning() {
        return PREVIEW != null && !PREVIEW.isRemoved();
    }

    /** Called by an open editor each tick so its preview outlives {@link #PREVIEW_TTL_TICKS}. */
    public static void keepPreviewAlive() {
        if (PREVIEW != null) {
            if (PREVIEW.isRemoved()) {
                PREVIEW = null;
            } else {
                PREVIEW.keepAliveFor(PREVIEW_TTL_TICKS);
            }
        }
    }

    /** Sets the preview's reach, in blocks. */
    public static void setPreviewRange(float range) {
        if (PREVIEW != null) {
            PREVIEW.setRange(range);
        }
    }

    /**
     * The server's beam visuals changed: every live channel beam takes its spell's new look at once,
     * and a beam whose spell is now switched off goes.
     */
    public static void restyleAll() {
        BEAMS.entrySet().removeIf(entry -> {
            BeamEntity beam = entry.getValue();
            Optional<BeamAppearance.Appearance> look = BeamAppearance.forSpell(Spells.byId(beam.getSpellId()));
            if (look.isEmpty()) {
                beam.discard();
                return true;
            }
            beam.restyle(look.get());
            return false;
        });
    }

    /** Drops the editor's preview beam. Real channel beams are untouched. */
    public static void stopPreview() {
        if (PREVIEW != null) {
            PREVIEW.discard();
            PREVIEW = null;
        }
    }

    /** How many channel beams this client is drawing (the Debug tab's readout). */
    public static int liveBeamCount() {
        return BEAMS.size();
    }

    /** The channel ended — let the beam fade out and drop it. */
    public static void stop(int casterId) {
        BeamEntity beam = BEAMS.remove(casterId);
        if (beam != null) {
            beam.setActive(false);
        }
    }

    /** One entity left the level; if it was a caster, its beam goes with it. */
    public static void forget(int casterId) {
        BeamEntity beam = BEAMS.remove(casterId);
        if (beam != null) {
            beam.discard();
        }
    }

    /** Level teardown — drop everything. */
    public static void clear() {
        BEAMS.values().forEach(BeamEntity::discard);
        BEAMS.clear();
        stopPreview();
    }
}
