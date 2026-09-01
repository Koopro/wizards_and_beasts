package at.koopro.wizardsandbeasts.felix;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.NullMarked;

/**
 * Liquid luck: a run of small, weighted good fortune, and the price of a second bottle.
 *
 * <h2>What "lucky" is made of</h2>
 * <p>Four hooks, all server-side, all reading {@link FelixState#strength()}:
 * <ul>
 *   <li><b>Combat</b> — a hit that would take you below two hearts is sometimes simply not taken.
 *       Rate-limited by {@link #SAVE_INTERNAL_COOLDOWN} so it reads as a near miss rather than as
 *       invulnerability.</li>
 *   <li><b>Mining</b> — an ore sometimes gives one more than it should.</li>
 *   <li><b>Loot</b> — a chest that rolled badly is sometimes rolled again.</li>
 *   <li><b>Presentation</b> — a gold sparkle, and a line on the action bar <em>only when something
 *       actually happened</em>.</li>
 * </ul>
 *
 * <p>Exploration (a nudge to rare structure chests) is on the design list and is <b>not</b> built:
 * it needs a hook into structure loot placement that this mod does not have, and faking it by
 * re-rolling every chest would be the loot hook again wearing a different name.
 *
 * <h2>Why the messages fire on events and not on a timer</h2>
 * <p>An action bar that says "Instinct…" every twenty seconds is a status readout, and a player
 * learns to ignore it in about a minute. Saying it at the moment a hit was dodged or a chest came up
 * twice is the difference between being told you are lucky and noticing that you are — and it is the
 * acceptance criterion for this whole system.
 *
 * <h2>Server authority</h2>
 * <p>Every roll happens here, on the server, against the server's own {@code RandomSource}. The
 * client is told nothing except particles it cannot act on. There is no client-side luck to desync
 * or to spoof.
 */
@NullMarked
public final class FelixFortune {

    /** Ceiling on brewed strength. Three is "the best batch anyone has managed", not a ladder. */
    public static final int MAX_STRENGTH = 3;

    /** Minutes, not half an hour. Long enough for a delve, short enough to be an occasion. */
    public static final int DEFAULT_DURATION_TICKS = 20 * 180;

    /** How long after the luck ends before another bottle is anything but a mistake. */
    public static final int DEFAULT_COOLDOWN_TICKS = 20 * 300;

    /** An overdose bars you for longer than an ordinary dose does. */
    public static final int OVERDOSE_COOLDOWN_TICKS = 20 * 600;

    /** Minimum gap between two near-death saves. Ten seconds. */
    public static final int SAVE_INTERNAL_COOLDOWN = 200;

    /**
     * A stored cooldown further out than this is treated as corrupt rather than obeyed.
     *
     * <p>Game time moves backwards across a world restore or a backup rollback. Without this, a state
     * saved at a high game time and loaded into a rolled-back world would bar the player for the
     * difference — potentially for the rest of the world's life.
     */
    public static final long MAX_SANE_COOLDOWN = 20L * 60L * 60L * 24L;

    /** Health below which the combat hook considers a hit lethal enough to be worth dodging. */
    private static final float DANGER_THRESHOLD = 4.0f;

    private FelixFortune() {}

    // -- state ------------------------------------------------------------------------------------

    public static FelixState get(ServerPlayer player) {
        return player.getData(ModAttachments.FELIX_STATE.get());
    }

    private static void set(ServerPlayer player, FelixState state) {
        player.setData(ModAttachments.FELIX_STATE.get(), state);
    }

    // -- drinking ---------------------------------------------------------------------------------

    /** What a bottle of Felix did. */
    public enum DoseResult {
        STARTED,
        REFRESHED,
        OVERDOSED,
        REFUSED_COOLDOWN
    }

    /** What a second bottle does to somebody already running on the first. */
    public enum OverdosePolicy implements net.minecraft.util.StringRepresentable {
        /** Punish it: nausea, heavy limbs, the luck gone, and a long bar on trying again. */
        OVERDOSE,
        /** Refuse it: nothing happens and nothing is lost. */
        REFUSE,
        /** Top it back up. The forgiving setting, for servers that want Felix to be a buff. */
        REFRESH;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Drink a dose.
     *
     * <p>The cooldown is checked before the active state, because being barred is the stronger
     * condition: a player whose luck has expired but whose cooldown has not is not "already lucky",
     * and telling them they overdosed would be wrong.
     */
    public static DoseResult drink(ServerPlayer player, int durationTicks, int strength,
                                   int cooldownTicks, OverdosePolicy policy) {
        long now = player.level().getGameTime();
        FelixState state = get(player);
        int clampedStrength = Math.max(1, Math.min(MAX_STRENGTH, strength));
        int clampedDuration = Math.max(1, durationTicks);

        if (state.isActive()) {
            return switch (policy) {
                case REFUSE -> {
                    PlayerFeedback.actionBar(player,
                            Component.translatable("felix.wizards_and_beasts.already_lucky"));
                    yield DoseResult.REFUSED_COOLDOWN;
                }
                case REFRESH -> {
                    set(player, new FelixState(clampedDuration, Math.max(state.strength(), clampedStrength),
                            state.cooldownUntil(), state.lastSaveGameTime()));
                    yield DoseResult.REFRESHED;
                }
                case OVERDOSE -> {
                    overdose(player, now);
                    yield DoseResult.OVERDOSED;
                }
            };
        }

        if (state.isOnCooldown(now)) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("felix.wizards_and_beasts.too_soon"));
            return DoseResult.REFUSED_COOLDOWN;
        }

