package at.koopro.wizardsandbeasts.settlement;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * A player has reached a settlement. Posts report this from their once-a-second look, so finding a place costs no
 * per-player tick: nothing runs for a player who is nowhere near one.
 */
@NullMarked
public final class SettlementDiscovery {

    private SettlementDiscovery() {}

    /** Records the first arrival and says the place's name once. Later visits are silent. */
    public static void arrive(ServerPlayer player, String site) {
        KnownPlaces known = player.getData(ModAttachments.KNOWN_PLACES.get());
        if (known.has(site)) {
            return;
        }
        player.setData(ModAttachments.KNOWN_PLACES.get(), known.with(site));
        // Diagon Alley and the Leaky Cauldron have names of their own; anything else is a hamlet with a rolled one.
        if (player.level().dimension() == at.koopro.wizardsandbeasts.diagon.DiagonAlley.LEVEL) {
            PlayerFeedback.toast(player, NoticeKind.DISCOVERY,
                    Component.translatable("settlement.wizards_and_beasts.diagon_alley.title"),
                    Component.translatable("settlement.wizards_and_beasts.diagon_alley.subtitle"));
            return;
        }
        if (at.koopro.wizardsandbeasts.diagon.DiagonAlleyEvents.inPub(player.level(), player.blockPosition())) {
            PlayerFeedback.toast(player, NoticeKind.DISCOVERY,
                    Component.translatable("settlement.wizards_and_beasts.leaky_cauldron.title"),
                    Component.translatable("settlement.wizards_and_beasts.leaky_cauldron.subtitle"));
            return;
        }
        SettlementData.Site data = SettlementData.get(player.level()).site(site);
        Component name = data == null ? Component.translatable("settlement.wizards_and_beasts.unnamed")
                : SettlementNames.ofSeed(data.seed());
        PlayerFeedback.toast(player, NoticeKind.DISCOVERY,
                Component.translatable("settlement.wizards_and_beasts.discovered.title"), name);
    }
}
