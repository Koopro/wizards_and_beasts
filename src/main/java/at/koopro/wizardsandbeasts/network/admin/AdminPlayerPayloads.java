package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.player.PlayerActionLog;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminAction;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Facet;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.FacetView;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Item;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Option;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.PlayerRow;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The Players section on the wire: search, one facet of one player, one action, the action log. Requests name a
 * player by UUID, an action by enum and a bounded argument; every reply is built on the server by
 * {@link PlayerAdminService} for the sender, after the sender's authority was checked. Read requests from a sender
 * without the players capability are dropped with a WARN and no reply; an action from one is refused (and answered,
 * so an administrator whose access was revoked sees why).
 */
@NullMarked
public final class AdminPlayerPayloads {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_ROWS = PlayerAdminService.MAX_PLAYERS;
    private static final int MAX_FACTS = 128;
    private static final int MAX_ITEMS = 1024;
    private static final int MAX_OPTIONS = 1024;
    private static final int MAX_LOG = 200;
    private static final int MAX_ARGUMENT = 128;
    private static final UUID NOBODY = new UUID(0L, 0L);

    private AdminPlayerPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    // ── list helpers ──

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

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static <E extends Enum<E>> E readEnum(ByteBuf buf, E[] values, String what) {
        int ordinal = buf.readByte();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException("unknown " + what + " " + ordinal);
        }
        return values[ordinal];
    }

    // ── wire records ──

    static void writeRow(ByteBuf buf, PlayerRow row) {
        PacketCodecUtils.writeUUID(buf, row.id());
        PacketCodecUtils.writeString(buf, clip(row.name(), 64));
        PacketCodecUtils.writeString(buf, clip(row.heritage(), 64));
        PacketCodecUtils.writeString(buf, clip(row.lineage(), 64));
        buf.writeFloat(row.health());
        buf.writeFloat(row.maxHealth());
        buf.writeInt(row.effects());
        PacketCodecUtils.writeString(buf, clip(row.wanted(), 32));
        PacketCodecUtils.writeString(buf, clip(row.location(), 160));
        PacketCodecUtils.writeString(buf, clip(row.gameMode(), 32));
    }

    static PlayerRow readRow(ByteBuf buf) {
        return new PlayerRow(PacketCodecUtils.readUUID(buf), PacketCodecUtils.readString(buf),
                PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readFloat(), buf.readFloat(),
                buf.readInt(), PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                PacketCodecUtils.readString(buf));
    }

    static void writeItem(ByteBuf buf, Item item) {
        PacketCodecUtils.writeString(buf, clip(item.id(), 256));
        PacketCodecUtils.writeString(buf, clip(item.label(), 256));
        buf.writeBoolean(item.labelKey());
        PacketCodecUtils.writeString(buf, clip(item.value(), 256));
        buf.writeBoolean(item.actionable());
    }

    static Item readItem(ByteBuf buf) {
        return new Item(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readBoolean(),
                PacketCodecUtils.readString(buf), buf.readBoolean());
    }

    static void writeOption(ByteBuf buf, Option option) {
        PacketCodecUtils.writeString(buf, clip(option.id(), 256));
        PacketCodecUtils.writeString(buf, clip(option.label(), 256));
        buf.writeBoolean(option.labelKey());
    }

    static Option readOption(ByteBuf buf) {
        return new Option(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readBoolean());
    }

    static void writeEntry(ByteBuf buf, PlayerActionLog.Entry entry) {
        buf.writeLong(entry.sequence());
        buf.writeLong(entry.timeMillis());
        PacketCodecUtils.writeString(buf, entry.adminName());
        PacketCodecUtils.writeUUID(buf, entry.playerId());
        PacketCodecUtils.writeString(buf, entry.playerName());
        PacketCodecUtils.writeString(buf, entry.action());
        PacketCodecUtils.writeString(buf, entry.argument());
        PacketCodecUtils.writeString(buf, entry.result());
        PacketCodecUtils.writeString(buf, entry.detail());
    }

    static PlayerActionLog.Entry readEntry(ByteBuf buf) {
        long sequence = buf.readLong();
        long time = buf.readLong();
        String admin = PacketCodecUtils.readString(buf);
        UUID player = PacketCodecUtils.readUUID(buf);
        return new PlayerActionLog.Entry(sequence, time, null, admin, player, PacketCodecUtils.readString(buf),
                PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                PacketCodecUtils.readString(buf));
    }

    // ── server side ──

    private static boolean admitted(ServerPlayer player, String what) {
        if (PlayerAdminService.authorised(AdminContext.of(player))) {
            return true;
        }
        LOGGER.warn("[Admin] Dropped player-admin {} request from unauthorised {} ({})", what,
                player.getName().getString(), player.getUUID());
        return false;
    }

    public static boolean sendSearch(ServerPlayer player, String query) {
        if (!admitted(player, "search")) {
            return false;
        }
        AdminContext actor = AdminContext.of(player);
        PacketDistributor.sendToPlayer(player, new SearchReply(
                PlayerAdminService.search(actor, player.level().getServer(), query),
                PlayerAdminService.maySeeLocation(actor), actor.canModify(AdminCapability.MONEY)));
        return true;
    }

    public static boolean sendFacet(ServerPlayer player, UUID target, Facet facet) {
        if (!admitted(player, "facet")) {
            return false;
        }
        FacetView view = PlayerAdminService.facet(AdminContext.of(player), player.level().getServer(), target, facet);
        PacketDistributor.sendToPlayer(player, view == null
                ? new FacetReply(new FacetView(facet, target, "", List.of(), List.of(), List.of()), false)
                : new FacetReply(view, true));
        return true;
    }

    public static boolean sendLog(ServerPlayer player, UUID target) {
        if (!admitted(player, "log")) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        PacketDistributor.sendToPlayer(player, new LogReply(target,
                PlayerActionLog.get(server).recent(target.equals(NOBODY) ? null : target, MAX_LOG)));
        return true;
    }

    /** Runs the action through the service and answers with its outcome. Never trusts anything but the ids. */
    public static PlayerAdminService.Outcome run(ServerPlayer player, ActionRequest request) {
        PlayerAdminService.Outcome outcome = PlayerAdminService.perform(AdminContext.of(player), player.level().getServer(),
                new PlayerAdminService.Request(request.player(), request.action(), request.argument(), request.amount(),
                        request.confirmed()));
        PacketDistributor.sendToPlayer(player, new ActionReply(request.player(), request.action(), outcome.success(),
                outcome.code(), clip(outcome.detail(), 256), outcome.logSequence()));
        return outcome;
    }

    // ── payloads ──

    public record SearchRequest(String query) implements CustomPacketPayload {
        public static final Type<SearchRequest> TYPE = new Type<>(id("admin_player_search"));
        public static final StreamCodec<ByteBuf, SearchRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> PacketCodecUtils.writeString(buf, clip(p.query, 64)),
                buf -> new SearchRequest(clip(PacketCodecUtils.readString(buf), 64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(SearchRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    sendSearch(player, payload.query);
                }
            });
        }
    }

    /** Online players, and what this viewer may see and do beyond reading. */
    public record SearchReply(List<PlayerRow> rows, boolean maySeeLocation, boolean mayChangeMoney)
            implements CustomPacketPayload {
        public static final Type<SearchReply> TYPE = new Type<>(id("admin_player_search_reply"));
        public static final StreamCodec<ByteBuf, SearchReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    writeList(buf, p.rows, MAX_ROWS, AdminPlayerPayloads::writeRow);
                    buf.writeBoolean(p.maySeeLocation);
                    buf.writeBoolean(p.mayChangeMoney);
                },
                buf -> new SearchReply(readList(buf, MAX_ROWS, "admin-player-rows", AdminPlayerPayloads::readRow),
                        buf.readBoolean(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(SearchReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onPlayerSearch(payload));
        }
    }

    public record FacetRequest(UUID player, Facet facet) implements CustomPacketPayload {
        public static final Type<FacetRequest> TYPE = new Type<>(id("admin_player_facet"));
        public static final StreamCodec<ByteBuf, FacetRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    PacketCodecUtils.writeUUID(buf, p.player);
                    buf.writeByte(p.facet.ordinal());
                },
                buf -> new FacetRequest(PacketCodecUtils.readUUID(buf), readEnum(buf, Facet.values(), "facet")));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(FacetRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    sendFacet(player, payload.player, payload.facet);
                }
            });
        }
    }

    /** One facet; {@code online} false when the player has left (the view is then empty). */
    public record FacetReply(FacetView view, boolean online) implements CustomPacketPayload {
        public static final Type<FacetReply> TYPE = new Type<>(id("admin_player_facet_reply"));
        public static final StreamCodec<ByteBuf, FacetReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.view.facet().ordinal());
                    PacketCodecUtils.writeUUID(buf, p.view.player());
                    PacketCodecUtils.writeString(buf, clip(p.view.playerName(), 64));
                    writeList(buf, p.view.facts(), MAX_FACTS, AdminSpellFact::write);
                    writeList(buf, p.view.items(), MAX_ITEMS, AdminPlayerPayloads::writeItem);
                    writeList(buf, p.view.options(), MAX_OPTIONS, AdminPlayerPayloads::writeOption);
                    buf.writeBoolean(p.online);
                },
                buf -> {
                    Facet facet = readEnum(buf, Facet.values(), "facet");
                    UUID player = PacketCodecUtils.readUUID(buf);
                    String name = PacketCodecUtils.readString(buf);
                    List<AdminSpellFact> facts = readList(buf, MAX_FACTS, "admin-player-facts", AdminSpellFact::read);
                    List<Item> items = readList(buf, MAX_ITEMS, "admin-player-items", AdminPlayerPayloads::readItem);
                    List<Option> options = readList(buf, MAX_OPTIONS, "admin-player-options", AdminPlayerPayloads::readOption);
                    return new FacetReply(new FacetView(facet, player, name, facts, items, options), buf.readBoolean());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(FacetReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onPlayerFacet(payload));
        }
    }

    /**
     * One action. The client names the player, the action and a bounded argument; {@code confirmed} is set only after
     * the administrator confirmed in a dialog, and the server refuses a destructive action without it.
     */
    public record ActionRequest(UUID player, PlayerAdminAction action, String argument, long amount, boolean confirmed)
            implements CustomPacketPayload {
        public static final Type<ActionRequest> TYPE = new Type<>(id("admin_player_action"));
        public static final StreamCodec<ByteBuf, ActionRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    PacketCodecUtils.writeUUID(buf, p.player);
                    buf.writeByte(p.action.ordinal());
                    PacketCodecUtils.writeString(buf, clip(p.argument, MAX_ARGUMENT));
                    buf.writeLong(p.amount);
                    buf.writeBoolean(p.confirmed);
                },
                buf -> new ActionRequest(PacketCodecUtils.readUUID(buf),
                        readEnum(buf, PlayerAdminAction.values(), "player action"),
                        clip(PacketCodecUtils.readString(buf), MAX_ARGUMENT), buf.readLong(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ActionRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    run(player, payload);
                }
            });
        }
    }

    public record ActionReply(UUID player, PlayerAdminAction action, boolean success, String code, String detail,
                              long logSequence) implements CustomPacketPayload {
        public static final Type<ActionReply> TYPE = new Type<>(id("admin_player_action_reply"));
        public static final StreamCodec<ByteBuf, ActionReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    PacketCodecUtils.writeUUID(buf, p.player);
                    buf.writeByte(p.action.ordinal());
                    buf.writeBoolean(p.success);
                    PacketCodecUtils.writeString(buf, p.code);
                    PacketCodecUtils.writeString(buf, p.detail);
                    buf.writeLong(p.logSequence);
                },
                buf -> new ActionReply(PacketCodecUtils.readUUID(buf),
                        readEnum(buf, PlayerAdminAction.values(), "player action"), buf.readBoolean(),
                        PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ActionReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onPlayerAction(payload));
        }
    }

    /** The action log for one player; the all-zero UUID asks for everyone's. */
    public record LogRequest(UUID player) implements CustomPacketPayload {
        public static final Type<LogRequest> TYPE = new Type<>(id("admin_player_log"));
        public static final StreamCodec<ByteBuf, LogRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> PacketCodecUtils.writeUUID(buf, p.player),
                buf -> new LogRequest(PacketCodecUtils.readUUID(buf)));

        public static LogRequest everyone() {
            return new LogRequest(NOBODY);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(LogRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    sendLog(player, payload.player);
                }
            });
        }
    }

    public record LogReply(UUID player, List<PlayerActionLog.Entry> entries) implements CustomPacketPayload {
        public static final Type<LogReply> TYPE = new Type<>(id("admin_player_log_reply"));
        public static final StreamCodec<ByteBuf, LogReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    PacketCodecUtils.writeUUID(buf, p.player);
                    writeList(buf, p.entries, MAX_LOG, AdminPlayerPayloads::writeEntry);
                },
                buf -> new LogReply(PacketCodecUtils.readUUID(buf),
                        readList(buf, MAX_LOG, "admin-player-log", AdminPlayerPayloads::readEntry)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(LogReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onPlayerLog(payload));
        }
    }
}
