package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.apparition.ApparitionWard;
import at.koopro.wizardsandbeasts.apparition.ApparitionWardRegistry;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.cast.SpellCastTelemetry;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ApparitionServerLogic {
    private ApparitionServerLogic() {
    }

    public static void handleRequest(ServerPlayer caster, net.minecraft.core.BlockPos targetBlockPos, Vec3 targetPosition) {
        if (!ModuleManager.isEnabled(Module.PLAYER_ABILITIES)) {
            return;
        }
        // Elf-Apparition: bound elf-magic Apparates without a test, licence, or wizard-heritage
        // restriction, and slips past wards that bind wizards. Splinch and cooldown still apply.
        boolean elfApparition = SkillSystemAPI.hasAbility(caster, "elf_apparition");
        if (!elfApparition) {
            if (!PlayerAbilityHelper.isApparitionUnlocked(caster)) {
                fail(caster, "You have not passed your Apparition test.");
                return;
            }
            if (!PlayerAbilityHelper.isApparitionLicensed(caster)) {
                fail(caster, "You are not licensed to Apparate.");
                return;
            }
            if (!isAllowedHeritage(caster)) {
                fail(caster, "Your heritage cannot Apparate.");
                return;
            }
        }
        if (PlayerAbilityHelper.getSplinchSeverity(caster) > 0) {
            fail(caster, "You are too splinched to Apparate.");
            return;
        }
        int cooldown = PlayerAbilityHelper.getApparitionCooldownTicks(caster);
        if (cooldown > 0) {
            fail(caster, "Apparition is on cooldown (" + Math.max(1, cooldown / 20) + "s).");
            return;
        }
        if (!(caster.level() instanceof ServerLevel)) {
            return;
        }
        ServerLevel level = (ServerLevel) caster.level();
        Vec3 clamped = clampTarget(caster.position(), targetPosition, 32.0);
        AABB atDestination = caster.getBoundingBox().move(clamped.x - caster.getX(), clamped.y - caster.getY(), clamped.z - caster.getZ());
        if (!elfApparition) {
            ApparitionWard ward = ApparitionWardRegistry.findBlockingWard(level, caster.createCommandSourceStack(), atDestination);
            if (ward != null) {
                fail(caster, ward.blockMessage().getString().isBlank()
                        ? "Something prevents you from Apparating here."
                        : ward.blockMessage().getString());
                return;
            }
        }

        Vec3 origin = caster.position();
        performApparition(caster, clamped, false);
        Player passenger = findSideAlongPassenger(caster);
        if (passenger instanceof ServerPlayer) {
            performApparition((ServerPlayer) passenger, clamped, true);
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.9f);
        level.playSound(null, net.minecraft.core.BlockPos.containing(clamped), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
        spawnBurst(level, origin);
        spawnBurst(level, clamped);
        PlayerAbilityHelper.setApparitionCooldownTicks(caster, 60);
    }

    public static void tick(ServerPlayer player) {
        int cooldown = PlayerAbilityHelper.getApparitionCooldownTicks(player);
        if (cooldown > 0) {
            PlayerAbilityHelper.setApparitionCooldownTicks(player, cooldown - 1);
        }
        int splinchRemaining = PlayerAbilityHelper.getSplinchTicksRemaining(player);
        if (splinchRemaining > 0) {
            PlayerAbilityHelper.setSplinchTicksRemaining(player, splinchRemaining - 1);
            if (splinchRemaining - 1 == 0) {
                PlayerAbilityHelper.setSplinchSeverity(player, 0);
            }
        }
    }

    private static void performApparition(ServerPlayer player, Vec3 targetPosition, boolean sideAlong) {
        float focusLevel = computeFocusLevel(player);
        float splinchChance = Math.max(0.0f, 0.35f - (focusLevel * 0.35f));
        if (sideAlong) {
            splinchChance *= 1.4f;
        }
        boolean splinched = player.getRandom().nextFloat() < splinchChance;
        int severity = 0;
        if (splinched) {
            severity = player.getRandom().nextFloat() < 0.25f ? 2 : 1;
        }
        player.teleportTo(targetPosition.x, targetPosition.y, targetPosition.z);
        if (!splinched) {
            return;
        }
        PlayerAbilityHelper.setSplinchSeverity(player, severity);
        PlayerAbilityHelper.setSplinchTicksRemaining(player, severity == 2 ? 1200 : 400);
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, severity == 2 ? 1200 : 400, 0, false, true, true));
        if (severity == 2) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1200, 0, false, true, true));
            DamageSource source = player.damageSources().magic();
            player.hurt(source, 3.0f);
            player.displayClientMessage(Component.literal("You splinched badly. Seek a Healer.").withStyle(ChatFormatting.RED), false);
        } else {
            player.displayClientMessage(Component.literal("You splinched yourself — part of you was left behind.").withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static float computeFocusLevel(ServerPlayer player) {
        float occlumency = PlayerAbilityHelper.getOcclumencyLevel(player);
        float recentFailureRate = SpellCastTelemetry.recentFailureRate(player);
        return Math.max(0.0f, Math.min(1.0f, (occlumency * 0.4f) + ((1.0f - recentFailureRate) * 0.3f) + 0.3f));
    }

    private static boolean isAllowedHeritage(ServerPlayer player) {
        Heritage heritage = HeritageAPI.getPlayerHeritage(player);
        HeritageVariant variant = HeritageAPI.getPlayerHeritageVariant(player);
        if (heritage == Heritage.WIZARDKIND) {
            return true;
        }
        return variant != null && variant.hasTag("can_apparate");
    }

    private static Player findSideAlongPassenger(ServerPlayer caster) {
        AABB area = caster.getBoundingBox().inflate(1.5);
        for (Player candidate : caster.level().getEntitiesOfClass(Player.class, area, p -> p != caster && p.isShiftKeyDown())) {
            return candidate;
        }
        return null;
    }

    private static Vec3 clampTarget(Vec3 origin, Vec3 target, double maxDistance) {
        Vec3 delta = target.subtract(origin);
        double len = delta.length();
        if (len <= maxDistance) {
            return target;
        }
        return origin.add(delta.normalize().scale(maxDistance));
    }

    private static void fail(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
    }

    private static void spawnBurst(ServerLevel level, Vec3 pos) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, pos.x, pos.y + 1.0, pos.z, 40, 0.3, 0.5, 0.3, 0.1);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, pos.x, pos.y + 1.0, pos.z, 15, 0.25, 0.25, 0.25, 0.01);
    }
}
