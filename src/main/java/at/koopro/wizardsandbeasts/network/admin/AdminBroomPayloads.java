package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.broom.BroomAdminService;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The Travel section's broom roster. Every value change — enabled, each flight stat — is an ordinary
 * {@link AdminChangeSettingC2SPayload} with a broom setting id. The server answers only administrators with the content
 * capability.
 */
@NullMarked
public final class AdminBroomPayloads {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_BROOMS = 256;
    private static final int MAX_LINES = 64;

    private AdminBroomPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    /**
     * One broom.
     *
     * @param authored  its {@code BroomStat} values as its datapack file defines them, in {@code BroomStat} order
     * @param effective the same values as the broom flies: overrides and the server's speed scale applied
     * @param tint      the wood tint (ARGB), or 0 for an untinted broom
     */
    public record BroomSummary(String id, String nameKey, String tier, boolean enabled, boolean overridden, int tint,
                               List<Float> authored, List<Float> effective, List<AdminSpellFact> facts) {

        static void write(ByteBuf buf, BroomSummary s) {
            PacketCodecUtils.writeString(buf, s.id);
            PacketCodecUtils.writeString(buf, s.nameKey);
            PacketCodecUtils.writeString(buf, s.tier);
            buf.writeBoolean(s.enabled);
            buf.writeBoolean(s.overridden);
            buf.writeInt(s.tint);
            writeList(buf, s.authored, MAX_LINES, ByteBuf::writeFloat);
            writeList(buf, s.effective, MAX_LINES, ByteBuf::writeFloat);
            writeList(buf, s.facts, MAX_LINES, AdminSpellFact::write);
        }

        static BroomSummary read(ByteBuf buf) {
            return new BroomSummary(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                    PacketCodecUtils.readString(buf), buf.readBoolean(), buf.readBoolean(), buf.readInt(),
                    readList(buf, MAX_LINES, "admin-broom-authored", ByteBuf::readFloat),
                    readList(buf, MAX_LINES, "admin-broom-effective", ByteBuf::readFloat),
                    readList(buf, MAX_LINES, "admin-broom-facts", AdminSpellFact::read));
        }
    }

    private static <T> void writeList(ByteBuf buf, List<T> list, int max, BiConsumer<ByteBuf, T> writer) {
        int count = Math.min(list.size(), max);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            writer.accept(buf, list.get(i));
        }
    }

    private static <T> List<T> readList(ByteBuf buf, int max, String what, Function<ByteBuf, T> reader) {
        int count = PacketCodecUtils.readBoundedCount(buf, max, what);
        List<T> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(reader.apply(buf));
        }
        return List.copyOf(out);
    }

    /** Server side: the roster, when the sender may read it. */
    public static boolean sendList(ServerPlayer player) {
        AdminContext actor = AdminContext.of(player);
        if (!BroomAdminService.authorised(actor)) {
            LOGGER.warn("[Admin] Dropped broom list request from unauthorised {} ({})",
                    player.getName().getString(), player.getUUID());
            return false;
        }
        PacketDistributor.sendToPlayer(player, new ListReply(BroomAdminService.list(actor)));
        return true;
    }

    public record ListRequest() implements CustomPacketPayload {
        public static final ListRequest INSTANCE = new ListRequest();
        public static final Type<ListRequest> TYPE = new Type<>(id("admin_broom_list_request"));
        public static final StreamCodec<ByteBuf, ListRequest> STREAM_CODEC = PacketCodecUtils.noPayloadCodec(() -> INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ListRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    sendList(player);
                }
            });
        }
    }

    public record ListReply(BroomAdminService.Listing listing) implements CustomPacketPayload {
        public static final Type<ListReply> TYPE = new Type<>(id("admin_broom_list"));
        public static final StreamCodec<ByteBuf, ListReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    writeList(buf, p.listing.brooms(), MAX_BROOMS, BroomSummary::write);
                    writeList(buf, p.listing.settings(), MAX_BROOMS * 12, AdminSettingDescriptor::write);
                },
                buf -> new ListReply(new BroomAdminService.Listing(
                        readList(buf, MAX_BROOMS, "admin-brooms", BroomSummary::read),
                        readList(buf, MAX_BROOMS * 12, "admin-broom-settings", AdminSettingDescriptor::read))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ListReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onBroomList(payload));
        }
    }
}
