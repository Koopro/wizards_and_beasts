package at.koopro.wizardsandbeasts.spell.tuning;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * An administrator's override of one spell's authored values. Every field is optional: empty means "as the
 * spell's definition says", so an override never has to restate a value it does not change, and removing a
 * field restores the authored behaviour exactly.
 *
 * <p>Deliberately covers only values the cast pipeline already reads through a single accessor
 * ({@code Spell#getBaseCooldownTicks}, {@code getBaseDamage}, {@code getProperties().getRange()},
 * {@code getRequirement}, the learning gate's required skill) plus the one new fact, {@link #enabled}. Nothing
 * here is a second copy of a value that some other code path still reads from the definition.
 *
 * @param enabled       false withdraws the spell from casting server-wide
 * @param cooldownTicks replaces the authored base cooldown
 * @param damage        replaces the authored base damage
 * @param range         replaces the authored range (targeted, cone and beam spells only)
 * @param requirement   replaces the authored prerequisite, in {@link SpellRequirementText} form
 * @param requiredSkill replaces the authored learning skill; {@code ""} means "no skill required"
 */
@NullMarked
public record SpellOverride(Optional<Boolean> enabled,
                            Optional<Integer> cooldownTicks,
                            Optional<Float> damage,
                            Optional<Float> range,
                            Optional<String> requirement,
                            Optional<String> requiredSkill) {

    public static final SpellOverride NONE = new SpellOverride(Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<SpellOverride> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.optionalFieldOf("enabled").forGetter(SpellOverride::enabled),
            Codec.INT.optionalFieldOf("cooldownTicks").forGetter(SpellOverride::cooldownTicks),
            Codec.FLOAT.optionalFieldOf("damage").forGetter(SpellOverride::damage),
            Codec.FLOAT.optionalFieldOf("range").forGetter(SpellOverride::range),
            Codec.STRING.optionalFieldOf("requirement").forGetter(SpellOverride::requirement),
            Codec.STRING.optionalFieldOf("requiredSkill").forGetter(SpellOverride::requiredSkill)
    ).apply(inst, SpellOverride::new));

    public boolean isEmpty() {
        return enabled.isEmpty() && cooldownTicks.isEmpty() && damage.isEmpty() && range.isEmpty()
                && requirement.isEmpty() && requiredSkill.isEmpty();
    }

    public SpellOverride withEnabled(Optional<Boolean> value) {
        return new SpellOverride(value, cooldownTicks, damage, range, requirement, requiredSkill);
    }

    public SpellOverride withCooldownTicks(Optional<Integer> value) {
        return new SpellOverride(enabled, value, damage, range, requirement, requiredSkill);
    }

    public SpellOverride withDamage(Optional<Float> value) {
        return new SpellOverride(enabled, cooldownTicks, value, range, requirement, requiredSkill);
    }

    public SpellOverride withRange(Optional<Float> value) {
        return new SpellOverride(enabled, cooldownTicks, damage, value, requirement, requiredSkill);
    }

    public SpellOverride withRequirement(Optional<String> value) {
        return new SpellOverride(enabled, cooldownTicks, damage, range, value, requiredSkill);
    }

    public SpellOverride withRequiredSkill(Optional<String> value) {
        return new SpellOverride(enabled, cooldownTicks, damage, range, requirement, value);
    }
}
