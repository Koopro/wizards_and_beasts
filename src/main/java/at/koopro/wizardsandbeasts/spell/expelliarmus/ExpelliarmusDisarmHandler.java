package at.koopro.wizardsandbeasts.spell.expelliarmus;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.item.wand.ExpelliarmusDropTag;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Lore-accurate Expelliarmus disarm: vector, proficiency fizzle, a defeat for wand allegiance, pickup lock.
 */
public final class ExpelliarmusDisarmHandler {

    private ExpelliarmusDisarmHandler() {}

    public static void apply(ServerLevel level, LivingEntity target, @Nullable ServerPlayer caster, float casterProficiencyScalar) {
        if (caster == null) {
            return;
        }
        ItemStack held = target.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        if (!WandHelper.isWand(held)) {
            applyMobKnockoff(level, target, caster, casterProficiencyScalar, held);
            return;
        }

        float allegiance = WandComponents.getAllegianceScore(held);
        if (casterProficiencyScalar < allegiance * 0.7f && level.random.nextFloat() < 0.30f) {
            // debug: surfaced via spell debug module later
            Vec3 kb = caster.getLookAngle().scale(0.4 + casterProficiencyScalar * 0.5);
            target.setDeltaMovement(target.getDeltaMovement().add(kb.x, 0.12, kb.z));
            target.hurtMarked = true;
            return;
        }

        ItemStack dropped = held.copy();
        WandInstanceIds.getOrAssign(level, dropped);
        if (target instanceof ServerPlayer victim) {
            // Disarming a wand's master is a defeat, and a defeat is how a wand is won (Deathly Hallows). Settled
            // on the stack before it leaves the hand, so the wand on the ground already says whose it now is.
            WandAllegianceService.onDefeat(victim, caster, WandAllegianceService.DefeatKind.DISARM, List.of(dropped));
        }

        target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        Vec3 throwVec = caster.getLookAngle().scale(0.5 + casterProficiencyScalar * 1.5);
        ItemEntity itemEntity = new ItemEntity(level,
                target.getX(), target.getEyeY() - 0.15, target.getZ(), dropped);
        itemEntity.setDeltaMovement(throwVec.x, Math.max(0.08, throwVec.y * 0.35 + 0.12), throwVec.z);
        UUID victimUuid = target.getUUID();
        dropped.set(ModDataComponents.EXPELLIARMUS_DROP.get(),
                new ExpelliarmusDropTag(victimUuid, caster.getUUID(), level.getGameTime()));
        itemEntity.setItem(dropped);
        level.addFreshEntity(itemEntity);

        int disarmEffectTicks = 40 + (int) (casterProficiencyScalar * 80);
        target.addEffect(new MobEffectInstance(ModEffects.EXPELLIARMUS_DISARMED, disarmEffectTicks, 0, false, true, true));
    }

    private static void applyMobKnockoff(ServerLevel level, LivingEntity target, ServerPlayer caster,
                                         float casterProficiencyScalar, ItemStack held) {
        ItemStack dropped = held.copy();
        target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Vec3 throwVec = caster.getLookAngle().scale(0.5 + casterProficiencyScalar * 1.5);
        ItemEntity itemEntity = new ItemEntity(level,
                target.getX(), target.getEyeY() - 0.15, target.getZ(), dropped);
        itemEntity.setDeltaMovement(throwVec.x, Math.max(0.06, throwVec.y * 0.25 + 0.1), throwVec.z);
        level.addFreshEntity(itemEntity);
    }

}
