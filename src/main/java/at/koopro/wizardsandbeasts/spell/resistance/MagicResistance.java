package at.koopro.wizardsandbeasts.spell.resistance;

import at.koopro.wizardsandbeasts.creature.Trait;
import at.koopro.wizardsandbeasts.creature.ability.SpellResist;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectRunner;
import at.koopro.wizardsandbeasts.spell.resistance.MagicResistanceRules.Strain;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one answer to "does this enchantment take hold on this body?" — see {@link MagicResistanceRules}.
 *
 * <p>Three things in the mod said a body resists magic, and none of them asked the others: a creature's
 * {@code spell_resist} ability (which healed back part of the damage and let every stun through), the basilisk's
 * coin flip against Stupefy, and the half-giant's {@code spell_resistant_hide} trait, which nothing read. They are
 * now one hide with one rule. Damage is untouched — {@link SpellResist} still handles that — this decides only
 * whether a spell's <em>enchantment</em> (the stun, the bind, the hoist, the authored effect list) takes hold.
 *
 * <p>Server-side and per target: the count lives on the target's {@link ModAttachments#MAGIC_STRAIN}, so several
 * casters working together fill one count, which is the canon way to stun a dragon.
 */
@NullMarked
public final class MagicResistance {

    /** The heritage trait that gives a player a hide: giants and half-giants. */
    public static final String HIDE_TRAIT = "spell_resistant_hide";
    /** A giant's blood in a wizard: Hagrid stood through several Stunners, not through any number. */
    public static final float HERITAGE_HIDE = 0.5f;
    /** A lethal-gaze boss (the basilisk) — the old coin flip, made a rule: two at once. */
    public static final float LETHAL_GAZE_HIDE = 0.5f;

    private MagicResistance() {}

    /** How strongly this body turns enchantments aside: 0 for none. */
    public static float hideOf(LivingEntity target) {
        if (target instanceof GenericBeastEntity beast) {
            SpellResist resist = beast.abilityOf(SpellResist.class);
            if (resist != null) {
                return resist.resistFraction();
            }
            if (beast.has(Trait.LETHAL_GAZE)) {
                return LETHAL_GAZE_HIDE;
            }
            return 0.0f;
        }
        if (target instanceof ServerPlayer player && HeritageAPI.getData(player).hasTrait(HIDE_TRAIT)) {
            return HERITAGE_HIDE;
        }
        return 0.0f;
    }

    /**
     * Whether this spell carries anything a hide can turn aside: a target effect, an authored effect list, a disarm,
     * a stun or a lift. A spell that is only force — Confringo's blast, Flipendo's shove — has nothing for the hide
     * to refuse, so it never counts toward (or reports on) the hide.
     */
    public static boolean carriesEnchantment(Spell spell) {
        SpellProperties props = spell.getProperties();
        return !SpellEffectRunner.effectsOf(spell).isEmpty()
                || (props != null && (!props.getTargetEffects().isEmpty() || props.disarms() || props.stuns()
                        || props.levitatesTarget()));
    }

    /** {@link #takesHold(LivingEntity, LivingEntity)} for {@code spell}: always true for a spell with no enchantment. */
    public static boolean takesHold(Spell spell, LivingEntity target, @Nullable LivingEntity caster) {
        return !carriesEnchantment(spell) || takesHold(target, caster);
    }

    /**
     * One spell's enchantment reaches {@code target}. Returns whether it takes hold; when it does not, the hide
     * visibly turns it aside and the caster is told how far the count has got.
     *
     * <p>Call once per spell per target, before running the spell's effects on it. A body with no hide always
     * answers {@code true} and nothing is stored.
     */
    public static boolean takesHold(LivingEntity target, @Nullable LivingEntity caster) {
        int needed = MagicResistanceRules.spellsToOvercome(hideOf(target));
        if (needed <= 1 || !(target.level() instanceof ServerLevel level)) {
            return true;
        }
        Strain next = MagicResistanceRules.afterSpell(target.getData(ModAttachments.MAGIC_STRAIN.get()),
                level.getGameTime());
        if (MagicResistanceRules.overcome(next, needed)) {
            target.setData(ModAttachments.MAGIC_STRAIN.get(), Strain.NONE);
            if (caster instanceof ServerPlayer player) {
                PlayerFeedback.actionBar(player, Component.translatable(
                        "spell.wizards_and_beasts.hide.overcome", target.getDisplayName()));
            }
            return true;
        }
        target.setData(ModAttachments.MAGIC_STRAIN.get(), next);
        level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + target.getBbHeight() * 0.6,
                target.getZ(), 12, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.4);
        level.playSound(null, target.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.NEUTRAL, 0.6f, 0.7f);
        if (caster instanceof ServerPlayer player) {
            PlayerFeedback.actionBar(player, Component.translatable(
                    "spell.wizards_and_beasts.hide.turned", target.getDisplayName(), next.spells(), needed));
        }
        return false;
    }
}
