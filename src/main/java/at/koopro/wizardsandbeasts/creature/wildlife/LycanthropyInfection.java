package at.koopro.wizardsandbeasts.creature.wildlife;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfConfig;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfState;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Lycanthropy passed on by a bite, as the books have it: a human bitten by a werewolf in wolf form under a full moon
 * carries the curse from then on, and the moon claims them at the next full moon.
 *
 * <p>The bite comes from a werewolf creature ({@code lycanthropic_bite} ability) or from a transformed werewolf player.
 * The victim keeps everything that makes them who they are — {@link HeritageAPI#afflict} changes the body, not the
 * character — and the existing moon lifecycle takes it from there: human, full moon, wolf, morning, human.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class LycanthropyInfection {

    private LycanthropyInfection() {}

    /**
     * A transformed werewolf's bite landed on {@code victim}.
     *
     * @return whether the victim now carries the curse
     */
    public static boolean bitten(ServerPlayer victim, float damageDealt) {
        return victim.level() instanceof ServerLevel level
                && bitten(victim, damageDealt, WerewolfRules.fullMoonNight(level));
    }

    /** {@link #bitten(ServerPlayer, float)} with the state of the moon given rather than read from the world. */
    public static boolean bitten(ServerPlayer victim, float damageDealt, boolean fullMoonNight) {
        if (!ModuleManager.isEnabled(Module.HERITAGE) || !(victim.level() instanceof ServerLevel level)) {
            return false;
        }
        PlayerHeritageData data = victim.getData(at.koopro.wizardsandbeasts.registry.ModAttachments.HERITAGE_DATA.get());
        boolean human = data.getSelectedHeritage() == Heritage.WIZARDKIND;
        if (!WildlifeRules.infects(WerewolfConfig.biteInfects, true, fullMoonNight, human,
                WerewolfRules.isWerewolf(data), damageDealt)) {
            return false;
        }
        HeritageAPI.afflict(victim, at.koopro.wizardsandbeasts.heritage.ConditionOrigin.BITTEN);
        WerewolfState.setOnsetDay(data, WerewolfRules.nightOf(level.getDayTime()));
        victim.sendSystemMessage(Component.translatable("heritage.wizards_and_beasts.werewolf.bitten")
                .withStyle(ChatFormatting.DARK_RED));
        return true;
    }

    /** A werewolf player in wolf form biting another player. */
    @SubscribeEvent
    public static void onPlayerBite(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer biter && biter != victim
                && WerewolfRules.inWolfForm(biter.getData(
                        at.koopro.wizardsandbeasts.registry.ModAttachments.HERITAGE_DATA.get()))) {
            bitten(victim, event.getNewDamage());
        }
    }
}
