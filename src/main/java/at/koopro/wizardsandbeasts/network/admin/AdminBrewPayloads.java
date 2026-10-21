package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.admin.access.AdminGate;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.brew.BrewAdminService;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.List;

import static at.koopro.wizardsandbeasts.network.PacketCodecUtils.writeList;
import static at.koopro.wizardsandbeasts.network.PacketCodecUtils.readList;

/**
 * The Brewing section's payloads: the brew list and one brew's page. Every value change — enabled, effects, heat
 * time, failure chance, tier, ingredient counts — is an ordinary {@link AdminChangeSettingC2SPayload} with a brew
 * setting id; nothing here carries a value to store. The server answers reads only for administrators with the
 * content capability, and drops the rest.
 */
@NullMarked
public final class AdminBrewPayloads {

    private static final int MAX_BREWS = 1024;
    private static final int MAX_LINES = 128;

    private AdminBrewPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    /**
     * One browser row.
     *
     * @param recipeId   the first recipe producing it, or "" when none does (it comes from elsewhere)
     * @param difficulty BASIC / STANDARD / ADVANCED / MASTER / UNBREWABLE, derived from tier, failure and catalyst
     * @param effects    mob effect ids it applies when drunk
     * @param overridden an administrator override is in force on it or its recipe
     */
    public record BrewSummary(String id, String nameKey, int color, boolean enabled, String recipeId, String tier,
                              int heatTicks, float failureChance, String difficulty, List<String> effects,
                              String effectText, boolean effectsEditable, boolean silver, boolean overridden) {

        static void write(ByteBuf buf, BrewSummary s) {
            PacketCodecUtils.writeString(buf, s.id);
            PacketCodecUtils.writeString(buf, s.nameKey);
            buf.writeInt(s.color);
            buf.writeBoolean(s.enabled);
            PacketCodecUtils.writeString(buf, s.recipeId);
            PacketCodecUtils.writeString(buf, s.tier);
            buf.writeInt(s.heatTicks);
            buf.writeFloat(s.failureChance);
            PacketCodecUtils.writeString(buf, s.difficulty);
            writeList(buf, s.effects, MAX_LINES, PacketCodecUtils::writeString);
            PacketCodecUtils.writeString(buf, s.effectText);
            buf.writeBoolean(s.effectsEditable);
            buf.writeBoolean(s.silver);
            buf.writeBoolean(s.overridden);
        }

        static BrewSummary read(ByteBuf buf) {
            return new BrewSummary(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readInt(),
                    buf.readBoolean(), PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readInt(),
                    buf.readFloat(), PacketCodecUtils.readString(buf),
                    readList(buf, MAX_LINES, "admin-brew-effects", PacketCodecUtils::readString),
                    PacketCodecUtils.readString(buf), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }
    }

    private static boolean authorised(ServerPlayer player, String what) {
        return AdminGate.admit(player, BrewAdminService::authorised, what);
    }

    /** Server side: the list, when the sender may read it. */
    public static boolean sendList(ServerPlayer player) {
        if (!authorised(player, "brew list")) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new ListReply(BrewAdminService.list()));
        return true;
    }

    /** Server side: one brew's page, when the sender may read it and the brew exists. */
    public static boolean sendDetail(ServerPlayer player, String brewId) {
        if (!authorised(player, "brew detail")) {
            return false;
        }
        BrewAdminService.Detail detail = BrewAdminService.detail(brewId, AdminContext.of(player));
        if (detail == null) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new DetailReply(detail));
        return true;
    }

    // ── client → server ──

    public record ListRequest() implements CustomPacketPayload {
        public static final ListRequest INSTANCE = new ListRequest();
        public static final Type<ListRequest> TYPE = new Type<>(id("admin_brew_list_request"));
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

    public record DetailRequest(String brewId) implements CustomPacketPayload {
        public static final Type<DetailRequest> TYPE = new Type<>(id("admin_brew_detail_request"));
        public static final StreamCodec<ByteBuf, DetailRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> PacketCodecUtils.writeString(buf, p.brewId),
                buf -> new DetailRequest(PacketCodecUtils.readString(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(DetailRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    sendDetail(player, payload.brewId);
                }
            });
        }
    }

    // ── server → client ──

    public record ListReply(List<BrewSummary> brews) implements CustomPacketPayload {
        public static final Type<ListReply> TYPE = new Type<>(id("admin_brew_list"));
        public static final StreamCodec<ByteBuf, ListReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> writeList(buf, p.brews, MAX_BREWS, BrewSummary::write),
                buf -> new ListReply(readList(buf, MAX_BREWS, "admin-brews", BrewSummary::read)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ListReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onBrewList(payload));
        }
    }

    /** One brew's page: its row, recipe facts, components, and its editable values as ordinary setting descriptors. */
    public record DetailReply(BrewAdminService.Detail detail) implements CustomPacketPayload {
        public static final Type<DetailReply> TYPE = new Type<>(id("admin_brew_detail"));
        public static final StreamCodec<ByteBuf, DetailReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    BrewSummary.write(buf, p.detail.summary());
                    writeList(buf, p.detail.recipe(), MAX_LINES, AdminSpellFact::write);
                    writeList(buf, p.detail.components(), MAX_LINES, PacketCodecUtils::writeString);
                    writeList(buf, p.detail.settings(), MAX_LINES, AdminSettingDescriptor::write);
                },
                buf -> new DetailReply(new BrewAdminService.Detail(BrewSummary.read(buf),
                        readList(buf, MAX_LINES, "admin-brew-recipe", AdminSpellFact::read),
                        readList(buf, MAX_LINES, "admin-brew-components", PacketCodecUtils::readString),
                        readList(buf, MAX_LINES, "admin-brew-settings", AdminSettingDescriptor::read))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(DetailReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onBrewDetail(payload));
        }
    }
}
