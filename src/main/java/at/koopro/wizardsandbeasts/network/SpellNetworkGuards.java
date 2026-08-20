package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.spell.SpellDeniedS2CPayload;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The two guards every wand-spell packet passes before its own logic runs.
 *
 * <p>Both refusals travel the same way as every other one in the cast pipeline: the counter ticks,
 * a {@link SpellDeniedS2CPayload} carries the <em>code</em>, and the client resolves it to a lang key,
 * plays the deny sound and picks the channel. They used to call {@code displayClientMessage} with a
 * hardcoded English string instead, which made them the only refusals in the system that were
 * untranslatable and silent — no deny sound, because no packet was ever sent.
 *
 * <p>The stored code keeps its caller prefix ({@code cast_}, {@code assign_}, {@code select_},
 * {@code leviosa_adjust_}) so telemetry can still tell which packet was refused;
 * {@code SpellRejectCodes} matches these two by suffix for exactly that reason.
 */
@NullMarked
public final class SpellNetworkGuards {

    private SpellNetworkGuards() {}

    public static boolean canUseWand(ServerPlayer player, PlayerSpellData data, String rejectReasonPrefix) {
        Heritage type = player.getData(ModAttachments.HERITAGE_DATA.get()).getSelectedHeritage();
        HeritageVariant subtype = player.getData(ModAttachments.HERITAGE_DATA.get()).getSelectedHeritageVariant();
        if (type == null || !type.canUseWand() || (subtype != null && subtype.hasTag("no_wand"))) {
            refuse(player, data, rejectReasonPrefix + SpellRejectCodes.SUFFIX_TYPE_CANNOT_USE_WAND);
            return false;
        }
        return true;
    }

    public static boolean isValidSlot(ServerPlayer player, PlayerSpellData data, int slotIndex, String rejectReasonPrefix) {
        if (slotIndex < 0 || slotIndex >= PlayerSpellData.LOADOUT_SIZE) {
            refuse(player, data, rejectReasonPrefix + SpellRejectCodes.SUFFIX_INVALID_SLOT);
            return false;
        }
        return true;
    }

    /** Record the refusal and hand the client the code to speak. Never writes player-facing text here. */
    private static void refuse(ServerPlayer player, PlayerSpellData data, String code) {
        data.incrementRejectReason(code);
        if (ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)) {
            SpellDeniedS2CPayload.sendTo(player, code);
        }
    }
}
