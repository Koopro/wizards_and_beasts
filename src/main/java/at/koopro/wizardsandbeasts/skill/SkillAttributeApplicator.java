package at.koopro.wizardsandbeasts.skill;

/*
Audit answers:
1) Unlocked skill nodes are stored in ModAttachments.SKILL_DATA as PlayerSkillData#unlockedSkills (Map<String, Integer>).
2) Skill unlock writes happen in SkillSystemAPI.tryUnlock/forceUnlock; profession unlock writes happen in ProfessionSystemAPI.tryUnlock.
3) Existing nodes already declare code-only SkillEffect entries, including LearnSpell and some passive/stat effects.
4) Skill tree nodes are Java-defined (SkillTrees + *Skills classes), while bestiary/spells are datapack-driven.
*/

import at.koopro.wizardsandbeasts.network.skill.SkillBonusSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillNodeEffect.AttributeBoost;
import at.koopro.wizardsandbeasts.skill.SkillNodeEffect.BestiaryXpMultiplier;
import at.koopro.wizardsandbeasts.skill.SkillNodeEffect.BroomSpeedBonus;
import at.koopro.wizardsandbeasts.skill.SkillNodeEffect.SpellCooldownMultiplier;
import at.koopro.wizardsandbeasts.skill.SkillNodeEffect.SpellDamageMultiplier;
import at.koopro.wizardsandbeasts.skill.SkillNodeEffect.UnlockSpellEarly;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionNode;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SkillAttributeApplicator {
    private static final String SKILL_MODIFIER_PATH_PREFIX = "skill/";

    private SkillAttributeApplicator() {
    }

    public static void applyAll(ServerPlayer player) {
        removeAll(player);

        Map<at.koopro.wizardsandbeasts.spell.core.SpellCategory, Float> cooldowns = new EnumMap<>(at.koopro.wizardsandbeasts.spell.core.SpellCategory.class);
        Map<at.koopro.wizardsandbeasts.spell.core.SpellCategory, Float> damages = new EnumMap<>(at.koopro.wizardsandbeasts.spell.core.SpellCategory.class);
        Map<net.minecraft.resources.Identifier, Integer> gateOverrides = new HashMap<>();
        Map<at.koopro.wizardsandbeasts.bestiary.BestiaryCategory, Float> bestiary = new EnumMap<>(at.koopro.wizardsandbeasts.bestiary.BestiaryCategory.class);
        float broomSpeedBonus = 0.0f;

        var skillData = player.getData(ModAttachments.SKILL_DATA.get());
        for (Map.Entry<String, Integer> entry : skillData.getUnlockedSkills().entrySet()) {
            Skill skill = SkillTrees.byId(entry.getKey());
            if (skill == null) {
                continue;
            }
            int level = Math.max(0, entry.getValue());
            for (int i = 0; i < level; i++) {
                for (SkillNodeEffect effect : skill.getNodeEffects()) {
                    applyNode(player, effect);
                    if (effect instanceof SpellCooldownMultiplier m) {
                        cooldowns.merge(m.category(), m.multiplier(), (a, b) -> a * b);
                    } else if (effect instanceof SpellDamageMultiplier m) {
                        damages.merge(m.category(), m.multiplier(), (a, b) -> a * b);
                    } else if (effect instanceof UnlockSpellEarly u) {
                        gateOverrides.merge(u.spellId(), u.proficiencyGateOverride(), Math::min);
                    } else if (effect instanceof BroomSpeedBonus b) {
                        broomSpeedBonus += b.additiveBonus();
                    } else if (effect instanceof BestiaryXpMultiplier x) {
                        bestiary.merge(x.category(), x.multiplier(), (a, b) -> a * b);
                    }
                }
            }
        }

        var heritageData = player.getData(ModAttachments.HERITAGE_DATA.get());
        for (String professionId : heritageData.getUnlockedProfessions()) {
            ProfessionNode node = ProfessionNode.byId(professionId);
            if (node == null) {
                continue;
            }
            for (SkillNodeEffect effect : node.getEffects()) {
                applyNode(player, effect);
                if (effect instanceof SpellCooldownMultiplier m) {
                    cooldowns.merge(m.category(), m.multiplier(), (a, b) -> a * b);
                } else if (effect instanceof SpellDamageMultiplier m) {
                    damages.merge(m.category(), m.multiplier(), (a, b) -> a * b);
                } else if (effect instanceof UnlockSpellEarly u) {
                    gateOverrides.merge(u.spellId(), u.proficiencyGateOverride(), Math::min);
                } else if (effect instanceof BroomSpeedBonus b) {
                    broomSpeedBonus += b.additiveBonus();
                } else if (effect instanceof BestiaryXpMultiplier x) {
                    bestiary.merge(x.category(), x.multiplier(), (a, b) -> a * b);
                }
            }
        }

        PlayerSkillBonusData bonusData = new PlayerSkillBonusData(cooldowns, damages, gateOverrides, broomSpeedBonus, bestiary);
        player.setData(ModAttachments.SKILL_BONUS_DATA.get(), bonusData);

        List<AttributeInstance> syncable = new ArrayList<>(player.getAttributes().getSyncableAttributes());
        player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(), syncable));
        PacketDistributor.sendToPlayer(player, new SkillBonusSyncS2CPayload(bonusData));
    }

    public static void removeAll(ServerPlayer player) {
        for (AttributeInstance instance : player.getAttributes().getSyncableAttributes()) {
            List<AttributeModifier> toRemove = instance.getModifiers().stream()
                    .filter(modifier -> modifier.id().getNamespace().equals("wizards_and_beasts"))
                    .filter(modifier -> modifier.id().getPath().startsWith(SKILL_MODIFIER_PATH_PREFIX))
                    .toList();
            for (AttributeModifier modifier : toRemove) {
                instance.removeModifier(modifier.id());
            }
        }
        player.setData(ModAttachments.SKILL_BONUS_DATA.get(), PlayerSkillBonusData.DEFAULT);
    }

    private static void applyNode(ServerPlayer player, SkillNodeEffect effect) {
        if (effect instanceof AttributeBoost attributeBoost) {
            AttributeInstance instance = player.getAttribute(attributeBoost.attribute());
            if (instance == null) {
                return;
            }
            instance.addOrReplacePermanentModifier(new AttributeModifier(
                    attributeBoost.modifierId(),
                    attributeBoost.value(),
                    attributeBoost.operation()));
        }
    }
}
