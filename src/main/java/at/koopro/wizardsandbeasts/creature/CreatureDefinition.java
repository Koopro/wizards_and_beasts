package at.koopro.wizardsandbeasts.creature;

import at.koopro.wizardsandbeasts.creature.ability.CreatureAbility;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Datapack-driven creature definition, mirroring {@code BestiaryEntry}/{@code BroomDefinition}.
 * Loaded from {@code data/wizards_and_beasts/creatures/<id>.json} by {@link CreatureDefinitionLoader}.
 *
 * <p>Runtime source of truth for per-creature attributes (applied over the locomotion baseline on spawn),
 * body-plan/temperament metadata, and asset ids. Hitbox size and the registered {@code EntityType} are
 * created at mod-init from {@code ModCreatures.MANIFEST}; {@link #width()}/{@link #height()} here mirror
 * the manifest for documentation and are not re-applied to the frozen {@code EntityType}.
 *
 * <p>This record has 17 components, one past {@code RecordCodecBuilder.group}'s 16-arg ceiling, so the
 * codec is assembled from two {@link MapCodec} halves merged with {@link Codec#mapPair}. Both halves read
 * the <b>same flat</b> JSON object — the on-disk shape is unchanged, no nesting is introduced, and the new
 * {@code abilities} field defaults to an empty list so every existing creature JSON parses untouched.
 */
public record CreatureDefinition(
        Identifier id,
        BodyPlan bodyPlan,
        Locomotion locomotion,
        Temperament temperament,
        float width,
        float height,
        double maxHealth,
        double movementSpeed,
        double flyingSpeed,
        double followRange,
        double attackDamage,
        List<Trait> traits,
        Identifier model,
        Identifier texture,
        Identifier animation,
        Optional<DragonTraits> dragon,
        List<CreatureAbility> abilities) {

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.xmap(s -> Enum.valueOf(type, s), Enum::name);
    }

    /** First flat half: id, body-plan/locomotion/temperament metadata, and the numeric attribute baseline. */
    private record StatBlock(
            Identifier id, BodyPlan bodyPlan, Locomotion locomotion, Temperament temperament,
            float width, float height, double maxHealth, double movementSpeed,
            double flyingSpeed, double followRange, double attackDamage) {

        static final MapCodec<StatBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(StatBlock::id),
                enumCodec(BodyPlan.class).fieldOf("bodyPlan").forGetter(StatBlock::bodyPlan),
                enumCodec(Locomotion.class).fieldOf("locomotion").forGetter(StatBlock::locomotion),
                enumCodec(Temperament.class).optionalFieldOf("temperament", Temperament.NEUTRAL).forGetter(StatBlock::temperament),
                Codec.FLOAT.fieldOf("width").forGetter(StatBlock::width),
                Codec.FLOAT.fieldOf("height").forGetter(StatBlock::height),
                Codec.DOUBLE.fieldOf("maxHealth").forGetter(StatBlock::maxHealth),
                Codec.DOUBLE.fieldOf("movementSpeed").forGetter(StatBlock::movementSpeed),
                Codec.DOUBLE.optionalFieldOf("flyingSpeed", 0.0).forGetter(StatBlock::flyingSpeed),
                Codec.DOUBLE.optionalFieldOf("followRange", 16.0).forGetter(StatBlock::followRange),
                Codec.DOUBLE.optionalFieldOf("attackDamage", 0.0).forGetter(StatBlock::attackDamage)
        ).apply(instance, StatBlock::new));
    }

    /** Second flat half: trait vocabulary, asset ids, the dragon sub-block, and the ability layer. */
    private record AssetBlock(
            List<Trait> traits, Identifier model, Identifier texture, Identifier animation,
            Optional<DragonTraits> dragon, List<CreatureAbility> abilities) {

        static final MapCodec<AssetBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                enumCodec(Trait.class).listOf().optionalFieldOf("traits", List.of()).forGetter(AssetBlock::traits),
                Identifier.CODEC.fieldOf("model").forGetter(AssetBlock::model),
                Identifier.CODEC.fieldOf("texture").forGetter(AssetBlock::texture),
                Identifier.CODEC.fieldOf("animation").forGetter(AssetBlock::animation),
                DragonTraits.CODEC.optionalFieldOf("dragon").forGetter(AssetBlock::dragon),
                // Datapack-driven ability layer. Default empty so every existing creature JSON parses unchanged.
                CreatureAbility.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(AssetBlock::abilities)
        ).apply(instance, AssetBlock::new));
    }

    public static final Codec<CreatureDefinition> CODEC = Codec.mapPair(StatBlock.CODEC, AssetBlock.CODEC).codec().xmap(
            pair -> {
                StatBlock s = pair.getFirst();
                AssetBlock a = pair.getSecond();
                return new CreatureDefinition(
                        s.id(), s.bodyPlan(), s.locomotion(), s.temperament(), s.width(), s.height(),
                        s.maxHealth(), s.movementSpeed(), s.flyingSpeed(), s.followRange(), s.attackDamage(),
                        a.traits(), a.model(), a.texture(), a.animation(), a.dragon(), a.abilities());
            },
            def -> Pair.of(
                    new StatBlock(def.id(), def.bodyPlan(), def.locomotion(), def.temperament(), def.width(),
                            def.height(), def.maxHealth(), def.movementSpeed(), def.flyingSpeed(),
                            def.followRange(), def.attackDamage()),
                    new AssetBlock(def.traits(), def.model(), def.texture(), def.animation(), def.dragon(),
                            def.abilities())));
}
