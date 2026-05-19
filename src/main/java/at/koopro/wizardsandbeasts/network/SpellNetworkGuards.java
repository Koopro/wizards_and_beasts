package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SpellNetworkGuards {

    private SpellNetworkGuards() {}

    public static boolean canUseWand(ServerPlayer player, PlayerSpellData data, String rejectReasonPrefix) {
        Heritage type = player.getData(ModAttachments.HERITAGE_DATA.get()).getSelectedHeritage();
        HeritageVariant subtype = player.getData(ModAttachments.HERITAGE_DATA.get()).getSelectedHeritageVariant();
        if (type == null || !type.canUseWand() || (subtype != null && subtype.hasTag("no_wand"))) {
            data.incrementRejectReason(rejectReasonPrefix + "_type_cannot_use_wand");
            player.displayClientMessage(Component.literal("\u00A7cYour type cannot use wand spells."), true);
            return false;
        }
        return true;
    }

    public static boolean isValidSlot(ServerPlayer player, PlayerSpellData data, int slotIndex, String rejectReasonPrefix) {
        if (slotIndex < 0 || slotIndex >= PlayerSpellData.LOADOUT_SIZE) {
            data.incrementRejectReason(rejectReasonPrefix + "_invalid_slot");
            player.displayClientMessage(Component.literal("\u00A7cInvalid spell slot."), true);
            return false;
        }
        return true;
    }
}
