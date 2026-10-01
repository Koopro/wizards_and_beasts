package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.spell.SpellTestService;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Magic section's payloads. Grouped in one file because each is a few lines of codec around a request the
 * server answers in {@link AdminSpellNetworkService}; none of them changes a value — edits travel as ordinary
 * {@link AdminChangeSettingC2SPayload}s with spell setting ids.
 */
@NullMarked
public final class AdminSpellPayloads {

    private static final int MAX_SPELLS = 1024;
    private static final int MAX_FACTS = 64;
    private static final int MAX_SETTINGS = 32;

    private AdminSpellPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    // ── client → server ──

    /** "Send me the spell browser's rows." */
    public record ListRequest() implements CustomPacketPayload {
        public static final ListRequest INSTANCE = new ListRequest();
        public static final Type<ListRequest> TYPE = new Type<>(id("admin_spell_list_request"));
        public static final StreamCodec<ByteBuf, ListRequest> STREAM_CODEC = PacketCodecUtils.noPayloadCodec(() -> INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ListRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    AdminSpellNetworkService.sendList(player);
                }
            });
        }
    }

    /** "Send me this spell's detail page." */
    public record DetailRequest(String spellId) implements CustomPacketPayload {
        public static final Type<DetailRequest> TYPE = new Type<>(id("admin_spell_detail_request"));
        public static final StreamCodec<ByteBuf, DetailRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> PacketCodecUtils.writeString(buf, p.spellId),
                buf -> new DetailRequest(PacketCodecUtils.readString(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(DetailRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    AdminSpellNetworkService.sendDetail(player, payload.spellId);
                }
            });
        }
    }

    public enum ResetScope { SPELL, CATEGORY }

    /**
     * "Reset every overridden value of this spell / this category." Each value is reset through
     * {@code AdminSettingService.reset}, exactly as if it had been reset one by one.
     *
     * @param target    a spell id, or a {@code SpellCategory} name
     * @param confirmed the administrator confirmed the batch in a dialog
     */
    public record ResetRequest(ResetScope scope, String target, boolean confirmed) implements CustomPacketPayload {
        public static final Type<ResetRequest> TYPE = new Type<>(id("admin_spell_reset"));
        public static final StreamCodec<ByteBuf, ResetRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.scope == ResetScope.CATEGORY);
                    PacketCodecUtils.writeString(buf, p.target);
                    buf.writeBoolean(p.confirmed);
                },
                buf -> new ResetRequest(buf.readBoolean() ? ResetScope.CATEGORY : ResetScope.SPELL,
                        PacketCodecUtils.readString(buf), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ResetRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    AdminSpellNetworkService.reset(player, payload);
                }
            });
        }
    }

    /**
     * "Test-cast this spell at a target of this kind." The client names a kind of target, never an entity; for
     * {@code PLAYER} it names a player, whom the server looks up and range-checks.
     */
    public record TestRequest(String spellId, SpellTestService.TargetMode mode, @Nullable UUID player)
            implements CustomPacketPayload {
        public static final Type<TestRequest> TYPE = new Type<>(id("admin_spell_test"));
        public static final StreamCodec<ByteBuf, TestRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    PacketCodecUtils.writeString(buf, p.spellId);
                    PacketCodecUtils.writeString(buf, p.mode.name());
                    buf.writeBoolean(p.player != null);
                    if (p.player != null) {
                        PacketCodecUtils.writeUUID(buf, p.player);
                    }
                },
                buf -> {
                    String spell = PacketCodecUtils.readString(buf);
                    SpellTestService.TargetMode mode = SpellTestService.TargetMode.byName(PacketCodecUtils.readString(buf));
                    UUID player = buf.readBoolean() ? PacketCodecUtils.readUUID(buf) : null;
                    return new TestRequest(spell, mode == null ? SpellTestService.TargetMode.SELF : mode, player);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(TestRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    AdminSpellNetworkService.test(player, payload);
                }
            });
        }
    }

    // ── server → client ──

    /** The spell browser's rows. */
    public record ListReply(List<AdminSpellSummary> spells) implements CustomPacketPayload {
        public static final Type<ListReply> TYPE = new Type<>(id("admin_spell_list"));
        public static final StreamCodec<ByteBuf, ListReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    int count = Math.min(p.spells.size(), MAX_SPELLS);
                    buf.writeInt(count);
                    for (int i = 0; i < count; i++) {
                        AdminSpellSummary.write(buf, p.spells.get(i));
                    }
                },
                buf -> {
                    int count = PacketCodecUtils.readBoundedCount(buf, MAX_SPELLS, "admin-spells");
                    List<AdminSpellSummary> spells = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        spells.add(AdminSpellSummary.read(buf));
                    }
                    return new ListReply(List.copyOf(spells));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ListReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onSpellList(payload));
        }
    }

    /** One spell's page: its row, its read-only facts, and its editable values as ordinary setting descriptors. */
    public record DetailReply(AdminSpellSummary summary, List<AdminSpellFact> facts,
                              List<AdminSettingDescriptor> settings) implements CustomPacketPayload {
        public static final Type<DetailReply> TYPE = new Type<>(id("admin_spell_detail"));
        public static final StreamCodec<ByteBuf, DetailReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    AdminSpellSummary.write(buf, p.summary);
                    int facts = Math.min(p.facts.size(), MAX_FACTS);
                    buf.writeInt(facts);
                    for (int i = 0; i < facts; i++) {
                        AdminSpellFact.write(buf, p.facts.get(i));
                    }
                    int settings = Math.min(p.settings.size(), MAX_SETTINGS);
                    buf.writeInt(settings);
                    for (int i = 0; i < settings; i++) {
                        AdminSettingDescriptor.write(buf, p.settings.get(i));
                    }
                },
                buf -> {
                    AdminSpellSummary summary = AdminSpellSummary.read(buf);
                    int factCount = PacketCodecUtils.readBoundedCount(buf, MAX_FACTS, "admin-spell-facts");
                    List<AdminSpellFact> facts = new ArrayList<>(factCount);
                    for (int i = 0; i < factCount; i++) {
                        facts.add(AdminSpellFact.read(buf));
                    }
                    int settingCount = PacketCodecUtils.readBoundedCount(buf, MAX_SETTINGS, "admin-spell-settings");
                    List<AdminSettingDescriptor> settings = new ArrayList<>(settingCount);
                    for (int i = 0; i < settingCount; i++) {
                        settings.add(AdminSettingDescriptor.read(buf));
                    }
                    return new DetailReply(summary, List.copyOf(facts), List.copyOf(settings));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(DetailReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onSpellDetail(payload));
        }
    }

    /** The outcome of a test cast or a batch reset, for the panel's status line. */
    public record ActionReply(boolean success, String messageKey, String detail) implements CustomPacketPayload {
        public static final Type<ActionReply> TYPE = new Type<>(id("admin_spell_action"));
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
            context.enqueueWork(() -> AdminClientHandlers.onSpellAction(payload));
        }
    }
}
