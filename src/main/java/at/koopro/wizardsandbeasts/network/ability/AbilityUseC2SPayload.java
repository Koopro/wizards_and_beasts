package at.koopro.wizardsandbeasts.network.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
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
 * Client → server "fire" request. {@code slot} is {@link AbilitySelectionState#SLOT_SELECTED} for the use
 * key (fires the wheel's armed selection) or a quick-slot index for one of the quick keys. {@code target}
 * carries the client's crosshair pick for targeted abilities ({@link AbilityTarget#NONE} otherwise) — a
 * request, not a fact: the server resolves which ability sits in that slot and re-validates grant, module,
 * cooldown and target via {@link AbilityTriggerHandler#use}.
 */
@NullMarked
public record AbilityUseC2SPayload(int slot, AbilityTarget target) implements CustomPacketPayload {

    public static final Type<AbilityUseC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ability_use_c2s"));

    public static final StreamCodec<ByteBuf, AbilityUseC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AbilityUseC2SPayload decode(ByteBuf buf) {
            int slot = buf.readByte();
            // Anything that is not a real quick slot collapses to "the armed selection".
            int safeSlot = AbilitySelectionState.isValidSlot(slot) ? slot : AbilitySelectionState.SLOT_SELECTED;
            return new AbilityUseC2SPayload(safeSlot, AbilityTarget.STREAM_CODEC.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, AbilityUseC2SPayload payload) {
            buf.writeByte(payload.slot);
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
                AbilityTriggerHandler.use(player, slot, target);
            }
        });
    }
}
