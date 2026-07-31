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
        // Resolve the form up front. A stored form is reused; otherwise it is determined from
        // heritage. A heritage that cannot conjure a Patronus rejects here — before super.executeCast
        // pays any cost/cooldown, the same courtesy the faint-memory fizzle above already grants.
        String storedForm = caster.getData(ModAttachments.PATRONUS_FORM.get());
        boolean firstForm = storedForm == null || storedForm.isEmpty();
        Identifier determined = null;
        if (firstForm) {
            determined = PatronusFormDeterminer.determine(
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
        }
        String formId = firstForm ? determined.toString() : storedForm;

        super.executeCast(ctx, level);
        int bonus = switch (getProficiency(caster)) {
            case MASTERED -> 80;
            case PROFICIENT -> 30;
            default -> 0;
        };
        ExpectoPatronumAuraHandler.activate(caster, level.getGameTime() + AURA_DURATION_TICKS + bonus);

        boolean corporeal = patronusPower >= at.koopro.wizardsandbeasts.entity.spell.PatronusEntity.CORPOREAL_POWER;
        if (firstForm) {
            // A faint memory (>=15) still glimpses the form on the caster's own client, but only a
            // strong one (>=40) commits it — that is the moment the Patronus takes corporeal shape.
            PacketDistributor.sendToPlayer(caster, new PatronusFormSetS2CPayload(formId));
            if (corporeal) {
                caster.setData(ModAttachments.PATRONUS_FORM.get(), formId);
                caster.displayClientMessage(
                        Component.translatable("spell.wizards_and_beasts.expecto_patronum.form_revealed",
                                        formDisplayName(determined))
                                .withStyle(net.minecraft.ChatFormatting.AQUA),
                        false);
            }
        }
        if (!corporeal) {
            caster.displayClientMessage(
                    Component.translatable("spell.wizards_and_beasts.expecto_patronum.mist")
                            .withStyle(net.minecraft.ChatFormatting.GRAY),
                    true);
        }

        at.koopro.wizardsandbeasts.entity.spell.PatronusEntity.trySpawn(
                level, caster, patronusPower, getProficiencyScalar(caster), formId);

        // A Patronus that holds a defined animal form (power >= 40) is corporeal — the milestone.
        if (corporeal) {
            at.koopro.wizardsandbeasts.stats.StatMilestones.onMilestoneTriggered(
                    caster, at.koopro.wizardsandbeasts.stats.MilestoneType.FIRST_PATRONUS_CORPOREAL);
        }
    }

    /** Turns a form id ({@code minecraft:polar_bear}) into a display label ({@code Polar Bear}). */
    private static Component formDisplayName(Identifier formId) {
        String raw = formId.getPath();
        String[] words = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return Component.literal(sb.toString());
    }
}
