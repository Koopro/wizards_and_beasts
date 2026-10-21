package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.operation.AdminOperation;
import at.koopro.wizardsandbeasts.admin.operation.AdminOperationService;
import at.koopro.wizardsandbeasts.admin.report.AdminReportService;
import at.koopro.wizardsandbeasts.admin.report.ReportBuilder;
import at.koopro.wizardsandbeasts.admin.report.ReportView;
import at.koopro.wizardsandbeasts.client.admin.AdminClientHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every admin report and operation on the wire: one request/reply pair each, whatever the report or operation. The
 * payloads carry ids and short text only; {@link AdminReportService} and {@link AdminOperationService} decide
 * everything on the server. A new report or operation needs no payload.
 *
 * <p>A report is answered at most every {@link #MIN_TICKS_BETWEEN} ticks per report per administrator (an open page
 * polls); operations go through the shared {@link AdminRequestLimiter} like every other admin request.
 */
@NullMarked
public final class AdminReportPayloads {

    public static final int MIN_TICKS_BETWEEN = 10;
    static final int MAX_SECTIONS = 128;
    private static final PlayerScopedState<Map<String, Long>> LAST = PlayerScopedState.create("admin-report-requests");

    private AdminReportPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    /** Answers one report request; false when too soon (an unauthorised one is answered with its refusal). */
    public static boolean answer(ServerPlayer player, ReportRequest request) {
        long now = player.level().getServer().getTickCount();
        Map<String, Long> last = LAST.get(player);
        if (last == null) {
            last = new HashMap<>();
            LAST.put(player, last);
        }
        String key = request.reportId() + "|" + request.subject();
        Long previous = last.get(key);
        if (previous != null && now - previous < MIN_TICKS_BETWEEN && now >= previous) {
            return false;
        }
        if (last.size() > 64) {
            last.clear();
        }
        last.put(key, now);
        AdminReportService.Result result = AdminReportService.read(AdminContext.of(player), player.level().getServer(),
                request.reportId(), request.subject());
        PacketDistributor.sendToPlayer(player, new ReportReply(request.reportId(), request.subject(),
                result.status().id(), result.view()));
        return true;
    }

    public static AdminOperation.Outcome perform(ServerPlayer player, OperationRequest request) {
        AdminOperation.Outcome outcome = AdminOperationService.perform(AdminContext.of(player), player.level().getServer(),
                request.operationId(), request.subject(), request.argument(), request.confirmed());
        PacketDistributor.sendToPlayer(player, new OperationReply(request.operationId(), request.subject(),
                request.argument(), outcome.success(), outcome.code(), outcome.detail()));
        return outcome;
    }

    // ── payloads ──

