package at.koopro.wizardsandbeasts.network.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.gui.OllivanderTrialMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncTrialResonancePayload(int containerId, float s0, float s1, float s2) implements CustomPacketPayload {

    public static final Type<SyncTrialResonancePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sync_trial_resonance"));

    public static final StreamCodec<ByteBuf, SyncTrialResonancePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncTrialResonancePayload::containerId,
            ByteBufCodecs.FLOAT, SyncTrialResonancePayload::s0,
            ByteBufCodecs.FLOAT, SyncTrialResonancePayload::s1,
            ByteBufCodecs.FLOAT, SyncTrialResonancePayload::s2,
            SyncTrialResonancePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncTrialResonancePayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player.containerMenu instanceof OllivanderTrialMenu menu && menu.containerId == pkt.containerId) {
                menu.applySyncedScores(pkt.s0, pkt.s1, pkt.s2);
            }
        });
    }
}
