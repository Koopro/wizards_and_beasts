package at.koopro.wizardsandbeasts.network.skill;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.skill.vocation.VocationManager;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.network.chat.Component;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server Vocation declaration, the counterpart to the selection screen.
 *
 * <p>Declaring used to be possible only through {@code /wandb player vocation set primary}, which a player
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
            // Declaring a vocation happens inside the vocation screen, so the result has to be a toast:
            // chat draws under an open screen and the player would see nothing either way.
            Component name = Component.literal(id.getPath().replace('_', ' '));
            String L = "vocation.wizards_and_beasts.commit.";
            switch (VocationManager.commit(player, id)) {
                case OK -> PlayerFeedback.toast(player, NoticeKind.UNLOCK, name,
                        Component.translatable(L + "ok"));
                case MODULE_DISABLED -> PlayerFeedback.refuse(player,
                        Component.translatable(L + "refused"), Component.translatable(L + "module_disabled"));
                case UNKNOWN_VOCATION -> PlayerFeedback.refuse(player,
                        Component.translatable(L + "refused"), Component.translatable(L + "unknown"));
                case DARK_ARTS_DISABLED -> PlayerFeedback.refuse(player,
                        Component.translatable(L + "refused"), Component.translatable(L + "dark_arts_disabled"));
            }
        });
    }
}
