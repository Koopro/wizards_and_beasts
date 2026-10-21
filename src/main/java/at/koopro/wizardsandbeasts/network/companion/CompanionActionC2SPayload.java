package at.koopro.wizardsandbeasts.network.companion;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.companion.CompanionCommand;
import at.koopro.wizardsandbeasts.creature.companion.Companions;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * C→S: a button on the Beasts tab — an order, or letting a companion go.
 *
 * <p>Names a creature and an intent. The server finds the creature itself, checks the sender is its owner, and
 * lets the companion layer decide whether it obeys ({@link Companions#command}); a client cannot order someone
 * else's creature, one out of range, or one that has not learned the command.
 */
@NullMarked
public record CompanionActionC2SPayload(UUID companion, Action action, CompanionCommand command)
        implements CustomPacketPayload {

    public enum Action { COMMAND, RELEASE, REFRESH }

    public static final Type<CompanionActionC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "companion_action"));

    private static final long MIN_TICKS_BETWEEN = 4;
    private static final PlayerScopedState<Long> LAST = PlayerScopedState.create("companion_action");

    public static final StreamCodec<ByteBuf, CompanionActionC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CompanionActionC2SPayload decode(ByteBuf buf) {
            UUID id = PacketCodecUtils.readUUID(buf);
            int a = buf.readByte();
            int c = buf.readByte();
            Action[] actions = Action.values();
            CompanionCommand[] commands = CompanionCommand.values();
            return new CompanionActionC2SPayload(id,
                    a >= 0 && a < actions.length ? actions[a] : Action.REFRESH,
                    c >= 0 && c < commands.length ? commands[c] : CompanionCommand.FOLLOW);
        }

        @Override
        public void encode(ByteBuf buf, CompanionActionC2SPayload payload) {
            PacketCodecUtils.writeUUID(buf, payload.companion());
            buf.writeByte(payload.action().ordinal());
            buf.writeByte(payload.command().ordinal());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CompanionActionC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            long now = player.level().getGameTime();
            Long last = LAST.get(player);
            if (last != null && now >= last && now - last < MIN_TICKS_BETWEEN) {
                return;
            }
            LAST.put(player, now);
            if (payload.action() == Action.REFRESH) {
                CompanionRosterS2CPayload.send(player);
                return;
            }
            BondableBeast beast = Companions.find(player, payload.companion());
            if (beast == null) {
                PlayerFeedback.actionBar(player, Component.translatable("companion.wizards_and_beasts.not_here"));
                return;
            }
            switch (payload.action()) {
                case COMMAND -> Companions.command(player, beast, payload.command());
                case RELEASE -> Companions.release(player, beast);
                default -> { }
            }
        });
    }
}
