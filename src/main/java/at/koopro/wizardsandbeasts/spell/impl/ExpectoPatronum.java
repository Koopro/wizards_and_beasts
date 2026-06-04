package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.event.spell.ExpectoPatronumAuraHandler;
import at.koopro.wizardsandbeasts.network.spell.PatronusFormSetS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.entity.spell.PatronusEntity;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import at.koopro.wizardsandbeasts.spell.patronus.PatronusFormDeterminer;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class ExpectoPatronum extends Spell {
    private static final int AURA_DURATION_TICKS = 140;

    public ExpectoPatronum() {
        super("expecto_patronum", "Expecto Patronum", SpellCategory.DEFENSE, 170, 5.0f, 0xFFCCDDFF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.cone(9.0f)
                .undeadBonus(11.0f)
                .knockback(2.0f)
                .sound(ModSounds.PATRONUS_SUMMON.get(), 0.9f, 1.2f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.proficiency(Spells.PROTEGO, Proficiency.PROFICIENT);
    }

    @Override
    public void executeCast(CastContext ctx, ServerLevel level) {
        ServerPlayer caster = ctx.caster();
        float happiness = caster.getData(ModAttachments.HAPPINESS.get());
        // Akkumulierte HAPPY-Memories: intensity-gewichtet + gedeckelt (MemoryService.MAX_MEMORY_BONUS).
        float memoryBonus = at.koopro.wizardsandbeasts.memory.MemoryService.patronusMemoryBonus(caster);
        float darkCorruption = caster.getData(ModAttachments.DARK_CORRUPTION.get());
        float patronusPower = Mth.clamp(
                happiness * 0.4f + memoryBonus + getProficiencyScalar(caster) * 20.0f - darkCorruption * 0.3f,
                0.0f, 100.0f);
        if (patronusPower < 15.0f) {
            level.playSound(null, caster.blockPosition(), ModSounds.SPELL_FIZZLE.get(), SoundSource.PLAYERS, 0.55f, 1.0f);
            caster.displayClientMessage(
                    Component.literal("You cannot find a happy enough memory.").withStyle(net.minecraft.ChatFormatting.GRAY),
                    true);
            // Failed Patronus must not apply cooldown (lore: retry when memory is brighter).
            return;
        }
        super.executeCast(ctx, level);
        int bonus = switch (getProficiency(caster)) {
            case MASTERED -> 80;
            case PROFICIENT -> 30;
            default -> 0;
        };
        ExpectoPatronumAuraHandler.activate(caster, level.getGameTime() + AURA_DURATION_TICKS + bonus);

        String storedForm = caster.getData(ModAttachments.PATRONUS_FORM.get());
        if (storedForm == null || storedForm.isEmpty()) {
            Identifier determined = PatronusFormDeterminer.determine(
                    HeritageAPI.getPlayerHeritage(caster),
                    HeritageAPI.getPlayerHeritageVariant(caster),
                    happiness);
            if (determined == null) {
                level.playSound(null, caster.blockPosition(), ModSounds.SPELL_FIZZLE.get(), SoundSource.PLAYERS, 0.55f, 1.0f);
                caster.displayClientMessage(
                        Component.translatable("spell.wizards_and_beasts.expecto_patronum.reject.heritage")
                                .withStyle(net.minecraft.ChatFormatting.GRAY),
                        true);
                return;
            }
            if (patronusPower >= 15.0f) {
                PacketDistributor.sendToPlayer(caster, new PatronusFormSetS2CPayload(determined.toString()));
            }
            if (patronusPower >= 40.0f) {
                caster.setData(ModAttachments.PATRONUS_FORM.get(), determined.toString());
            }
        }
        at.koopro.wizardsandbeasts.entity.spell.PatronusEntity.trySpawn(level, caster, patronusPower, getProficiencyScalar(caster));

        // A Patronus that holds a defined animal form (power >= 40) is corporeal — the milestone.
        if (patronusPower >= 40.0f) {
            at.koopro.wizardsandbeasts.stats.StatMilestones.onMilestoneTriggered(
                    caster, at.koopro.wizardsandbeasts.stats.MilestoneType.FIRST_PATRONUS_CORPOREAL);
        }
    }
}
