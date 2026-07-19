package at.koopro.wizardsandbeasts.network.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityTarget;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityTriggerHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client → server "fire" request. {@code quick = false} fires the armed selection (use key); {@code quick =
 * true} fires the pinned ability (quick key). {@code target} carries the client's crosshair pick for
 * targeted abilities ({@link AbilityTarget#NONE} otherwise) — a request, not a fact: the server resolves
 * which ability is armed and re-validates grant, module, cooldown and target via
 * {@link AbilityTriggerHandler#use}.
 */
@NullMarked
public record AbilityUseC2SPayload(boolean quick, AbilityTarget target) implements CustomPacketPayload {

    public static final Type<AbilityUseC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ability_use_c2s"));

    public static final StreamCodec<ByteBuf, AbilityUseC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AbilityUseC2SPayload decode(ByteBuf buf) {
            boolean quick = buf.readBoolean();
            return new AbilityUseC2SPayload(quick, AbilityTarget.STREAM_CODEC.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, AbilityUseC2SPayload payload) {
            buf.writeBoolean(payload.quick);
            AbilityTarget.STREAM_CODEC.encode(buf, payload.target);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AbilityTriggerHandler.use(player, quick, target);
            }
        });
    }
}
