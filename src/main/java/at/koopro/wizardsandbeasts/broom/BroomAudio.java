package at.koopro.wizardsandbeasts.broom;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.Optional;

/**
 * The sound a broom makes and the trail it leaves.
 *
 * <p>All three are optional; a broom naming none keeps the shared flight sound and cloud wisps that
 * every broom had before, so this is additive by construction.
 *
 * <h2>Why these are ids and not registry objects</h2>
 * A broom definition is datapack data and loads on the server, where a client-only particle provider
 * does not exist and a sound event may belong to a mod the server does not have. Holding the id and
 * resolving it at the point of use means a bad id costs one silent effect, not a failed datapack
 * load — and {@link #trailParticleOrDefault()} falls back rather than throwing.
 *
 * <h2>Trail particles are vanilla types on purpose</h2>
 * {@code trailParticle} resolves against the live particle registry, so a definition can name
 * {@code minecraft:flame}, {@code minecraft:end_rod}, {@code minecraft:ash} or any mod particle that
 * takes no options. Registering seven bespoke {@code broom_trail_*} types whose only difference is
 * colour would be seven registry entries, seven providers and seven sprite sets to say what
 * {@code minecraft:flame} already says.
 */
public record BroomAudio(
        Optional<Identifier> boostSound,
        Optional<Identifier> idleLoopSound,
        Optional<Identifier> trailParticle) {

    /** What a broom that names no audio gets: the shared wind, no boost cue, cloud wisps. */
    public static final BroomAudio DEFAULT =
            new BroomAudio(Optional.empty(), Optional.empty(), Optional.empty());

    static BroomAudio decode(BroomFields<?> fields) {
        return new BroomAudio(
                fields.maybe("boostSound", Identifier.CODEC),
                fields.maybe("idleLoopSound", Identifier.CODEC),
                fields.maybe("trailParticle", Identifier.CODEC));
    }

    /** The registered boost cue, or empty when the broom names none or names one nothing registered. */
    public Optional<SoundEvent> resolveBoostSound() {
        return boostSound.map(BuiltInRegistries.SOUND_EVENT::getValue);
    }

    /** The registered flight loop, or empty — callers keep their own fallback. */
    public Optional<SoundEvent> resolveIdleLoopSound() {
        return idleLoopSound.map(BuiltInRegistries.SOUND_EVENT::getValue);
    }

    /**
     * The trail particle, falling back to the cloud wisp every broom used to shed.
     *
     * <p>Only option-free particle types can be named. A type that needs options — the mod's own
     * tinted spell particles, for one — cannot be built from an id alone, so it falls back rather
     * than crashing the render thread on a value a datapack was free to write.
     */
    public SimpleParticleType trailParticleOrDefault() {
        return trailParticle
                .map(BuiltInRegistries.PARTICLE_TYPE::getValue)
                .filter(SimpleParticleType.class::isInstance)
                .map(SimpleParticleType.class::cast)
                .orElse(ParticleTypes.CLOUD);
    }

    <T> void encode(RecordBuilder<T> builder, DynamicOps<T> ops) {
        boostSound.ifPresent(id ->
                builder.add("boostSound", Identifier.CODEC.encodeStart(ops, id).result().orElseThrow()));
        idleLoopSound.ifPresent(id ->
                builder.add("idleLoopSound", Identifier.CODEC.encodeStart(ops, id).result().orElseThrow()));
        trailParticle.ifPresent(id ->
                builder.add("trailParticle", Identifier.CODEC.encodeStart(ops, id).result().orElseThrow()));
    }
}
