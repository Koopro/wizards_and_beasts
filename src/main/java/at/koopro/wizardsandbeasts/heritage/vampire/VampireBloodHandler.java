package at.koopro.wizardsandbeasts.heritage.vampire;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionEnforcer;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicy;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicyResolver;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;

/**
 * The blood economy itself: what it costs to be awake, what a bite is worth, and what running dry does.
 *
 * <p>All server-side and all static, in the shape {@code ObscurialServerLogic} and
 * {@code WerewolfTransformService} use — {@link VampireBloodEvents} decides <em>when</em> these run and
 * this decides what happens. The split is worth the extra file: every rule below is a balance decision
 * somebody will want to read without wading through {@code @SubscribeEvent} plumbing.
 *
 * <p>Nothing here asks whether a player is a vampire. It asks {@link NutritionPolicyResolver} whether
 * they are on the {@link NutritionPolicy#BLOOD} economy, which is the same question for now and the
 * right question when it stops being.
 */
@NullMarked
public final class VampireBloodHandler {

    /**
     * Ticks between enforcement passes.
     *
     * <p>Ten rather than one: the pool moves by a fraction of a point per second, the debuffs are
     * re-applied with a duration longer than this interval so they never blink, and the mirrored food
     * level has nothing to say more often than twice a second. One pass per tick would be twenty times
     * the work for no visible difference.
     */
    public static final int TICK_INTERVAL = 10;

    /** Duration the thirst debuffs are re-applied with. Longer than the interval, so they never lapse. */
    private static final int DEBUFF_TICKS = TICK_INTERVAL + 20;

    /** Ticks between starvation hits once the pool is under the damage floor. */
    private static final int STARVATION_DAMAGE_INTERVAL = 40;

    /**
     * Exhaustion banked per pass while sprinting.
     *
     * <p>0.1 per half-second is 0.2 a second, which at the shipped drain rate makes running exactly twice
     * as expensive as walking. Not a config key yet on purpose: it is the only exertion cost there is, and
     * a knob for one caller is a knob for nobody.
     */
    private static final float SPRINT_EXHAUSTION_PER_PASS = 0.1f;

    private VampireBloodHandler() {}

    /**
     * One enforcement pass for one player. Called every {@link #TICK_INTERVAL} ticks.
     *
     * @param serverTick the server's tick count, used only to space starvation damage out
     */
    public static void tick(ServerPlayer player, ServerLevel level, long serverTick) {
        NutritionPolicy policy = NutritionPolicyResolver.resolve(player);
        if (policy.usesVanillaHunger()) {
            return;
        }
        if (policy != NutritionPolicy.BLOOD) {
            // A policy with no pool still needs its vanilla hunger driven, or it starves invisibly.
            NutritionEnforcer.driveVanillaHunger(player, policy, 1.0f);
            return;
        }

        VampireBloodData data = VampireBloodAPI.getData(player);
        data.addTicksSinceLastFeed(TICK_INTERVAL);

        // Creative and spectator are outside the economy entirely. Not merely exempt from the damage:
        // draining a creative player's pool would leave them STARVING the instant they switched back.
        boolean exempt = player.isCreative() || player.isSpectator();
        if (!exempt) {
            drain(player, data, VampireBloodConfig.drainPerSecond * TICK_INTERVAL / 20.0f);
        }

        ThirstStage stage = data.getThirstStage();
        NutritionEnforcer.driveVanillaHunger(player, policy, data.getBloodPercent());

        if (!exempt) {
            applyThirstEffects(player, level, data, stage, serverTick);
        }
        announceStageChange(player, data, stage);
        VampireBloodAPI.syncIfChanged(player);
    }

    /**
     * Spends the pass's share of the passive drain, plus whatever exertion has banked up.
     *
     * <p>Two channels, mirroring vanilla's own split between a hunger clock and an exhaustion counter.
     * The passive drain is the cost of existing and is spent immediately. Exhaustion is the cost of
     * effort — sprinting for now, and whatever else wants to charge for itself later — and is banked
     * until it is worth a whole point, so a sprint that lasts half a second costs a fraction rather than
     * being rounded to nothing or to one.
     *
     * <p>Sprinting therefore roughly doubles the drain at the shipped rates, which is the honest
     * consequence of a body running on someone else's blood: a vampire in a hurry gets thirsty faster.
     */
    private static void drain(ServerPlayer player, VampireBloodData data, float passive) {
        if (player.isSprinting()) {
            data.addExhaustion(SPRINT_EXHAUSTION_PER_PASS);
        }
        float total = Math.max(0f, passive);
        float banked = Mth.floor(data.getExhaustion());
        if (banked > 0f) {
            data.setExhaustion(data.getExhaustion() - banked);
            total += banked;
        }
        data.drainBlood(total);
    }

