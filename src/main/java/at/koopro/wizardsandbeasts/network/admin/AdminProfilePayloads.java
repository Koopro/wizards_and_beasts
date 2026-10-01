package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminResult;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.history.AdminChangeRecord;
import at.koopro.wizardsandbeasts.admin.profile.ProfileCodec;
import at.koopro.wizardsandbeasts.admin.profile.ProfileDocument;
import at.koopro.wizardsandbeasts.admin.profile.ProfileService;
import at.koopro.wizardsandbeasts.admin.profile.ProfileValidator;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
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
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The Profiles section: the profile and snapshot library, the change history, the importable files, a profile's
 * preview, and one action request for everything that changes something. Every action runs through
 * {@link ProfileService} (or {@code AdminSettingService#revert}) exactly as {@code /wandb admin profile} does, so
 * validation, all-or-nothing apply and history are identical; the client only ever sees results.
 *
 * <p>Read requests from a sender without the config capability are dropped with a WARN and no reply. An action
 * that applies, deletes or reverts must carry {@code confirmed}: the client asks in a dialog, the server refuses
 * an unconfirmed one.
 */
@NullMarked
public final class AdminProfilePayloads {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_PROFILES = 160;
    private static final int MAX_FILES = 200;
    private static final int MAX_HISTORY = 200;
    private static final int MAX_CHANGES = 4_000;
    private static final int MAX_ISSUES = 400;
    /** Values on the wire are for reading, not for editing: long ones are cut. */
    private static final int MAX_SHOWN_VALUE = 160;

    private AdminProfilePayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    /** What an action does. Applying, deleting and reverting need {@code confirmed}. */
    public enum Op {
        APPLY(true), SAVE_AS(false), SNAPSHOT(false), DUPLICATE(false), RENAME(false), DELETE(true), EXPORT(false),
        IMPORT(false), REVERT_ONE(true), REVERT_GROUP(true);

        private final boolean needsConfirmation;

        Op(boolean needsConfirmation) {
            this.needsConfirmation = needsConfirmation;
        }

        public boolean needsConfirmation() {
            return needsConfirmation;
        }
    }

    // ── wire records ──

    /** One stored or shipped profile. {@code size}: how many values it names. */
    public record ProfileRow(String id, String name, String description, String author, ProfileDocument.Kind kind,
                             long createdMillis, int size) {

        static ProfileRow of(ProfileDocument document) {
            ProfileDocument.Meta meta = document.meta();
            return new ProfileRow(meta.id(), meta.name(), clip(meta.description(), 512), meta.author(), meta.kind(),
                    meta.createdMillis(), document.size());
        }

        static void write(ByteBuf buf, ProfileRow row) {
            PacketCodecUtils.writeString(buf, row.id);
            PacketCodecUtils.writeString(buf, row.name);
            PacketCodecUtils.writeString(buf, row.description);
            PacketCodecUtils.writeString(buf, row.author);
            buf.writeByte(row.kind.ordinal());
            buf.writeLong(row.createdMillis);
            buf.writeInt(row.size);
        }

        static ProfileRow read(ByteBuf buf) {
            String id = PacketCodecUtils.readString(buf);
            String name = PacketCodecUtils.readString(buf);
            String description = PacketCodecUtils.readString(buf);
            String author = PacketCodecUtils.readString(buf);
            return new ProfileRow(id, name, description, author, AdminProfilePayloads.kind(buf.readByte()), buf.readLong(), buf.readInt());
        }
    }

    /**
     * One history record, newest first on the wire. {@code nameKey} is empty when the setting no longer exists;
     * {@code revertible}: applied and not yet undone, so "revert this change" may be offered.
     */
    public record HistoryRow(long sequence, String settingId, String nameKey, AdminChangeRecord.Kind kind,
                             String oldValue, String newValue, String actor, long timestampMillis, boolean applied,
                             String rejection, boolean undone, String group) {

        public boolean revertible() {
            return applied && !undone;
        }

        static HistoryRow of(AdminChangeRecord record) {
            AdminSetting<?> setting = AdminSettings.service().registry().get(record.settingId());
            return new HistoryRow(record.sequence(), record.settingId().toString(), setting == null ? "" : setting.nameKey(),
                    record.kind(), clip(record.oldValue(), MAX_SHOWN_VALUE), clip(record.newValue(), MAX_SHOWN_VALUE),
                    clip(record.actorName(), 64), record.timestampMillis(), record.applied(),
                    record.rejection() == null ? "" : record.rejection().name(), record.undone(),
                    record.group() == null ? "" : clip(record.group(), 256));
        }

        static void write(ByteBuf buf, HistoryRow row) {
            buf.writeLong(row.sequence);
            PacketCodecUtils.writeString(buf, row.settingId);
            PacketCodecUtils.writeString(buf, row.nameKey);
            buf.writeByte(row.kind.ordinal());
            PacketCodecUtils.writeString(buf, row.oldValue);
            PacketCodecUtils.writeString(buf, row.newValue);
            PacketCodecUtils.writeString(buf, row.actor);
            buf.writeLong(row.timestampMillis);
            buf.writeBoolean(row.applied);
            PacketCodecUtils.writeString(buf, row.rejection);
            buf.writeBoolean(row.undone);
            PacketCodecUtils.writeString(buf, row.group);
        }

        static HistoryRow read(ByteBuf buf) {
            long sequence = buf.readLong();
            String settingId = PacketCodecUtils.readString(buf);
            String nameKey = PacketCodecUtils.readString(buf);
            int kindOrdinal = buf.readByte();
            AdminChangeRecord.Kind[] kinds = AdminChangeRecord.Kind.values();
            AdminChangeRecord.Kind kind = kindOrdinal >= 0 && kindOrdinal < kinds.length ? kinds[kindOrdinal]
                    : AdminChangeRecord.Kind.CHANGE;
            return new HistoryRow(sequence, settingId, nameKey, kind, PacketCodecUtils.readString(buf),
                    PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readLong(), buf.readBoolean(),
                    PacketCodecUtils.readString(buf), buf.readBoolean(), PacketCodecUtils.readString(buf));
        }
    }

    /** One problem with a file, a profile or an action. Mirrors {@link ProfileCodec.Issue}. */
    public record IssueRow(String code, String subject, String detail) {

        static IssueRow of(ProfileCodec.Issue issue) {
            return new IssueRow(clip(issue.code(), 64), clip(issue.subject(), 256), clip(issue.detail(), 256));
        }

        static void write(ByteBuf buf, IssueRow row) {
            PacketCodecUtils.writeString(buf, row.code);
            PacketCodecUtils.writeString(buf, row.subject);
            PacketCodecUtils.writeString(buf, row.detail);
        }

        static IssueRow read(ByteBuf buf) {
            return new IssueRow(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                    PacketCodecUtils.readString(buf));
        }
    }

    /** One value a profile would change: "Spell Damage 1.0 → 1.5". {@code applyMode} is the {@code ApplyMode} name. */
    public record ChangeRow(String settingId, String nameKey, String from, String to, String applyMode) {

        static ChangeRow of(ProfileValidator.Change change) {
            AdminSetting<?> setting = AdminSettings.service().registry().get(change.id());
            return new ChangeRow(change.id().toString(), setting == null ? "" : setting.nameKey(),
                    clip(change.from(), MAX_SHOWN_VALUE), clip(change.to(), MAX_SHOWN_VALUE), change.applyMode().name());
        }

        static void write(ByteBuf buf, ChangeRow row) {
            PacketCodecUtils.writeString(buf, row.settingId);
            PacketCodecUtils.writeString(buf, row.nameKey);
            PacketCodecUtils.writeString(buf, row.from);
            PacketCodecUtils.writeString(buf, row.to);
            PacketCodecUtils.writeString(buf, row.applyMode);
        }

        static ChangeRow read(ByteBuf buf) {
            return new ChangeRow(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                    PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf));
        }
    }

    /** The section's state: every profile (presets first), snapshots, importable files, recent history. */
    public record Listing(List<ProfileRow> profiles, List<String> files, List<HistoryRow> history, String folder) {}

    // ── helpers ──

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static ProfileDocument.Kind kind(int ordinal) {
        ProfileDocument.Kind[] kinds = ProfileDocument.Kind.values();
        return ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : ProfileDocument.Kind.CUSTOM;
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

    private static List<IssueRow> issues(List<ProfileCodec.Issue> issues) {
        return issues.stream().limit(MAX_ISSUES).map(IssueRow::of).toList();
    }

    // ── server side ──

    private static boolean admitted(ServerPlayer player, String what) {
        if (ProfileService.authorised(AdminContext.of(player))) {
            return true;
        }
        LOGGER.warn("[Admin] Dropped profile {} request from unauthorised {} ({})", what,
                player.getName().getString(), player.getUUID());
        return false;
    }

    public static Listing listing(MinecraftServer server) {
        List<ProfileRow> profiles = ProfileService.all(server).stream().map(ProfileRow::of).toList();
        List<HistoryRow> history = AdminSettings.service().history().recent(MAX_HISTORY).stream().map(HistoryRow::of).toList();
        String folder = server.getServerDirectory().toAbsolutePath().normalize()
                .relativize(ProfileService.folder(server).toAbsolutePath().normalize()).toString().replace('\\', '/');
        return new Listing(profiles, ProfileService.importable(server), history, clip(folder, 256));
    }

    /** Server side: the section's state, when the sender may manage profiles. */
    public static boolean sendList(ServerPlayer player) {
        if (!admitted(player, "list")) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new ListReply(listing(player.level().getServer())));
        return true;
    }

    /** Server side: what applying {@code profileId} would do for this sender, right now. */
    public static boolean sendPreview(ServerPlayer player, String profileId) {
        if (!admitted(player, "preview")) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        ProfileDocument document = ProfileService.find(server, profileId);
        if (document == null) {
            PacketDistributor.sendToPlayer(player, new PreviewReply(profileId, profileId, List.of(),
                    List.of(new IssueRow("not_found", profileId, "")), List.of(), 0, false, false));
            return true;
        }
        ProfileValidator.Plan plan = ProfileService.preview(server, AdminContext.of(player), document);
        PacketDistributor.sendToPlayer(player, new PreviewReply(profileId, document.meta().name(),
                plan.changes().stream().limit(MAX_CHANGES).map(ChangeRow::of).toList(), issues(plan.errors()),
                issues(plan.warnings()), plan.resetToDefault(), plan.needsRestart(), plan.touchesWorldgen()));
        return true;
    }

    /** Server side: one action, answered with its outcome, then fresh section state and fresh setting values. */
    public static boolean run(ServerPlayer player, ActionRequest request) {
        if (!admitted(player, request.op().name().toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        AdminContext actor = AdminContext.of(player);
        ActionReply reply;
        if (request.op().needsConfirmation() && !request.confirmed()) {
            reply = new ActionReply(request.op(), "confirmation_required", request.target(), 0, List.of());
        } else if (request.op() == Op.REVERT_ONE) {
            reply = revertOne(actor, request.target());
        } else {
            ProfileService.Result result = switch (request.op()) {
                case APPLY -> ProfileService.apply(server, actor, request.target());
                case SAVE_AS -> ProfileService.saveAs(server, actor, request.name(), ProfileDocument.Kind.CUSTOM);
                case SNAPSHOT -> ProfileService.saveAs(server, actor, request.name(), ProfileDocument.Kind.SNAPSHOT);
                case DUPLICATE -> ProfileService.duplicate(server, actor, request.target(), request.name());
                case RENAME -> ProfileService.rename(server, actor, request.target(), request.name());
                case DELETE -> ProfileService.delete(server, actor, request.target());
                case EXPORT -> ProfileService.export(server, actor, request.target());
                case IMPORT -> ProfileService.importFile(server, actor, request.target());
                case REVERT_GROUP -> ProfileService.revertGroup(server, actor, request.target());
                case REVERT_ONE -> throw new IllegalStateException("handled above");
            };
            reply = new ActionReply(request.op(), result.outcome(), result.id(), result.changed(), issues(result.issues()));
        }
        PacketDistributor.sendToPlayer(player, reply);
        PacketDistributor.sendToPlayer(player, new ListReply(listing(server)));
        if (reply.ok() && reply.changed() > 0 || reply.ok() && request.op() == Op.REVERT_ONE) {
            // Values moved under the open screen: resend every setting so the other sections show them.
            AdminNetworkService.refreshFor(player, (AdminCategory) null);
        }
        return true;
    }

    private static ActionReply revertOne(AdminContext actor, String sequenceText) {
        long sequence;
        try {
            sequence = Long.parseLong(sequenceText);
        } catch (NumberFormatException e) {
            return new ActionReply(Op.REVERT_ONE, "not_found", sequenceText, 0, List.of());
        }
        AdminResult result = AdminSettings.service().revert(actor, sequence);
        if (result.applied() || result.status() == AdminResult.Status.UNCHANGED) {
            return new ActionReply(Op.REVERT_ONE, "ok", sequenceText, result.applied() ? 1 : 0, List.of());
        }
        String reason = result.rejection() == null ? "refused" : result.rejection().name().toLowerCase(java.util.Locale.ROOT);
        return new ActionReply(Op.REVERT_ONE, reason, sequenceText, 0,
                List.of(new IssueRow(reason, result.settingId().toString(), result.detailKey() == null ? "" : result.detailKey())));
    }

    // ── payloads ──

    public record ListRequest() implements CustomPacketPayload {
        public static final ListRequest INSTANCE = new ListRequest();
        public static final Type<ListRequest> TYPE = new Type<>(id("admin_profile_list_request"));
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

    public record ListReply(Listing listing) implements CustomPacketPayload {
        public static final Type<ListReply> TYPE = new Type<>(id("admin_profile_list"));
        public static final StreamCodec<ByteBuf, ListReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    writeList(buf, p.listing.profiles(), MAX_PROFILES, ProfileRow::write);
                    writeList(buf, p.listing.files(), MAX_FILES, PacketCodecUtils::writeString);
                    writeList(buf, p.listing.history(), MAX_HISTORY, HistoryRow::write);
                    PacketCodecUtils.writeString(buf, p.listing.folder());
                },
                buf -> new ListReply(new Listing(
                        readList(buf, MAX_PROFILES, "admin-profiles", ProfileRow::read),
                        readList(buf, MAX_FILES, "admin-profile-files", PacketCodecUtils::readString),
                        readList(buf, MAX_HISTORY, "admin-profile-history", HistoryRow::read),
                        PacketCodecUtils.readString(buf))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ListReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onProfileList(payload));
        }
    }

    public record PreviewRequest(String profileId) implements CustomPacketPayload {
        public static final Type<PreviewRequest> TYPE = new Type<>(id("admin_profile_preview_request"));
        public static final StreamCodec<ByteBuf, PreviewRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> PacketCodecUtils.writeString(buf, p.profileId),
                buf -> new PreviewRequest(PacketCodecUtils.readString(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(PreviewRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    sendPreview(player, payload.profileId);
                }
            });
        }
    }

    /**
     * What applying a profile would do: the changes in apply order, errors (any → it cannot be applied), warnings,
     * how many of the changes are resets to default, and whether a restart or new chunks are needed for all of it.
     */
    public record PreviewReply(String profileId, String name, List<ChangeRow> changes, List<IssueRow> errors,
                               List<IssueRow> warnings, int resetToDefault, boolean needsRestart, boolean touchesWorldgen)
            implements CustomPacketPayload {
        public static final Type<PreviewReply> TYPE = new Type<>(id("admin_profile_preview"));
        public static final StreamCodec<ByteBuf, PreviewReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    PacketCodecUtils.writeString(buf, p.profileId);
                    PacketCodecUtils.writeString(buf, p.name);
                    writeList(buf, p.changes, MAX_CHANGES, ChangeRow::write);
                    writeList(buf, p.errors, MAX_ISSUES, IssueRow::write);
                    writeList(buf, p.warnings, MAX_ISSUES, IssueRow::write);
                    buf.writeInt(p.resetToDefault);
                    buf.writeBoolean(p.needsRestart);
                    buf.writeBoolean(p.touchesWorldgen);
                },
                buf -> new PreviewReply(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                        readList(buf, MAX_CHANGES, "admin-profile-changes", ChangeRow::read),
                        readList(buf, MAX_ISSUES, "admin-profile-errors", IssueRow::read),
                        readList(buf, MAX_ISSUES, "admin-profile-warnings", IssueRow::read),
                        buf.readInt(), buf.readBoolean(), buf.readBoolean()));

        public boolean applicable() {
            return errors.isEmpty();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(PreviewReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onProfilePreview(payload));
        }
    }

    /**
     * One action. {@code target}: the profile id (apply, duplicate, rename, delete, export), the file name (import),
     * the history sequence (revert one) or the group (revert group); {@code name}: the new name (save as, snapshot
     * label, duplicate, rename).
     */
    public record ActionRequest(Op op, String target, String name, boolean confirmed) implements CustomPacketPayload {
        public static final Type<ActionRequest> TYPE = new Type<>(id("admin_profile_action"));
        public static final StreamCodec<ByteBuf, ActionRequest> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.op.ordinal());
                    PacketCodecUtils.writeString(buf, p.target);
                    PacketCodecUtils.writeString(buf, p.name);
                    buf.writeBoolean(p.confirmed);
                },
                buf -> {
                    int ordinal = buf.readByte();
                    Op[] ops = Op.values();
                    if (ordinal < 0 || ordinal >= ops.length) {
                        throw new io.netty.handler.codec.DecoderException("unknown profile op " + ordinal);
                    }
                    return new ActionRequest(ops[ordinal], PacketCodecUtils.readString(buf),
                            PacketCodecUtils.readString(buf), buf.readBoolean());
                });

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

    /**
     * An action's outcome ({@code ok}, {@code refused}, {@code invalid_file}, {@code builtin}, {@code name_taken},
     * {@code confirmation_required}, …), the id or file it concerns, how many values changed, and every issue.
     */
    public record ActionReply(Op op, String outcome, String id, int changed, List<IssueRow> issues)
            implements CustomPacketPayload {
        public static final Type<ActionReply> TYPE = new Type<>(AdminProfilePayloads.id("admin_profile_action_reply"));
        public static final StreamCodec<ByteBuf, ActionReply> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.op.ordinal());
                    PacketCodecUtils.writeString(buf, p.outcome);
                    PacketCodecUtils.writeString(buf, p.id);
                    buf.writeInt(p.changed);
                    writeList(buf, p.issues, MAX_ISSUES, IssueRow::write);
                },
                buf -> {
                    int ordinal = buf.readByte();
                    Op[] ops = Op.values();
                    Op op = ordinal >= 0 && ordinal < ops.length ? ops[ordinal] : Op.APPLY;
                    return new ActionReply(op, PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                            buf.readInt(), readList(buf, MAX_ISSUES, "admin-profile-issues", IssueRow::read));
                });

        public boolean ok() {
            return "ok".equals(outcome);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ActionReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onProfileAction(payload));
        }
    }
}
