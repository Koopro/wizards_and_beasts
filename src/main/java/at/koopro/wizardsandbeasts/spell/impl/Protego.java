package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.effect.ProtegoShieldEffect;
import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.network.spell.ProtegoSpawnS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import at.koopro.wizardsandbeasts.spell.cast.WandCastTiming;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoProjectileHandler;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoWardManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class Protego extends Spell {
    public static final int PROTEGO_DURATION_TICKS = 100;

    public Protego() {
        super("protego", "Protego", SpellCategory.DEFENSE, 100, 0.0f, 0xFF4488FF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .sound(net.minecraft.sounds.SoundEvents.SHIELD_BLOCK.value(), 1.0f, 1.2f)
                .build();
    }

    @Override
    public void executeCast(CastContext ctx, ServerLevel level) {
        ServerPlayer caster = ctx.caster();
        int heldTicks = WandCastTiming.consumeLastHoldTicks(caster);
        int tier = ProtegoProjectileHandler.resolveTier(caster, heldTicks);
        int duration = 60 + (int) (getProficiencyScalar(caster) * 100);
        String label = switch (tier) {
            case 0 -> "Protego";
            case 1 -> "Protego Totalum";
            case 2 -> "Protego Maxima";
            default -> "Protego Horribilis";
        };
        caster.displayClientMessage(Component.literal(label).withStyle(ChatFormatting.AQUA), true);
        int deflections = switch (tier) {
            case 0 -> 1;
            case 1 -> 3;
            default -> 5;
        };
        int amplifier = ProtegoShieldEffect.encodeAmplifier(tier, deflections);
        caster.addEffect(new MobEffectInstance(ModEffects.PROTEGO_SHIELD, duration, amplifier, false, false, true));
        ProtegoWardManager.shatterExistingIfPresent(caster);
        ProtegoShieldEntity shield = new ProtegoShieldEntity(level, caster, tier);
        level.addFreshEntity(shield);
        ProtegoWardManager.register(caster.getUUID(), shield.getId());
        PacketDistributor.sendToPlayersNear(level, null, shield.getX(), shield.getY(), shield.getZ(), 32.0,
                new ProtegoSpawnS2CPayload(shield.getId(), tier, caster.getUUID(), shield.getX(), shield.getY(), shield.getZ()));
        level.playSound(null, caster.blockPosition(), ModSounds.PROTEGO_RAISE.get(), SoundSource.PLAYERS, 0.75f, 1.1f);
        if (tier == 1) {
            level.playSound(null, caster.blockPosition(), ModSounds.PROTEGO_TOTALUM_RAISE.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
        } else if (tier >= 2) {
            level.playSound(null, caster.blockPosition(), ModSounds.PROTEGO_MAXIMA_RAISE.get(), SoundSource.PLAYERS, 1.0f, 0.95f);
        }
        // TODO(protego-tiers): wire tier selection from ProtegoSpell if/when this class is migrated.
        SpellHelper.applyProtegoCastPulse(level, caster, this);
        if (getProficiency(caster) == Proficiency.MASTERED) {
            caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 2, false, true, true));
        }
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.EXPELLIARMUS);
    }

    @Override
    public int getBaseEffectDurationTicks() {
        return 200;
    }

    @Override
    public float getProjectileSpeed() {
        return 0.0f;
    }
}