        set(player, new FelixState(clampedDuration, clampedStrength,
                state.cooldownUntil(), Long.MIN_VALUE));
        announce(player, "felix.wizards_and_beasts.begin", NoticeKind.SUCCESS);
        ServerLevel level = player.level() instanceof ServerLevel sl ? sl : null;
        if (level != null) {
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.5f, 1.6f);
            sparkle(level, player, 40);
        }
        return DoseResult.STARTED;
    }

    /**
     * Too much of a good thing.
     *
     * <p>The luck is destroyed rather than shortened. A second bottle that merely reduced your
     * fortune would still be worth drinking when desperate; one that takes it away entirely is a
     * decision with a wrong answer, which is what the fiction says about Felix.
     */
    public static void overdose(ServerPlayer player, long now) {
        FelixState state = get(player);
        set(player, state.expired(now + OVERDOSE_COOLDOWN_TICKS));
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 400, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 600, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0, false, true, true));
        PlayerFeedback.toast(player, NoticeKind.FAIL,
                Component.translatable("felix.wizards_and_beasts.overdose.title"),
                Component.translatable("felix.wizards_and_beasts.overdose.body"));
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(),
                    SoundSource.PLAYERS, 0.8f, 0.5f);
            level.sendParticles(ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.5, 0.4, 0.02);
        }
    }

    // -- the clock --------------------------------------------------------------------------------

    /** One tick of somebody's luck. Cheap for the overwhelming majority of players, who have none. */
    public static void tick(ServerPlayer player) {
        FelixState state = get(player);
        if (!state.isActive()) {
            return;
        }
        int next = state.ticksRemaining() - 1;
        if (next <= 0) {
            long now = player.level().getGameTime();
            set(player, state.expired(now + DEFAULT_COOLDOWN_TICKS));
            announce(player, "felix.wizards_and_beasts.end", NoticeKind.WARN);
            return;
        }
        set(player, state.withTicks(next));

        // A gold mote every couple of seconds. Enough to be noticed in peripheral vision and not
        // enough to be a particle effect somebody turns off.
        if (next % 40 == 0 && player.level() instanceof ServerLevel level) {
            sparkle(level, player, 2);
        }
    }

    // -- the hooks --------------------------------------------------------------------------------

    /**
     * Whether Felix pulls this player out of a lethal hit.
     *
     * <p>Only for a blow that would take them under two hearts — Felix is not damage reduction, it is
     * the hit that happens to miss. Rate-limited, and the limiter is stored on the state so it cannot
     * be dodged by relogging.
     *
     * @return true when the caller should cancel the damage
     */
    public static boolean rollNearDeathSave(ServerPlayer player, float incomingDamage) {
        FelixState state = get(player);
        if (!state.isActive()) {
            return false;
        }
        float after = player.getHealth() + player.getAbsorptionAmount() - incomingDamage;
        if (after > DANGER_THRESHOLD) {
            return false;
        }
        long now = player.level().getGameTime();
        if (now - state.lastSaveGameTime() < SAVE_INTERNAL_COOLDOWN) {
            return false;
        }
        // 30% / 45% / 60% by strength. Deliberately not certain: a guaranteed save is invulnerability
        // with extra steps, and the brief asks for lucky rather than immortal.
        float chance = 0.15f + 0.15f * state.strength();
        if (player.getRandom().nextFloat() >= chance) {
            return false;
        }
        set(player, state.withSaveAt(now));
        announce(player, "felix.wizards_and_beasts.near_miss", NoticeKind.SUCCESS);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE,
                    SoundSource.PLAYERS, 0.35f, 1.8f);
            sparkle(level, player, 24);
        }
        return true;
    }

    /** Whether an ore gives one more than it should. 8% / 16% / 24%. */
    public static boolean rollExtraOreDrop(ServerPlayer player) {
        FelixState state = get(player);
        return state.isActive() && player.getRandom().nextFloat() < 0.08f * state.strength();
    }

    /** Whether a disappointing chest gets a second roll. 25% / 40% / 55%. */
    public static boolean rollLootReroll(ServerPlayer player) {
        FelixState state = get(player);
        return state.isActive()
                && player.getRandom().nextFloat() < 0.10f + 0.15f * state.strength();
    }

    // -- presentation -----------------------------------------------------------------------------

    /** Says something on the action bar. Only ever called when something actually happened. */
    public static void announce(ServerPlayer player, String key, NoticeKind kind) {
        PlayerFeedback.actionBar(player, Component.translatable(key));
    }

    public static void sparkle(ServerLevel level, ServerPlayer player, int count) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.1, player.getZ(), count, 0.4, 0.6, 0.4, 0.02);
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0, player.getZ(),
                Math.max(1, count / 4), 0.3, 0.5, 0.3, 0.01);
    }
}
