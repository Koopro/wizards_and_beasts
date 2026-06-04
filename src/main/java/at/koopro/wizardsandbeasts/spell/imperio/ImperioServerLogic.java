package at.koopro.wizardsandbeasts.spell.imperio;

import at.koopro.wizardsandbeasts.spell.imperio.ImperioControlState;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.spell.CrucioIntentFeedbackS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioControlS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioResistS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioVictimBoundS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ImperioServerLogic {

    private static final Map<UUID, Long> LAST_RESIST_PACKET_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> CASTER_TO_VICTIM = new ConcurrentHashMap<>();

    private ImperioServerLogic() {}

    public static void beginControl(ServerLevel level, ServerPlayer caster, LivingEntity target, int durationTicks) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return;
        }
        ImperioControlState state = new ImperioControlState(true, caster.getUUID(), durationTicks, 0f,
                ImperioCommand.FOLLOW_CASTER.ordinal(), false);
        target.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), state);
        target.addEffect(new MobEffectInstance(ModEffects.IMPERIO_EUPHORIA, durationTicks, 0, false, true, true));
        if (target instanceof ServerPlayer victim) {
            PacketDistributor.sendToPlayer(victim, new ImperioControlS2CPayload(true, caster.getUUID()));
        }
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        float corruption = caster.getData(ModAttachments.DARK_CORRUPTION.get());
        caster.setData(ModAttachments.DARK_CORRUPTION.get(), Math.min(100f, corruption + 8f));
        level.playSound(null, target.blockPosition(), ModSounds.SPELL_CAST_DARK.get(), SoundSource.PLAYERS, 0.75f, 1.1f);
        Vec3 p = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y + 1.0, p.z, 20, 0.35, 0.2, 0.35, 0.02);
        CASTER_TO_VICTIM.put(caster.getUUID(), target.getUUID());
        PacketDistributor.sendToPlayer(caster, new ImperioVictimBoundS2CPayload(target.getUUID()));
    }

    public static void applyCommand(ServerPlayer caster, ImperioCommand cmd) {
        UUID victimId = CASTER_TO_VICTIM.get(caster.getUUID());
        if (victimId == null || !(caster.level() instanceof ServerLevel sl)) {
            return;
        }
        LivingEntity victim = findLiving(sl, victimId);
        if (victim == null) {
            return;
        }
        ImperioControlState st = victim.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
        if (!st.isControlled() || !st.controllerUUID().equals(caster.getUUID())) {
            return;
        }
        victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(),
                new ImperioControlState(true, caster.getUUID(), st.remainingTicks(), st.resistanceProgress(),
                        cmd.ordinal(), st.sneakAttemptSinceLastResistTick()));
    }

    public static void onSneakResistAttempt(ServerPlayer victim) {
        if (!(victim.level() instanceof ServerLevel sl)) {
            return;
        }
        long now = sl.getGameTime();
        Long last = LAST_RESIST_PACKET_TICK.get(victim.getUUID());
        if (last != null && now - last < 20) {
            return;
        }
        LAST_RESIST_PACKET_TICK.put(victim.getUUID(), now);
        ImperioControlState st = victim.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
        if (!st.isControlled()) {
            return;
        }
        victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), st.withSneakAttempt(true));
    }

    public static void tickVictim(ServerPlayer victim) {
        if (!(victim.level() instanceof ServerLevel sl)) {
            return;
        }
        ImperioControlState st = victim.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
        if (!st.isControlled()) {
            return;
        }
        if (victim.tickCount % 20 == 0 && st.sneakAttemptSinceLastResistTick()) {
            float will = victim.getData(ModAttachments.WILLPOWER.get());
            ServerPlayer controller = sl.getServer().getPlayerList().getPlayer(st.controllerUUID());
            if (will <= 0f) {
                victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), st.withSneakAttempt(false));
            } else {
                float imperioProf = Spells.IMPERIO.getProficiencyScalar(victim);
                float resistChance = (will / 100f) * (imperioProf * 0.5f + 0.5f);
                boolean success = sl.random.nextFloat() < resistChance;
                if (success) {
                    clearControl(sl, victim, st.controllerUUID());
                    victim.setData(ModAttachments.WILLPOWER.get(), Math.max(0f, will - 30f));
                    victim.displayClientMessage(
                            Component.literal("You throw off the Imperius Curse!").withStyle(ChatFormatting.GOLD), true);
                    PacketDistributor.sendToPlayer(victim, new ImperioResistS2CPayload(true, 1f));
                    at.koopro.wizardsandbeasts.stats.StatMilestones.onMilestoneTriggered(
                            victim, at.koopro.wizardsandbeasts.stats.MilestoneType.FIRST_IMPERIUS_RESISTED);
                } else {
                    victim.setData(ModAttachments.WILLPOWER.get(), Math.max(0f, will - 15f));
                    victim.addEffect(new MobEffectInstance(ModEffects.IMPERIO_RESISTING, 20, 0, false, true, true));
                    float prog = Math.min(1f, st.resistanceProgress() + 0.1f);
                    victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(),
                            new ImperioControlState(true, st.controllerUUID(), st.remainingTicks(), prog,
                                    st.imperioCommandOrdinal(), false));
                    PacketDistributor.sendToPlayer(victim, new ImperioResistS2CPayload(false, prog));
                }
                if (controller != null) {
                    PacketDistributor.sendToPlayer(controller, new CrucioIntentFeedbackS2CPayload(1.0f));
                }
            }
        }
        ImperioControlState st2 = victim.getData(ModAttachments.IMPERIO_CONTROL_STATE.get());
        if (!st2.isControlled()) {
            return;
        }
        ImperioControlState next = st2.tickDown();
        if (!next.isControlled()) {
            clearControl(sl, victim, st2.controllerUUID());
        } else {
            victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), next);
        }
    }

    private static void clearControl(ServerLevel level, LivingEntity victim, UUID controllerUuid) {
        victim.removeEffect(ModEffects.IMPERIO_EUPHORIA);
        victim.removeEffect(ModEffects.IMPERIO_RESISTING);
        victim.setData(ModAttachments.IMPERIO_CONTROL_STATE.get(), ImperioControlState.DEFAULT);
        if (victim instanceof ServerPlayer pl) {
            PacketDistributor.sendToPlayer(pl, new ImperioControlS2CPayload(false, pl.getUUID()));
        }
        ServerPlayer controller = level.getServer().getPlayerList().getPlayer(controllerUuid);
        if (controller != null) {
            CASTER_TO_VICTIM.remove(controllerUuid, victim.getUUID());
            PacketDistributor.sendToPlayer(controller, new ImperioVictimBoundS2CPayload(new UUID(0L, 0L)));
        }
    }

    private static @Nullable LivingEntity findLiving(ServerLevel sl, UUID id) {
        net.minecraft.world.entity.Entity e = sl.getEntity(id);
        return e instanceof LivingEntity ? (LivingEntity) e : null;
    }
}