    public record ReportRequest(String reportId, String subject) implements CustomPacketPayload {
        public static final Type<ReportRequest> TYPE = new Type<>(id("admin_report_request"));
        public static final StreamCodec<ByteBuf, ReportRequest> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    PacketCodecUtils.writeString(buf, r.reportId());
                    PacketCodecUtils.writeString(buf, r.subject());
                },
                buf -> new ReportRequest(bounded(buf, 48), bounded(buf, AdminReportService.MAX_SUBJECT)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ReportRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    answer(player, payload);
                }
            });
        }
    }

    /** {@code status}: {@link AdminReportService.Status#id()}. */
    public record ReportReply(String reportId, String subject, String status, ReportView view) implements CustomPacketPayload {
        public static final Type<ReportReply> TYPE = new Type<>(id("admin_report"));
        public static final StreamCodec<ByteBuf, ReportReply> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    PacketCodecUtils.writeString(buf, r.reportId());
                    PacketCodecUtils.writeString(buf, r.subject());
                    PacketCodecUtils.writeString(buf, r.status());
                    writeView(buf, r.view());
                },
                buf -> new ReportReply(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                        PacketCodecUtils.readString(buf), readView(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(ReportReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onReport(payload));
        }
    }

    public record OperationRequest(String operationId, String subject, String argument, boolean confirmed)
            implements CustomPacketPayload {
        public static final Type<OperationRequest> TYPE = new Type<>(id("admin_operation_request"));
        public static final StreamCodec<ByteBuf, OperationRequest> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    PacketCodecUtils.writeString(buf, r.operationId());
                    PacketCodecUtils.writeString(buf, r.subject());
                    PacketCodecUtils.writeString(buf, r.argument());
                    buf.writeBoolean(r.confirmed());
                },
                buf -> new OperationRequest(bounded(buf, 48), bounded(buf, AdminOperationService.MAX_SUBJECT),
                        bounded(buf, AdminOperationService.MAX_ARGUMENT), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(OperationRequest payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    perform(player, payload);
                }
            });
        }
    }

    public record OperationReply(String operationId, String subject, String argument, boolean success, String code,
                                 String detail) implements CustomPacketPayload {
        public static final Type<OperationReply> TYPE = new Type<>(id("admin_operation"));
        public static final StreamCodec<ByteBuf, OperationReply> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    PacketCodecUtils.writeString(buf, r.operationId());
                    PacketCodecUtils.writeString(buf, r.subject());
                    PacketCodecUtils.writeString(buf, r.argument());
                    buf.writeBoolean(r.success());
                    PacketCodecUtils.writeString(buf, r.code());
                    PacketCodecUtils.writeString(buf, r.detail());
                },
                buf -> new OperationReply(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                        PacketCodecUtils.readString(buf), buf.readBoolean(), PacketCodecUtils.readString(buf),
                        PacketCodecUtils.readString(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handleClient(OperationReply payload, IPayloadContext context) {
            context.enqueueWork(() -> AdminClientHandlers.onOperation(payload));
        }
    }

    // ── view codec ──

    /** Reads a string a client sent and refuses one longer than {@code max} characters. */
    private static String bounded(ByteBuf buf, int max) {
        String text = PacketCodecUtils.readString(buf);
        if (text.length() > max) {
            throw new IllegalArgumentException("admin request text over " + max);
        }
        return text;
    }

    static void writeView(ByteBuf buf, ReportView view) {
        PacketCodecUtils.writeString(buf, view.subject());
        int sections = Math.min(view.sections().size(), MAX_SECTIONS);
        buf.writeInt(sections);
        for (int s = 0; s < sections; s++) {
            ReportView.Section section = view.sections().get(s);
            PacketCodecUtils.writeString(buf, section.heading());
            buf.writeBoolean(section.headingKey());
            int lines = Math.min(section.lines().size(), ReportBuilder.MAX_LINES);
            buf.writeInt(lines);
            for (int l = 0; l < lines; l++) {
                ReportView.Line line = section.lines().get(l);
                PacketCodecUtils.writeString(buf, line.label());
                buf.writeBoolean(line.labelKey());
                PacketCodecUtils.writeString(buf, line.value());
                buf.writeBoolean(line.valueKey());
                buf.writeByte(line.tone().ordinal());
            }
        }
        int subjects = Math.min(view.subjects().size(), ReportBuilder.MAX_SUBJECTS);
        buf.writeInt(subjects);
        for (int i = 0; i < subjects; i++) {
            ReportView.Subject subject = view.subjects().get(i);
            PacketCodecUtils.writeString(buf, subject.id());
            PacketCodecUtils.writeString(buf, subject.label());
            PacketCodecUtils.writeString(buf, subject.note());
        }
        int actions = Math.min(view.actions().size(), ReportBuilder.MAX_ACTIONS);
        buf.writeInt(actions);
        for (int i = 0; i < actions; i++) {
            ReportView.Action action = view.actions().get(i);
            PacketCodecUtils.writeString(buf, action.operation());
            PacketCodecUtils.writeString(buf, action.labelKey());
            PacketCodecUtils.writeString(buf, action.subject());
            PacketCodecUtils.writeString(buf, action.argument());
            buf.writeBoolean(action.destructive());
        }
    }

    static ReportView readView(ByteBuf buf) {
        String subject = PacketCodecUtils.readString(buf);
        int sectionCount = PacketCodecUtils.readBoundedCount(buf, MAX_SECTIONS, "report sections");
        List<ReportView.Section> sections = new ArrayList<>(sectionCount);
        int total = 0;
        for (int s = 0; s < sectionCount; s++) {
            String heading = PacketCodecUtils.readString(buf);
            boolean headingKey = buf.readBoolean();
            int lineCount = PacketCodecUtils.readBoundedCount(buf, ReportBuilder.MAX_LINES, "report lines");
            total += lineCount;
            if (total > ReportBuilder.MAX_LINES + MAX_SECTIONS) {
                throw new IllegalArgumentException("report over line budget");
            }
            List<ReportView.Line> lines = new ArrayList<>(lineCount);
            for (int l = 0; l < lineCount; l++) {
                lines.add(new ReportView.Line(PacketCodecUtils.readString(buf), buf.readBoolean(),
                        PacketCodecUtils.readString(buf), buf.readBoolean(), toneOf(buf.readByte())));
            }
            sections.add(new ReportView.Section(heading, headingKey, lines));
        }
        int subjectCount = PacketCodecUtils.readBoundedCount(buf, ReportBuilder.MAX_SUBJECTS, "report subjects");
        List<ReportView.Subject> subjects = new ArrayList<>(subjectCount);
        for (int i = 0; i < subjectCount; i++) {
            subjects.add(new ReportView.Subject(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                    PacketCodecUtils.readString(buf)));
        }
        int actionCount = PacketCodecUtils.readBoundedCount(buf, ReportBuilder.MAX_ACTIONS, "report actions");
        List<ReportView.Action> actions = new ArrayList<>(actionCount);
        for (int i = 0; i < actionCount; i++) {
            actions.add(new ReportView.Action(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                    PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readBoolean()));
        }
        return new ReportView(subject, sections, subjects, actions);
    }

    private static ReportView.Tone toneOf(int ordinal) {
        ReportView.Tone[] tones = ReportView.Tone.values();
        return ordinal >= 0 && ordinal < tones.length ? tones[ordinal] : ReportView.Tone.NORMAL;
    }
}
