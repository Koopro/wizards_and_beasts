package at.koopro.wizardsandbeasts.brew.silver;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Silver biting a dark creature.
 *
 * <p>Hooks the victim's incoming damage rather than the attacker's swing, which is the only place
 * both halves of the question are answerable at once: what is being hit, and what it is being hit
 * with. It also means a silvered weapon works in the hands of anything that can hold one, not only a
 * player.
 *
 * <p>Runs on {@link LivingIncomingDamageEvent} — before armour and resistances — so the bonus is
 * mitigated like the rest of the blow. Silver makes the strike land harder; it does not bypass a
 * Dementor's nature or a datapack's resistances.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SilverBiteHandler {

    private SilverBiteHandler() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!SilveredWeapons.isDarkCreature(victim)) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof LivingEntity attacker)) {
            return;
        }
        // Main hand only. Silver on the blade you are swinging, not on whatever happens to be in the
        // other hand — otherwise a silvered pickaxe in the off hand would buff every punch.
        ItemStack weapon = attacker.getMainHandItem();
        float bonus = SilveredWeapons.bonusFor(weapon, true);
        if (bonus <= 0.0f) {
            return;
        }

        event.setAmount(event.getAmount() + bonus);
        flash(victim);
    }

    /** A brief silver spark, so the bonus is visible rather than merely true. */
    private static void flash(LivingEntity victim) {
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.8f);
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                    6, 0.2, 0.2, 0.2, 0.02);
        }
    }
}
