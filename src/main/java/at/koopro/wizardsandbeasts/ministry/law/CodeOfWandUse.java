package at.koopro.wizardsandbeasts.ministry.law;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicenceTags;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Clause Three of the Code of Wand Use: "No non-human creature is permitted to carry or use a wand" (GoF ch.9 — Winky,
 * found holding Harry's wand under the Dark Mark).
 *
 * <p>The peoples the Code means are the ones that bond no wand at all ({@link Heritage#canUseWand()} false: goblins,
 * house-elves, centaurs, merpeople). For them the law has two faces, and both read this class:
 *
 * <ul>
 *   <li><b>No wandmaker will sell to them.</b> Ollivanders refuses the trial and the commission.</li>
 *   <li><b>Carrying one is an offence when the Ministry sees it.</b> A wand in either hand within
 *       {@link #WATCH_RADIUS} of a Ministry official who can see them files {@link MagicalOffence#NON_HUMAN_WAND}
 *       (a fine) — once a day, not once a tick. Out of sight, nothing: the Ministry knows only what it sees.</li>
 * </ul>
 *
 * Holding a wand they cannot use is already pointless (every cast is refused); this is the world noticing.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class CodeOfWandUse {

    public static final double WATCH_RADIUS = 16.0;
    private static final int CHECK_INTERVAL = 40;

    /** The last in-game day each player was reported, so one carried wand is one offence a day. */
    private static final PlayerScopedState<Long> REPORTED = PlayerScopedState.create("wand_code_reported");

    private CodeOfWandUse() {}

    /** Whether the Code forbids this character a wand: their people bonds none. */
    public static boolean forbidden(ServerPlayer player) {
        Heritage heritage = HeritageAPI.getData(player).getSelectedHeritage();
        return heritage != null && !heritage.canUseWand();
    }

    /** Whether they are carrying a wand in either hand. */
    public static boolean carrying(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof WandItem
                || player.getOffhandItem().getItem() instanceof WandItem;
    }

    /** Whether a Ministry official within the watch radius can see them. */
    public static boolean seenByOfficial(ServerPlayer player) {
        double r2 = WATCH_RADIUS * WATCH_RADIUS;
        return !player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WATCH_RADIUS),
                e -> e != player && e.isAlive() && !e.isSleeping() && e.getType().is(MinistryLicenceTags.MINISTRY_OFFICIALS)
                        && e.distanceToSqr(player) <= r2 && e.hasLineOfSight(player)).isEmpty();
    }

    /**
     * Files the offence if this player is breaking the Code in an official's sight and has not been reported today.
     *
     * @return whether a report was filed
     */
    public static boolean check(ServerPlayer player) {
        if (!TraceService.isActive() || !forbidden(player) || !carrying(player) || !seenByOfficial(player)) {
            return false;
        }
        long day = SpellPractice.day(player.level().getGameTime());
        Long last = REPORTED.get(player);
        if (last != null && last == day) {
            return false;
        }
        REPORTED.put(player, day);
        TraceService.report(player, MagicalOffence.NON_HUMAN_WAND);
        PlayerFeedback.toast(player, NoticeKind.FAIL, Component.translatable("ministry.wizards_and_beasts.wand_code.title"),
                Component.translatable("ministry.wizards_and_beasts.wand_code.body"));
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_INTERVAL == 0) {
            check(player);
        }
    }

    /** The refusal a wandmaker gives a non-human customer. */
    public static Component refusal() {
        return Component.translatable("ministry.wizards_and_beasts.wand_code.refused");
    }
}
