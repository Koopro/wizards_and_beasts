package at.koopro.wizardsandbeasts.command.debug;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DebugHooks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Long> LAST_BROOM_LOG_TICK = new ConcurrentHashMap<>();
    private static final long BROOM_LOG_INTERVAL_TICKS = 20L;

    private DebugHooks() {
    }

    public static void logSpellCast(ServerPlayer player, String event, String detail) {
        if (!DebugModeService.isEnabled(player)) return;
        LOGGER.info("[W&B Debug][Spell] player='{}' event='{}' detail='{}'",
                player.getName().getString(), event, detail);
    }

    public static void logVault(ServerPlayer player, String action, int amount, long knuts, long sickles, long galleons) {
        if (!DebugModeService.isEnabled(player)) return;
        LOGGER.info("[W&B Debug][Vault] player='{}' action='{}' amount={} balances={}k/{}s/{}g",
                player.getName().getString(), action, amount, knuts, sickles, galleons);
    }

    public static void logBroomInput(ServerPlayer player, boolean forward, boolean backward, boolean up, boolean down, boolean boosting) {
        if (!DebugModeService.isEnabled(player)) return;
        long tick = player.level().getGameTime();
        Long lastTick = LAST_BROOM_LOG_TICK.get(player.getUUID());
        if (lastTick != null && tick - lastTick < BROOM_LOG_INTERVAL_TICKS) return;

        LAST_BROOM_LOG_TICK.put(player.getUUID(), tick);
        LOGGER.info("[W&B Debug][Broom] player='{}' fwd={} back={} up={} down={} boost={}",
                player.getName().getString(), forward, backward, up, down, boosting);
    }
}
