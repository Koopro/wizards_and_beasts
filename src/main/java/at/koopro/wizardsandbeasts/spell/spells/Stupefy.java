package at.koopro.wizardsandbeasts.spell.spells;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.spell.*;
import net.minecraft.world.effect.MobEffectInstance;

public class Stupefy extends Spell {

    public Stupefy() {
        super("stupefy", "Stupefy", SpellCategory.COMBAT, 40, 4.0f, 0xFFFF0000);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.projectile()
                .stuns()
                .targetEffect(() -> {
                    if (!ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
                        return null;
                    }
                    // TODO(effects): Read cast context for proficiency-aware duration.
                    return new MobEffectInstance(ModEffects.STUPEFY, 60, 0, false, true, true);
                })
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.FLIPENDO);
    }

    @Override
    public int getBaseEffectDurationTicks() {
        return 60;
    }

    @Override
    public float getProjectileSpeed() {
        return 1.8f;
    }

    @Override
    public float getProjectileSpread() {
        return 0.04f;
    }

    @Override
    public float getBaseKnockback() {
        return 0.5f;
    }
}
