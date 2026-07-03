package at.koopro.wizardsandbeasts.spell.def;

import at.koopro.wizardsandbeasts.spell.core.CastType;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import at.koopro.wizardsandbeasts.spell.cast.CastContext;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectEntry;
import at.koopro.wizardsandbeasts.spell.gamp.GampDomain;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Data-driven, JSON-friendly description of a spell. Loaded by
 * {@link SpellReloadListener} from {@code data/<ns>/<modId>/spells/*.json} and
 * adapted to a {@link at.koopro.wizardsandbeasts.spell.JsonSpell} which delegates to the
 * existing {@link at.koopro.wizardsandbeasts.spell.SpellExecutor} pipeline.
 *
 * <p>Covers the common 80% of spells expressible through {@link CastType} +
 * {@link at.koopro.wizardsandbeasts.spell.SpellProperties}: combat damage, ignite, explode,
 * disarm, status effects, sounds, prerequisites. Spells that need bespoke
 * logic (Nox removing effects, AvadaKedavra one-shotting, Reparo,
 * Wingardium Leviosa, Accio, Imperio, Alohomora) stay as Java
 * {@link at.koopro.wizardsandbeasts.spell.Spell} subclasses on purpose.
 *
 * <p>Identity fields ({@link #displayName}, {@link #category},
 * {@link #cooldownTicks}, {@link #color}) are required. Behavior fields default
 * to the no-op values used by the existing
 * {@link at.koopro.wizardsandbeasts.spell.SpellProperties.Builder}.
 *
 * <p>JSON fields map to {@link RecordCodecBuilder#group}; prefer nested sub-records
 * (mirroring {@link ExplosionDef} or {@link SoundDef}) when adding many more knobs.
 */
public record SpellDefinition(
        String displayName,
        SpellCategory category,
        int cooldownTicks,
        float baseDamage,
        int color,
        int baseEffectDurationTicks,
        float projectileSpeed,
        float projectileSpread,
        float baseKnockback,
        float baseAoeRadius,

        CastType castType,
        float range,
        float knockback,
        Optional<Integer> igniteSeconds,
        Optional<ExplosionDef> explode,
        boolean disarms,

        List<MobEffectDef> selfEffects,
        List<MobEffectDef> targetEffects,
        Optional<SoundDef> sound,
        SpellRequirementDef requirement,
        LearningDef learning,
        boolean unblockable,
        Optional<SpellFamily> spellFamily,
        Set<GampDomain> gampDomains,

        /**
         * Composable, data-driven effect primitives (Step 2), each wrapped with an optional F2
         * {@code cadence} tag (default {@code tick}; honored only by BEAM_CHANNEL dispatch). Optional
         * and defaulted to empty so all pre-existing JSON spells deserialize unchanged.
         */
        List<SpellEffectEntry> effectComponents,

        /**
         * Step 5 hybrid props. {@code opensBlocks} routes the targeted handler's lock/unlock branch
         * (alohomora/colloportus); {@code pullStrength} feeds the cone handler's pull logic (accio).
         * Both map onto the existing {@link at.koopro.wizardsandbeasts.spell.core.SpellProperties}
         * builder flags — the rich behavior stays in the id-keyed handlers.
         */
        boolean opensBlocks,
        float pullStrength) {

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

    /** Optional progression gates used by teacher learning offers. */
    public record LearningDef(
            Optional<String> requiredSkillId,
            Optional<String> requiredProfessionId,
            Optional<String> masterySpellId,
            Optional<PlayerSpellData.MasteryTier> minMasteryTier) {

        public static final LearningDef NONE = new LearningDef(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        public static final Codec<LearningDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.optionalFieldOf("requiredSkillId").forGetter(LearningDef::requiredSkillId),
                Codec.STRING.optionalFieldOf("requiredProfessionId").forGetter(LearningDef::requiredProfessionId),
                Codec.STRING.optionalFieldOf("masterySpellId").forGetter(LearningDef::masterySpellId),
                StringRepresentable.fromValues(MasteryTierValues::values).optionalFieldOf("minMasteryTier")
                        .xmap(o -> o.map(MasteryTierValues::toTier),
                                o -> o.map(MasteryTierValues::fromTier))
                        .forGetter(LearningDef::minMasteryTier)
        ).apply(inst, LearningDef::new));

        public enum MasteryTierValues implements StringRepresentable {
            NOVICE("novice", PlayerSpellData.MasteryTier.NOVICE),
            PROFICIENT("proficient", PlayerSpellData.MasteryTier.PROFICIENT),
            MASTERED("mastered", PlayerSpellData.MasteryTier.MASTERED);

            private final String name;
            private final PlayerSpellData.MasteryTier value;

            MasteryTierValues(String name, PlayerSpellData.MasteryTier value) {
                this.name = name;
                this.value = value;
            }

            @Override
            public String getSerializedName() { return name; }

            public PlayerSpellData.MasteryTier toTier() { return value; }

            public static MasteryTierValues fromTier(PlayerSpellData.MasteryTier tier) {
                return switch (tier) {
                    case NOVICE -> NOVICE;
                    case PROFICIENT -> PROFICIENT;
                    case MASTERED -> MASTERED;
                };
            }
        }
    }

    /**
     * DFU {@link RecordCodecBuilder#group} supports at most 16 fields.
     * Split into two {@link MapCodec}s merged with {@link Codec#mapPair} so JSON stays one flat object.
     */
    private record SpellDefinitionFieldsA(
            String displayName,
            SpellCategory category,
            int cooldownTicks,
            float baseDamage,
            int color,
            int baseEffectDurationTicks,
            float projectileSpeed,
            float projectileSpread,
            float baseKnockback,
            float baseAoeRadius,
            CastType castType,
            float range,
            float knockback,
            Optional<Integer> igniteSeconds) {

        static final MapCodec<SpellDefinitionFieldsA> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("displayName").forGetter(SpellDefinitionFieldsA::displayName),
                StringRepresentable.fromValues(SpellCategory::values).fieldOf("category").forGetter(SpellDefinitionFieldsA::category),
                Codec.INT.fieldOf("cooldownTicks").forGetter(SpellDefinitionFieldsA::cooldownTicks),
                Codec.FLOAT.optionalFieldOf("baseDamage", 0.0f).forGetter(SpellDefinitionFieldsA::baseDamage),
                Codec.INT.fieldOf("color").forGetter(SpellDefinitionFieldsA::color),
                Codec.INT.optionalFieldOf("baseEffectDurationTicks", 0).forGetter(SpellDefinitionFieldsA::baseEffectDurationTicks),
                Codec.FLOAT.optionalFieldOf("projectileSpeed", 1.5f).forGetter(SpellDefinitionFieldsA::projectileSpeed),
                Codec.FLOAT.optionalFieldOf("projectileSpread", 0.0f).forGetter(SpellDefinitionFieldsA::projectileSpread),
                Codec.FLOAT.optionalFieldOf("baseKnockback", 0.0f).forGetter(SpellDefinitionFieldsA::baseKnockback),
                Codec.FLOAT.optionalFieldOf("baseAoeRadius", 0.0f).forGetter(SpellDefinitionFieldsA::baseAoeRadius),
                StringRepresentable.fromValues(CastType::values).fieldOf("castType").forGetter(SpellDefinitionFieldsA::castType),
                Codec.FLOAT.optionalFieldOf("range", 0.0f).forGetter(SpellDefinitionFieldsA::range),
                Codec.FLOAT.optionalFieldOf("knockback", 0.0f).forGetter(SpellDefinitionFieldsA::knockback),
                Codec.INT.optionalFieldOf("igniteSeconds").forGetter(SpellDefinitionFieldsA::igniteSeconds)
        ).apply(inst, SpellDefinitionFieldsA::new));
    }

    private record SpellDefinitionFieldsB(
            Optional<ExplosionDef> explode,
            boolean disarms,
            List<MobEffectDef> selfEffects,
            List<MobEffectDef> targetEffects,
            Optional<SoundDef> sound,
            SpellRequirementDef requirement,
            LearningDef learning,
            boolean unblockable,
            Optional<SpellFamily> spellFamily,
            Set<GampDomain> gampDomains,
            List<SpellEffectEntry> effectComponents,
            boolean opensBlocks,
            float pullStrength) {

        static final MapCodec<SpellDefinitionFieldsB> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                ExplosionDef.CODEC.optionalFieldOf("explode").forGetter(SpellDefinitionFieldsB::explode),
                Codec.BOOL.optionalFieldOf("disarms", false).forGetter(SpellDefinitionFieldsB::disarms),
                MobEffectDef.CODEC.listOf().optionalFieldOf("selfEffects", List.of()).forGetter(SpellDefinitionFieldsB::selfEffects),
                MobEffectDef.CODEC.listOf().optionalFieldOf("targetEffects", List.of()).forGetter(SpellDefinitionFieldsB::targetEffects),
                SoundDef.CODEC.optionalFieldOf("sound").forGetter(SpellDefinitionFieldsB::sound),
                SpellRequirementDef.CODEC.optionalFieldOf("requirement", SpellRequirementDef.NONE).forGetter(SpellDefinitionFieldsB::requirement),
                LearningDef.CODEC.optionalFieldOf("learning", LearningDef.NONE).forGetter(SpellDefinitionFieldsB::learning),
                Codec.BOOL.optionalFieldOf("unblockable", false).forGetter(SpellDefinitionFieldsB::unblockable),
                StringRepresentable.fromValues(SpellFamily::values).optionalFieldOf("family").forGetter(SpellDefinitionFieldsB::spellFamily),
                /*
                 * Optional spell-law declaration used by Gamp's Law validation.
                 * Valid values:
                 * - FOOD_CONJURATION: producing food ex nihilo
                 * - CURRENCY_CONJURATION: producing wizarding currency ex nihilo
                 * - LIFE_CREATION: creating true life
                 * - LOVE_CONJURATION: conjuring genuine love/emotional bonds
                 * - INFORMATION_GENESIS: creating information with no world source
                 */
                GampDomain.CODEC.listOf()
                        .xmap(Set::copyOf, List::copyOf)
                        .optionalFieldOf("gampDomains", Set.of())
                        .forGetter(SpellDefinitionFieldsB::gampDomains),
                SpellEffectEntry.CODEC.listOf()
                        .optionalFieldOf("effects", List.of())
                        .forGetter(SpellDefinitionFieldsB::effectComponents),
                Codec.BOOL.optionalFieldOf("opensBlocks", false).forGetter(SpellDefinitionFieldsB::opensBlocks),
                Codec.FLOAT.optionalFieldOf("pullStrength", 0.0f).forGetter(SpellDefinitionFieldsB::pullStrength)
        ).apply(inst, SpellDefinitionFieldsB::new));
    }

    public static final Codec<SpellDefinition> CODEC = Codec.mapPair(
            SpellDefinitionFieldsA.MAP_CODEC,
            SpellDefinitionFieldsB.MAP_CODEC
    ).codec().xmap(
            pair -> new SpellDefinition(
                    pair.getFirst().displayName(),
                    pair.getFirst().category(),
                    pair.getFirst().cooldownTicks(),
                    pair.getFirst().baseDamage(),
                    pair.getFirst().color(),
                    pair.getFirst().baseEffectDurationTicks(),
                    pair.getFirst().projectileSpeed(),
                    pair.getFirst().projectileSpread(),
                    pair.getFirst().baseKnockback(),
                    pair.getFirst().baseAoeRadius(),
                    pair.getFirst().castType(),
                    pair.getFirst().range(),
                    pair.getFirst().knockback(),
                    pair.getFirst().igniteSeconds(),
                    pair.getSecond().explode(),
                    pair.getSecond().disarms(),
                    pair.getSecond().selfEffects(),
                    pair.getSecond().targetEffects(),
                    pair.getSecond().sound(),
                    pair.getSecond().requirement(),
                    pair.getSecond().learning(),
                    pair.getSecond().unblockable(),
                    pair.getSecond().spellFamily(),
                    pair.getSecond().gampDomains(),
                    pair.getSecond().effectComponents(),
                    pair.getSecond().opensBlocks(),
                    pair.getSecond().pullStrength()),
            def -> Pair.of(
                    new SpellDefinitionFieldsA(
                            def.displayName(),
                            def.category(),
                            def.cooldownTicks(),
                            def.baseDamage(),
                            def.color(),
                            def.baseEffectDurationTicks(),
                            def.projectileSpeed(),
                            def.projectileSpread(),
                            def.baseKnockback(),
                            def.baseAoeRadius(),
                            def.castType(),
                            def.range(),
                            def.knockback(),
                            def.igniteSeconds()),
                    new SpellDefinitionFieldsB(
                            def.explode(),
                            def.disarms(),
                            def.selfEffects(),
                            def.targetEffects(),
                            def.sound(),
                            def.requirement(),
                            def.learning(),
                            def.unblockable(),
                            def.spellFamily(),
                            def.gampDomains(),
                            def.effectComponents(),
                            def.opensBlocks(),
                            def.pullStrength())));

    // Gamp's Law checks. GampsLaw.validate() only consults these for domains the spell DECLARES
    // in its `gampDomains` JSON list, so declaring a domain marks every cast as a violation by
    // default. Bespoke spells may override with context-sensitive logic (e.g. only when the
    // target stack is food).

    public boolean wouldConjureFood(CastContext ctx) {
        return gampDomains().contains(at.koopro.wizardsandbeasts.spell.gamp.GampDomain.FOOD_CONJURATION);
    }

    public boolean wouldConjureCurrency(CastContext ctx) {
        return gampDomains().contains(at.koopro.wizardsandbeasts.spell.gamp.GampDomain.CURRENCY_CONJURATION);
    }

    public boolean wouldCreateLife(CastContext ctx) {
        return gampDomains().contains(at.koopro.wizardsandbeasts.spell.gamp.GampDomain.LIFE_CREATION);
    }

    public boolean wouldConjureLove(CastContext ctx) {
        return gampDomains().contains(at.koopro.wizardsandbeasts.spell.gamp.GampDomain.LOVE_CONJURATION);
    }

    public boolean wouldGenesisInformation(CastContext ctx) {
        return gampDomains().contains(at.koopro.wizardsandbeasts.spell.gamp.GampDomain.INFORMATION_GENESIS);
    }

    /**
     * The item a cast would materialize, if any. Consulted by GampsLaw for the tag-driven
     * {@code cannot_conjure/food} and {@code cannot_conjure/currency} soft penalties; base
     * spells produce nothing, conjuration overrides supply their output stack.
     */
    public @Nullable ItemStack previewProducedItem(CastContext ctx) {
        return null;
    }
}
