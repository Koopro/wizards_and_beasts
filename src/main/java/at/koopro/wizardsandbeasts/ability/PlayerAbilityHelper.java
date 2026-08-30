package at.koopro.wizardsandbeasts.ability;

import at.koopro.wizardsandbeasts.ability.data.PlayerAbilityData;
import at.koopro.wizardsandbeasts.network.ability.AbilityDataSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class PlayerAbilityHelper {
    private PlayerAbilityHelper() {
    }

    public static @NonNull PlayerAbilityData get(@NonNull Player player) {
        return player.getData(ModAttachments.PLAYER_ABILITY_DATA.get());
    }

    public static void reset(@NonNull Player player) {
        set(player, prev -> PlayerAbilityData.DEFAULT);
    }

    public static boolean isApparitionUnlocked(@NonNull Player player) { return get(player).apparitionUnlocked(); }
    public static void setApparitionUnlocked(@NonNull Player player, boolean value) { set(player, p -> p.withApparitionUnlocked(value)); }

    public static boolean isApparitionLicensed(@NonNull Player player) { return get(player).apparitionLicensed(); }
    public static void setApparitionLicensed(@NonNull Player player, boolean value) { set(player, p -> p.withApparitionLicensed(value)); }

    public static int getApparitionCooldownTicks(@NonNull Player player) { return get(player).apparitionCooldownTicks(); }
    public static void setApparitionCooldownTicks(@NonNull Player player, int value) { set(player, p -> p.withApparitionCooldownTicks(Math.max(0, value))); }

    public static float getOcclumencyLevel(@NonNull Player player) { return get(player).occlumencyLevel(); }
    public static void setOcclumencyLevel(@NonNull Player player, float value) { set(player, p -> p.withOcclumencyLevel(clamp01(value))); }

    public static int getLegilimencyCooldownTicks(@NonNull Player player) { return get(player).legilimencyCooldownTicks(); }
    public static void setLegilimencyCooldownTicks(@NonNull Player player, int value) { set(player, p -> p.withLegilimencyCooldownTicks(Math.max(0, value))); }

    public static boolean isAnimagusUnlocked(@NonNull Player player) { return get(player).animagusUnlocked(); }
    public static void setAnimagusUnlocked(@NonNull Player player, boolean value) { set(player, p -> p.withAnimagusUnlocked(value)); }

    public static @Nullable String getAnimagusFormId(@NonNull Player player) { return get(player).animagusFormId(); }
    public static void setAnimagusFormId(@NonNull Player player, @Nullable String value) { set(player, p -> p.withAnimagusFormId(blankToNull(value))); }

    public static boolean isAnimagusRegistered(@NonNull Player player) { return get(player).animagusRegistered(); }
    public static void setAnimagusRegistered(@NonNull Player player, boolean value) { set(player, p -> p.withAnimagusRegistered(value)); }

    public static boolean isCurrentlyTransformed(@NonNull Player player) { return get(player).currentlyTransformed(); }
    public static void setCurrentlyTransformed(@NonNull Player player, boolean value) { set(player, p -> p.withCurrentlyTransformed(value)); }

    public static boolean isWolfsbaneActive(@NonNull Player player) { return get(player).wolfsbaneActive(); }

    public static @Nullable String getCurrentDisguiseFormId(@NonNull Player player) { return get(player).currentDisguiseFormId(); }

    public static boolean hasAbilityFlag(@NonNull Player player, @NonNull String flag) {
        return !flag.isBlank() && get(player).abilityFlags().contains(flag);
    }

    public static void addAbilityFlag(@NonNull Player player, @NonNull String flag) {
        if (flag.isBlank()) {
            return;
        }
        set(player, p -> {
            LinkedHashSet<String> flags = new LinkedHashSet<>(p.abilityFlags());
            flags.add(flag);
            return p.withAbilityFlags(Set.copyOf(flags));
        });
    }

    private static void set(@NonNull Player player, @NonNull UnaryOperator<PlayerAbilityData> mutator) {
        PlayerAbilityData previous = player.getData(ModAttachments.PLAYER_ABILITY_DATA.get());
        PlayerAbilityData next = mutator.apply(previous);
        if (next.equals(previous)) {
            return;
        }
        player.setData(ModAttachments.PLAYER_ABILITY_DATA.get(), next);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            AbilityDataSyncPayload.syncToPlayer(serverPlayer);
        }
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
