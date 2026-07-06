package at.koopro.wizardsandbeasts.entity.niffler;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

/**
 * Cast-time consumer of the player HAPPINESS attachment: a contented wizard casts harder.
 * Linear ramp from {@link #THRESHOLD} (no bonus) to 100 happiness ({@link #MAX_BONUS}).
 * Companion to {@link at.koopro.wizardsandbeasts.event.bestiary.niffler.HappinessTickHandler},
 * which accrues/drains the stat; gated on CREATURES like the rest of the happiness pillar.
 */
public final class HappinessSpellPower {

    /** Happiness level where the spell-power bonus starts. */
    public static final float THRESHOLD = 80.0f;
    /** Damage multiplier bonus at full (100) happiness. */
    public static final float MAX_BONUS = 0.10f;

    private HappinessSpellPower() {}

    public static void applyCastModifiers(@NonNull ModifierStack modifiers, @NonNull ServerPlayer caster) {
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        float happiness = caster.getData(ModAttachments.HAPPINESS.get());
        if (happiness < THRESHOLD) {
            return;
        }
        float t = Mth.clamp((happiness - THRESHOLD) / (100.0f - THRESHOLD), 0.0f, 1.0f);
        modifiers.multiplyDamage(1.0f + t * MAX_BONUS, "happiness");
    }
}
