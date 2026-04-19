package at.koopro.neo.spell.def;

import at.koopro.neo.spell.CastType;
import at.koopro.neo.spell.Proficiency;
import at.koopro.neo.spell.SpellCategory;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import java.util.List;
import java.util.Optional;

/**
 * Data-driven, JSON-friendly description of a spell. Loaded by
 * {@link SpellReloadListener} from {@code data/<ns>/neo/spells/*.json} and
 * adapted to a {@link at.koopro.neo.spell.JsonSpell} which delegates to the
 * existing {@link at.koopro.neo.spell.SpellExecutor} pipeline.
 *
 * <p>Covers the common 80% of spells expressible through {@link CastType} +
 * {@link at.koopro.neo.spell.SpellProperties}: combat damage, ignite, explode,
 * disarm, status effects, sounds, prerequisites. Spells that need bespoke
 * logic (Nox removing effects, AvadaKedavra one-shotting, Reparo,
 * Wingardium Leviosa, Accio, Imperio, Alohomora) stay as Java
 * {@link at.koopro.neo.spell.Spell} subclasses on purpose.
 *
 * <p>Identity fields ({@link #displayName}, {@link #category},
 * {@link #cooldownTicks}, {@link #color}) are required. Behavior fields default
 * to the no-op values used by the existing
 * {@link at.koopro.neo.spell.SpellProperties.Builder}.
 *
 * <p>Field count is intentionally capped to fit
 * {@link RecordCodecBuilder#group} (max 16 columns); add more behavior knobs
 * by introducing a nested sub-record (mirroring {@link ExplosionDef} or
 * {@link SoundDef}) rather than adding more top-level fields.
 */
public record SpellDefinition(
        String displayName,
        SpellCategory category,
        int cooldownTicks,
        float baseDamage,
        int color,

        CastType castType,
        float range,
        float knockback,
        Optional<Integer> igniteSeconds,
        Optional<ExplosionDef> explode,
        boolean disarms,

        List<MobEffectDef> selfEffects,
        List<MobEffectDef> targetEffects,
        Optional<SoundDef> sound,
        SpellRequirementDef requirement) {

    public static final Codec<SpellDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("displayName").forGetter(SpellDefinition::displayName),
            StringRepresentable.fromValues(SpellCategory::values).fieldOf("category").forGetter(SpellDefinition::category),
            Codec.INT.fieldOf("cooldownTicks").forGetter(SpellDefinition::cooldownTicks),
            Codec.FLOAT.optionalFieldOf("baseDamage", 0.0f).forGetter(SpellDefinition::baseDamage),
            Codec.INT.fieldOf("color").forGetter(SpellDefinition::color),

            StringRepresentable.fromValues(CastType::values).fieldOf("castType").forGetter(SpellDefinition::castType),
            Codec.FLOAT.optionalFieldOf("range", 0.0f).forGetter(SpellDefinition::range),
            Codec.FLOAT.optionalFieldOf("knockback", 0.0f).forGetter(SpellDefinition::knockback),
            Codec.INT.optionalFieldOf("igniteSeconds").forGetter(SpellDefinition::igniteSeconds),
            ExplosionDef.CODEC.optionalFieldOf("explode").forGetter(SpellDefinition::explode),
            Codec.BOOL.optionalFieldOf("disarms", false).forGetter(SpellDefinition::disarms),

            MobEffectDef.CODEC.listOf().optionalFieldOf("selfEffects", List.of()).forGetter(SpellDefinition::selfEffects),
            MobEffectDef.CODEC.listOf().optionalFieldOf("targetEffects", List.of()).forGetter(SpellDefinition::targetEffects),
            SoundDef.CODEC.optionalFieldOf("sound").forGetter(SpellDefinition::sound),
            SpellRequirementDef.CODEC.optionalFieldOf("requirement", SpellRequirementDef.NONE).forGetter(SpellDefinition::requirement)
    ).apply(inst, SpellDefinition::new));

    /** Embedded "explode" block. */
    public record ExplosionDef(float power, boolean breaksBlocks) {
        public static final Codec<ExplosionDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.FLOAT.fieldOf("power").forGetter(ExplosionDef::power),
                Codec.BOOL.optionalFieldOf("breaksBlocks", false).forGetter(ExplosionDef::breaksBlocks)
        ).apply(inst, ExplosionDef::new));
    }

    /** Embedded "sound" block. {@code id} is a vanilla SoundEvent Identifier. */
    public record SoundDef(Identifier id, float volume, float pitch) {
        public static final Codec<SoundDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Identifier.CODEC.fieldOf("id").forGetter(SoundDef::id),
                Codec.FLOAT.optionalFieldOf("volume", 0.8f).forGetter(SoundDef::volume),
                Codec.FLOAT.optionalFieldOf("pitch", 1.2f).forGetter(SoundDef::pitch)
        ).apply(inst, SoundDef::new));
    }

    /** Embedded mob-effect entry, applied to caster (selfEffects) or target (targetEffects). */
    public record MobEffectDef(Identifier id, int duration, int amplifier) {
        public static final Codec<MobEffectDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Identifier.CODEC.fieldOf("id").forGetter(MobEffectDef::id),
                Codec.INT.fieldOf("duration").forGetter(MobEffectDef::duration),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(MobEffectDef::amplifier)
        ).apply(inst, MobEffectDef::new));
    }

    /** Embedded "requirement" block. */
    public record SpellRequirementDef(
            Type type,
            Optional<String> prerequisiteId,
            Optional<Proficiency> minProficiency) {

        public static final SpellRequirementDef NONE =
                new SpellRequirementDef(Type.NONE, Optional.empty(), Optional.empty());

        public static final Codec<SpellRequirementDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                StringRepresentable.fromValues(Type::values).optionalFieldOf("type", Type.NONE).forGetter(SpellRequirementDef::type),
                Codec.STRING.optionalFieldOf("prerequisiteId").forGetter(SpellRequirementDef::prerequisiteId),
                StringRepresentable.fromValues(ProficiencyValues::values).optionalFieldOf("minProficiency")
                        .xmap(o -> o.map(ProficiencyValues::toProficiency),
                              o -> o.map(ProficiencyValues::fromProficiency))
                        .forGetter(SpellRequirementDef::minProficiency)
        ).apply(inst, SpellRequirementDef::new));

        public enum Type implements StringRepresentable {
            NONE("none"),
            KNOWS("knows"),
            PROFICIENCY("proficiency");

            private final String name;

            Type(String name) { this.name = name; }

            @Override
            public String getSerializedName() {
                return name;
            }
        }

        /** {@link Proficiency} doesn't implement {@link StringRepresentable}; this thin adapter does. */
        public enum ProficiencyValues implements StringRepresentable {
            NOVICE("novice", Proficiency.NOVICE),
            PROFICIENT("proficient", Proficiency.PROFICIENT),
            MASTERED("mastered", Proficiency.MASTERED);

            private final String name;
            private final Proficiency value;

            ProficiencyValues(String name, Proficiency value) {
                this.name = name;
                this.value = value;
            }

            @Override
            public String getSerializedName() { return name; }

            public Proficiency toProficiency() { return value; }

            public static ProficiencyValues fromProficiency(Proficiency p) {
                return switch (p) {
                    case NOVICE -> NOVICE;
                    case PROFICIENT -> PROFICIENT;
                    case MASTERED -> MASTERED;
                };
            }
        }
    }
}
