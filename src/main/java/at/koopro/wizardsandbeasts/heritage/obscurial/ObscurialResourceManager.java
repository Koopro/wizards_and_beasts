package at.koopro.wizardsandbeasts.heritage.obscurial;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pure state accessors for Obscurial player resources: drain, charge, stress, lockout, and vent cooldown.
 * All values are clamped to [0, MAX_DRAIN]. No gameplay logic lives here.
 */
public final class ObscurialResourceManager {

    private static final String FLAG_DRAIN = "obscurial_drain";
    private static final String FLAG_CHARGE = "obscurial_charge";
    private static final String FLAG_STRESS = "obscurial_stress";
    private static final String FLAG_LOCKOUT_UNTIL = "obscurial_lockout_until_tick";
    private static final String FLAG_VENT_COOLDOWN_UNTIL = "obscurial_vent_cooldown_until_tick";

    static final float MAX_DRAIN = 100f;
    private static final float LOW_DRAIN_WARNING = 20f;
    private static final float STRESS_VENT_RECOVERY = 24.0f;
    private static final long STRESS_VENT_COOLDOWN_TICKS = 20L * 16L;
    private static final int STRESS_VENT_DRAWBACK_TICKS = 20 * 4;
    private static final long LOCKOUT_TICKS = 20L * 18L;

    private ObscurialResourceManager() {}

    public static float getDrain(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        return clamp(ObscurialValueCodec.parseFloat(data.getFlag(FLAG_DRAIN), MAX_DRAIN));
    }

    public static void setDrain(ServerPlayer player, float value) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.setFlag(FLAG_DRAIN, String.valueOf(clamp(value)));
    }

    public static float getMaxDrain() {
        return MAX_DRAIN;
    }

    public static float getLowDrainWarning() {
        return LOW_DRAIN_WARNING;
    }

    public static float getCharge(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        return clamp(ObscurialValueCodec.parseFloat(data.getFlag(FLAG_CHARGE), MAX_DRAIN));
    }

    public static void setCharge(ServerPlayer player, float value) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.setFlag(FLAG_CHARGE, String.valueOf(clamp(value)));
    }

    public static float getStress(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        return clamp(ObscurialValueCodec.parseFloat(data.getFlag(FLAG_STRESS), 0f));
    }

    public static void setStress(ServerPlayer player, float value) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.setFlag(FLAG_STRESS, String.valueOf(clamp(value)));
    }

    public static void addStress(ServerPlayer player, float amount) {
        setStress(player, getStress(player) + Math.max(0f, amount));
    }

    public static void reduceStress(ServerPlayer player, float amount) {
        setStress(player, getStress(player) - Math.max(0f, amount));
    }

    public static void setLockoutUntilTick(ServerPlayer player, long gameTick) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.setFlag(FLAG_LOCKOUT_UNTIL, String.valueOf(gameTick));
    }

    public static long getLockoutUntilTick(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        return ObscurialValueCodec.parseLong(data.getFlag(FLAG_LOCKOUT_UNTIL), 0L);
    }

    public static boolean isTransformLockedOut(ServerPlayer player, long gameTick) {
        return gameTick < getLockoutUntilTick(player);
    }

    public static long getDefaultLockoutTicks() {
        return LOCKOUT_TICKS;
    }

    public static long getStressVentCooldownUntil(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        return ObscurialValueCodec.parseLong(data.getFlag(FLAG_VENT_COOLDOWN_UNTIL), 0L);
    }

    public static void setStressVentCooldownUntil(ServerPlayer player, long gameTick) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.setFlag(FLAG_VENT_COOLDOWN_UNTIL, String.valueOf(gameTick));
    }

    public static float getStressVentRecovery() {
        return STRESS_VENT_RECOVERY;
    }

    public static long getStressVentCooldownTicks() {
        return STRESS_VENT_COOLDOWN_TICKS;
    }

    public static int getStressVentDrawbackTicks() {
        return STRESS_VENT_DRAWBACK_TICKS;
    }

    static float clamp(float value) {
        return ObscurialValueCodec.clampPercent(value, MAX_DRAIN);
    }
}
