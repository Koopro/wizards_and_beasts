package at.koopro.wizardsandbeasts.stats;

import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class PlayerStatsAPI {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PlayerStatsAPI() {}

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns the current value of the given stat for the player. KNOWLEDGE is derived: on the
     * server it is computed live from learning sources; on the client it reads the last synced
     * snapshot ({@link PlayerStatsData#knowledge()}).
     */
    public static int getStat(@NonNull Player player, @NonNull PlayerStat stat) {
        if (stat == PlayerStat.KNOWLEDGE) {
            return player.level().isClientSide()
                    ? getData(player).knowledge()
                    : computeKnowledge(player);
        }
        return getData(player).get(stat);
    }

    /**
     * Derives the KNOWLEDGE stat from the player's accumulated learning: spells known, bestiary
     * entries discovered, skill nodes unlocked, and lore books read. Server-authoritative — reads
     * the source attachments directly. Safe to call client-side too (attachments mirror via sync),
     * but prefer the synced snapshot for display.
     */
    public static int computeKnowledge(@NonNull Player player) {
        int spells   = player.getData(ModAttachments.SPELL_DATA.get()).getKnownSpells().size();
        int bestiary = player.getData(ModAttachments.BESTIARY_DATA.get()).tiers().size();
        var skill    = player.getData(ModAttachments.SKILL_DATA.get());
        int nodes    = skill.getUnlockedSkills().size();
        int books    = skill.getLoreItemsRead();
        return KnowledgeFormula.compute(spells, bestiary, nodes, books);
    }

    /**
     * The highest POWER this player's heritage allows them to grow to, or
     * {@link PlayerStatsData#MAX_VALUE} when no heritage has been chosen yet.
     *
     * <p>Server-side. The character sheet computes the same number on the client from the synced
     * {@link HeritageVariant} rather than being told it — {@link PowerBandTable} is pure and lives in
     * the common package precisely so both sides can read one table. Nothing on the client is
     * <em>trusted</em> with it: {@link #grantPowerGrowth} is the only thing that raises POWER through
     * play, and it clamps here regardless of what any client believes.
     */
    public static int getPowerCap(@NonNull Player player) {
        if (!(player instanceof ServerPlayer sp)) return PlayerStatsData.MAX_VALUE;
        HeritageVariant variant = HeritageAPI.getPlayerHeritageVariant(sp);
        return variant == null ? PlayerStatsData.MAX_VALUE : PowerBandTable.getBandMax(variant);
    }

    /** True when POWER can no longer grow: either the band max or the growth allowance is spent. */
    public static boolean isPowerCapped(@NonNull Player player) {
        return getData(player).power() >= getPowerCap(player) || getRemainingPowerGrowth(player) <= 0;
    }

    /** Returns true if the player rolled a prodigy result at character creation. */
    public static boolean isProdigy(@NonNull Player player) {
        return getData(player).isProdigy();
    }

    /**
     * Returns how many more Power growth points this player can still accumulate.
     * Squib always returns 0.
     */
    public static int getRemainingPowerGrowth(@NonNull Player player) {
        if (!(player instanceof ServerPlayer sp)) return 0;
        HeritageVariant variant = HeritageAPI.getPlayerHeritageVariant(sp);
        if (variant == null) return 0;
        int cap = PowerBandTable.getGrowthCap(variant);
        return Math.max(0, cap - getData(player).powerGrowthAccumulated());
    }

    /** Returns a snapshot of all five stats including derived Knowledge. */
    @NonNull
    public static Map<PlayerStat, Integer> getAllStats(@NonNull Player player) {
        Map<PlayerStat, Integer> snapshot = new EnumMap<>(PlayerStat.class);
        for (PlayerStat stat : PlayerStat.values()) {
            snapshot.put(stat, getStat(player, stat));
        }
        return snapshot;
    }

    // -------------------------------------------------------------------------
    // Write (server-only)
    // -------------------------------------------------------------------------

    /**
     * Admin override: sets a stat directly, bypassing growth caps. Clamps to 0–100.
     * Setting KNOWLEDGE is a no-op (derived stat).
     */
    public static void setStat(@NonNull Player player, @NonNull PlayerStat stat, int value) {
        requireServer(player);
        if (stat.isDerived()) {
            LOGGER.warn("[WizardsAndBeasts] setStat: cannot set derived stat {} for {}",
                    stat.getId(), player.getName().getString());
            return;
        }
        // Derived stats returned above, so this is always a real write; the record clamps.
        PlayerStatsData updated = getData(player).with(stat, value);
        LOGGER.info("[WizardsAndBeasts] setStat: {} {} = {}", player.getName().getString(), stat.getId(), value);
        setAndSync(player, updated);
    }

    /**
     * Grants Power growth from a milestone or admin command, respecting the Heritage growth cap.
     * No-op for Squib players.
     */
    public static void grantPowerGrowth(@NonNull Player player, int amount) {
        grantPowerGrowth(player, amount, null);
    }

    /** @param sourceKey lang key naming what caused this, for the level-up notice; may be null. */
    public static void grantPowerGrowth(@NonNull Player player, int amount, @Nullable String sourceKey) {
        requireServer(player);
        if (!(player instanceof ServerPlayer sp)) return;
        HeritageVariant variant = HeritageAPI.getPlayerHeritageVariant(sp);
        if (variant == null) return;
        if ("squib".equals(variant.getId())) {
            LOGGER.debug("[WizardsAndBeasts] grantPowerGrowth: {} is a Squib — cannot grow Power",
                    player.getName().getString());
            return;
        }
        int cap = PowerBandTable.getGrowthCap(variant);
        int bandMax = PowerBandTable.getBandMax(variant);
        PlayerStatsData old = getData(player);
        int accumulated = old.powerGrowthAccumulated();
        int allowed = Math.max(0, cap - accumulated);
        int actual = Math.min(amount, allowed);
        if (actual <= 0) {
            LOGGER.debug("[WizardsAndBeasts] grantPowerGrowth: {} already at growth cap", player.getName().getString());
            return;
        }
        // max(), not a bare min against the band: `/wandb player stats set` is an admin override that
        // deliberately ignores the band, and a bare clamp would let the next milestone silently drag
        // an overridden Power back down to it. Growth can stall at the cap; it must never reverse.
        int newPower = Math.max(old.power(), Math.min(old.power() + actual, bandMax));
        int newAccumulated = accumulated + actual;
        LOGGER.debug("[WizardsAndBeasts] grantPowerGrowth: {} +{} Power (now {}, accumulated {})",
                player.getName().getString(), actual, newPower, newAccumulated);
        setAndSync(player, old.withPower(newPower).withPowerGrowthAccumulated(newAccumulated));
        if (newPower > old.power() && player instanceof ServerPlayer sp2) {
            StatProgression.announceLevelUp(sp2, PlayerStat.POWER, old.power(), newPower, sourceKey);
        }
    }

    /**
     * Increments training progress for a trainable stat by the given raw amount (before S-curve
     * scaling). When the accumulator reaches 1.0+, the stat integer is incremented and the
     * accumulator decremented. Rejects calls for non-trainable stats.
     *
     * <p>The arithmetic lives in {@link StatTrainingScaler#apply}; this method is the part that
     * needs a {@link Player} — reading the block, writing it back, and announcing any point earned.
     * The loop used to be inline here and re-read the <em>player attachment</em> for its ceiling
     * check, which cannot have changed since nothing had been written yet, so the guard was dead and
     * the leftover fraction at 100 was banked forever.
     */
    public static void addTrainingProgress(@NonNull Player player, @NonNull PlayerStat stat, float rawAmount) {
        addTrainingProgress(player, stat, rawAmount, null);
    }

    /** @param sourceKey lang key naming what caused this, for the level-up notice; may be null. */
    public static void addTrainingProgress(@NonNull Player player, @NonNull PlayerStat stat,
                                           float rawAmount, @Nullable String sourceKey) {
        requireServer(player);
        if (!stat.isTrainable()) {
            LOGGER.warn("[WizardsAndBeasts] addTrainingProgress: stat {} is not trainable", stat.getId());
            return;
        }
        PlayerStatsData old = getData(player);
        int before = old.get(stat);
        float progressBefore = old.trainingProgress().getOrDefault(stat, 0f);

        StatTrainingScaler.Step step = StatTrainingScaler.apply(before, progressBefore, rawAmount);
        if (step.stat() == before && step.progress() == progressBefore) {
            return; // at the ceiling, or a zero-value grant: nothing to write and nothing to sync
        }

        Map<PlayerStat, Float> newTraining = new HashMap<>(old.trainingProgress());
        newTraining.put(stat, step.progress());
        setAndSync(player, old.with(stat, step.stat()).withTrainingProgress(newTraining));

        if (step.gained() > 0 && player instanceof ServerPlayer sp) {
            StatProgression.announceLevelUp(sp, stat, before, step.stat(), sourceKey);
        }
    }

    /**
     * Direct stat bump bypassing training progress, clamped to 0–100.
     * For POWER, routes through grantPowerGrowth.
     */
    public static void grantMilestoneBump(@NonNull Player player, @NonNull PlayerStat stat, int amount) {
        grantMilestoneBump(player, stat, amount, null);
    }

    /** @param sourceKey lang key naming the milestone, for the level-up notice; may be null. */
    public static void grantMilestoneBump(@NonNull Player player, @NonNull PlayerStat stat, int amount,
                                          @Nullable String sourceKey) {
        requireServer(player);
        if (stat == PlayerStat.POWER) {
            grantPowerGrowth(player, amount, sourceKey);
            return;
        }
        if (stat.isDerived()) {
            LOGGER.warn("[WizardsAndBeasts] grantMilestoneBump: cannot bump derived stat {}", stat.getId());
            return;
        }
        LOGGER.info("[WizardsAndBeasts] grantMilestoneBump: {} {} +{}", player.getName().getString(), stat.getId(), amount);
        int before = getStat(player, stat);
        setStat(player, stat, before + amount);
        int after = getStat(player, stat);
        if (after > before && player instanceof ServerPlayer sp) {
            StatProgression.announceLevelUp(sp, stat, before, after, sourceKey);
        }
    }

    /**
     * Idempotent character creation initializer. Rolls Power for the given heritage, sets isProdigy,
     * leaves trainable stats at 0. If this player already has non-empty stats, logs a warning and returns.
     * Must be called from the server thread.
     */
    public static void initializeStatsForNewPlayer(
            @NonNull Player player,
            @NonNull HeritageVariant heritage,
            @NonNull RandomSource random) {
        requireServer(player);
        PlayerStatsData existing = getData(player);
        if (!existing.isEmpty()) {
            LOGGER.warn("[WizardsAndBeasts] initializeStatsForNewPlayer: {} already has stats — skipping re-roll",
                    player.getName().getString());
            return;
        }
        PowerBandTable.PowerRollResult result = PowerBandTable.rollInitialPower(heritage, random);
        PlayerStatsData fresh = PlayerStatsData.EMPTY
                .withPower(result.power())
                .withProdigy(result.isProdigy());
        LOGGER.info("[WizardsAndBeasts] initializeStatsForNewPlayer: {} heritage={} power={} prodigy={}",
                player.getName().getString(), heritage.getId(), result.power(), result.isProdigy());
        setAndSync(player, fresh);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    @NonNull
    public static PlayerStatsData getData(@NonNull Player player) {
        return player.getData(ModAttachments.PLAYER_STATS.get());
    }

    public static void setAndSync(@NonNull Player player, @NonNull PlayerStatsData data) {
        player.setData(ModAttachments.PLAYER_STATS.get(), data);
        if (player instanceof ServerPlayer sp) {
            PlayerStatsSyncPayload.syncToPlayer(sp);
        }
    }

    private static void requireServer(Player player) {
        if (player.level().isClientSide()) {
            throw new IllegalStateException("PlayerStatsAPI write methods must be called server-side");
        }
    }
}
