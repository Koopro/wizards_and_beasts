package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Lore: shields cannot stop the Killing Curse. The beam applies an extreme
 * {@link DamageTypes#PLAYER_ATTACK} amount in {@link at.koopro.wizardsandbeasts.spell.WandBeamChannelLogic}.
 */
public final class SpellProtegoRules {

    /** {@link at.koopro.wizardsandbeasts.spell.WandBeamChannelLogic} uses 1_000_000f — stay well below float noise. */
    public static final float UNBLOCKABLE_LETHAL_DAMAGE_THRESHOLD = 500_000f;

    private SpellProtegoRules() {}

    public static boolean bypassesProtego(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!source.is(DamageTypes.PLAYER_ATTACK)) {
            return false;
        }
        return event.getContainer().getOriginalDamage() >= UNBLOCKABLE_LETHAL_DAMAGE_THRESHOLD;
    }
}
