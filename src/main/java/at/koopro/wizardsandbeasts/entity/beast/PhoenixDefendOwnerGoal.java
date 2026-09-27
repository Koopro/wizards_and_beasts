package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.creature.Trait;
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * A loyal phoenix flies at whatever is hurting its person, strikes at its eyes and pulls away.
 *
 * <p>Canon: Fawkes answers Harry's loyalty in the Chamber of Secrets and blinds the basilisk; Harry does the
 * killing, and Fantastic Beasts says the phoenix "has never been known to kill". So the strike wounds and
 * blinds and is capped below lethal ({@link WildlifeRules#phoenixStrikeDamage}), and a creature whose power is
 * its gaze is blinded for long enough that the gaze stops working ({@code DeathGazeGoal} refuses a blind gazer).
 *
 * <p>Only a phoenix that trusts its person enough to weep for them ({@link PhoenixEntity#TEARS_BOND}) fights for
 * them; the attacker must have struck its person in the last five seconds and be near.
 */
final class PhoenixDefendOwnerGoal extends Goal {

    private static final int STRIKE_INTERVAL = 25;
    private static final int GIVE_UP_TICKS = 200;
    private static final double RANGE = 24.0;

    private final PhoenixEntity phoenix;
    private LivingEntity attacker;
    private int strikeCooldown;
    private int ticks;

    PhoenixDefendOwnerGoal(PhoenixEntity phoenix) {
        this.phoenix = phoenix;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        attacker = findAttacker();
        return attacker != null;
    }

    private LivingEntity findAttacker() {
        if (phoenix.rebirth().burning() || phoenix.bondLevel() < PhoenixEntity.TEARS_BOND) {
            return null;
        }
        Player owner = phoenix.resolveBondOwner();
        if (owner == null || !owner.isAlive()) {
            return null;
        }
        LivingEntity threat = owner.getLastHurtByMob();
        if (threat == null || !threat.isAlive() || threat == phoenix || threat == owner
                || owner.tickCount - owner.getLastHurtByMobTimestamp() > 100
                || threat.distanceToSqr(phoenix) > RANGE * RANGE) {
            return null;
        }
        return threat;
    }

    @Override
    public boolean canContinueToUse() {
        return attacker != null && attacker.isAlive() && ticks < GIVE_UP_TICKS && !phoenix.rebirth().burning()
                && attacker.distanceToSqr(phoenix) < RANGE * RANGE * 1.5;
    }

    @Override
    public void start() {
        ticks = 0;
        strikeCooldown = 0;
    }

    @Override
    public void stop() {
        attacker = null;
        phoenix.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticks++;
        if (strikeCooldown > 0) {
            strikeCooldown--;
        }
        phoenix.getLookControl().setLookAt(attacker, 30.0f, 30.0f);
        phoenix.getNavigation().moveTo(attacker.getX(), attacker.getEyeY(), attacker.getZ(), 1.4);
        double reach = phoenix.getBbWidth() * 0.5 + attacker.getBbWidth() * 0.5 + 1.2;
        if (strikeCooldown == 0 && phoenix.distanceToSqr(attacker) <= reach * reach * 2.5
                && phoenix.level() instanceof ServerLevel level) {
            strike(level, attacker);
            strikeCooldown = STRIKE_INTERVAL;
        }
    }

    private void strike(ServerLevel level, LivingEntity target) {
        float damage = WildlifeRules.phoenixStrikeDamage(target.getHealth(), WildlifeRules.PHOENIX_STRIKE_DAMAGE);
        if (damage > 0.0f) {
            target.hurtServer(level, phoenix.damageSources().mobAttack(phoenix), damage);
        }
        boolean gazer = target instanceof GenericBeastEntity beast && beast.has(Trait.DEATH_GAZE);
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, gazer ? 600 : 60, 0), phoenix);
        phoenix.playFlap();
    }
}
