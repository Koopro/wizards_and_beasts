package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.type.WizSubtype;
import at.koopro.wizardsandbeasts.type.WizType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SpellNetworkGuards {

    private SpellNetworkGuards() {}

    public static boolean canUseWand(ServerPlayer player, PlayerSpellData data, String rejectReasonPrefix) {
        WizType type = player.getData(ModAttachments.TYPE_DATA.get()).getSelectedType();
        WizSubtype subtype = player.getData(ModAttachments.TYPE_DATA.get()).getSelectedSubtype();
        if (type == null || !type.canUseWand() || subtype == WizSubtype.SQUIB) {
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
