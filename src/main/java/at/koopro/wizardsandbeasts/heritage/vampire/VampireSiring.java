package at.koopro.wizardsandbeasts.heritage.vampire;

import at.koopro.wizardsandbeasts.event.heritage.VampireHeritageHandler;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.ConditionRules;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageAssignment;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicyResolver;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * How a witch, wizard or Veela becomes a vampire in play: a sire offers their blood, and the one offered takes it.
 *
 * <p>Two deliberate acts, never one. The sire crouches and takes the other's hand (a bare-handed crouching
 * right-click); that is an <em>offer</em>, nothing more, and it says so to both of them. The offered player accepts by
 * doing the same back within {@link #OFFER_TICKS}. Nobody is turned by a stray click, a feed, or a fight — a bite that
 * could turn a player without their say would make vampirism a griefing tool, and a condition with no cure must not be
 * one.
 *
 * <p>Every refusal goes through {@link ConditionRules}, the same door as the werewolf's bite: a Goblin cannot be turned,
 * a werewolf has no room for it, a vampire already is one. The offer is refused at once when the rules already say no,
 * and the acceptance asks again, because the world may have changed in the thirty seconds between. The sire pays
 * {@link #SIRE_COST} blood — the first caller of {@link VampireBloodAPI#consumeBlood} — and the check that they can
 * afford it comes before anything is written, so a refused turning costs nobody anything.
 */
@NullMarked
public final class VampireSiring {

    /** Blood the sire gives up. A third of the shipped pool: a vampire can make one, not a household, in a night. */
    public static final float SIRE_COST = 35.0f;

    /** How long an offer stands: thirty seconds, long enough to read it and cross the room, not to forget it. */
    public static final long OFFER_TICKS = 600L;

    /** What a crouching hand-to-hand between two players came to. */
    public enum Outcome {
        /** Not a siring at all: the click belongs to someone else (vanilla, the feed). */
        NONE,
        /** The sire's blood was offered. */
        OFFERED,
        /** The offer was taken and the player carries vampirism now. */
        TURNED,
        /** It was a siring, and it was refused with a sentence to both sides. Nothing changed. */
        REFUSED
    }

    private record Offer(UUID sire, long expiresAt) {}

    /** The open offer each player holds, keyed by the one offered. Dropped on logout by {@link PlayerScopedState}. */
    private static final PlayerScopedState<Offer> OFFERS = PlayerScopedState.create("vampire-sire-offers");

    private VampireSiring() {}

    /** A vampire who can give the gift: thirsts and burns. A Dhampir has the thirst and not the rest. */
    public static boolean canSire(PlayerHeritageData data) {
        return data.hasTrait(NutritionPolicyResolver.TAG_BLOOD_HUNGER)
                && data.hasTrait(VampireHeritageHandler.TAG_SUNLIGHT_WEAKNESS);
    }

    /**
     * A crouching, bare-handed right-click from {@code actor} on {@code other}. Accepts an offer {@code other} made,
     * or makes one when {@code actor} can sire; anything else is {@link Outcome#NONE} and the click goes on its way.
     */
    public static Outcome interact(ServerPlayer actor, ServerPlayer other, long gameTime) {
        if (!actor.isShiftKeyDown() || actor == other) {
            return Outcome.NONE;
        }
        Offer pending = OFFERS.get(actor);
        if (pending != null && pending.sire().equals(other.getUUID())) {
            OFFERS.remove(actor);
            if (gameTime > pending.expiresAt()) {
                PlayerFeedback.actionBar(actor, Component.translatable(
                        "message.wizards_and_beasts.vampire.sire.lapsed", other.getDisplayName()));
                return Outcome.REFUSED;
            }
            return accept(other, actor);
        }
        if (canSire(HeritageAPI.getData(actor))) {
            return offer(actor, other, gameTime);
        }
        return Outcome.NONE;
    }

    /** The sire's half. Refused at once when the rules or the sire's thirst already say no. */
    public static Outcome offer(ServerPlayer sire, ServerPlayer target, long gameTime) {
        Component refusal = refusal(sire, target);
        if (refusal != null) {
            sire.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.DARK_PURPLE));
            return Outcome.REFUSED;
        }
        OFFERS.put(target, new Offer(sire.getUUID(), gameTime + OFFER_TICKS));
        PlayerFeedback.actionBar(sire, Component.translatable(
                "message.wizards_and_beasts.vampire.sire.offered", target.getDisplayName()));
        target.sendSystemMessage(Component.translatable(
                "message.wizards_and_beasts.vampire.sire.offer", sire.getDisplayName(), OFFER_TICKS / 20)
                .withStyle(ChatFormatting.DARK_PURPLE));
        return Outcome.OFFERED;
    }

    /** The offered player's half. Everything is asked again; only then is anything written. */
    public static Outcome accept(ServerPlayer sire, ServerPlayer target) {
        Component refusal = refusal(sire, target);
        if (refusal != null) {
            Component told = refusal.copy().withStyle(ChatFormatting.DARK_PURPLE);
            sire.sendSystemMessage(told);
            target.sendSystemMessage(told);
            return Outcome.REFUSED;
        }
        // refusal() has just ruled out every reason the door could say no, and nothing has run in between; the check
        // stays because a refused afflict must never leave the sire a pool short.
        if (HeritageAssignment.afflict(target, ConditionOrigin.SIRED) != null
                || !VampireBloodAPI.consumeBlood(sire, SIRE_COST)) {
            return Outcome.REFUSED;
        }
        target.sendSystemMessage(Component.translatable(
                "message.wizards_and_beasts.vampire.sire.turned", sire.getDisplayName())
                .withStyle(ChatFormatting.DARK_RED));
        sire.sendSystemMessage(Component.translatable(
                "message.wizards_and_beasts.vampire.sire.sired", target.getDisplayName())
                .withStyle(ChatFormatting.DARK_RED));
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + target.getBbHeight() * 0.8,
                    target.getZ(), 10, 0.25, 0.25, 0.25, 0.02);
            level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS,
                    0.8f, 0.5f);
        }
        return Outcome.TURNED;
    }

    /** Whether {@code target} holds an open offer from {@code sire}. For tests and the character sheet. */
    public static boolean hasOffer(ServerPlayer target, ServerPlayer sire) {
        Offer offer = OFFERS.get(target);
        return offer != null && offer.sire().equals(sire.getUUID());
    }

    /** Why this siring cannot happen, as a sentence; {@code null} when it can. Writes nothing. */
    private static @Nullable Component refusal(ServerPlayer sire, ServerPlayer target) {
        if (!canSire(HeritageAPI.getData(sire))) {
            return Component.translatable("message.wizards_and_beasts.vampire.sire.not_a_sire", sire.getDisplayName());
        }
        PlayerHeritageData data = HeritageAPI.getData(target);
        ConditionRules.Refusal rule = ConditionRules.toAcquire(data.getSelectedHeritage(),
                data.getSelectedHeritageVariant(), data.getCondition(), ConditionOrigin.SIRED);
        if (rule != null) {
            return ConditionRules.describe(rule, target.getDisplayName(), ConditionOrigin.SIRED, data.getCondition(),
                    data.getSelectedHeritageVariant());
        }
        if (VampireBloodAPI.getBlood(sire) < SIRE_COST) {
            return Component.translatable("message.wizards_and_beasts.vampire.sire.too_thirsty", sire.getDisplayName());
        }
        return null;
    }
}