    /**
     * The penalties for each band.
     *
     * <p>Notice how little is here. Two of the three things thirst does — no sprinting, no natural
     * regeneration — are not applied by this method at all: they fall out of the mirrored food level in
     * {@link NutritionEnforcer}, because vanilla already gates both on it. Duplicating them as attribute
     * modifiers would have meant two systems taking a sprint away and a race to give it back.
     */
    private static void applyThirstEffects(ServerPlayer player, ServerLevel level,
                                           VampireBloodData data, ThirstStage stage, long serverTick) {
        switch (stage) {
            case SATED, THIRSTY -> {
                // Nothing. THIRSTY is a warning, and it has already been given by the bar's colour.
            }
            case PARCHED -> player.addEffect(
                    new MobEffectInstance(MobEffects.WEAKNESS, DEBUFF_TICKS, 0, false, false, true));
            case STARVING -> {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DEBUFF_TICKS, 1, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DEBUFF_TICKS, 0, false, false, true));
                if (data.getBlood() <= VampireBloodConfig.starvationDamageFloor
                        && VampireBloodConfig.starvationDamage > 0f
                        && serverTick % STARVATION_DAMAGE_INTERVAL == 0) {
                    // starve(), not magic(): it is starvation, and a server that has turned starvation
                    // damage off through the vanilla difficulty rules should not be surprised by this one.
                    // hurtServer rather than the deprecated hurt(): the level is already in hand here.
                    player.hurtServer(level, level.damageSources().starve(), VampireBloodConfig.starvationDamage);
                }
            }
        }
    }

    /** Tells the player once, when they fall into a worse band. Climbing back out is silent. */
    private static void announceStageChange(ServerPlayer player, VampireBloodData data, ThirstStage stage) {
        if (data.getLastNotifiedStage() == stage) {
            return;
        }
        boolean worse = data.getLastNotifiedStage() == null || stage.ordinal() > data.getLastNotifiedStage().ordinal();
        data.setLastNotifiedStage(stage);
        if (worse && stage != ThirstStage.SATED) {
            PlayerFeedback.actionBar(player, Component.translatable(
                    "message.wizards_and_beasts.vampire.thirst." + stage.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    /**
     * A bare-handed right-click on something with blood in it.
     *
     * @return {@code true} when the interaction belongs to this system and the caller should cancel it —
     *         including every refusal. A refused feed still consumes the click, because the alternative is
     *         a vampire who fails to bite a horse and mounts it instead.
     */
    public static boolean attemptFeed(ServerPlayer player, LivingEntity target, ServerLevel level) {
        if (player.isSpectator() || target == player) {
            return false;
        }
        if (NutritionPolicyResolver.resolve(player) != NutritionPolicy.BLOOD) {
            return false;
        }
        // The escape hatch. Crouching hands the click back to vanilla, which is how a vampire still
        // mounts a horse, leashes a wolf, or shears a sheep by hand — every empty-hand interaction the
        // feed would otherwise swallow, reachable without a keybind or a config toggle.
        if (player.isShiftKeyDown()) {
            return false;
        }
        // Not a blood source at all: pass the click through untouched. A vampire opening a villager's
        // trades or riding a strider should behave exactly like anyone else.
        if (!BloodSourceRules.hasBlood(target)) {
            return false;
        }
        if (BloodSourceRules.isProtected(target)) {
            refuse(player, "message.wizards_and_beasts.vampire.feed.protected");
            return true;
        }

        VampireBloodData data = VampireBloodAPI.getData(player);
        if (data.getTicksSinceLastFeed() < VampireBloodConfig.feedCooldownTicks) {
            refuse(player, "message.wizards_and_beasts.vampire.feed.too_soon");
            return true;
        }
        long gameTime = level.getGameTime();
        if (BloodSourceRules.isRecentlyDrained(target, gameTime)) {
            refuse(player, "message.wizards_and_beasts.vampire.feed.drained");
            return true;
        }
        if (data.getBlood() >= data.getMaxBlood()) {
            // Refused before the target is touched. Biting a cow for nothing is not a flourish, it is a
            // hurt animal and a confused player.
            refuse(player, "message.wizards_and_beasts.vampire.feed.full");
            return true;
        }

        feed(player, target, level, gameTime);
        return true;
    }

    /** The successful half of {@link #attemptFeed}, once every refusal has been ruled out. */
    private static void feed(ServerPlayer player, LivingEntity target, ServerLevel level, long gameTime) {
        float gained = VampireBloodAPI.addBlood(player, BloodSourceRules.yieldFrom(target));
        VampireBloodData data = VampireBloodAPI.getData(player);
        data.setTicksSinceLastFeed(0);
        BloodSourceRules.markDrained(target, gameTime);

        // playerAttack rather than a bespoke source: a bitten animal should panic, a bitten monster
        // should fight back, and the kill should count as the vampire's. All three come free from
        // vanilla's own aggro and drop handling once the source names the player.
        if (VampireBloodConfig.feedDamage > 0f) {
            target.hurtServer(level, level.damageSources().playerAttack(player), VampireBloodConfig.feedDamage);
        }
        // The mark of a fed-on creature, and the reason a herd is not a buffet: it is visible, it lasts
        // as long as the drained window, and it tells anyone watching what just happened.
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                Math.max(1, VampireBloodConfig.drainedImmunityTicks), 0, false, true, true));

        if (VampireBloodConfig.bloodRushTicks > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED,
                    VampireBloodConfig.bloodRushTicks, 0, false, true, true));
        }

        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY() + target.getBbHeight() * 0.75, target.getZ(),
                8, 0.2, 0.2, 0.2, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_DRINK.value(),
                SoundSource.PLAYERS, 0.8f, 0.6f);

        PlayerFeedback.actionBar(player, Component.translatable(
                "message.wizards_and_beasts.vampire.feed.success", Math.round(gained)));
    }

    /**
     * Nutrition a non-vanilla eater must not keep.
     *
     * <p>Called from the finish of any use-item action; {@link NutritionEnforcer} restores the bar to
     * where it was before the bite. The message is only sent for something that actually was food,
     * because "that does nothing for you" is confusing feedback for drawing a bow.
     */
    public static void denyNutrition(ServerPlayer player, boolean wasFood) {
        NutritionEnforcer.restoreAfterMeal(player);
        if (wasFood) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("message.wizards_and_beasts.vampire.food_useless"));
        }
    }

    private static void refuse(ServerPlayer player, String translationKey) {
        PlayerFeedback.actionBar(player, Component.translatable(translationKey));
    }
}
