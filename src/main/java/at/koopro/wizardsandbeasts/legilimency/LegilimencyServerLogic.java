package at.koopro.wizardsandbeasts.legilimency;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.legilimency.LegilimencyVisionS2CPayload;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class LegilimencyServerLogic {
    private LegilimencyServerLogic() {
    }

    public static void handleRequest(ServerPlayer caster, int targetEntityId) {
        if (!ModuleManager.isEnabled(Module.PLAYER_ABILITIES)) {
            return;
        }
        if (!canLegilimise(caster)) {
            return;
        }
        if (PlayerAbilityHelper.getLegilimencyCooldownTicks(caster) > 0) {
            caster.displayClientMessage(Component.literal("Legilimency is still on cooldown.").withStyle(ChatFormatting.RED), true);
            return;
        }
        Entity target = caster.level().getEntity(targetEntityId);
        if (!(target instanceof LivingEntity)) {
            return;
        }
        if (caster.distanceTo(target) > 8.0f) {
            return;
        }

        if (target instanceof ServerPlayer) {
            ServerPlayer targetPlayer = (ServerPlayer) target;
            // Occlumency training decides whether a defence exists at all; Willpower decides how well
            // it holds. Scaling rather than adding keeps an untrained Occlumens at zero.
            int willTrait = at.koopro.wizardsandbeasts.stats.PlayerStatsAPI.getStat(
                    targetPlayer, at.koopro.wizardsandbeasts.stats.PlayerStat.WILLPOWER);
            float resistChance = PlayerAbilityHelper.getOcclumencyLevel(targetPlayer) * 0.8f
                    * at.koopro.wizardsandbeasts.stats.StatEffects.resistScalar(willTrait);
            if (targetPlayer.getRandom().nextFloat() < resistChance) {
                targetPlayer.displayClientMessage(Component.literal("You felt a presence attempting to enter your mind — and repelled it.").withStyle(ChatFormatting.AQUA), false);
                caster.displayClientMessage(Component.literal("Their mind resisted your intrusion.").withStyle(ChatFormatting.YELLOW), false);
                PlayerAbilityHelper.setLegilimencyCooldownTicks(caster, 600);
                at.koopro.wizardsandbeasts.stats.StatTraining.onMindDefended(targetPlayer);
                at.koopro.wizardsandbeasts.stats.StatMilestones.onMilestoneTriggered(
                        targetPlayer, at.koopro.wizardsandbeasts.stats.MilestoneType.FIRST_OCCLUMENCY_DEFENCE_SUCCESS);
                return;
            }
            targetPlayer.displayClientMessage(Component.literal("You felt someone enter your mind.").withStyle(ChatFormatting.DARK_PURPLE), false);
            caster.displayClientMessage(Component.literal("Ability flags: " + String.join(", ", PlayerAbilityHelper.getAbilityFlags(targetPlayer))).withStyle(ChatFormatting.GRAY), false);
            applyVisionFromTarget(caster, targetPlayer);
        } else {
            LivingEntity mob = (LivingEntity) target;
            if (mob instanceof Mob && ((Mob) mob).getTarget() != null) {
                caster.displayClientMessage(Component.literal("Mob target entity id: " + ((Mob) mob).getTarget().getId()).withStyle(ChatFormatting.GRAY), false);
            } else {
                caster.displayClientMessage(Component.literal("Mob target entity id: none").withStyle(ChatFormatting.GRAY), false);
            }
            int hpPct = (int) Math.round((mob.getHealth() / Math.max(1.0f, mob.getMaxHealth())) * 100.0);
            caster.displayClientMessage(Component.literal("Mob health: " + hpPct + "%").withStyle(ChatFormatting.GRAY), false);
            LegilimencyVisionS2CPayload.sendTo(caster, BlockPos.containing(mob.position()), 200);
        }
        PlayerAbilityHelper.setLegilimencyCooldownTicks(caster, 600);
    }

    private static void applyVisionFromTarget(ServerPlayer caster, ServerPlayer target) {
        BlockPos marker = BlockPos.containing(target.position());
        if (target.getLastDeathLocation().isPresent()) {
            marker = target.getLastDeathLocation().get().pos();
        }
        LegilimencyVisionS2CPayload.sendTo(caster, marker, 200);
    }

    /**
     * Whether the player may use Legilimency at all (heritage rule only — no cooldown, no target).
     * Read-only; also consulted by the ability grant layer for wheel visibility.
     * {@link #handleRequest} remains the authority and re-runs it itself.
     */
    public static boolean canLegilimise(ServerPlayer player) {
        Heritage heritage = HeritageAPI.getPlayerHeritage(player);
        HeritageVariant variant = HeritageAPI.getPlayerHeritageVariant(player);
        if (heritage != Heritage.WIZARDKIND) {
            return false;
        }
        // Was `variant.hasTag("can_legilimise")` — a tag no HeritageVariant has ever declared, so this
        // returned false for every player (every wizard has a non-null variant) and the whole ability was
        // unreachable. Gated on the mod's existing capability vocabulary instead: any wizard who can work
        // magic at all, which excludes the Squib. `can_legilimise` still grants it explicitly, so a
        // datapack or future variant can opt in without touching this code.
        return variant == null
                || variant.hasTag("can_legilimise")
                || !variant.hasTag("no_casting");
    }
}
