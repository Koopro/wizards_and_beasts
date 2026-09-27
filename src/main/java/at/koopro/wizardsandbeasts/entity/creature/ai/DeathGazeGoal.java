package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.basilisk.BasiliskDamageTypes;
import at.koopro.wizardsandbeasts.basilisk.BasiliskGaze;
import at.koopro.wizardsandbeasts.creature.Trait;
import at.koopro.wizardsandbeasts.effect.BasiliskGazeLockEffect;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.creature.BasiliskEntity;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.spell.petrify.PetrifyServerLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A killing stare, for creatures carrying {@code Trait.DEATH_GAZE}.
 *
 * <p><b>Lethal gaze</b> ({@code Trait.LETHAL_GAZE}, the basilisk). Every {@link #INTERVAL} ticks it measures each
 * player in range with {@link BasiliskGaze}: facing, walls, whether they meet its eyes, and whether they see them
 * directly or indirectly. Eye contact has to <em>hold</em> for {@link BasiliskGazeLockEffect#WINDUP_TICKS} — a
 * heartbeat telegraph ({@code BASILISK_GAZE_LOCK}) runs meanwhile — and is re-measured at every interval, so looking
 * away, stepping behind a wall, closing your eyes (a Blindfold) or raising Protego breaks it. When it holds, the
 * outcome lands once: death for direct sight, petrification for indirect. The outcome is decided here, on the
 * server, from fresh geometry; the telegraph effect expiring decides nothing.
 *
 * <p><b>Debuff gaze</b> (no {@code LETHAL_GAZE}; the lethifold): unchanged — a met stare withers and blinds, a
 * glancing one slows.
 *
 * <p>Cost: one player query per interval, one block ray per player in range, a handful of fluid lookups only when
 * the ray is clear. A blinded gazer (Fawkes's work) has no gaze at all.
 */
public final class DeathGazeGoal extends Goal {

    public static final int INTERVAL = 5;
    private static final double DEBUFF_RANGE = 12.0;
    private static final int DEBUFF_INTERVAL = 20;
    private static final double MEET_DOT = 0.6;
    private static final double PERIPHERAL_DOT = 0.1;

    private final GenericBeastEntity mob;
    /** Player → ticks of unbroken eye contact, and the outcome it is heading for. Server-side, never saved. */
    private final Map<UUID, Lock> locks = new HashMap<>();
    private int tick;

    private record Lock(int ticks, BasiliskGaze.Outcome outcome) {}

    public DeathGazeGoal(GenericBeastEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        // A blinded gazer has no gaze: Fawkes put out the basilisk's eyes and it could no longer kill by
        // looking (Chamber of Secrets). A phoenix defending its person blinds what it strikes.
        return mob.isAlive() && !mob.hasEffect(MobEffects.BLINDNESS);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        locks.keySet().forEach(this::releaseTelegraph);
        locks.clear();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (++tick % INTERVAL != 0 || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (mob.has(Trait.LETHAL_GAZE)) {
            lethalGaze(level);
        } else if (tick % DEBUFF_INTERVAL == 0) {
            debuffGaze();
        }
    }

    // ── lethal ───────────────────────────────────────────────────────────────

    private void lethalGaze(ServerLevel level) {
        Vec3 eyes = eyes();
        Vec3 facing = Vec3.directionFromRotation(0, mob.getYHeadRot());
        Set<UUID> seen = new HashSet<>();
        boolean landedNew = false;
        for (Player player : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(BasiliskGaze.RANGE + 4),
                p -> p.isAlive() && !p.isSpectator())) {
            BasiliskGaze.Outcome outcome = BasiliskGaze.evaluate(level, eyes, facing, player);
            if (outcome == BasiliskGaze.Outcome.NONE) {
                continue;
            }
            UUID id = player.getUUID();
            seen.add(id);
            Lock was = locks.get(id);
            int held = (was == null ? 0 : was.ticks()) + INTERVAL;
            // Direct sight wins over indirect for the whole windup once it has happened.
            BasiliskGaze.Outcome heading = was != null && was.outcome() == BasiliskGaze.Outcome.DEATH
                    ? BasiliskGaze.Outcome.DEATH : outcome;
            if (was == null || !player.hasEffect(ModEffects.BASILISK_GAZE_LOCK)) {
                player.addEffect(new MobEffectInstance(ModEffects.BASILISK_GAZE_LOCK,
                        BasiliskGazeLockEffect.WINDUP_TICKS - held + INTERVAL,
                        heading == BasiliskGaze.Outcome.DEATH ? 1 : 0, false, false, true), mob);
                landedNew |= was == null;
            }
            if (held >= BasiliskGazeLockEffect.WINDUP_TICKS) {
                locks.remove(id);
                resolve(level, player, heading);
            } else {
                locks.put(id, new Lock(held, heading));
            }
        }
        // Contact broken — looked away, stepped behind a wall, left range, shut their eyes.
        locks.keySet().removeIf(id -> {
            if (seen.contains(id)) {
                return false;
            }
            releaseTelegraph(id);
            return true;
        });
        if (landedNew) {
            // The rig's `gaze` clip plays when a stare first lands on someone, not every interval.
            mob.triggerDeclared("gaze");
        }
    }

    /** The outcome, once. Petrification is idempotent; a dead player is not killed again. */
    private void resolve(ServerLevel level, Player player, BasiliskGaze.Outcome outcome) {
        player.removeEffect(ModEffects.BASILISK_GAZE_LOCK);
        if (outcome == BasiliskGaze.Outcome.DEATH) {
            player.hurtServer(level, level.damageSources().source(BasiliskDamageTypes.GAZE, mob), Float.MAX_VALUE);
        } else if (outcome == BasiliskGaze.Outcome.PETRIFY) {
            PetrifyServerLogic.beginPetrify(level, player);
        }
    }

    private void releaseTelegraph(UUID id) {
        if (mob.level().getPlayerByUUID(id) instanceof Player player) {
            player.removeEffect(ModEffects.BASILISK_GAZE_LOCK);
        }
    }

    /** Where its eyes are: the basilisk's head, or an ordinary creature's eye height. */
    private Vec3 eyes() {
        return mob instanceof BasiliskEntity basilisk ? basilisk.headPosition() : mob.getEyePosition();
    }

    // ── debuff (lethifold) ──────────────────────────────────────────────────

    private void debuffGaze() {
        Vec3 mobEye = mob.getEyePosition();
        boolean gazed = false;
        for (Player player : mob.level().getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(DEBUFF_RANGE),
                p -> p.isAlive() && !p.isSpectator())) {
            if (isGazeImmune(player) || !mob.hasLineOfSight(player)) {
                continue;
            }
            Vec3 toMob = mobEye.subtract(player.getEyePosition()).normalize();
            double dot = player.getViewVector(1.0f).dot(toMob);
            if (dot > MEET_DOT) {
                gazed = true;
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 4));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
            } else if (dot > PERIPHERAL_DOT) {
                gazed = true;
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
            }
        }
        if (gazed) {
            mob.triggerDeclared("gaze");
        }
    }

    /**
     * Blindness, a worn Blindfold, or an active Protego ward all make a player un-gazeable — and so does already being
     * stone: the petrified see nothing, so a statue that happens to face the eyes is not then killed by them.
     */
    public static boolean isGazeImmune(Player player) {
        if (player.hasEffect(MobEffects.BLINDNESS) || player.hasEffect(ModEffects.PROTEGO_SHIELD)
                || PetrifyServerLogic.isPetrified(player)) {
            return true;
        }
        return player.getItemBySlot(EquipmentSlot.HEAD).is(MiscItemRegistry.BLINDFOLD.get());
    }
}
