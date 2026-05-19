package at.koopro.wizardsandbeasts.spell.expelliarmus;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.item.wand.ExpelliarmusDropTag;
import at.koopro.wizardsandbeasts.event.wand.DisarmLogState;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.cast.WandCastingAllegianceSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lore-accurate Expelliarmus disarm: vector, proficiency fizzle, disarm log / allegiance, pickup lock.
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
        UUID wandInstanceId = WandInstanceIds.getOrAssign(level, dropped);
        recordDisarmForAllegiance(caster, wandInstanceId, level.getGameTime());

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

        tryTransferAllegianceFromLog(level, caster, dropped, wandInstanceId);
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

    private static void recordDisarmForAllegiance(ServerPlayer disarmer, UUID wandInstanceId, long gameTime) {
        DisarmLogState log = disarmer.getData(ModAttachments.DISARM_LOG.get());
        Map<UUID, List<Long>> entries = new java.util.HashMap<>(log.entries());
        List<Long> times = new ArrayList<>(entries.getOrDefault(wandInstanceId, List.of()));
        times.add(gameTime);
        times.removeIf(t -> gameTime - t > 12000L);
        entries.put(wandInstanceId, times);
        disarmer.setData(ModAttachments.DISARM_LOG.get(), new DisarmLogState(entries));
    }

    private static void tryTransferAllegianceFromLog(ServerLevel level, ServerPlayer disarmer, ItemStack wandStack, UUID wandInstanceId) {
        var log = disarmer.getData(ModAttachments.DISARM_LOG.get());
        List<Long> times = log.entries().get(wandInstanceId);
        if (times == null || times.size() < 3) {
            return;
        }
        java.util.Optional<UUID> prevMaster = WandComponents.getMaster(wandStack);
        WandCastingAllegianceSystem.transferTo(wandStack, disarmer.getUUID(), 0.55f, level.getGameTime());
        disarmer.sendSystemMessage(Component.literal("The wand has chosen you.").withStyle(ChatFormatting.GOLD));
        prevMaster.ifPresent(prev -> {
            Player prevPlayer = level.getPlayerByUUID(prev);
            if (prevPlayer instanceof ServerPlayer prevSp) {
                prevSp.sendSystemMessage(
                        Component.literal("Your wand no longer answers to you.").withStyle(ChatFormatting.DARK_RED));
            }
        });
        java.util.HashMap<UUID, List<Long>> next = new java.util.HashMap<>(log.entries());
        next.remove(wandInstanceId);
        disarmer.setData(ModAttachments.DISARM_LOG.get(), new DisarmLogState(next));
    }
}
