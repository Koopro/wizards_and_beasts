package at.koopro.wizardsandbeasts.admin.report;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.report.ReportView.Tone;
import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.location.PlaceStanding;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.law.LawReputation;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseData;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicences;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCaseData;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.standing.StandingService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Ministry section reports: one player's licences (the scrolls they carry — licences are items, so only an online
 * player can be read), one player's reputation on every axis the game keeps, and the Ministry's own machinery.
 * Evidence and cases are the Cases tab ({@code CaseReport}); these do not repeat it.
 */
@NullMarked
final class MinistryReports {

    private MinistryReports() {}

    static void registerAll() {
        AdminReports.register(AdminReport.of("ministry_licences", AdminCategory.MINISTRY, MinistryReports::licences));
        AdminReports.register(AdminReport.of("ministry_standing", AdminCategory.MINISTRY, MinistryReports::standing));
        AdminReports.register(AdminReport.of("ministry_debug", AdminCategory.MINISTRY, MinistryReports::debug));
    }

    /** Online players as subjects; the one {@code subject} names (name or UUID), or null. */
    static @Nullable ServerPlayer playerSubject(MinecraftServer server, String subject, ReportBuilder out) {
        server.getPlayerList().getPlayers().stream()
                .sorted(Comparator.comparing(p -> p.getName().getString().toLowerCase(Locale.ROOT)))
                .forEach(p -> out.subjectOption(p.getUUID().toString(), p.getName().getString(), ""));
        if (subject.isEmpty()) {
            return null;
        }
        ServerPlayer byName = server.getPlayerList().getPlayerByName(subject);
        if (byName != null) {
            return byName;
        }
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(subject));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String key(Component component) {
        return component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                ? t.getKey() : component.getString();
    }

    private static void licences(AdminReport.Request request, ReportBuilder out) {
        ServerPlayer player = playerSubject(request.server(), request.subject(), out);
        if (player == null) {
            out.section("pick");
            out.note("pick_player");
            out.section("types");
            for (LicenseType type : LicenseType.values()) {
                out.literal(type.getSerializedName(), "max rank " + LicenseType.MAX_RANK);
            }
            return;
        }
        long now = player.level().getGameTime();
        long trace = MinistryTrace.now(request.server());
        PlayerMinistryRecord record = MinistryRecords.get(player);
        out.literalSection(player.getName().getString());
        for (LicenseType type : LicenseType.values()) {
            ItemStack document = MinistryLicences.findDocument(player, type);
            LicenseData data = document == null ? null : MinistryLicences.read(document);
            boolean suspended = record.suspendedAt(type, trace);
            String state;
            Tone tone;
            if (data == null) {
                state = suspended ? "none · suspended" : "none";
                tone = Tone.MUTED;
            } else {
                StringBuilder text = new StringBuilder("rank ").append(data.rank());
                boolean valid = !data.revoked() && !data.hasExpired(now) && data.issuedTo().equals(player.getUUID())
                        && !suspended;
                if (data.revoked()) {
                    text.append(" · revoked");
                }
                if (data.hasExpired(now)) {
                    text.append(" · expired");
                } else if (!data.isPermanent()) {
                    text.append(" · expires in ").append(Math.max(0, (data.expiryTick() - now) / 20 / 60)).append(" min");
                }
                if (!data.issuedTo().equals(player.getUUID())) {
                    text.append(" · issued to another");
                }
                if (data.forged()) {
                    text.append(" · forged");
                }
                if (suspended) {
                    text.append(" · suspended");
                }
                state = text.toString();
                tone = data.forged() ? Tone.BAD : valid ? Tone.GOOD : Tone.WARN;
            }
            out.literal(type.getSerializedName(), state, tone);
        }
        out.note("licences_note");
    }

    private static void standing(AdminReport.Request request, ReportBuilder out) {
        ServerPlayer player = playerSubject(request.server(), request.subject(), out);
        if (player == null) {
            out.section("pick");
            out.note("pick_player");
            return;
        }
        out.literalSection(player.getName().getString());
        Map<StandingAxis, Float> axes = StandingService.snapshot(player);
        for (StandingAxis axis : StandingAxis.values()) {
            Float value = axes.get(axis);
            out.literal(axis.getSerializedName(), value == null ? "—" : String.format(Locale.ROOT, "%+.1f · %s", value,
                    StandingService.bandOf(player, axis).name().toLowerCase(Locale.ROOT)));
        }
        out.flag("good_standing", LawReputation.goodStanding(player), true);
        out.flag("credible", LawReputation.credible(player), true);

        out.section("ministry_record");
        PlayerMinistryRecord record = MinistryRecords.get(player);
        out.rowKey("rank", key(record.rank().displayName()), Tone.NORMAL);
        out.row("notoriety", String.format(Locale.ROOT, "%.1f / %.0f", record.notoriety(), PlayerMinistryRecord.MAX_NOTORIETY),
                record.notoriety() > PlayerMinistryRecord.MAX_NOTORIETY / 2 ? Tone.WARN : Tone.NORMAL);
        out.row("conduct", record.conduct());
        out.row("offences", record.totalOffences(), record.totalOffences() > 0 ? Tone.WARN : Tone.NORMAL);
        out.flag("fugitive", record.fugitive(), false);

        out.section("places");
        for (PlaceStanding place : LocationProgression.standings(player)) {
            out.literal(place.id().getPath(), "tier " + (place.tierIndex() + 1) + "/" + place.tierCount()
                    + " · rep " + place.rep() + "/" + place.nextRep() + (place.illicit() ? " · illicit" : ""));
        }
    }

    private static void debug(AdminReport.Request request, ReportBuilder out) {
        MinecraftServer server = request.server();
        MinistryCaseData cases = MinistryCaseData.get(server);
        out.section("machinery");
        out.flag("ministry_module", ModuleManager.isEnabled(Module.MINISTRY), true);
        out.flag("trace_active", TraceService.isActive(), true);
        out.row("trace_clock", MinistryTrace.now(server));
        out.row("incidents", cases.incidentCount());
        out.row("pending_reports", cases.pending().size());
        out.row("dossiers", cases.dossiers().size());
        out.row("open_cases", cases.dossiers().values().stream().filter(d -> d.openCase().isPresent()).count());
        out.row("unsolved", cases.unsolved().size());
        out.row("npc_cases", cases.npcCases().size());

        out.section("online");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMinistryRecord record = MinistryRecords.get(player);
            out.literal(player.getName().getString(), record.wantedLevel().name().toLowerCase(Locale.ROOT)
                    + String.format(Locale.ROOT, " · notoriety %.1f", record.notoriety())
                    + (MinistryTrace.isUnderage(player) ? " · underage" : "")
                    + (record.isServingSentence() ? " · serving" : ""),
                    record.fugitive() ? Tone.BAD : Tone.NORMAL);
        }
    }
}
