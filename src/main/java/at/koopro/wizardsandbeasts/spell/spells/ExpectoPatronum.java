package at.koopro.wizardsandbeasts.spell.spells;

import at.koopro.wizardsandbeasts.event.ExpectoPatronumAuraHandler;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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
    public void execute(ServerLevel level, ServerPlayer caster, ItemStack wandStack) {
        super.execute(level, caster, wandStack);
        int bonus = switch (getProficiency(caster)) {
            case MASTERED -> 80;
            case PROFICIENT -> 30;
            default -> 0;
        };
        ExpectoPatronumAuraHandler.activate(caster, level.getGameTime() + AURA_DURATION_TICKS + bonus);
    }
}
