package at.koopro.wizardsandbeasts.stats;

import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NonNull;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class PlayerStatsAPI {

    private static final Logger LOGGER = LogManager.getLogger(PlayerStatsAPI.class);

    private PlayerStatsAPI() {}

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /** Returns the current value of the given stat for the player. KNOWLEDGE always returns 0 (TODO: derive). */
    public static int getStat(@NonNull Player player, @NonNull PlayerStat stat) {
        if (stat.isDerived()) {
            // TODO: derive from registries (spells learned + bestiary discoveries + skill nodes + books read)
            return 0;
        }
        PlayerStatsData data = getData(player);
        return switch (stat) {
            case POWER     -> data.power();
            case PRECISION -> data.precision();
            case WILLPOWER -> data.willpower();
            case REFLEXES  -> data.reflexes();
            case KNOWLEDGE -> 0;
        };
    }

    /** Returns the current Power ceiling: initial rolled Power + accumulated growth, capped at the Heritage band max. */
    public static int getPowerCeiling(@NonNull Player player) {
        return getData(player).power();
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
        PlayerStatsData old = getData(player);
        PlayerStatsData updated = switch (stat) {
            case POWER     -> old.withPower(value);
            case PRECISION -> old.withPrecision(value);
            case WILLPOWER -> old.withWillpower(value);
            case REFLEXES  -> old.withReflexes(value);
            case KNOWLEDGE -> old;
        };
        LOGGER.info("[WizardsAndBeasts] setStat: {} {} = {}", player.getName().getString(), stat.getId(), value);
        setAndSync(player, updated);
    }

    /**
     * Grants Power growth from a milestone or admin command, respecting the Heritage growth cap.
     * No-op for Squib players.
     */
    public static void grantPowerGrowth(@NonNull Player player, int amount) {
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
        int newPower = Math.min(old.power() + actual, bandMax);
        int newAccumulated = accumulated + actual;
        LOGGER.debug("[WizardsAndBeasts] grantPowerGrowth: {} +{} Power (now {}, accumulated {})",
                player.getName().getString(), actual, newPower, newAccumulated);
        setAndSync(player, old.withPower(newPower).withPowerGrowthAccumulated(newAccumulated));
    }

    /**
     * Increments training progress for a trainable stat by the given raw amount (after S-curve scaling).
     * When the accumulator reaches 1.0+, the stat integer is incremented and the accumulator decremented.
     * Rejects calls for non-trainable stats.
     */
    public static void addTrainingProgress(@NonNull Player player, @NonNull PlayerStat stat, float rawAmount) {
        requireServer(player);
        if (!stat.isTrainable()) {
            LOGGER.warn("[WizardsAndBeasts] addTrainingProgress: stat {} is not trainable", stat.getId());
            return;
        }
        PlayerStatsData old = getData(player);
        int currentStatValue = getStat(player, stat);
        if (currentStatValue >= 100) return;

        float scaled = StatTrainingScaler.scale(rawAmount, currentStatValue);

        Map<PlayerStat, Float> newTraining = new HashMap<>(old.trainingProgress());
        float current = newTraining.getOrDefault(stat, 0f);
        float accumulated = current + scaled;

        PlayerStatsData updated = old;
        while (accumulated >= 1.0f) {
            accumulated -= 1.0f;
            // Increment the stat integer
            updated = incrementStatInt(updated, stat);
            if (getStat(player, stat) >= 100) break;
        }
        newTraining.put(stat, Math.max(0f, accumulated));
        updated = updated.withTrainingProgress(newTraining);
        setAndSync(player, updated);
    }

    /**
     * Direct stat bump bypassing training progress, clamped to 0–100.
     * For POWER, routes through grantPowerGrowth.
     */
    public static void grantMilestoneBump(@NonNull Player player, @NonNull PlayerStat stat, int amount) {
        requireServer(player);
        if (stat == PlayerStat.POWER) {
            grantPowerGrowth(player, amount);
            return;
        }
        if (stat.isDerived()) {
            LOGGER.warn("[WizardsAndBeasts] grantMilestoneBump: cannot bump derived stat {}", stat.getId());
            return;
        }
        LOGGER.info("[WizardsAndBeasts] grantMilestoneBump: {} {} +{}", player.getName().getString(), stat.getId(), amount);
        setStat(player, stat, getStat(player, stat) + amount);
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

    private static PlayerStatsData incrementStatInt(PlayerStatsData data, PlayerStat stat) {
        return switch (stat) {
            case PRECISION -> data.withPrecision(data.precision() + 1);
            case WILLPOWER -> data.withWillpower(data.willpower() + 1);
            case REFLEXES  -> data.withReflexes(data.reflexes() + 1);
            default        -> data;
        };
    }

    private static void requireServer(Player player) {
        if (player.level().isClientSide()) {
            throw new IllegalStateException("PlayerStatsAPI write methods must be called server-side");
        }
    }
}
