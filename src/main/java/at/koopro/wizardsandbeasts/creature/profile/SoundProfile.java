package at.koopro.wizardsandbeasts.creature.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * What a creature sounds like.
 *
 * <p>The audit's starkest number: one hundred and nineteen of a hundred and twenty entities are
 * silent, and every entity sound in the mod belongs to the Niffler. Meanwhile sixty-nine rigs already
 * carry a voice clip — {@code call}, {@code hiss}, {@code groan}, {@code song}, {@code howl} — which
 * {@code playAmbientSound} already fires. The animations for vocalising exist and play against
 * nothing.
 *
 * <p>Sounds are named by registry id rather than by a mod enum so a datapack can point a creature at
 * a vanilla sound without waiting for a code change. Resolution goes through
 * {@code BuiltInRegistries.SOUND_EVENT}, so this reuses the existing sound registration wholesale and
 * adds no second registry. A creature id is never mentioned in here: the engine reads the profile,
 * the profile comes from the creature's own file.
 *
 * <p>Every field is optional, and an absent field means no sound rather than a fallback. Silence is a
 * legitimate design choice — a Lethifold ought to be silent — so this never invents a noise for a
 * creature whose author did not ask for one.
 *
 * @param ambient the idle voice, on vanilla's ambient beat
 * @param hurt    on taking damage
 * @param death   on dying
 * @param attack  on landing a melee attack
 * @param special for an ability or a bespoke moment to fire deliberately
 */
@NullMarked
public record SoundProfile(
        Optional<Identifier> ambient,
        Optional<Identifier> hurt,
        Optional<Identifier> death,
        Optional<Identifier> attack,
        Optional<Identifier> special) {

    public static final SoundProfile SILENT = new SoundProfile(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<SoundProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("ambient").forGetter(SoundProfile::ambient),
            Identifier.CODEC.optionalFieldOf("hurt").forGetter(SoundProfile::hurt),
            Identifier.CODEC.optionalFieldOf("death").forGetter(SoundProfile::death),
            Identifier.CODEC.optionalFieldOf("attack").forGetter(SoundProfile::attack),
            Identifier.CODEC.optionalFieldOf("special").forGetter(SoundProfile::special)
    ).apply(instance, SoundProfile::new));

    public @Nullable SoundEvent ambientSound() {
        return resolve(ambient);
    }

    public @Nullable SoundEvent hurtSound() {
        return resolve(hurt);
    }

    public @Nullable SoundEvent deathSound() {
        return resolve(death);
    }

    public @Nullable SoundEvent attackSound() {
        return resolve(attack);
    }

    public @Nullable SoundEvent specialSound() {
        return resolve(special);
    }

    /**
     * Null for an id nothing registered, rather than a throw.
     *
     * <p>A datapack naming a sound that is not present should leave the creature quiet, not crash the
     * entity every time it is hurt. The same tolerance the clip gate applies to animations.
     */
    private static @Nullable SoundEvent resolve(Optional<Identifier> id) {
        return id.map(BuiltInRegistries.SOUND_EVENT::getValue).orElse(null);
    }
}
