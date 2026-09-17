package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.spell.SpellDeniedS2CPayload;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.form.constraint.FormConstraint;
import at.koopro.wizardsandbeasts.form.constraint.FormConstraints;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
        String refusal = wandRefusal(player);
        if (refusal != null) {
            refuse(player, data, rejectReasonPrefix + refusal);
            return false;
        }
        return true;
    }

    /**
     * Why this player may not use a wand at all, as a reject-code suffix, or {@code null} when they may.
     *
     * <p>Side-effect free, so a check that runs every tick — the beam channel, which must refuse whatever
     * the release under it would — can ask without counting a refusal or sending a denial each time.
     */
    public static @Nullable String wandRefusal(ServerPlayer player) {
        if (!player.getData(ModAttachments.HERITAGE_DATA.get()).canUseWand()) {
            return SpellRejectCodes.SUFFIX_TYPE_CANNOT_USE_WAND;
        }
        // A beast holds no wand. Enforced here rather than in the cast pipeline on purpose: this guard
        // is the one thing every wand packet passes, so one check covers casting, assigning, selecting
        // and the Leviosa adjustment alike, and a spell added tomorrow is covered without being asked.
        //
        // Asked of the shared constraint layer rather than of the werewolf, which is what closed the
        // gap this used to have: the Animagus wall cancelled right-clicks but never touched the cast
        // packet, so a wizard in a cat's body could still cast from the spell wheel.
        if (FormConstraints.denies(player, FormConstraint.NO_SPELLCASTING)) {
            return SpellRejectCodes.SUFFIX_FERAL;
        }
        // A wand held by the Ministry after a hearing. Checked here for the same reason as the form wall: every
        // wand packet passes this guard, so no spell can be cast around the confiscation.
        if (at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace.wandConfiscated(player)) {
            return SpellRejectCodes.SUFFIX_WAND_CONFISCATED;
        }
        return null;
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
