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
    public static void setApparitionUnlocked(@NonNull Player player, boolean value) { set(player, p -> new PlayerAbilityData(value, p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static boolean isApparitionLicensed(@NonNull Player player) { return get(player).apparitionLicensed(); }
    public static void setApparitionLicensed(@NonNull Player player, boolean value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), value, p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static int getApparitionCooldownTicks(@NonNull Player player) { return get(player).apparitionCooldownTicks(); }
    public static void setApparitionCooldownTicks(@NonNull Player player, int value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), Math.max(0, value), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static int getSplinchSeverity(@NonNull Player player) { return get(player).splinchSeverity(); }
    public static void setSplinchSeverity(@NonNull Player player, int value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), Math.max(0, Math.min(2, value)), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static int getSplinchTicksRemaining(@NonNull Player player) { return get(player).splinchTicksRemaining(); }
    public static void setSplinchTicksRemaining(@NonNull Player player, int value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), Math.max(0, value), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static float getOcclumencyLevel(@NonNull Player player) { return get(player).occlumencyLevel(); }
    public static void setOcclumencyLevel(@NonNull Player player, float value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), clamp01(value), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static int getLegilimencyCooldownTicks(@NonNull Player player) { return get(player).legilimencyCooldownTicks(); }
    public static void setLegilimencyCooldownTicks(@NonNull Player player, int value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), Math.max(0, value), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static boolean isAnimagusUnlocked(@NonNull Player player) { return get(player).animagusUnlocked(); }
    public static void setAnimagusUnlocked(@NonNull Player player, boolean value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), value, p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static @Nullable String getAnimagusFormId(@NonNull Player player) { return get(player).animagusFormId(); }
    public static void setAnimagusFormId(@NonNull Player player, @Nullable String value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), blankToNull(value), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static boolean isAnimagusRegistered(@NonNull Player player) { return get(player).animagusRegistered(); }
    public static void setAnimagusRegistered(@NonNull Player player, boolean value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), value, p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static boolean isCurrentlyTransformed(@NonNull Player player) { return get(player).currentlyTransformed(); }
    public static void setCurrentlyTransformed(@NonNull Player player, boolean value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), value, p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static boolean isWolfsbaneActive(@NonNull Player player) { return get(player).wolfsbaneActive(); }
    public static void setWolfsbaneActive(@NonNull Player player, boolean value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), value, p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static boolean isParseltongueSpeaker(@NonNull Player player) { return get(player).parseltongueSpeaker(); }
    public static void setParseltongueSpeaker(@NonNull Player player, boolean value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), value, p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static @Nullable String getParseltongueSource(@NonNull Player player) { return get(player).parseltongueSource(); }
    public static void setParseltongueSource(@NonNull Player player, @Nullable String value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), blankToNull(value), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static boolean isMetamorphmagus(@NonNull Player player) { return get(player).metamorphmagus(); }
    public static void setMetamorphmagus(@NonNull Player player, boolean value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), value, p.currentDisguiseFormId(), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static @Nullable String getCurrentDisguiseFormId(@NonNull Player player) { return get(player).currentDisguiseFormId(); }
    public static void setCurrentDisguiseFormId(@NonNull Player player, @Nullable String value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), blankToNull(value), p.wandlessCastingLevel(), p.abilityFlags())); }

    public static float getWandlessCastingLevel(@NonNull Player player) { return get(player).wandlessCastingLevel(); }
    public static void setWandlessCastingLevel(@NonNull Player player, float value) { set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), clamp01(value), p.abilityFlags())); }

    public static @NonNull Set<String> getAbilityFlags(@NonNull Player player) {
        return Set.copyOf(get(player).abilityFlags());
    }

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
            return new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), Set.copyOf(flags));
        });
    }

    public static void removeAbilityFlag(@NonNull Player player, @NonNull String flag) {
        if (flag.isBlank()) {
            return;
        }
        set(player, p -> {
            LinkedHashSet<String> flags = new LinkedHashSet<>(p.abilityFlags());
            flags.remove(flag);
            return new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), Set.copyOf(flags));
        });
    }

    public static void setAbilityFlags(@NonNull Player player, @NonNull Set<String> flags) {
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String flag : flags) {
            if (flag != null && !flag.isBlank()) {
                clean.add(flag);
            }
        }
        set(player, p -> new PlayerAbilityData(p.apparitionUnlocked(), p.apparitionLicensed(), p.apparitionCooldownTicks(), p.splinchSeverity(), p.splinchTicksRemaining(), p.occlumencyLevel(), p.legilimencyCooldownTicks(), p.animagusUnlocked(), p.animagusFormId(), p.animagusRegistered(), p.currentlyTransformed(), p.wolfsbaneActive(), p.parseltongueSpeaker(), p.parseltongueSource(), p.metamorphmagus(), p.currentDisguiseFormId(), p.wandlessCastingLevel(), Set.copyOf(clean)));
    }

    public static void forceSync(@NonNull Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            AbilityDataSyncPayload.syncToPlayer(serverPlayer);
        }
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
