package at.koopro.wizardsandbeasts.creature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Datapack-driven creature definition, mirroring {@code BestiaryEntry}/{@code BroomDefinition}.
 * Loaded from {@code data/wizards_and_beasts/creatures/<id>.json} by {@link CreatureDefinitionLoader}.
 *
 * <p>Runtime source of truth for per-creature attributes (applied over the locomotion baseline on spawn),
 * body-plan/temperament metadata, and asset ids. Hitbox size and the registered {@code EntityType} are
 * created at mod-init from {@code ModCreatures.MANIFEST}; {@link #width()}/{@link #height()} here mirror
 * the manifest for documentation and are not re-applied to the frozen {@code EntityType}.
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
        Identifier animation) {

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.xmap(s -> Enum.valueOf(type, s), Enum::name);
    }

    public static final Codec<CreatureDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(CreatureDefinition::id),
            enumCodec(BodyPlan.class).fieldOf("bodyPlan").forGetter(CreatureDefinition::bodyPlan),
            enumCodec(Locomotion.class).fieldOf("locomotion").forGetter(CreatureDefinition::locomotion),
            enumCodec(Temperament.class).optionalFieldOf("temperament", Temperament.NEUTRAL).forGetter(CreatureDefinition::temperament),
            Codec.FLOAT.fieldOf("width").forGetter(CreatureDefinition::width),
            Codec.FLOAT.fieldOf("height").forGetter(CreatureDefinition::height),
            Codec.DOUBLE.fieldOf("maxHealth").forGetter(CreatureDefinition::maxHealth),
            Codec.DOUBLE.fieldOf("movementSpeed").forGetter(CreatureDefinition::movementSpeed),
            Codec.DOUBLE.optionalFieldOf("flyingSpeed", 0.0).forGetter(CreatureDefinition::flyingSpeed),
            Codec.DOUBLE.optionalFieldOf("followRange", 16.0).forGetter(CreatureDefinition::followRange),
            Codec.DOUBLE.optionalFieldOf("attackDamage", 0.0).forGetter(CreatureDefinition::attackDamage),
            enumCodec(Trait.class).listOf().optionalFieldOf("traits", List.of()).forGetter(CreatureDefinition::traits),
            Identifier.CODEC.fieldOf("model").forGetter(CreatureDefinition::model),
            Identifier.CODEC.fieldOf("texture").forGetter(CreatureDefinition::texture),
            Identifier.CODEC.fieldOf("animation").forGetter(CreatureDefinition::animation)
    ).apply(instance, CreatureDefinition::new));
}
