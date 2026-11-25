package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.entity.spell.FiendfyreEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Fiendfyre (systems audit B3, 2026-11-25): cursed fire, one of the few things that end a Horcrux (DH ch.31).
 * Not an Unforgivable in canon, but Dark magic of the worst kind: the Dark Arts web teaches it, behind the Dark Arts
 * module, after a wizard has command of ordinary fire (Incendio) and the Blasting Curse (Confringo). Crabbe learned it
 * from the Carrows and could not stop it; the fire lives in {@link FiendfyreEntity}.
 */
public class Fiendfyre extends Spell {

    private static final double LOOSE_AHEAD = 3.0;

    public Fiendfyre() {
        super("fiendfyre", "Fiendfyre", SpellCategory.DARK_ARTS, 1200, 0.0f, 0xFFFF5A1A);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .sound(SoundEvents.BLAZE_SHOOT, 1.0f, 0.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.allOf(
                SpellRequirement.proficiency("incendio", Proficiency.PROFICIENT),
                SpellRequirement.proficiency("confringo", Proficiency.PROFICIENT));
    }

    @Override
    public void execute(ServerLevel level, ServerPlayer caster, ItemStack wandStack) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return;
        }
        Vec3 look = caster.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0e-6 ? Vec3.ZERO : look.normalize();
        FiendfyreEntity.loose(level, caster.position().add(look.scale(LOOSE_AHEAD)), caster,
                FiendfyreEntity.controlChance(getProficiency(caster)));
        playSound(level, caster);
    }
}
