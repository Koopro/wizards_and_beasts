package at.koopro.wizardsandbeasts.apparition;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.Config;
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

    /** Blocks of over-range travel that add one full unit of extra splinch risk. */
    private static final double DISTANCE_RISK_BLOCKS = 900.0;
    /** Ceiling on the distance risk multiplier, so a very long jump stays survivable. */
    private static final double MAX_DISTANCE_RISK = 3.0;

    private ApparitionServerLogic() {
    }

    /**
     * Whether the player is <i>permitted</i> to Apparate at all — the same licence/test/heritage rule
     * {@link #handleRequest} enforces, minus the transient checks (splinch, cooldown, wards) and minus the
     * per-reason feedback. Read-only; used by the ability grant layer to decide wheel visibility.
     * {@code handleRequest} remains the authority and re-runs every check itself.
     */
    public static boolean canApparate(ServerPlayer player) {
        if (SkillSystemAPI.hasAbility(player, "elf_apparition")) {
            return true;
        }
        return PlayerAbilityHelper.isApparitionUnlocked(player)
                && PlayerAbilityHelper.isApparitionLicensed(player)
                && isAllowedHeritage(player);
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
        Vec3 clamped = clampTarget(caster.position(), targetPosition, Config.apparitionRangeBlocks);
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
        PlayerAbilityHelper.setApparitionCooldownTicks(caster, Config.apparitionCooldownTicks);
    }

    /**
     * Apparates to a memorised {@link ApparitionPoint}. Runs the same gauntlet as the aimed request —
     * module, licence/test/heritage, splinch, cooldown, destination wards — and differs only where a known
     * destination genuinely differs from a line-of-sight hop:
     *
     * <ul>
     *   <li>no range clamp, because the whole point of a remembered destination is that it is far away;</li>
     *   <li>same dimension only — Apparition does not cross worlds;</li>
     *   <li>splinch risk grows with distance, so cross-continent jumps stay respectably dangerous;</li>
     *   <li>the saved facing is restored, so you do not arrive spun around.</li>
     * </ul>
     */
    public static void travelTo(ServerPlayer caster, ApparitionPoint point) {
        if (!ModuleManager.isEnabled(Module.PLAYER_ABILITIES)) {
            return;
        }
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
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(point.dimension())) {
            fail(caster, "You cannot Apparate to another world.");
            return;
        }

        Vec3 destination = point.position();
        AABB atDestination = caster.getBoundingBox().move(
                destination.x - caster.getX(), destination.y - caster.getY(), destination.z - caster.getZ());
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
        performApparition(caster, destination, false, distanceRiskMultiplier(origin.distanceTo(destination)));
        Player passenger = findSideAlongPassenger(caster);
        if (passenger instanceof ServerPlayer) {
            performApparition((ServerPlayer) passenger, destination, true,
                    distanceRiskMultiplier(origin.distanceTo(destination)));
        }
        caster.setYRot(point.yaw());
        level.playSound(null, net.minecraft.core.BlockPos.containing(origin), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.9f);
        level.playSound(null, net.minecraft.core.BlockPos.containing(destination), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
        spawnBurst(level, origin);
        spawnBurst(level, destination);
        PlayerAbilityHelper.setApparitionCooldownTicks(caster, Config.apparitionCooldownTicks);
    }

    /**
     * Extra splinch risk for a long jump: 1× at the aimed-hop range, rising to a hard ceiling so a very
     * long trip is dangerous without being a coin flip.
     */
    private static float distanceRiskMultiplier(double distance) {
        double beyond = Math.max(0.0, distance - Config.apparitionRangeBlocks);
        return (float) Math.min(MAX_DISTANCE_RISK, 1.0 + beyond / DISTANCE_RISK_BLOCKS);
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
        performApparition(player, targetPosition, sideAlong, 1.0f);
    }

    private static void performApparition(ServerPlayer player, Vec3 targetPosition, boolean sideAlong,
                                          float riskMultiplier) {
        float focusLevel = computeFocusLevel(player);
        float baseChance = Config.apparitionSplinchBaseChance;
        float splinchChance = Math.max(0.0f, baseChance - (focusLevel * baseChance)) * riskMultiplier;
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

    private static @Nullable Player findSideAlongPassenger(ServerPlayer caster) {
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
