package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.bestiary.BestiaryCategory;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public record PlayerSkillBonusData(
        Map<SpellCategory, Float> cooldownMultipliers,
        Map<SpellCategory, Float> damageMultipliers,
        Map<Identifier, Integer> spellGateOverrides,
        float broomSpeedBonus,
        Map<BestiaryCategory, Float> bestiaryXpMultipliers
) {
    public static final @NonNull PlayerSkillBonusData DEFAULT =
            new PlayerSkillBonusData(Map.of(), Map.of(), Map.of(), 0.0f, Map.of());

    private static final Codec<Map<SpellCategory, Float>> SPELL_CATEGORY_FLOAT_MAP_CODEC =
            Codec.unboundedMap(SpellCategory.CODEC, Codec.FLOAT);
    private static final Codec<Map<Identifier, Integer>> SPELL_GATE_MAP_CODEC =
            Codec.unboundedMap(Identifier.CODEC, Codec.INT);
    private static final Codec<Map<BestiaryCategory, Float>> BESTIARY_CATEGORY_FLOAT_MAP_CODEC =
            Codec.unboundedMap(BestiaryCategory.CODEC, Codec.FLOAT);

    public static final Codec<PlayerSkillBonusData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SPELL_CATEGORY_FLOAT_MAP_CODEC.optionalFieldOf("cooldown_multipliers", Map.of()).forGetter(PlayerSkillBonusData::cooldownMultipliers),
            SPELL_CATEGORY_FLOAT_MAP_CODEC.optionalFieldOf("damage_multipliers", Map.of()).forGetter(PlayerSkillBonusData::damageMultipliers),
            SPELL_GATE_MAP_CODEC.optionalFieldOf("spell_gate_overrides", Map.of()).forGetter(PlayerSkillBonusData::spellGateOverrides),
            Codec.FLOAT.optionalFieldOf("broom_speed_bonus", 0.0f).forGetter(PlayerSkillBonusData::broomSpeedBonus),
            BESTIARY_CATEGORY_FLOAT_MAP_CODEC.optionalFieldOf("bestiary_xp_multipliers", Map.of()).forGetter(PlayerSkillBonusData::bestiaryXpMultipliers)
    ).apply(instance, PlayerSkillBonusData::new));

    public static @NonNull PlayerSkillBonusData forPlayer(ServerPlayer player) {
        return player.getExistingData(ModAttachments.SKILL_BONUS_DATA.get()).orElse(DEFAULT);
    }
}
