package at.koopro.wizardsandbeasts.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side passive and active abilities for Animagus beast forms.
 * <p>
 * Vanilla beast powers live on the beast entities' AI goals and so cannot be
 * borrowed onto a player; this service re-implements per-form behaviour keyed on
 * the form id. Passive effects are refreshed every tick while transformed and
 * stripped on revert ({@link #clearPassives}); active abilities are cooldown-gated
 * bursts triggered by the Animagus ability key.
 */
public final class AnimagusAbilityService {

    private AnimagusAbilityService() {}

    /** Refreshed passive effects (short duration, re-applied each tick) tracked per player for clean removal. */
    private static final Map<UUID, Set<Holder<MobEffect>>> APPLIED = new HashMap<>();
    /** Active-ability cooldown, in ticks, per player. */
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();

    private static final int PASSIVE_DURATION = 40; // ticks; refreshed every server tick

    /** Refreshes per-form passive effects and runs sensory passives. Call each server tick while transformed. */
    public static void tick(ServerPlayer player, String formId) {
        UUID id = player.getUUID();
        Integer cd = COOLDOWN.get(id);
        if (cd != null && cd > 0) {
            COOLDOWN.put(id, cd - 1);
        }

        Set<Holder<MobEffect>> applied = new HashSet<>();
        for (Holder<MobEffect> effect : passiveEffects(formId)) {
            int amplifier = effect == MobEffects.JUMP_BOOST && "animagus_hare".equals(formId) ? 1 : 0;
            player.addEffect(new MobEffectInstance(effect, PASSIVE_DURATION, amplifier, true, false, true));
            applied.add(effect);
        }
        APPLIED.put(id, applied);

        // Scare radius (8) far exceeds creeper walking distance per 10 ticks, so a
        // throttled scan is indistinguishable in-game and drops 90% of the AABB queries.
        if ("animagus_cat".equals(formId) && player.tickCount % 10 == 0) {
            scareCreepers(player);
        }
    }

    /** Removes everything this service applied to the player. Safe to call when nothing was applied. */
    public static void clearPassives(ServerPlayer player) {
        UUID id = player.getUUID();
        Set<Holder<MobEffect>> applied = APPLIED.remove(id);
        if (applied != null) {
            for (Holder<MobEffect> effect : applied) {
                player.removeEffect(effect);
            }
        }
        COOLDOWN.remove(id);
    }

    /** True if the cat-form fall-damage immunity should cancel a fall. */
    public static boolean negatesFallDamage(ServerPlayer player, String formId) {
        return "animagus_cat".equals(formId);
    }

    /**
     * True if the player is in a valid Animagus beast form — the standing requirement for the per-form
     * active ability, used by the ability grant layer for wheel visibility.
     */
    public static boolean canUseActive(ServerPlayer player) {
        return PlayerAbilityHelper.isCurrentlyTransformed(player)
                && AnimagusForms.isAnimagusForm(PlayerAbilityHelper.getAnimagusFormId(player));
    }

    /**
     * Triggers the current beast form's active ability, re-checking the transformed state itself. The
     * form-guard was lifted verbatim from the C2S payload handler when this moved onto the ability wheel.
     */
    public static void useActive(ServerPlayer player) {
        if (!canUseActive(player)) {
            return;
        }
        useActive(player, PlayerAbilityHelper.getAnimagusFormId(player));
    }

    /** Triggers the active ability for the player's current beast form. */
    public static void useActive(ServerPlayer player, String formId) {
        UUID id = player.getUUID();
        Integer cd = COOLDOWN.get(id);
        if (cd != null && cd > 0) {
            feedback(player, "Your beast is still catching its breath.", ChatFormatting.GRAY);
            return;
        }

        boolean fired = switch (formId) {
            case "animagus_cat" -> { leap(player, 0.9, 0.42); yield cool(id, 30, player, "Pounce!"); }
            case "animagus_dog" -> { barkFear(player); yield cool(id, 100, player, "A fearsome bark!"); }
            case "animagus_stag" -> { leap(player, 1.3, 0.30); yield cool(id, 80, player, "Charge!"); }
            case "animagus_hawk" -> { flap(player); yield cool(id, 25, player, "You beat your wings."); }
            case "animagus_hare" -> { leap(player, 0.7, 0.55); yield cool(id, 25, player, "A great bound!"); }
            case "animagus_beetle" -> { vanish(player); yield cool(id, 200, player, "You scuttle out of sight."); }
            default -> false;
        };

        if (!fired) {
            feedback(player, "This form has no special ability.", ChatFormatting.GRAY);
        }
    }

    // ── per-form passives ──────────────────────────────────────────────

    private static List<Holder<MobEffect>> passiveEffects(String formId) {
        List<Holder<MobEffect>> fx = new ArrayList<>();
        switch (formId) {
            case "animagus_cat" -> fx.add(MobEffects.NIGHT_VISION); // plus fall immunity + creeper scare
            case "animagus_dog" -> fx.add(MobEffects.SPEED);
            case "animagus_stag" -> { fx.add(MobEffects.SPEED); fx.add(MobEffects.JUMP_BOOST); }
            case "animagus_hawk" -> fx.add(MobEffects.SLOW_FALLING);
            case "animagus_hare" -> { fx.add(MobEffects.JUMP_BOOST); fx.add(MobEffects.SPEED); }
            case "animagus_beetle" -> { /* tiny and elusive — no passive effect */ }
            default -> { }
        }
        return fx;
    }

    /** Cat passive: nearby creepers lose their target and back away. */
    private static void scareCreepers(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(8.0);
        List<Creeper> creepers = player.level().getEntitiesOfClass(Creeper.class, area);
        for (Creeper creeper : creepers) {
            creeper.setTarget(null);
            Vec3 away = creeper.position().subtract(player.position()).normalize().scale(0.18);
            creeper.setDeltaMovement(creeper.getDeltaMovement().add(away.x, 0.0, away.z));
            creeper.hurtMarked = true;
        }
    }

    // ── per-form actives ───────────────────────────────────────────────

    private static void leap(ServerPlayer player, double horizontal, double vertical) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize().scale(horizontal);
        player.setDeltaMovement(flat.x, vertical, flat.z);
        player.hurtMarked = true; // forces a velocity packet to the client
        spark(player, ParticleTypes.CLOUD);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 0.4f, 1.6f);
    }

    private static void flap(ServerPlayer player) {
        Vec3 dm = player.getDeltaMovement();
        player.setDeltaMovement(dm.x, 0.6, dm.z);
        player.hurtMarked = true;
        player.fallDistance = 0.0f;
        spark(player, ParticleTypes.CLOUD);
    }

    private static void barkFear(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(8.0);
        for (Monster monster : player.level().getEntitiesOfClass(Monster.class, area)) {
            monster.setTarget(null);
            Vec3 away = monster.position().subtract(player.position()).normalize().scale(0.6);
            monster.setDeltaMovement(monster.getDeltaMovement().add(away.x, 0.1, away.z));
            monster.hurtMarked = true;
        }
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    private static void vanish(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false, true));
        APPLIED.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(MobEffects.INVISIBILITY);
        spark(player, ParticleTypes.SMOKE);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static boolean cool(UUID id, int ticks, ServerPlayer player, String message) {
        COOLDOWN.put(id, ticks);
        feedback(player, message, ChatFormatting.AQUA);
        return true;
    }

    private static void spark(ServerPlayer player, net.minecraft.core.particles.ParticleOptions particle) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(particle, player.getX(), player.getY() + 0.4, player.getZ(),
                    8, 0.3, 0.2, 0.3, 0.02);
        }
    }

    private static void feedback(ServerPlayer player, String message, ChatFormatting color) {
        player.displayClientMessage(Component.literal(message).withStyle(color), true);
    }

    /** Players who log out mid-form never hit clearPassives, so drop their entries here. */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class LogoutCleanup {
        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            UUID id = event.getEntity().getUUID();
            APPLIED.remove(id);
            COOLDOWN.remove(id);
        }
    }
}
