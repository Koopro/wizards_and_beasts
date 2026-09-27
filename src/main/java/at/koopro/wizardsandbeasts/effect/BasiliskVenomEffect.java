package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.basilisk.BasiliskDamageTypes;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Basilisk venom in the blood.
 *
 * <p>Canon (Chamber of Secrets): a fang through Harry's arm, and within a minute or two he is dying — the venom
 * spreads, the sight goes, and only Fawkes's tears save him. Phoenix tears are the one known antidote. So:
 * <ul>
 *   <li>it hurts every {@link #INTERVAL} ticks, faster with each dose (amplifier), and dims the sight toward the
 *       end ({@link MobEffects#DARKNESS} for the last {@link #FAILING_TICKS});</li>
 *   <li>a second bite deepens it rather than stacking a copy ({@link #inject}: one instance, amplifier up to
 *       {@link #MAX_AMPLIFIER}, duration reset);</li>
 *   <li>nothing removes it while the victim lives except phoenix tears ({@link Antidote}) — not milk, not a bezoar,
 *       not chocolate, not {@code /effect clear}. Death clears it.</li>
 * </ul>
 */
public final class BasiliskVenomEffect extends MobEffect {

    /** Two minutes from the bite to the end, if nothing is done. */
    public static final int DURATION = 2400;
    public static final int MAX_AMPLIFIER = 2;
    public static final int INTERVAL = 40;
    public static final int FAILING_TICKS = 600;

    public BasiliskVenomEffect() {
        super(MobEffectCategory.HARMFUL, 0x3E5A1E);
    }

    /** Ticks between hurts at this dose: 40, 30, 20. */
    public static int interval(int amplifier) {
        return Math.max(20, INTERVAL - 10 * Math.min(amplifier, MAX_AMPLIFIER));
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % interval(amplifier) == 0;
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        entity.hurtServer(level, level.damageSources().source(BasiliskDamageTypes.VENOM), 1.0f + amplifier);
        MobEffectInstance self = entity.getEffect(ModEffects.BASILISK_VENOM);
        if (self != null && self.getDuration() <= FAILING_TICKS) {
            entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
        }
        return true;
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }

    /**
     * Venom into {@code victim} from {@code source}: a new dose, or a deeper one if already poisoned. One instance
     * always; the timer restarts.
     *
     * @return the amplifier now in the blood
     */
    public static int inject(LivingEntity victim, @Nullable Entity source) {
        MobEffectInstance current = victim.getEffect(ModEffects.BASILISK_VENOM);
        int amplifier = current == null ? 0 : Math.min(MAX_AMPLIFIER, current.getAmplifier() + 1);
        if (current != null) {
            Antidote.allowRemoval(() -> victim.removeEffect(ModEffects.BASILISK_VENOM));
        }
        victim.addEffect(new MobEffectInstance(ModEffects.BASILISK_VENOM, DURATION, amplifier, false, true, true), source);
        return amplifier;
    }

    /**
     * Refuses every removal of the venom from a living body — except the one {@link #allowRemoval} brackets, which is
     * phoenix tears (and the venom re-dosing itself).
     */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class Antidote {

        /** Server-thread only; set for the length of one sanctioned removal. */
        private static boolean sanctioned;

        private Antidote() {}

        /** Runs {@code removal} with the venom's guard lifted. */
        public static void allowRemoval(Runnable removal) {
            boolean was = sanctioned;
            sanctioned = true;
            try {
                removal.run();
            } finally {
                sanctioned = was;
            }
        }

        @SubscribeEvent
        public static void onRemove(MobEffectEvent.Remove event) {
            if (refuses(event.getEntity(), event.getEffect())) {
                event.setCanceled(true);
            }
        }

        static boolean refuses(LivingEntity entity, Holder<MobEffect> effect) {
            return !sanctioned && effect.value() == ModEffects.BASILISK_VENOM.get() && entity.isAlive();
        }
    }
}
