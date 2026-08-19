package at.koopro.wizardsandbeasts.network.feedback;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Server -> Client: show a toast.
 *
 * <p>Carries rendered {@link Component}s rather than a lang key plus arguments, so a call site can pass
 * either a translatable or a literal without the payload having to model the difference. Most of the
 * messages being migrated are literals today.
 */
@NullMarked
public record NotifyS2CPayload(NoticeKind kind, Component title, @Nullable Component body)
        implements CustomPacketPayload {

    public static final Type<NotifyS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "notify"));

    public static final StreamCodec<ByteBuf, NotifyS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        // TRUSTED_CONTEXT_FREE_STREAM_CODEC is the only Component codec typed StreamCodec<ByteBuf, _>.
        // The other four in ComponentSerialization need a RegistryFriendlyByteBuf, which no payload in
        // this mod uses; picking one of those would force the whole registrar onto a different buffer.
        private final StreamCodec<ByteBuf, Component> component =
                ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC;

        @Override
        public NotifyS2CPayload decode(ByteBuf buf) {
            NoticeKind kind = NoticeKind.byName(PacketCodecUtils.readString(buf));
            Component title = component.decode(buf);
            Component body = buf.readBoolean() ? component.decode(buf) : null;
            return new NotifyS2CPayload(kind, title, body);
        }

        @Override
        public void encode(ByteBuf buf, NotifyS2CPayload payload) {
            PacketCodecUtils.writeString(buf, payload.kind.serializedName());
            component.encode(buf, payload.title);
            buf.writeBoolean(payload.body != null);
            if (payload.body != null) {
                component.encode(buf, payload.body);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, NoticeKind kind, Component title, @Nullable Component body) {
        PacketDistributor.sendToPlayer(player, new NotifyS2CPayload(kind, title, body));
    }
}
