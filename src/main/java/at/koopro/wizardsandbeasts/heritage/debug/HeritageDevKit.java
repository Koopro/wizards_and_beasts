package at.koopro.wizardsandbeasts.heritage.debug;

import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Get the player through the front door.
 *
 * <p>The first-join selection ceremony gates most of the mod, and a player who has not been through
 * it reads downstream as "chose nothing" — every heritage bonus resolves to zero and nothing says
 * why. So this runs before every other kit: it is the difference between the rest of dev setup
 * working and the rest of dev setup appearing to work.
 *
 * <p>It picks <b>Half-Blood Wizardkind</b> and only if nothing is selected yet. Wizardkind because
 * it is the plain case with no transformation layer sitting over the render path to confuse whatever
 * you are actually testing; Half-Blood because it is the middle of that ladder rather than either
 * end. And it never overwrites a choice already made — a developer who set themselves to Werewolf to
 * test the moon does not want dev setup quietly turning them back into a wizard.
 */
@NullMarked
public final class HeritageDevKit implements FeatureDevKit {

    private static final Heritage DEFAULT_HERITAGE = Heritage.WIZARDKIND;
    private static final HeritageVariant DEFAULT_VARIANT = HeritageVariant.HALF_BLOOD;
    /** Enough to buy into a profession tree without having to grind the ceremony's award. */
    private static final int PROFESSION_POINTS = 10;

    @Override
    public String id() {
        return "heritage";
    }

    @Override
    public String title() {
        return "Heritage";
    }

    @Override
    public String summary() {
        return "Push the player through the selection gate as a Half-Blood, if they have not chosen.";
    }

    @Override
    public void open(ServerPlayer target, DevLog log) {
        PlayerHeritageData data = target.getData(ModAttachments.HERITAGE_DATA.get());
        if (data.hasHeritageSelected()) {
            log.skip("already " + data.getSelectedHeritage() + " / " + data.getSelectedHeritageVariant());
        } else {
            // Through the same routine the gate itself runs, so a dev-kit wizard is a real one: rolled
            // POWER, the right body, the right ability grants, and visible to other clients. Setting the
            // three fields by hand gave none of that.
            HeritageAPI.commit(target, DEFAULT_HERITAGE, DEFAULT_VARIANT);
            log.changed("heritage", DEFAULT_HERITAGE.getDisplayName()
                    + " / " + DEFAULT_VARIANT.getDisplayName());
        }
        if (data.getProfessionPoints() < PROFESSION_POINTS) {
            data.addProfessionPoints(PROFESSION_POINTS - data.getProfessionPoints());
            log.changed("profession points", data.getProfessionPoints());
            HeritageDataSyncS2CPayload.syncToPlayer(target, false);
        }
    }

    /**
     * Back to unchosen, and the selection screen opens again on the client.
     *
     * <p>{@code true} on the sync is what reopens it — resetting the data without that leaves a
     * player with no heritage and no way to pick one, which is worse than either end state.
     */
    @Override
    public void reset(ServerPlayer target, DevLog log) {
        HeritageAPI.clear(target, true);
        log.changed("heritage reset", "selection screen reopened");
    }
}
