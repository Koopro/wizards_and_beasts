package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Sent server-to-client whenever a cast attempt is blocked (cooldown, GCD, Gamp, fizzle).
 *
 * <p>{@code reason} is the stored reject code from {@link at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes},
 * possibly carrying a {@code :detail} suffix. It is <b>a code, not a sentence</b>: the client resolves it
 * to a lang key and renders it, which is what makes refusal text translatable and lets the client pick
 * the channel (spell HUD when it is up, action bar when it is not) without the server guessing.
 *
 * <p>Sending a reason is not a leak — every code describes the receiving player's own attempt.
 */
@NullMarked
public record SpellDeniedS2CPayload(String reason) implements CustomPacketPayload {

    public static final Type<SpellDeniedS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_denied"));

    public static final StreamCodec<ByteBuf, SpellDeniedS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SpellDeniedS2CPayload decode(ByteBuf buf) {
            return new SpellDeniedS2CPayload(PacketCodecUtils.readString(buf));
        }

        @Override
        public void encode(ByteBuf buf, SpellDeniedS2CPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.reason);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player, String reason) {
        PacketDistributor.sendToPlayer(player, new SpellDeniedS2CPayload(reason));
    }
}
