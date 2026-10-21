package at.koopro.wizardsandbeasts.skill.build.capstone;

import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Healer capstone, {@code healers_hands}: Episkey becomes a field medic's charm.
 *
 * <p>Episkey already mends a faced ally a little. In a Healer's hands it also lifts what leaves a friend unable to
 * act — Stupefy, Petrificus Totalus and a splinching — which is the Rennervate the mod does not otherwise have. And
 * cast at one of the Healer's own companions, it mends the creature, which nothing but the apothecary's counter
 * could do before.
 *
 * <p>Wired at {@code SpellCastUtilityHandler.handleEpiskeySelf}, after the ordinary ally heal.
 */
@NullMarked
public final class HealersHands {

    public static final String ABILITY = "healers_hands";
    /** What a Healer's Episkey restores to their own companion. */
    public static final float COMPANION_HEAL = 8.0f;

    private HealersHands() {}

    /**
     * Episkey's Healer tail: lift the faced ally, or — with no ally in front — mend the caster's own companion.
     *
     * @return whether anything was lifted or mended
     */
    public static boolean afterEpiskey(ServerPlayer caster, @Nullable LivingEntity faced) {
        if (!has(caster)) {
            return false;
        }
        return faced != null ? revive(faced) > 0 : mendCompanion(caster);
    }

    public static boolean has(ServerPlayer caster) {
        return SkillSystemAPI.hasAbility(caster, ABILITY);
    }

    /** The conditions a Healer lifts: incapacitation, not injury. */
    public static List<Holder<MobEffect>> lifted() {
        return List.of(ModEffects.STUPEFY, ModEffects.PETRIFICUS_TOTALUS, ModEffects.SPLINCHED);
    }

    /** Lifts every incapacitating effect; returns how many were lifted. */
    public static int revive(LivingEntity target) {
        int lifted = 0;
        for (Holder<MobEffect> effect : lifted()) {
            if (target.removeEffect(effect)) {
                lifted++;
            }
        }
        if (lifted > 0 && target.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + target.getBbHeight() * 0.6,
                    target.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        }
        return lifted;
    }

    /** Mends one of the caster's own companions they are facing, if any. Returns whether one was mended. */
    public static boolean mendCompanion(ServerPlayer caster) {
        LivingEntity companion = facedCompanion(caster, 8.0);
        if (companion == null) {
            return false;
        }
        companion.heal(COMPANION_HEAL);
        companion.removeEffect(MobEffects.POISON);
        companion.removeEffect(MobEffects.WITHER);
        companion.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, true, true));
        revive(companion);
        return true;
    }

    /** Nearest of the caster's own bonded creatures inside a tight look-cone, else null. */
    private static @Nullable LivingEntity facedCompanion(ServerPlayer caster, double reach) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        LivingEntity best = null;
        double bestDot = 0.94;
        AABB box = caster.getBoundingBox().inflate(reach);
        for (LivingEntity e : caster.level().getEntitiesOfClass(LivingEntity.class, box,
                other -> other.isAlive() && other instanceof BondableBeast beast
                        && caster.getUUID().equals(beast.bondOwner()))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            if (to.lengthSqr() > reach * reach || to.lengthSqr() < 1.0e-4) {
                continue;
            }
            double dot = to.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }
}
