package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.wand.WandAdminService;
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
 * The Wands section's payloads: the catalog (woods, cores, pairings, presets and their rule settings), a generator
 * preview, and a test wand. Withdrawing a wood, core or pairing is an ordinary {@link AdminChangeSettingC2SPayload};
 * nothing here carries a value to store. A preview or test-wand request names ids and numbers only — the server
 * checks every one against its own registries, recipes and rules ({@link WandAdminService#problem}) and builds the
 * wand itself, so a client cannot name a pairing the server does not allow or hand itself a bonded wand.
 */
@NullMarked
public final class AdminWandPayloads {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_PARTS = 256;
    private static final int MAX_PAIRS = 4096;
    private static final int MAX_LINES = 64;

    private AdminWandPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    /**
     * One wood or core.
     *
     * @param name            its display name's translation key, or the literal name when it has none
     * @param nameTranslatable whether {@code name} is a key
     * @param lore            the canonical {@code _lore} note of its datapack file, as written ("" when none)
     * @param tint            the wood's appearance tint (ARGB); 0 for a core
     */
    public record PartInfo(String id, String name, boolean nameTranslatable, String lore, List<AdminSpellFact> facts,
                           int tint) {

        static void write(ByteBuf buf, PartInfo p) {
            PacketCodecUtils.writeString(buf, p.id);
            PacketCodecUtils.writeString(buf, p.name);
            buf.writeBoolean(p.nameTranslatable);
            PacketCodecUtils.writeString(buf, p.lore);
            writeList(buf, p.facts, MAX_LINES, AdminSpellFact::write);
            buf.writeInt(p.tint);
        }

        static PartInfo read(ByteBuf buf) {
            return new PartInfo(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readBoolean(),
                    PacketCodecUtils.readString(buf), readList(buf, MAX_LINES, "admin-wand-facts", AdminSpellFact::read),
                    buf.readInt());
        }
    }

    /** A generator preview: whether the server would make it, the reason key, and the resolved numbers when it would. */
    public record Preview(boolean valid, String messageKey, List<AdminSpellFact> facts) {

        static void write(ByteBuf buf, Preview p) {
            buf.writeBoolean(p.valid);
            PacketCodecUtils.writeString(buf, p.messageKey);
            writeList(buf, p.facts, MAX_LINES, AdminSpellFact::write);
        }

        static Preview read(ByteBuf buf) {
            return new Preview(buf.readBoolean(), PacketCodecUtils.readString(buf),
                    readList(buf, MAX_LINES, "admin-wand-preview", AdminSpellFact::read));
        }
    }

    static void writeRequest(ByteBuf buf, WandAdminService.Request r) {
        PacketCodecUtils.writeString(buf, r.wood());
        PacketCodecUtils.writeString(buf, r.core());
        buf.writeFloat(r.lengthInches());
        PacketCodecUtils.writeString(buf, r.flexibility());
        PacketCodecUtils.writeString(buf, r.preset());
    }

    static WandAdminService.Request readRequest(ByteBuf buf) {
        return new WandAdminService.Request(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                buf.readFloat(), PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf));
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

    private static boolean authorised(ServerPlayer player, String what) {
        if (WandAdminService.authorised(AdminContext.of(player))) {
            return true;
        }
        LOGGER.warn("[Admin] Dropped {} request from unauthorised {} ({})", what, player.getName().getString(), player.getUUID());
        return false;
    }

    /** Server side: the catalog, when the sender may read it. */
    public static boolean sendCatalog(ServerPlayer player) {
        if (!authorised(player, "wand catalog")) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new CatalogReply(
                WandAdminService.catalog(player.level().getServer(), AdminContext.of(player))));
        return true;
    }

    // ── client → server ──

    public record CatalogRequest() implements CustomPacketPayload {
        public static final CatalogRequest INSTANCE = new CatalogRequest();
        public static final Type<CatalogRequest> TYPE = new Type<>(id("admin_wand_catalog_request"));
        public static final StreamCodec<ByteBuf, CatalogRequest> STREAM_CODEC = PacketCodecUtils.noPayloadCodec(() -> INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(CatalogRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    sendCatalog(player);
                }
            });
        }
    }

    public record PreviewRequest(WandAdminService.Request request) implements CustomPacketPayload {
        public static final Type<PreviewRequest> TYPE = new Type<>(id("admin_wand_preview_request"));
        public static final StreamCodec<ByteBuf, PreviewRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> writeRequest(buf, p.request), buf -> new PreviewRequest(readRequest(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(PreviewRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player && authorised(player, "wand preview")) {
                    PacketDistributor.sendToPlayer(player, new PreviewReply(payload.request,
                            WandAdminService.preview(player.level().getServer(), payload.request)));
                }
            });
        }
    }

    public record GiveRequest(WandAdminService.Request request) implements CustomPacketPayload {
        public static final Type<GiveRequest> TYPE = new Type<>(id("admin_wand_give_request"));
        public static final StreamCodec<ByteBuf, GiveRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> writeRequest(buf, p.request), buf -> new GiveRequest(readRequest(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(GiveRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    WandAdminService.Outcome outcome = WandAdminService.giveTestWand(player, payload.request);
                    PacketDistributor.sendToPlayer(player,
                            new ActionReply(outcome.success(), outcome.messageKey(), outcome.detail()));
                }
            });
        }
    }

    // ── server → client ──

    public record CatalogReply(WandAdminService.Catalog catalog) implements CustomPacketPayload {
        public static final Type<CatalogReply> TYPE = new Type<>(id("admin_wand_catalog"));
        public static final StreamCodec<ByteBuf, CatalogReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    writeList(buf, p.catalog.woods(), MAX_PARTS, PartInfo::write);
                    writeList(buf, p.catalog.cores(), MAX_PARTS, PartInfo::write);
                    writeList(buf, p.catalog.pairs(), MAX_PAIRS, PacketCodecUtils::writeString);
                    writeList(buf, p.catalog.presets(), MAX_PARTS, PacketCodecUtils::writeString);
                    writeList(buf, p.catalog.settings(), MAX_PAIRS, AdminSettingDescriptor::write);
                },
                buf -> new CatalogReply(new WandAdminService.Catalog(
                        readList(buf, MAX_PARTS, "admin-wand-woods", PartInfo::read),
                        readList(buf, MAX_PARTS, "admin-wand-cores", PartInfo::read),
                        readList(buf, MAX_PAIRS, "admin-wand-pairs", PacketCodecUtils::readString),
                        readList(buf, MAX_PARTS, "admin-wand-presets", PacketCodecUtils::readString),
                        readList(buf, MAX_PAIRS, "admin-wand-settings", AdminSettingDescriptor::read))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(CatalogReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onWandCatalog(payload));
        }
    }

    /** The answer to a preview, with the request it answers so a late reply for an older selection is ignored. */
    public record PreviewReply(WandAdminService.Request request, Preview preview) implements CustomPacketPayload {
        public static final Type<PreviewReply> TYPE = new Type<>(id("admin_wand_preview"));
        public static final StreamCodec<ByteBuf, PreviewReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    writeRequest(buf, p.request);
                    Preview.write(buf, p.preview);
                },
                buf -> new PreviewReply(readRequest(buf), Preview.read(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(PreviewReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onWandPreview(payload));
        }
    }

    public record ActionReply(boolean success, String messageKey, String detail) implements CustomPacketPayload {
        public static final Type<ActionReply> TYPE = new Type<>(id("admin_wand_action"));
        public static final StreamCodec<ByteBuf, ActionReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.success);
                    PacketCodecUtils.writeString(buf, p.messageKey);
                    PacketCodecUtils.writeString(buf, p.detail);
                },
                buf -> new ActionReply(buf.readBoolean(), PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ActionReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onWandAction(payload));
        }
    }
}
