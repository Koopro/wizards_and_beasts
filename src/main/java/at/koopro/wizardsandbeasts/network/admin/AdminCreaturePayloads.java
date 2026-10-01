package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.creature.CreatureAdminService;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The Creatures section's payloads. Rule edits are not here — they are ordinary settings and travel as
 * {@link AdminChangeSettingC2SPayload}s. These cover reading the roster, one creature's page, and the two world
 * actions (spawn a test creature, clean up), which name a creature and a variant but never a position or an entity.
 */
@NullMarked
public final class AdminCreaturePayloads {

    private static final int MAX_CREATURES = 1024;
    private static final int MAX_FACTS = 64;
    private static final int MAX_ENTRIES = 256;

    private AdminCreaturePayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    // ── shared records ──

    /**
     * One browser row.
     *
     * @param temperament HOSTILE / NEUTRAL / PASSIVE, or "" when nothing records it (a bespoke creature)
     * @param category    the bestiary category, or "" when the creature has no bestiary entry
     * @param icon        the bestiary icon texture, or ""
     */
    public record CreatureSummary(String id, String nameKey, String category, boolean magical, String temperament,
                                  boolean flying, boolean tameable, boolean breedable, boolean naturalSpawnRule,
                                  int spawnEntries, int variants, String icon, boolean bespoke) {

        static void write(ByteBuf buf, CreatureSummary s) {
            PacketCodecUtils.writeString(buf, s.id);
            PacketCodecUtils.writeString(buf, s.nameKey);
            PacketCodecUtils.writeString(buf, s.category);
            buf.writeBoolean(s.magical);
            PacketCodecUtils.writeString(buf, s.temperament);
            buf.writeBoolean(s.flying);
            buf.writeBoolean(s.tameable);
            buf.writeBoolean(s.breedable);
            buf.writeBoolean(s.naturalSpawnRule);
            buf.writeInt(s.spawnEntries);
            buf.writeInt(s.variants);
            PacketCodecUtils.writeString(buf, s.icon);
            buf.writeBoolean(s.bespoke);
        }

        static CreatureSummary read(ByteBuf buf) {
            return new CreatureSummary(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                    PacketCodecUtils.readString(buf), buf.readBoolean(), PacketCodecUtils.readString(buf),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readInt(), buf.readInt(), PacketCodecUtils.readString(buf), buf.readBoolean());
        }
    }

    /** One {@code neoforge:add_spawns} entry naming the creature, as this server loaded it. */
    public record SpawnEntry(String biomes, int weight, int minCount, int maxCount, String source) {
        static void write(ByteBuf buf, SpawnEntry e) {
            PacketCodecUtils.writeString(buf, e.biomes);
            buf.writeInt(e.weight);
            buf.writeInt(e.minCount);
            buf.writeInt(e.maxCount);
            PacketCodecUtils.writeString(buf, e.source);
        }

        static SpawnEntry read(ByteBuf buf) {
            return new SpawnEntry(PacketCodecUtils.readString(buf), buf.readInt(), buf.readInt(), buf.readInt(),
                    PacketCodecUtils.readString(buf));
        }
    }

    /** One variant: id, texture ("" = the base texture), authored and effective weight, and whether it is rolled. */
    public record VariantInfo(String id, String texture, int authoredWeight, int weight, boolean enabled) {
        static void write(ByteBuf buf, VariantInfo v) {
            PacketCodecUtils.writeString(buf, v.id);
            PacketCodecUtils.writeString(buf, v.texture);
            buf.writeInt(v.authoredWeight);
            buf.writeInt(v.weight);
            buf.writeBoolean(v.enabled);
        }

