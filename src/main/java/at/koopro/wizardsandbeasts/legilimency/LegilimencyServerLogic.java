package at.koopro.wizardsandbeasts.legilimency;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
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

    private static final String L = "legilimency.wizards_and_beasts.";
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
            float resistChance = PlayerAbilityHelper.getOcclumencyLevel(targetPlayer) * 0.8f
                    * at.koopro.wizardsandbeasts.stats.StatResistModifiers.resistScalar(targetPlayer);
            if (targetPlayer.getRandom().nextFloat() < resistChance) {
                PlayerFeedback.toast(targetPlayer, NoticeKind.WARN,
                        Component.translatable(L + "defended.title"),
                        Component.translatable(L + "defended.body"));
                PlayerFeedback.toast(caster, NoticeKind.FAIL,
                        targetPlayer.getName().copy(),
                        Component.translatable(L + "resisted"));
                PlayerAbilityHelper.setLegilimencyCooldownTicks(caster, 600);
                at.koopro.wizardsandbeasts.stats.StatTraining.onMindDefended(targetPlayer);
                at.koopro.wizardsandbeasts.stats.StatMilestones.onMilestoneTriggered(
                        targetPlayer, at.koopro.wizardsandbeasts.stats.MilestoneType.FIRST_OCCLUMENCY_DEFENCE_SUCCESS);
                return;
            }
            PlayerFeedback.toast(targetPlayer, NoticeKind.WARN,
                    Component.translatable(L + "breached.title"),
                    Component.translatable(L + "breached.body"));
            // The old line here dumped the target's raw internal ability flags to the caster's chat.
            // That was developer output shipped as gameplay; what a Legilimens is meant to come away
            // with is the vision below, not a list of feature toggles.
            applyVisionFromTarget(caster, targetPlayer);
        } else {
            LivingEntity mob = (LivingEntity) target;
            // Reworded from "Mob target entity id: 4711" / "Mob health: 62%". The reading itself is
            // legitimate — knowing what a beast is hunting is the point of the spell — but an entity
            // id is not something a player can act on, so it reports the quarry by name instead.
            LivingEntity quarry = mob instanceof Mob m ? m.getTarget() : null;
            int hpPct = (int) Math.round((mob.getHealth() / Math.max(1.0f, mob.getMaxHealth())) * 100.0);
            PlayerFeedback.toast(caster, NoticeKind.DISCOVERY,
                    mob.getName().copy(),
                    quarry == null
                            ? Component.translatable(L + "mob.calm", hpPct)
                            : Component.translatable(L + "mob.hunting", quarry.getName(), hpPct));
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
