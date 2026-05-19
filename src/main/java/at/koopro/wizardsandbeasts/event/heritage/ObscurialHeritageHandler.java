package at.koopro.wizardsandbeasts.event.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.form.TransitionManager;
import at.koopro.wizardsandbeasts.event.form.FormEvents;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialCombatRules;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialResourceManager;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialValueCodec;
import at.koopro.wizardsandbeasts.heritage.TransformationState;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Maintains Obscurial state and condition-based effects.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ObscurialHeritageHandler {

    private static final int STAT_REFRESH_INTERVAL_TICKS = 40;
    private static final String FLAG_FLIGHT_GRANTED = "obscurial_flight_granted";
    private static final String FLAG_AUTO_TRANSFORM_READY_TICK = "obscurial_auto_transform_ready_tick";
    private static final String FLAG_LAST_DRAIN_WARN_TICK = "obscurial_last_drain_warn_tick";
    private static final String FLAG_LAST_RESOURCE_SYNC_TICK = "obscurial_last_resource_sync_tick";
    private static final String FLAG_FORCED_DARK_UNTIL_TICK = "obscurial_forced_dark_until_tick";
    private static final String FLAG_LAST_HOSTILE_COUNT = "obscurial_last_hostile_count";
    private static final String FLAG_LAST_VOLATILE_WARN_TICK = "obscurial_last_volatile_warn_tick";
    private static final String FLAG_COLLAPSE_CAST_INSTABILITY_UNTIL = "obscurial_collapse_cast_instability_until_tick";
    private static final String FLAG_RAGE_ACTIVE = "obscurial_rage_active";
    private static final float AUTO_TRANSFORM_HP_THRESHOLD = 0.40f;
    private static final long AUTO_TRANSFORM_COOLDOWN_TICKS = 20L * 55L;
    private static final float EMOTIONAL_TRIGGER_HP_THRESHOLD = 0.28f;
    private static final float EMOTIONAL_TRIGGER_MIN_STRESS = 80.0f;
    private static final long RESOURCE_SYNC_INTERVAL_TICKS = 5L;
    private static final long HOSTILE_SCAN_INTERVAL_TICKS = 20L;
    private static final double HOSTILE_SCAN_RADIUS = 8.0;

    private ObscurialHeritageHandler() {}

    @SubscribeEvent
    public static void onTransitionStart(FormEvents.PlayerFormTransitionStartEvent event) {
        ServerPlayer player = event.getPlayer();
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!ObscurialRules.isObscurial(data)) {
            return;
        }

        if (event.getFromFormId().startsWith("obscurial_") || event.getToFormId().startsWith("obscurial_")) {
            data.setTransformationState(TransformationState.TRANSITIONING);
            HeritageDataSyncS2CPayload.syncToPlayer(player, false);
        }
    }

    @SubscribeEvent
    public static void onTransitionEnd(FormEvents.PlayerFormTransitionEndEvent event) {
        ServerPlayer player = event.getPlayer();
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!ObscurialRules.isObscurial(data)) {
            return;
        }

        syncStateFromActiveForm(player, data, true);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
            if (!ObscurialRules.isObscurial(data)) {
                continue;
            }
            if (!(player.level() instanceof ServerLevel playerLevel)) {
                continue;
            }

            syncStateFromActiveForm(player, data, false);
            applyConditionalEffects(playerLevel, player);
            updateResourcesAndLockout(player, data, playerLevel);
            enforceForcedDarkFormDuration(player, data, playerLevel);
            updateDarkFormFlight(player, data);
            maybeTriggerLowHpTransform(player, playerLevel, data);
            applyDaylightVulnerability(player, playerLevel, data);

            long gameTime = playerLevel.getGameTime();
            if (gameTime % STAT_REFRESH_INTERVAL_TICKS == 0) {
                HeritageAPI.applyStats(player);
            }
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!ObscurialRules.isObscurial(data)) {
            return;
        }

        ObscurialCombatRules.applyDamageStressSpike(player);
        if (!ObscurialRules.isDarkForm(data)) return;

        DamageSource source = event.getSource();
        if (ObscurialDamageRules.isAllowedInDarkForm(player, source)) {
            return;
        }

        ObscurialCombatRules.applyDarkFormStressSpike(player);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }
        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hitResult)) {
            return;
        }
        if (!(hitResult.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!ObscurialRules.isObscurial(data) || !ObscurialRules.isDarkForm(data)) {
            return;
        }

        // Let physical projectiles pass through dark smoke form.
        if (!(projectile instanceof at.koopro.wizardsandbeasts.entity.spell.SpellProjectileEntity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isDarkObscurial(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isDarkObscurial(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isDarkObscurial(player)) {
            event.setCanceled(true);
        }
    }

    private static void syncStateFromActiveForm(ServerPlayer player, PlayerHeritageData data, boolean forceSync) {
        if (data.getTransformationState() == TransformationState.TRANSITIONING && !forceSync) {
            return;
        }

        TransformationState derived = ObscurialRules.deriveState(data);
        if (data.getTransformationState() != derived || forceSync) {
            data.setTransformationState(derived);
            HeritageAPI.applyStats(player);
            HeritageDataSyncS2CPayload.syncToPlayer(player, false);
        }
    }

    private static void applyConditionalEffects(ServerLevel level, ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        boolean strained = ObscurialRules.isDaylightStrained(level, player);
        boolean empowered = ObscurialRules.isNightEmpowered(level);
        boolean darkForm = ObscurialRules.isDarkForm(data);

        if (strained) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, darkForm ? 0 : 1, true, false, true));
        } else if (empowered) {
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, darkForm ? 1 : 0, true, false, true));
        }
    }

    private static void updateDarkFormFlight(ServerPlayer player, PlayerHeritageData data) {
        boolean darkForm = ObscurialRules.isDarkForm(data);
        boolean canKeepNativeFlight = player.isCreative() || player.isSpectator();
        boolean grantedByObscurial = "true".equals(data.getFlag(FLAG_FLIGHT_GRANTED));

        if (darkForm) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
            }
            if (!player.getAbilities().flying) {
                player.getAbilities().flying = true;
            }
            player.getAbilities().setFlyingSpeed(0.05f);
            if (!grantedByObscurial && !canKeepNativeFlight) {
                data.setFlag(FLAG_FLIGHT_GRANTED, "true");
            }
            player.onUpdateAbilities();
            return;
        }

        if (grantedByObscurial && !canKeepNativeFlight) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.getAbilities().setFlyingSpeed(0.05f);
            data.removeFlag(FLAG_FLIGHT_GRANTED);
            player.onUpdateAbilities();
        }
    }

    private static void maybeTriggerLowHpTransform(ServerPlayer player, ServerLevel level, PlayerHeritageData data) {
        if (ObscurialRules.isDarkForm(data)) return;
        if (TransitionManager.isTransitioning(player.getUUID())) return;
        if (ObscurialResourceManager.isTransformLockedOut(player, level.getGameTime())) return;

        float hpRatio = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
        float control = ObscurialResourceManager.getDrain(player);

        long now = level.getGameTime();
        long readyTick = ObscurialValueCodec.parseLong(data.getFlag(FLAG_AUTO_TRANSFORM_READY_TICK), 0L);
        if (now < readyTick) return;

        boolean lowHpTrigger = hpRatio <= AUTO_TRANSFORM_HP_THRESHOLD;
        boolean volatileStressTrigger = ObscurialRules.getStressTier(ObscurialResourceManager.getStress(player))
                == ObscurialRules.StressTier.VOLATILE
                && ObscurialRules.getLoreControlTier(control)
                == ObscurialRules.LoreControlTier.CATASTROPHIC
                && hpRatio <= EMOTIONAL_TRIGGER_HP_THRESHOLD
                && ObscurialResourceManager.getStress(player) >= EMOTIONAL_TRIGGER_MIN_STRESS;
        if (!lowHpTrigger && !volatileStressTrigger) return;

        if (TransitionManager.startTransition(player, "obscurial_dark")) {
            data.setFlag(FLAG_AUTO_TRANSFORM_READY_TICK, String.valueOf(now + AUTO_TRANSFORM_COOLDOWN_TICKS));
            data.setFlag(FLAG_FORCED_DARK_UNTIL_TICK, String.valueOf(now + ObscurialCombatRules.getForcedDarkFormDurationTicks()));
            player.displayClientMessage(Component.literal("\u00A75Your obscurus breaks loose under emotional strain."), true);
        }
    }

    private static boolean isDarkObscurial(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        return ObscurialRules.isObscurial(data) && ObscurialRules.isDarkForm(data);
    }

    private static void updateResourcesAndLockout(ServerPlayer player, PlayerHeritageData data, ServerLevel level) {
        long gameTime = level.getGameTime();
        float stability = ObscurialResourceManager.getDrain(player);
        float charge = ObscurialResourceManager.getCharge(player);
        if (ObscurialRules.isDarkForm(data)) {
            stability -= ObscurialRules.computeDarkDrainPerTick(player, level);
            charge -= ObscurialRules.computeDarkChargeCostPerTick(player, level);
        } else {
            stability += ObscurialRules.computeHumanRegenPerTick(player, level);
            charge += ObscurialRules.computeHumanChargeRegenPerTick(player, level);
        }
        ObscurialResourceManager.setDrain(player, stability);
        ObscurialResourceManager.setCharge(player, charge);

        float updatedStability = ObscurialResourceManager.getDrain(player);
        float updatedCharge = ObscurialResourceManager.getCharge(player);
        int nearbyHostiles = ObscurialValueCodec.parseInt(data.getFlag(FLAG_LAST_HOSTILE_COUNT), 0);
        if (gameTime % HOSTILE_SCAN_INTERVAL_TICKS == 0L) {
            nearbyHostiles = countNearbyHostiles(player, level);
            data.setFlag(FLAG_LAST_HOSTILE_COUNT, String.valueOf(nearbyHostiles));
        }

        if (ObscurialRules.isDarkForm(data)) {
            if (ObscurialCombatRules.isRageThresholdActive(player)) {
                ObscurialCombatRules.applyRageThresholdEffects(player, level);
                data.setFlag(FLAG_RAGE_ACTIVE, "true");
            } else {
                data.setFlag(FLAG_RAGE_ACTIVE, "false");
            }
            if (nearbyHostiles > 0) {
                ObscurialResourceManager.addStress(player, Math.min(1.3f, nearbyHostiles * 0.10f));
            }
            if (ObscurialRules.isDaylightStrained(level, player)) {
                ObscurialResourceManager.addStress(player, 0.10f);
            }
            if (ObscurialRules.getStressTier(ObscurialResourceManager.getStress(player)) == ObscurialRules.StressTier.VOLATILE) {
                long lastVolatileWarn = ObscurialValueCodec.parseLong(data.getFlag(FLAG_LAST_VOLATILE_WARN_TICK), 0L);
                if (gameTime - lastVolatileWarn >= 40L) {
                    data.setFlag(FLAG_LAST_VOLATILE_WARN_TICK, String.valueOf(gameTime));
                    player.displayClientMessage(Component.literal("\u00A74Volatile obscurus strain is accelerating collapse."), true);
                }
            }
        } else {
            data.setFlag(FLAG_RAGE_ACTIVE, "false");
            float stressGain = ObscurialRules.computeStressGainPerTick(player, level, nearbyHostiles);
            float stressRecovery = ObscurialRules.computeStressRecoveryPerTick(player, level, nearbyHostiles > 0);
            ObscurialResourceManager.setStress(player, ObscurialResourceManager.getStress(player) + stressGain - stressRecovery);
        }

        if (updatedStability <= ObscurialResourceManager.getLowDrainWarning()
                && ObscurialRules.getStabilityTier(updatedStability) == ObscurialRules.StabilityTier.CRITICAL) {
            long lastWarn = ObscurialValueCodec.parseLong(data.getFlag(FLAG_LAST_DRAIN_WARN_TICK), 0L);
            if (gameTime - lastWarn >= 40L) {
                data.setFlag(FLAG_LAST_DRAIN_WARN_TICK, String.valueOf(gameTime));
                player.displayClientMessage(Component.literal("\u00A75Obscurus instability critical: collapse is imminent."), true);
            }
        }

        if (ObscurialRules.isDarkForm(data)
                && (updatedStability <= 0f || updatedCharge <= 0f)
                && !TransitionManager.isTransitioning(player.getUUID())) {
            if (TransitionManager.startTransition(player, "obscurial_human")) {
                long lockoutUntil = gameTime + ObscurialResourceManager.getDefaultLockoutTicks();
                ObscurialResourceManager.setLockoutUntilTick(player, lockoutUntil);
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                        ObscurialCombatRules.getCollapseWeaknessTicks(),
                        ObscurialCombatRules.getCollapseWeaknessAmplifier(),
                        false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                        ObscurialCombatRules.getCollapseSlownessTicks(),
                        ObscurialCombatRules.getCollapseSlownessAmplifier(),
                        false, true, true));
                data.setFlag(FLAG_COLLAPSE_CAST_INSTABILITY_UNTIL,
                        String.valueOf(gameTime + ObscurialCombatRules.getCollapseCastInstabilityTicks()));
                player.displayClientMessage(Component.literal("\u00A74Obscurus collapse. Dark form is now exhausted."), false);
            }
        }

        // Keep client HUD values responsive without syncing every tick.
        long lastSync = ObscurialValueCodec.parseLong(data.getFlag(FLAG_LAST_RESOURCE_SYNC_TICK), 0L);
        if (gameTime - lastSync >= RESOURCE_SYNC_INTERVAL_TICKS) {
            data.setFlag(FLAG_LAST_RESOURCE_SYNC_TICK, String.valueOf(gameTime));
            HeritageDataSyncS2CPayload.syncToPlayer(player, false);
        }
    }

    private static void applyDaylightVulnerability(ServerPlayer player, ServerLevel level, PlayerHeritageData data) {
        if (!ObscurialRules.shouldApplyDaylightVulnerability(player, level, level.getGameTime())) {
            return;
        }
        player.hurt(level.damageSources().magic(), ObscurialRules.getDaylightVulnerabilityDamage());
        player.displayClientMessage(Component.literal("\u00A7eDaylight scorches your obscurus and drives it unstable."), true);
    }

    private static void enforceForcedDarkFormDuration(ServerPlayer player, PlayerHeritageData data, ServerLevel level) {
        if (!ObscurialRules.isDarkForm(data)) return;
        long forcedUntil = ObscurialValueCodec.parseLong(data.getFlag(FLAG_FORCED_DARK_UNTIL_TICK), 0L);
        if (forcedUntil <= 0L) return;
        if (level.getGameTime() < forcedUntil) return;
        if (TransitionManager.isTransitioning(player.getUUID())) return;
        if (TransitionManager.startTransition(player, "obscurial_human")) {
            data.removeFlag(FLAG_FORCED_DARK_UNTIL_TICK);
            player.displayClientMessage(Component.literal("\u00A75You force the obscurus back under control."), true);
        }
    }

    private static int countNearbyHostiles(ServerPlayer player, ServerLevel level) {
        AABB box = player.getBoundingBox().inflate(HOSTILE_SCAN_RADIUS);
        return level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && mob.getTarget() == player).size();
    }
}
