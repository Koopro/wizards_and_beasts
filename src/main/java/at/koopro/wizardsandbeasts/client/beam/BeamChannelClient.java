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
        beam.setSpellId(spellId);
        beam.setRange(range);
        mc.level.addEntity(beam);
        BEAMS.put(casterId, beam);
    }

    /**
     * Spawns a beam on the local player that is not tied to a channel, so the editor has something
     * to look at. Uses the editor's own values directly rather than a spell's, and its own range —
     * there is no server session behind it to ramp against.
     */
    public static void startPreview() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        stopPreview();
        BeamEntity beam = new BeamEntity(ModEntities.BEAM.get(), mc.level);
        beam.setId(BeamEntity.nextClientId());
        beam.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        beam.configure(mc.player.getId(), BeamStyleEditor.style(), BeamStyleEditor.shape(), 0f);
        beam.setRange(BeamStyleEditor.previewRange);
        mc.level.addEntity(beam);
        PREVIEW = beam;
    }

    /** Drops the editor's preview beam. Real channel beams are untouched. */
    public static void stopPreview() {
        if (PREVIEW != null) {
            PREVIEW.discard();
            PREVIEW = null;
        }
    }

    /** Keeps the preview's reach in step with the editor's range slider. */
    public static void syncPreviewRange() {
        if (PREVIEW != null) {
            PREVIEW.setRange(BeamStyleEditor.previewRange);
        }
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
