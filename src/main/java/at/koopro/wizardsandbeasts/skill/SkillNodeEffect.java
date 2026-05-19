package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.bestiary.BestiaryCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Locale;

public sealed interface SkillNodeEffect permits
        SkillNodeEffect.AttributeBoost,
        SkillNodeEffect.SpellCooldownMultiplier,
        SkillNodeEffect.SpellDamageMultiplier,
        SkillNodeEffect.UnlockSpellEarly,
        SkillNodeEffect.BroomSpeedBonus,
        SkillNodeEffect.BestiaryXpMultiplier {

    Codec<Holder<Attribute>> ATTRIBUTE_HOLDER_CODEC =
            BuiltInRegistries.ATTRIBUTE.holderByNameCodec();

    Codec<AttributeModifier.Operation> OPERATION_CODEC = Codec.STRING.xmap(
            value -> AttributeModifier.Operation.valueOf(value.toUpperCase(Locale.ROOT)),
            operation -> operation.name().toLowerCase(Locale.ROOT)
    );

    Codec<SkillNodeEffect> CODEC = Type.CODEC.dispatch(SkillNodeEffect::type, Type::codec);

    Type type();

    enum Type implements StringRepresentable {
        ATTRIBUTE_BOOST("attribute_boost", AttributeBoost.CODEC),
        SPELL_COOLDOWN_MULTIPLIER("spell_cooldown_multiplier", SpellCooldownMultiplier.CODEC),
        SPELL_DAMAGE_MULTIPLIER("spell_damage_multiplier", SpellDamageMultiplier.CODEC),
        UNLOCK_SPELL_EARLY("unlock_spell_early", UnlockSpellEarly.CODEC),
        BROOM_SPEED_BONUS("broom_speed_bonus", BroomSpeedBonus.CODEC),
        BESTIARY_XP_MULTIPLIER("bestiary_xp_multiplier", BestiaryXpMultiplier.CODEC);

        public static final Codec<Type> CODEC = StringRepresentable.fromValues(Type::values);

        private final String serializedName;
        private final MapCodec<? extends SkillNodeEffect> codec;

        Type(String serializedName, MapCodec<? extends SkillNodeEffect> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public MapCodec<? extends SkillNodeEffect> codec() {
            return codec;
        }
    }

    record AttributeBoost(
            Holder<Attribute> attribute,
            double value,
            AttributeModifier.Operation operation,
            Identifier modifierId
    ) implements SkillNodeEffect {
        public static final MapCodec<AttributeBoost> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ATTRIBUTE_HOLDER_CODEC.fieldOf("attribute").forGetter(AttributeBoost::attribute),
                Codec.DOUBLE.fieldOf("value").forGetter(AttributeBoost::value),
                OPERATION_CODEC.fieldOf("operation").forGetter(AttributeBoost::operation),
                Identifier.CODEC.fieldOf("modifier_id").forGetter(AttributeBoost::modifierId)
        ).apply(instance, AttributeBoost::new));

        @Override
        public Type type() {
            return Type.ATTRIBUTE_BOOST;
        }
    }

    record SpellCooldownMultiplier(SpellCategory category, float multiplier) implements SkillNodeEffect {
        public static final MapCodec<SpellCooldownMultiplier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                SpellCategory.CODEC.fieldOf("category").forGetter(SpellCooldownMultiplier::category),
                Codec.FLOAT.fieldOf("multiplier").forGetter(SpellCooldownMultiplier::multiplier)
        ).apply(instance, SpellCooldownMultiplier::new));

        @Override
        public Type type() {
            return Type.SPELL_COOLDOWN_MULTIPLIER;
        }
    }

    record SpellDamageMultiplier(SpellCategory category, float multiplier) implements SkillNodeEffect {
        public static final MapCodec<SpellDamageMultiplier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                SpellCategory.CODEC.fieldOf("category").forGetter(SpellDamageMultiplier::category),
                Codec.FLOAT.fieldOf("multiplier").forGetter(SpellDamageMultiplier::multiplier)
        ).apply(instance, SpellDamageMultiplier::new));

        @Override
        public Type type() {
            return Type.SPELL_DAMAGE_MULTIPLIER;
        }
    }

    record UnlockSpellEarly(Identifier spellId, int proficiencyGateOverride) implements SkillNodeEffect {
        public static final MapCodec<UnlockSpellEarly> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("spell_id").forGetter(UnlockSpellEarly::spellId),
                Codec.INT.fieldOf("proficiency_gate_override").forGetter(UnlockSpellEarly::proficiencyGateOverride)
        ).apply(instance, UnlockSpellEarly::new));

        @Override
        public Type type() {
            return Type.UNLOCK_SPELL_EARLY;
        }
    }

    record BroomSpeedBonus(float additiveBonus) implements SkillNodeEffect {
        public static final MapCodec<BroomSpeedBonus> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.FLOAT.fieldOf("additive_bonus").forGetter(BroomSpeedBonus::additiveBonus)
        ).apply(instance, BroomSpeedBonus::new));

        @Override
        public Type type() {
            return Type.BROOM_SPEED_BONUS;
        }
    }

    record BestiaryXpMultiplier(BestiaryCategory category, float multiplier) implements SkillNodeEffect {
        public static final MapCodec<BestiaryXpMultiplier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                BestiaryCategory.CODEC.fieldOf("category").forGetter(BestiaryXpMultiplier::category),
                Codec.FLOAT.fieldOf("multiplier").forGetter(BestiaryXpMultiplier::multiplier)
        ).apply(instance, BestiaryXpMultiplier::new));

        @Override
        public Type type() {
            return Type.BESTIARY_XP_MULTIPLIER;
        }
    }
}
