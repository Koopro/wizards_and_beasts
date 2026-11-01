package at.koopro.wizardsandbeasts.chamber;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.journey.ApprenticePathService;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;

/**
 * Records Chamber quest stages on players, tells them once, and refreshes their sheet. The stages are reached by what
 * happens in a Chamber (a seal opening, the gate rising, the basilisk dying); this only writes them down.
 */
@NullMarked
public final class ChamberQuestService {

    private static final String KEY = "chamber.wizards_and_beasts.";

    private ChamberQuestService() {}

    public static ChamberQuest quest(ServerPlayer player) {
        return player.getData(ModAttachments.CHAMBER_QUEST.get());
    }

    /** Records {@code stage} on {@code player}. Returns whether it was new; a stage is announced only the first time. */
    public static boolean reach(ServerPlayer player, ChamberStage stage) {
        ChamberQuest quest = quest(player);
        if (quest.has(stage)) {
            return false;
        }
        player.setData(ModAttachments.CHAMBER_QUEST.get(), quest.with(stage));
        PlayerFeedback.toast(player, NoticeKind.DISCOVERY, Component.translatable(KEY + "title"),
                Component.translatable(stage.reachedKey()));
        ApprenticePathService.sync(player);
        return true;
    }

    /** Every living, non-spectating player within {@code radius} blocks of {@code pos} reaches {@code stage}. */
    public static void reachNear(ServerLevel level, BlockPos pos, double radius, ChamberStage stage) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(radius),
                p -> p.isAlive() && !p.isSpectator())) {
            reach(player, stage);
        }
    }

    public static boolean worthy(ServerPlayer player) {
        return quest(player).worthy();
    }
}
