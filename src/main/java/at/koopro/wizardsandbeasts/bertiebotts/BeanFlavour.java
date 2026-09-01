package at.koopro.wizardsandbeasts.bertiebotts;

import at.koopro.wizardsandbeasts.chocolate.ChocolateFrog;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every flavour a bean can be, and what each one does to you.
 *
 * <p>Twelve flavours across five tiers. The old item was a coin flip between Speed and Poison, which
 * is a fair gamble and a terrible joke: the whole point of Every-Flavour Beans is that you cannot
 * predict <em>what</em>, only roughly how bad. Drawing a tier and then a flavour within it gives that
 * — most beans are unremarkable, a few are memorable, and one in fifty is worth telling somebody
 * about.
 *
 * <h2>Every bean announces itself</h2>
 * The name is the payload. A bean that quietly applied Nausea would be an unexplained debuff; a bean
 * that says <i>Earwax…</i> first is a joke the player is in on. {@link #displayName()} is what the
 * item puts on the action bar, always, for every flavour including the dull ones.
 *
 * <h2>Where the JSON goes</h2>
 * The brief calls for a datapack table later. This enum is shaped for it: tier, name key and effect
 * are the three fields such a file would carry, and {@link BeanTier} already holds the weights
 * separately from the flavours. What would need writing is a codec and a reload listener, not a
 * rethink — the {@code apply} bodies become the one part that stays in Java, keyed by id, exactly as
 * {@code SpellEffectComponent} does it.
 */
@NullMarked
public enum BeanFlavour implements StringRepresentable {

    // ── common pleasant (40%) ────────────────────────────────────────────────
    /** A surprisingly good one. */
    HONEY(BeanTier.COMMON_PLEASANT) {
        @Override
        public void apply(Player eater, ServerLevel level) {
            eater.addEffect(new MobEffectInstance(MobEffects.SATURATION, 3, 1));
        }
    },
    /** Sharp and cold, and you find yourself moving. */
    PEPPERMINT(BeanTier.COMMON_PLEASANT) {
        @Override
        public void apply(Player eater, ServerLevel level) {
            eater.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0, false, true, true));
        }
    },
    /** Warm, buttery, and it takes the edge off a scrape. */
    TOFFEE(BeanTier.COMMON_PLEASANT) {
        @Override
        public void apply(Player eater, ServerLevel level) {
            eater.heal(2.0f);
        }
    },

    // ── common unpleasant (35%) ──────────────────────────────────────────────
    /** Earwax. */
    EARWAX(BeanTier.COMMON_UNPLEASANT) {
        @Override
        public void apply(Player eater, ServerLevel level) {
            eater.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0, false, true, true));
        }
    },
    /** Vomit. */
    VOMIT(BeanTier.COMMON_UNPLEASANT) {
        @Override
        public void apply(Player eater, ServerLevel level) {
            eater.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300, 0, false, true, true));
        }
    },
    /** Dirt. Gritty, and it sets your teeth wrong for a while. */
    DIRT(BeanTier.COMMON_UNPLEASANT) {
        @Override
        public void apply(Player eater, ServerLevel level) {
            eater.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 0, false, true, true));
        }
    },

    // ── rare good (15%) ──────────────────────────────────────────────────────
    /** Rich and fortifying. */
    TREACLE_TART(BeanTier.RARE_GOOD) {
        @Override
        public void apply(Player eater, ServerLevel level) {
            eater.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 1, false, true, true));
        }
    },
    /**
     * Improbably good, and things go your way for a bit.
     *
     * <p>Vanilla Luck, which really is read by loot tables — so "a bonus to your next loot" is a
     * thing that happens rather than a thing the tooltip claims.
     */
    FOUR_LEAF_CLOVER(BeanTier.RARE_GOOD) {
        @Override
        public void apply(Player eater, ServerLevel level) {
            eater.addEffect(new MobEffectInstance(MobEffects.LUCK, 2400, 1, false, true, true));
        }
    },

    // ── rare bad (8%) ────────────────────────────────────────────────────────
    /** Rotten, and it goes straight to your eyes. */
    ROTTEN_EGG(BeanTier.RARE_BAD) {
        @Override
        public void apply(Player eater, ServerLevel level) {
            eater.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0, false, true, true));
            eater.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, true, true));
        }
    },
    /**
     * Burning hot.
     *
     * <p>Two seconds of fire, not a burning. Long enough to hurt and to make the player flinch for
     * water, short enough that it cannot kill somebody who ate a sweet.
     */
    PEPPER(BeanTier.RARE_BAD) {
        @Override
        public void apply(Player eater, ServerLevel level) {
            eater.setRemainingFireTicks(40);
        }
    },

    // ── legendary (2%) ───────────────────────────────────────────────────────
    /**
     * Chocolate. Actual chocolate.
     *
     * <p>Does exactly what a Chocolate Frog does, by calling the same code — the despair goes and
     * cannot come back for half a minute. One bean in a hundred being genuine first aid is the kind
     * of thing a player remembers.
     */
    CHOCOLATE(BeanTier.LEGENDARY) {
        @Override
        public void apply(Player eater, ServerLevel level) {
            ChocolateFrog.clearDespair(eater);
            eater.addEffect(new MobEffectInstance(
                    ModEffects.CHOCOLATE_WARD, ChocolateFrog.WARD_TICKS, 0, false, false, true));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    eater.getX(), eater.getEyeY(), eater.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
        }
    },
    /**
     * Bogey. You sneeze, hard, and stop being useful for half a second.
     *
     * <p>Ten ticks of {@code STUPEFY} — the mod's own stun, so a sneeze locks you exactly the way a
     * Stupefy does rather than inventing a second kind of immobility.
     */
    BOGEY(BeanTier.LEGENDARY) {
        @Override
        public void apply(Player eater, ServerLevel level) {
            eater.addEffect(new MobEffectInstance(ModEffects.STUPEFY, 10, 0, false, true, true));
            level.playSound(null, eater.getX(), eater.getY(), eater.getZ(),
                    SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.7f, 1.9f);
            level.sendParticles(ParticleTypes.SNEEZE,
                    eater.getX(), eater.getEyeY(), eater.getZ(), 12, 0.2, 0.1, 0.2, 0.05);
        }
    };

    private final BeanTier tier;

    BeanFlavour(BeanTier tier) {
        this.tier = tier;
    }

    /** What eating this one does. Runs server-side, after the hunger has already been restored. */
    public abstract void apply(Player eater, ServerLevel level);

    public BeanTier tier() {
        return tier;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The name the eater is told, in their tier's colour. */
    public Component displayName() {
        return Component.translatable("item.wizards_and_beasts.bertie_botts.flavour." + getSerializedName())
                .withStyle(tier.colour());
    }

    /** Every flavour in one tier, in declaration order. */
    public static List<BeanFlavour> inTier(BeanTier tier) {
        List<BeanFlavour> found = new ArrayList<>();
        for (BeanFlavour flavour : values()) {
            if (flavour.tier == tier) {
                found.add(flavour);
            }
        }
        return found;
    }
}
