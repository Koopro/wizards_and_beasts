package at.koopro.wizardsandbeasts.network.ministry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.law.MinistryFines;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCaseData;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;

/**
 * The player's own Ministry record, pushed to their client so the Character Sheet can show it.
 *
 * <p>Only ever sent to the subject: a criminal record is not public information, and broadcasting it to
 * trackers would let any client read every other player's notoriety. The Auror phase will need a separate,
 * deliberately coarser broadcast (a wanted <em>band</em>, not a file) for that.
 *
 * <p>Carries the two module flags with it rather than making the client resolve them: the sheet has to
 * distinguish "clean record" from "the Trace is switched off", and those read identically from the record
 * alone.
 *
 * @param record       the full server-side record
 * @param traceActive  whether {@code Module.MINISTRY} is on and offences are being recorded
 * @param finesActive  whether fines can be assessed at all (Ministry + Gringotts + non-zero scale)
 * @param underage     the Trace is on this wizard; decided on the server, whose clock and year length count
 * @param wandHeld     the Ministry holds this wizard's wand
 * @param caseStage    the open case's stage name, or empty when there is none
 */
public record MinistryRecordSyncS2CPayload(PlayerMinistryRecord record, boolean traceActive, boolean finesActive,
                                           boolean underage, boolean wandHeld, String caseStage)
        implements CustomPacketPayload {

    public static final Type<MinistryRecordSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ministry_record_sync"));

    // Reuses the record's own persistence codec rather than hand-rolling a field-by-field stream codec:
    // the record gains fields as enforcement lands, and a second serialiser would have to be remembered
    // and updated in lockstep with the first.
    public static final StreamCodec<ByteBuf, MinistryRecordSyncS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(PlayerMinistryRecord.CODEC), MinistryRecordSyncS2CPayload::record,
            ByteBufCodecs.BOOL, MinistryRecordSyncS2CPayload::traceActive,
            ByteBufCodecs.BOOL, MinistryRecordSyncS2CPayload::finesActive,
            ByteBufCodecs.BOOL, MinistryRecordSyncS2CPayload::underage,
            ByteBufCodecs.BOOL, MinistryRecordSyncS2CPayload::wandHeld,
            ByteBufCodecs.stringUtf8(32), MinistryRecordSyncS2CPayload::caseStage,
            MinistryRecordSyncS2CPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void syncToPlayer(@NonNull ServerPlayer player) {
        String caseStage = MinistryCaseData.get(player.level().getServer()).dossier(player.getUUID()).openCase()
                .map(open -> open.stage().getSerializedName()).orElse("");
        PacketDistributor.sendToPlayer(player, new MinistryRecordSyncS2CPayload(
                MinistryRecords.get(player), TraceService.isActive(), MinistryFines.isActive(),
                MinistryTrace.isUnderage(player), MinistryTrace.wandConfiscated(player), caseStage));
    }
}
