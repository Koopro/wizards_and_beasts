package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.ability.grant.AbilityKey;
import at.koopro.wizardsandbeasts.ability.grant.AbilityModifierAxis;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What unlocking a skill does. Effects are pure data — the player data layer stores only
 * skill IDs + levels; effects are derived from definitions. Serialized as part of the
 * datapack skill node JSON via {@link #CODEC} (dispatch on {@code type}).
 */
public sealed interface SkillEffect {

    Codec<GameplayStat> GAMEPLAY_STAT_CODEC = Codec.STRING.xmap(
            value -> GameplayStat.valueOf(value.toUpperCase(Locale.ROOT)),
            stat -> stat.name().toLowerCase(Locale.ROOT)
    );

    Codec<SkillEffect> CODEC = Type.CODEC.dispatch(SkillEffect::type, Type::codec);

    Type type();

    enum Type implements StringRepresentable {
        LEARN_SPELL("learn_spell", LearnSpell.CODEC),
        SPELL_DAMAGE_BONUS("spell_damage_bonus", SpellDamageBonus.CODEC),
        SPELL_COOLDOWN_REDUCTION("spell_cooldown_reduction", SpellCooldownReduction.CODEC),
        CATEGORY_DAMAGE_BONUS("category_damage_bonus", CategoryDamageBonus.CODEC),
        CATEGORY_COOLDOWN_REDUCTION("category_cooldown_reduction", CategoryCooldownReduction.CODEC),
        PASSIVE_ATTRIBUTE("passive_attribute", PassiveAttribute.CODEC),
        UNLOCK_ABILITY("unlock_ability", UnlockAbility.CODEC),
        GAMEPLAY_BONUS("gameplay_bonus", GameplayBonus.CODEC),
        GRANT_ABILITY("grant_ability", GrantAbility.CODEC),
        ABILITY_REFINEMENT("ability_refinement", AbilityRefinement.CODEC);

        public static final Codec<Type> CODEC = StringRepresentable.fromValues(Type::values);

        private final String serializedName;
        private final MapCodec<? extends SkillEffect> codec;

        Type(String serializedName, MapCodec<? extends SkillEffect> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public MapCodec<? extends SkillEffect> codec() {
            return codec;
        }
    }

    /** Teaches the player a spell when unlocked. */
    record LearnSpell(String spellId) implements SkillEffect {
        public static final MapCodec<LearnSpell> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("spellId").forGetter(LearnSpell::spellId)
        ).apply(instance, LearnSpell::new));

        @Override
        public Type type() {
            return Type.LEARN_SPELL;
        }
    }

    /** Adds a per-level damage bonus to a specific spell. */
    record SpellDamageBonus(String spellId, float bonusPerLevel) implements SkillEffect {
        public static final MapCodec<SpellDamageBonus> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("spellId").forGetter(SpellDamageBonus::spellId),
                Codec.FLOAT.fieldOf("bonusPerLevel").forGetter(SpellDamageBonus::bonusPerLevel)
        ).apply(instance, SpellDamageBonus::new));

        @Override
        public Type type() {
            return Type.SPELL_DAMAGE_BONUS;
        }
    }

    /** Reduces cooldown of a specific spell per level. */
    record SpellCooldownReduction(String spellId, float reductionPerLevel) implements SkillEffect {
        public static final MapCodec<SpellCooldownReduction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("spellId").forGetter(SpellCooldownReduction::spellId),
                Codec.FLOAT.fieldOf("reductionPerLevel").forGetter(SpellCooldownReduction::reductionPerLevel)
        ).apply(instance, SpellCooldownReduction::new));

        @Override
        public Type type() {
            return Type.SPELL_COOLDOWN_REDUCTION;
        }
    }

    /** Adds a per-level damage bonus to all spells in a category. */
    record CategoryDamageBonus(SpellCategory category, float bonusPerLevel) implements SkillEffect {
        public static final MapCodec<CategoryDamageBonus> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                SpellCategory.CODEC.fieldOf("category").forGetter(CategoryDamageBonus::category),
                Codec.FLOAT.fieldOf("bonusPerLevel").forGetter(CategoryDamageBonus::bonusPerLevel)
        ).apply(instance, CategoryDamageBonus::new));

        @Override
        public Type type() {
            return Type.CATEGORY_DAMAGE_BONUS;
        }
    }

    /** Reduces cooldown of all spells in a category per level. */
    record CategoryCooldownReduction(SpellCategory category, float reductionPerLevel) implements SkillEffect {
        public static final MapCodec<CategoryCooldownReduction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                SpellCategory.CODEC.fieldOf("category").forGetter(CategoryCooldownReduction::category),
                Codec.FLOAT.fieldOf("reductionPerLevel").forGetter(CategoryCooldownReduction::reductionPerLevel)
        ).apply(instance, CategoryCooldownReduction::new));

        @Override
        public Type type() {
            return Type.CATEGORY_COOLDOWN_REDUCTION;
        }
    }

    /** Applies an attribute modifier per level (e.g., max health, speed). */
    record PassiveAttribute(String attributeId, double amountPerLevel) implements SkillEffect {
        public static final MapCodec<PassiveAttribute> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("attributeId").forGetter(PassiveAttribute::attributeId),
                Codec.DOUBLE.fieldOf("amountPerLevel").forGetter(PassiveAttribute::amountPerLevel)
        ).apply(instance, PassiveAttribute::new));

        @Override
        public Type type() {
            return Type.PASSIVE_ATTRIBUTE;
        }
    }

    /** Unlocks a named ability flag (checked via SkillEffectCache.hasAbility). */
    record UnlockAbility(String abilityId) implements SkillEffect {
        public static final MapCodec<UnlockAbility> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("abilityId").forGetter(UnlockAbility::abilityId)
        ).apply(instance, UnlockAbility::new));

        @Override
        public Type type() {
            return Type.UNLOCK_ABILITY;
        }
    }

    /** Adds a per-level scalar gameplay bonus (e.g. beast resistance, harvest luck). */
    record GameplayBonus(GameplayStat stat, float perLevel) implements SkillEffect {
        public static final MapCodec<GameplayBonus> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                GAMEPLAY_STAT_CODEC.fieldOf("stat").forGetter(GameplayBonus::stat),
                Codec.FLOAT.fieldOf("perLevel").forGetter(GameplayBonus::perLevel)
        ).apply(instance, GameplayBonus::new));

        @Override
        public Type type() {
            return Type.GAMEPLAY_BONUS;
        }
    }

    /**
     * Grants an {@link AbilityKey} through the source-tracked
     * {@link at.koopro.wizardsandbeasts.ability.grant.AbilityGrants} layer (source = SKILL_NODE), so the
     * grant strips automatically when the node is refunded. The clean forward path for node ability grants;
     * the legacy free-string {@link UnlockAbility} still feeds {@link SkillEffectCache} and is bridged into
     * the grant layer too. Ships with zero nodes using it.
     */
    record GrantAbility(AbilityKey ability) implements SkillEffect {
        public static final MapCodec<GrantAbility> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                AbilityKey.CODEC.fieldOf("ability").forGetter(GrantAbility::ability)
        ).apply(instance, GrantAbility::new));

        @Override
        public Type type() {
            return Type.GRANT_ABILITY;
        }
    }

    /**
     * A refinement scoped to a granted ability: a per-level magnitude on one {@link AbilityModifierAxis},
     * readable via {@link at.koopro.wizardsandbeasts.ability.grant.AbilityModifiers}. Pure scoping mechanism
     * — ships with zero authored nodes and zero consumers; wiring a consumer is content-era work.
     */
    record AbilityRefinement(AbilityKey ability, AbilityModifierAxis axis, float magnitudePerLevel)
            implements SkillEffect {
        public static final MapCodec<AbilityRefinement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                AbilityKey.CODEC.fieldOf("ability").forGetter(AbilityRefinement::ability),
                AbilityModifierAxis.CODEC.fieldOf("axis").forGetter(AbilityRefinement::axis),
                Codec.FLOAT.fieldOf("magnitudePerLevel").forGetter(AbilityRefinement::magnitudePerLevel)
        ).apply(instance, AbilityRefinement::new));

        @Override
        public Type type() {
            return Type.ABILITY_REFINEMENT;
        }
    }
}
