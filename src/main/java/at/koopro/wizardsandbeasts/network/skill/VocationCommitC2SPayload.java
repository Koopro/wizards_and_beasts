package at.koopro.wizardsandbeasts.network.skill;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.skill.vocation.VocationManager;
import at.koopro.wizardsandbeasts.util.ChatHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server Vocation declaration, the counterpart to the selection screen.
 *
 * <p>Declaring used to be possible only through {@code /wandb skill vocation set primary}, which a player
 * on a world with cheats disabled cannot type at all. Validation is not duplicated here: it belongs to
 * {@link VocationManager#commit}, which this only forwards to.
 */
public record VocationCommitC2SPayload(String vocationId) implements CustomPacketPayload {

    public static final Type<VocationCommitC2SPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "vocation_commit"));

    public static final StreamCodec<ByteBuf, VocationCommitC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VocationCommitC2SPayload decode(ByteBuf buf) {
            return new VocationCommitC2SPayload(PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf)));
        }

        @Override
        public void encode(ByteBuf buf, VocationCommitC2SPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.vocationId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VocationCommitC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            Identifier id = Identifier.tryParse(pkt.vocationId);
            if (id == null) {
                return;
            }
            VocationManager.CommitResult result = VocationManager.commit(player, id);
            switch (result) {
                case OK -> ChatHelper.sendSuccess(player,
                        "Vocation declared: " + id.getPath().replace('_', ' '));
                case MODULE_DISABLED -> ChatHelper.sendError(player, "Skill trees are disabled.");
                case UNKNOWN_VOCATION -> ChatHelper.sendError(player, "Unknown vocation.");
                case DARK_ARTS_DISABLED -> ChatHelper.sendError(player, "The Dark Arts are sealed away.");
            }
        });
    }
}
