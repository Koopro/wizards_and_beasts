package at.koopro.wizardsandbeasts.event.skill;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.skill.GameplayStat;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Consumers for the Magizoology "Beast Handler" line. The {@code beast_handler} and
 * {@code dragon_tamer} nodes grant a stacking {@link GameplayStat#BEAST_DAMAGE_RESISTANCE},
 * cutting damage dealt to the player by living creatures (anything that isn't another player).
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MagizoologyAbilityHandler {

    /** Hard floor so stacked nodes can never trivialise creature combat. */
    private static final float MAX_RESISTANCE = 0.8f;

    private MagizoologyAbilityHandler() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Only mitigate harm from living, non-player creatures (a beast handler's affinity).
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker instanceof Player) {
            return;
        }

        float bonus = SkillSystemAPI.getGameplayBonus(player, GameplayStat.BEAST_DAMAGE_RESISTANCE);
        if (at.koopro.wizardsandbeasts.skill.vocation.VocationHelper.hasGrantedAbility(player, "beast_capacity")) {
            bonus += at.koopro.wizardsandbeasts.skill.vocation.VocationAbilityHooks.BEAST_RESISTANCE_BONUS;
        }
        float resistance = Math.min(MAX_RESISTANCE, bonus);
        if (resistance <= 0f) return;

        event.setAmount(event.getAmount() * (1.0f - resistance));
    }

    /** Mount Loyalty: a committed magizoologist's mount shrugs off part of any harm while ridden. */
    @SubscribeEvent
    public static void onMountIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player) return;
        if (!(event.getEntity().getControllingPassenger() instanceof ServerPlayer rider)) return;
        if (!at.koopro.wizardsandbeasts.skill.vocation.VocationHelper.hasGrantedAbility(rider, "mount_loyalty")) return;
        event.setAmount(event.getAmount()
                * at.koopro.wizardsandbeasts.skill.vocation.VocationAbilityHooks.MOUNT_DAMAGE_MULT);
    }
}
