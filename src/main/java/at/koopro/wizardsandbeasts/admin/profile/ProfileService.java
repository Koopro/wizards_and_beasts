package at.koopro.wizardsandbeasts.admin.profile;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.history.AdminChangeRecord;
import at.koopro.wizardsandbeasts.admin.profile.ProfileCodec.Issue;
import at.koopro.wizardsandbeasts.admin.profile.ProfileValidator.Change;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import at.koopro.wizardsandbeasts.module.ModuleState;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Server configuration profiles: the built-in presets, the world's own profiles and snapshots, and everything done with
 * them — save as, duplicate, rename, delete, preview, apply, export, import, revert. Every value change goes through
 * {@link ProfileApplier}, so nothing about a profile is applied that a single setting change would have refused, and a
 * profile is applied whole or not at all.
 *
 * <p>Built-in presets are data, not code: {@code data/<ns>/admin_profiles/*.json} in the same schema as an export,
 * read from the server's resources. They are starting points; a preset is never applied implicitly and is validated
 * like any import.
 */
@NullMarked
public final class ProfileService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PRESET_FOLDER = "admin_profiles";
    /** Snapshot capability: viewing and taking them needs CONFIG; applying a profile needs whatever its settings need. */
    private static final AdminCapability MANAGE = AdminCapability.CONFIG;

    /**
     * What an operation did. {@code outcome}: {@code ok}, {@code unauthorized}, {@code not_found}, {@code builtin},
     * {@code invalid_name}, {@code name_taken}, {@code full}, {@code invalid_file}, {@code refused}, {@code io_error}.
     */
    public record Result(String outcome, String id, List<Issue> issues, int changed) {
        public boolean ok() {
            return "ok".equals(outcome);
        }

        static Result of(String outcome, String id) {
            return new Result(outcome, id, List.of(), 0);
        }
    }

    private ProfileService() {}

    public static boolean authorised(AdminContext actor) {
        return actor.canRead() && actor.canModify(MANAGE);
    }

    private static AdminSettingService service() {
        return AdminSettings.service();
    }

    public static String modVersion() {
        return ModList.get().getModContainerById(WizardsAndBeastsMod.MODID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("");
    }

    // ── listing ──

    /** Built-in presets from the server's resources, each validated in shape; a broken one is logged and skipped. */
    public static List<ProfileDocument> presets(MinecraftServer server) {
        List<ProfileDocument> out = new ArrayList<>();
        Map<Identifier, Resource> files = server.getResourceManager().listResources(PRESET_FOLDER,
                id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : new java.util.TreeMap<>(files).entrySet()) {
            String path = entry.getKey().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
            try (Reader reader = entry.getValue().openAsReader()) {
                ProfileCodec.Parsed parsed = ProfileCodec.parse(read(reader));
                if (parsed.ok() && ProfileIds.validId(id)) {
                    ProfileDocument doc = parsed.document();
                    out.add(doc.withMeta(doc.meta().withId(id).withKind(ProfileDocument.Kind.PRESET)));
                } else {
                    LOGGER.error("[Admin] Built-in profile {} is not a valid profile: {}", entry.getKey(), parsed.errors());
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[Admin] Could not read built-in profile {}", entry.getKey(), e);
            }
        }
        return out;
    }

    private static String read(Reader reader) throws IOException {
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[4096];
        int n;
        while ((n = reader.read(buffer)) > 0) {
            out.append(buffer, 0, n);
            if (out.length() > ProfileCodec.MAX_BYTES) {
                break;
            }
        }
        return out.toString();
    }

    /** The world's own profiles or snapshots; a stored entry that no longer parses is skipped (and logged). */
    public static List<ProfileDocument> stored(MinecraftServer server, ProfileDocument.Kind kind) {
        List<ProfileDocument> out = new ArrayList<>();
        AdminProfileData.get(server).all(kind).forEach((id, text) -> {
            ProfileCodec.Parsed parsed = ProfileCodec.parse(text);
            if (parsed.ok()) {
                out.add(parsed.document());
            } else {
                LOGGER.error("[Admin] Stored {} {} no longer reads: {}", kind.id(), id, parsed.errors());
            }
        });
        out.sort(Comparator.comparingLong((ProfileDocument d) -> -d.meta().createdMillis()));
        return out;
    }

    public static List<ProfileDocument> all(MinecraftServer server) {
        List<ProfileDocument> out = new ArrayList<>(presets(server));
        out.addAll(stored(server, ProfileDocument.Kind.CUSTOM));
        out.addAll(stored(server, ProfileDocument.Kind.SNAPSHOT));
        return out;
    }

    public static @Nullable ProfileDocument find(MinecraftServer server, String id) {
        for (ProfileDocument document : all(server)) {
            if (document.meta().id().equals(id)) {
                return document;
            }
        }
        return null;
    }

    // ── capture ──

    /** The server's configuration now: every server-owned value that differs from its default, in replace mode. */
    public static ProfileDocument capture(MinecraftServer server, ProfileDocument.Meta meta) {
        Map<String, String> settings = new LinkedHashMap<>();
        Map<String, String> modules = new LinkedHashMap<>();
        for (AdminSetting<?> setting : service().registry().everything(server)) {
            if (!setting.writable() || !setting.binding().available() || setting.isDefault()) {
                continue;
            }
            Module module = LiveProfileTarget.moduleOf(setting.id());
            if (module != null) {
                ModuleState state = ModuleState.parse(setting.currentText());
                if (state != null) {
                    modules.put(ModuleIds.of(module).getPath(), state.getSerializedName());
                }
            } else {
                settings.put(setting.id().toString(), setting.currentText());
            }
        }
        return new ProfileDocument(ProfileCodec.CURRENT_VERSION, modVersion(), meta, ProfileDocument.Mode.REPLACE,
                settings, modules);
    }

    // ── store operations ──

    public static Result saveAs(MinecraftServer server, AdminContext actor, String name, ProfileDocument.Kind kind) {
        if (!authorised(actor)) {
            return Result.of("unauthorized", "");
        }
        String trimmed = name.trim();
        if (kind == ProfileDocument.Kind.SNAPSHOT && trimmed.isEmpty()) {
            trimmed = ProfileIds.snapshotName(java.time.LocalDateTime.now());
        }
        if (!ProfileIds.validName(trimmed)) {
            return Result.of("invalid_name", "");
        }
        AdminProfileData data = AdminProfileData.get(server);
        if (data.full(kind)) {
            return Result.of("full", "");
        }
        if (kind != ProfileDocument.Kind.SNAPSHOT && nameTaken(server, trimmed, null)) {
            return Result.of("name_taken", "");
        }
        String id = freeId(server, ProfileIds.idFor(trimmed));
        data.put(capture(server, new ProfileDocument.Meta(id, trimmed, "", actor.actorName(), System.currentTimeMillis(), kind)));
        LOGGER.info("[Admin] {} saved {} {}", actor.actorName(), kind.id(), id);
        return Result.of("ok", id);
    }

    public static Result duplicate(MinecraftServer server, AdminContext actor, String sourceId, String name) {
        if (!authorised(actor)) {
            return Result.of("unauthorized", sourceId);
        }
        ProfileDocument source = find(server, sourceId);
        if (source == null) {
            return Result.of("not_found", sourceId);
        }
        String trimmed = name.trim();
        if (!ProfileIds.validName(trimmed)) {
            return Result.of("invalid_name", sourceId);
        }
        if (nameTaken(server, trimmed, null)) {
            return Result.of("name_taken", sourceId);
        }
        AdminProfileData data = AdminProfileData.get(server);
        if (data.full(ProfileDocument.Kind.CUSTOM)) {
            return Result.of("full", sourceId);
        }
        String id = freeId(server, ProfileIds.idFor(trimmed));
        data.put(source.withMeta(new ProfileDocument.Meta(id, trimmed, source.meta().description(), actor.actorName(),
                System.currentTimeMillis(), ProfileDocument.Kind.CUSTOM)));
        return Result.of("ok", id);
    }

    public static Result rename(MinecraftServer server, AdminContext actor, String id, String name) {
        if (!authorised(actor)) {
            return Result.of("unauthorized", id);
        }
        ProfileDocument document = find(server, id);
        if (document == null) {
            return Result.of("not_found", id);
        }
        if (document.meta().kind() == ProfileDocument.Kind.PRESET) {
            return Result.of("builtin", id);
        }
        String trimmed = name.trim();
        if (!ProfileIds.validName(trimmed)) {
            return Result.of("invalid_name", id);
        }
        if (nameTaken(server, trimmed, id)) {
            return Result.of("name_taken", id);
        }
        AdminProfileData.get(server).put(document.withMeta(document.meta().withName(trimmed)));
        return Result.of("ok", id);
    }

    public static Result delete(MinecraftServer server, AdminContext actor, String id) {
        if (!authorised(actor)) {
            return Result.of("unauthorized", id);
        }
        ProfileDocument document = find(server, id);
        if (document == null) {
            return Result.of("not_found", id);
        }
        if (document.meta().kind() == ProfileDocument.Kind.PRESET) {
            return Result.of("builtin", id);
        }
        AdminProfileData.get(server).remove(document.meta().kind(), id);
        LOGGER.info("[Admin] {} deleted {} {}", actor.actorName(), document.meta().kind().id(), id);
        return Result.of("ok", id);
    }

    private static boolean nameTaken(MinecraftServer server, String name, @Nullable String exceptId) {
        for (ProfileDocument document : all(server)) {
            if (document.meta().kind() != ProfileDocument.Kind.SNAPSHOT && !document.meta().id().equals(exceptId)
                    && document.meta().name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static String freeId(MinecraftServer server, String base) {
        String id = base;
        for (int n = 2; find(server, id) != null; n++) {
            String suffix = "_" + n;
            id = (base.length() + suffix.length() > 48 ? base.substring(0, 48 - suffix.length()) : base) + suffix;
        }
        return id;
    }

    // ── validation and application ──

    public static ProfileValidator.Plan preview(MinecraftServer server, AdminContext actor, ProfileDocument document) {
        return ProfileValidator.validate(document, new LiveProfileTarget(service().registry(), actor, server),
                DeprecatedSettings.CURRENT);
    }

    /**
     * Applies a profile: validated again now (the preview the administrator saw may be old), refused whole on any error,
     * otherwise applied all-or-nothing under one history group.
     */
    public static Result apply(MinecraftServer server, AdminContext actor, String id) {
        if (!actor.canRead()) {
            return Result.of("unauthorized", id);
        }
        ProfileDocument document = find(server, id);
        if (document == null) {
            return Result.of("not_found", id);
        }
        ProfileValidator.Plan plan = preview(server, actor, document);
        if (!plan.applicable()) {
            return new Result("refused", id, plan.errors(), 0);
        }
        String kind = document.meta().kind() == ProfileDocument.Kind.SNAPSHOT ? "snapshot" : "profile";
        ProfileApplier.Outcome outcome = ProfileApplier.apply(service(), actor, plan.changes(),
                kind + ":" + id + ":" + System.currentTimeMillis(), AdminChangeRecord.Kind.PROFILE, server);
        if (!outcome.applied()) {
            List<Issue> issues = new ArrayList<>(outcome.failures());
            issues.addAll(outcome.rollbackFailed());
            return new Result("refused", id, issues, 0);
        }
        return new Result("ok", id, List.of(), outcome.changed());
    }

    /**
     * Reverts a whole recorded group (a profile applied, a snapshot restored): every setting it changed goes back to the
     * value it had before the group, all-or-nothing; refused if any of them has been changed since.
     */
    public static Result revertGroup(MinecraftServer server, AdminContext actor, String group) {
        if (!actor.canRead()) {
            return Result.of("unauthorized", group);
        }
        Map<Identifier, String> before = new LinkedHashMap<>();
        Map<Identifier, String> after = new LinkedHashMap<>();
        List<AdminChangeRecord> records = new ArrayList<>();
        for (AdminChangeRecord record : service().history().inGroup(group)) {
            if (record.applied() && !record.undone() && record.kind() != AdminChangeRecord.Kind.REVERT) {
                before.putIfAbsent(record.settingId(), record.oldValue());
                after.put(record.settingId(), record.newValue());
                records.add(record);
            }
        }
        if (records.isEmpty()) {
            return Result.of("not_found", group);
        }
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<Identifier, String> entry : after.entrySet()) {
            AdminSetting<?> setting = service().registry().get(entry.getKey());
            changes.add(new Change(entry.getKey(), entry.getValue(), before.get(entry.getKey()),
                    setting == null ? at.koopro.wizardsandbeasts.admin.config.ApplyMode.RUNTIME : setting.applyMode(),
                    LiveProfileTarget.moduleOf(entry.getKey())));
        }
        ProfileApplier.Outcome outcome = ProfileApplier.apply(service(), actor, changes,
                "revert:" + group + ":" + System.currentTimeMillis(), AdminChangeRecord.Kind.REVERT, server);
        if (!outcome.applied()) {
            List<Issue> issues = new ArrayList<>(outcome.failures());
            issues.addAll(outcome.rollbackFailed());
            return new Result("refused", group, issues, 0);
        }
        for (AdminChangeRecord record : records) {
            service().history().markUndone(record.sequence());
        }
        return new Result("ok", group, List.of(), outcome.changed());
    }

    // ── export and import ──

    /** Where exports are written and imports are read: {@code <world>/wizards_and_beasts/admin_profiles}. */
    public static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(WizardsAndBeastsMod.MODID).resolve(PRESET_FOLDER);
    }

    public static Result export(MinecraftServer server, AdminContext actor, String id) {
        if (!authorised(actor)) {
            return Result.of("unauthorized", id);
        }
        ProfileDocument document = find(server, id);
        if (document == null) {
            return Result.of("not_found", id);
        }
        try {
            Path folder = folder(server);
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(id + ".json"), ProfileCodec.write(document), StandardCharsets.UTF_8);
            return Result.of("ok", id + ".json");
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[Admin] Could not export profile {}", id, e);
            return Result.of("io_error", id);
        }
    }

    /** The files an administrator could import, by plain name. */
    public static List<String> importable(MinecraftServer server) {
        Path folder = folder(server);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(p -> p.getFileName().toString()).filter(ProfileIds::validFileName).sorted().limit(200).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Reads, checks and validates a file from the profile folder, and keeps it as a custom profile when it is usable.
     * Never applies it. Malformed data is reported, never thrown.
     */
    public static Result importFile(MinecraftServer server, AdminContext actor, String fileName) {
        if (!authorised(actor)) {
            return Result.of("unauthorized", fileName);
        }
        if (!ProfileIds.validFileName(fileName)) {
            return Result.of("invalid_file", fileName);
        }
        Path file = folder(server).resolve(fileName).normalize();
        if (!file.startsWith(folder(server).normalize()) || !Files.isRegularFile(file)) {
            return Result.of("not_found", fileName);
        }
        String text;
        try {
            if (Files.size(file) > ProfileCodec.MAX_BYTES) {
                return new Result("invalid_file", fileName, List.of(new Issue("too_large", "file", "")), 0);
            }
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return Result.of("io_error", fileName);
        }
        return importText(server, actor, text, fileName);
    }

    /** {@link #importFile} after reading: shared with tests that feed text directly. */
    public static Result importText(MinecraftServer server, AdminContext actor, String text, String source) {
        if (!authorised(actor)) {
            return Result.of("unauthorized", source);
        }
        ProfileCodec.Parsed parsed = ProfileCodec.parse(text);
        if (!parsed.ok()) {
            return new Result("invalid_file", source, parsed.errors(), 0);
        }
        ProfileDocument document = parsed.document();
        ProfileValidator.Plan plan = preview(server, actor, document);
        List<Issue> report = new ArrayList<>(parsed.warnings());
        report.addAll(plan.warnings());
        if (!plan.applicable()) {
            List<Issue> errors = new ArrayList<>(plan.errors());
            errors.addAll(report);
            return new Result("refused", source, errors, 0);
        }
        AdminProfileData data = AdminProfileData.get(server);
        if (data.full(ProfileDocument.Kind.CUSTOM)) {
            return Result.of("full", source);
        }
        String name = document.meta().name();
        for (int n = 2; nameTaken(server, name, null); n++) {
            name = trimName(document.meta().name(), " (" + n + ")");
        }
        String id = freeId(server, ProfileIds.idFor(name));
        data.put(document.withMeta(new ProfileDocument.Meta(id, name, document.meta().description(),
                document.meta().author(), document.meta().createdMillis(), ProfileDocument.Kind.CUSTOM)));
        LOGGER.info("[Admin] {} imported {} as profile {} ({} warning(s))", actor.actorName(), source, id, report.size());
        return new Result("ok", id, report, plan.changes().size());
    }

    /** "Hogwarts RP" + " (2)", shortened so the result stays a valid name. */
    private static String trimName(String name, String suffix) {
        return (name.length() + suffix.length() > 48 ? name.substring(0, 48 - suffix.length()) : name) + suffix;
    }
}