        static VariantInfo read(ByteBuf buf) {
            return new VariantInfo(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readInt(),
                    buf.readInt(), buf.readBoolean());
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

    // ── client → server ──

    public record ListRequest() implements CustomPacketPayload {
        public static final ListRequest INSTANCE = new ListRequest();
        public static final Type<ListRequest> TYPE = new Type<>(id("admin_creature_list_request"));
        public static final StreamCodec<ByteBuf, ListRequest> STREAM_CODEC = PacketCodecUtils.noPayloadCodec(() -> INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ListRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    AdminCreatureNetworkService.sendList(player);
                }
            });
        }
    }

    public record DetailRequest(String creatureId) implements CustomPacketPayload {
        public static final Type<DetailRequest> TYPE = new Type<>(id("admin_creature_detail_request"));
        public static final StreamCodec<ByteBuf, DetailRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> PacketCodecUtils.writeString(buf, p.creatureId),
                buf -> new DetailRequest(PacketCodecUtils.readString(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(DetailRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    AdminCreatureNetworkService.sendDetail(player, payload.creatureId);
                }
            });
        }
    }

    /** "Spawn a test creature of this kind in front of me." {@code variant} may be empty (roll as usual). */
    public record SpawnRequest(String creatureId, String variant, boolean noAi) implements CustomPacketPayload {
        public static final Type<SpawnRequest> TYPE = new Type<>(id("admin_creature_spawn"));
        public static final StreamCodec<ByteBuf, SpawnRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    PacketCodecUtils.writeString(buf, p.creatureId);
                    PacketCodecUtils.writeString(buf, p.variant);
                    buf.writeBoolean(p.noAi);
                },
                buf -> new SpawnRequest(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(SpawnRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    AdminCreatureNetworkService.spawn(player, payload);
                }
            });
        }
    }

    /** "Remove my test creatures." */
    public record CleanupRequest() implements CustomPacketPayload {
        public static final CleanupRequest INSTANCE = new CleanupRequest();
        public static final Type<CleanupRequest> TYPE = new Type<>(id("admin_creature_cleanup"));
        public static final StreamCodec<ByteBuf, CleanupRequest> STREAM_CODEC = PacketCodecUtils.noPayloadCodec(() -> INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(CleanupRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    AdminCreatureNetworkService.cleanup(player);
                }
            });
        }
    }

    // ── server → client ──

    public record ListReply(List<CreatureSummary> creatures) implements CustomPacketPayload {
        public static final Type<ListReply> TYPE = new Type<>(id("admin_creature_list"));
        public static final StreamCodec<ByteBuf, ListReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> writeList(buf, p.creatures, MAX_CREATURES, CreatureSummary::write),
                buf -> new ListReply(readList(buf, MAX_CREATURES, "admin-creatures", CreatureSummary::read)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ListReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onCreatureList(payload));
        }
    }

    /**
     * One creature's page. Its rule settings are not repeated here: they are static settings, already in the
     * Control Center's snapshot, and the panel draws them from there.
     */
    public record DetailReply(CreatureAdminService.Detail detail) implements CustomPacketPayload {
        public static final Type<DetailReply> TYPE = new Type<>(id("admin_creature_detail"));
        public static final StreamCodec<ByteBuf, DetailReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    CreatureAdminService.Detail d = p.detail;
                    CreatureSummary.write(buf, d.summary());
                    writeList(buf, d.attributes(), MAX_FACTS, AdminSpellFact::write);
                    writeList(buf, d.behaviour(), MAX_FACTS, AdminSpellFact::write);
                    writeList(buf, d.abilities(), MAX_FACTS, PacketCodecUtils::writeString);
                    writeList(buf, d.spawns(), MAX_ENTRIES, SpawnEntry::write);
                    writeList(buf, d.conditions(), MAX_FACTS, PacketCodecUtils::writeString);
                    writeList(buf, d.variants(), MAX_FACTS, VariantInfo::write);
                },
                buf -> new DetailReply(new CreatureAdminService.Detail(
                        CreatureSummary.read(buf),
                        readList(buf, MAX_FACTS, "admin-creature-attributes", AdminSpellFact::read),
                        readList(buf, MAX_FACTS, "admin-creature-behaviour", AdminSpellFact::read),
                        readList(buf, MAX_FACTS, "admin-creature-abilities", PacketCodecUtils::readString),
                        readList(buf, MAX_ENTRIES, "admin-creature-spawns", SpawnEntry::read),
                        readList(buf, MAX_FACTS, "admin-creature-conditions", PacketCodecUtils::readString),
                        readList(buf, MAX_FACTS, "admin-creature-variants", VariantInfo::read))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(DetailReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onCreatureDetail(payload));
        }
    }

    /** The outcome of a test spawn or a cleanup, for the panel's status line. */
    public record ActionReply(boolean success, String messageKey, String detail) implements CustomPacketPayload {
        public static final Type<ActionReply> TYPE = new Type<>(id("admin_creature_action"));
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
            context.enqueueWork(() -> AdminClientHandlers.onCreatureAction(payload));
        }
    }
}
