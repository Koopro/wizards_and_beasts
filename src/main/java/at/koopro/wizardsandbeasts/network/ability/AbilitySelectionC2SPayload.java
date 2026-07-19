package at.koopro.wizardsandbeasts.network.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityTriggerHandler;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client → server request to change wheel state for a specific ability. The server re-validates everything
 * (grant, module, type) via {@link AbilityTriggerHandler}; the client-claimed target is never trusted.
 */
@NullMarked
public record AbilitySelectionC2SPayload(Action action, Identifier target) implements CustomPacketPayload {

    /** Wheel gestures: {@code CONFIRM} = arm/flip hovered, {@code PIN} = pin/unpin hovered, {@code TOGGLE} = flip. */
    public enum Action {
        CONFIRM,
        PIN,
        TOGGLE;

        private static final Action[] VALUES = values();

        static Action byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : CONFIRM;
        }
    }

    public static final Type<AbilitySelectionC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ability_selection_c2s"));

    public static final StreamCodec<ByteBuf, AbilitySelectionC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AbilitySelectionC2SPayload decode(ByteBuf buf) {
            Action action = Action.byId(buf.readByte());
            Identifier target = Identifier.parse(PacketCodecUtils.readString(buf));
            return new AbilitySelectionC2SPayload(action, target);
        }

        @Override
        public void encode(ByteBuf buf, AbilitySelectionC2SPayload payload) {
            buf.writeByte(payload.action.ordinal());
            PacketCodecUtils.writeString(buf, payload.target.toString());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                switch (action) {
                    case CONFIRM -> AbilityTriggerHandler.confirmSelection(player, target);
                    case PIN -> AbilityTriggerHandler.togglePin(player, target);
                    case TOGGLE -> AbilityTriggerHandler.toggle(player, target);
                }
            }
        });
    }
}
