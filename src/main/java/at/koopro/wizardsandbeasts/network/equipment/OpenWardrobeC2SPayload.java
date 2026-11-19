package at.koopro.wizardsandbeasts.network.equipment;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.equipment.curios.WardrobeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

/**
 * "Open my wardrobe." Carries nothing: the server opens a {@link WardrobeMenu} over the sender's own Curios slots and
 * nobody else's, so there is nothing a client could name to reach another player's wardrobe.
 */
@NullMarked
public record OpenWardrobeC2SPayload() implements CustomPacketPayload {

    public static final OpenWardrobeC2SPayload INSTANCE = new OpenWardrobeC2SPayload();

    public static final Type<OpenWardrobeC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "open_wardrobe"));

    public static final StreamCodec<ByteBuf, OpenWardrobeC2SPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(TYPE, STREAM_CODEC, OpenWardrobeC2SPayload::handle);
    }

    private static void handle(OpenWardrobeC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
                open(player);
            }
        });
    }

    /** Opens the wardrobe for {@code player}. Also the GameTests' and commands' door. */
    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new WardrobeMenu(id, inventory),
                Component.translatable("gui.wizards_and_beasts.wardrobe.title")));
    }
}
