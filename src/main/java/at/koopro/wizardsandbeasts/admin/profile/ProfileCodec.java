package at.koopro.wizardsandbeasts.admin.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The profile file format, version 1:
 *
 * <pre>{@code
 * {
 *   "schema": "wizards_and_beasts:admin_profile",
 *   "schema_version": 1,
 *   "mod_version": "0.1.0-alpha.1",
 *   "profile": { "id": "hogwarts_rp", "name": "Hogwarts RP", "description": "…", "author": "…",
 *                "created": 1759300000000, "kind": "custom" },
 *   "mode": "replace",
 *   "settings": { "wizards_and_beasts:spell_damage_multiplier": "1.5", … },
 *   "modules":  { "dark_arts": "enabled", … }
 * }
 * }</pre>
 *
 * <p>{@link #parse} reads untrusted text — an imported file — and never throws: anything wrong becomes an
 * {@link Issue}. It checks shape only (sizes, types, the schema id and version); whether a setting exists and whether
 * its value is legal is {@link ProfileValidator}'s job against the live server.
 */
@NullMarked
public final class ProfileCodec {

    public static final String SCHEMA_ID = "wizards_and_beasts:admin_profile";
    public static final int CURRENT_VERSION = 1;
    /** The oldest version this build still reads. */
    public static final int OLDEST_SUPPORTED_VERSION = 1;

    public static final int MAX_BYTES = 512 * 1024;
    public static final int MAX_ENTRIES = 20_000;
    public static final int MAX_KEY = 256;
    public static final int MAX_VALUE = 2_048;
    public static final int MAX_NAME = 48;
    public static final int MAX_DESCRIPTION = 512;

    private static final Set<String> KNOWN_FIELDS = Set.of("schema", "schema_version", "mod_version", "profile",
            "mode", "settings", "modules");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** A problem with a file, before or after looking at the server. {@code subject} is a field or setting id. */
    public record Issue(String code, String subject, String detail) {}

    /** What {@link #parse} found: a document when the shape was usable, and every error and warning on the way. */
    public record Parsed(@Nullable ProfileDocument document, List<Issue> errors, List<Issue> warnings) {
        public boolean ok() {
            return document != null && errors.isEmpty();
        }
    }

    private ProfileCodec() {}

    public static Parsed parse(String text) {
        List<Issue> errors = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            errors.add(new Issue("too_large", "file", Integer.toString(MAX_BYTES)));
            return new Parsed(null, errors, warnings);
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(text);
        } catch (RuntimeException | StackOverflowError malformed) {
            errors.add(new Issue("malformed", "file", clip(String.valueOf(malformed.getMessage()))));
            return new Parsed(null, errors, warnings);
        }
        if (!root.isJsonObject()) {
            errors.add(new Issue("not_a_profile", "file", "not a JSON object"));
            return new Parsed(null, errors, warnings);
        }
        JsonObject object = root.getAsJsonObject();
        String schema = string(object, "schema");
        if (!SCHEMA_ID.equals(schema)) {
            errors.add(new Issue("not_a_profile", "schema", schema == null ? "missing" : clip(schema)));
            return new Parsed(null, errors, warnings);
        }
        Integer version = integer(object, "schema_version");
        if (version == null) {
            errors.add(new Issue("bad_field", "schema_version", "missing or not a whole number"));
            return new Parsed(null, errors, warnings);
        }
        if (version > CURRENT_VERSION || version < OLDEST_SUPPORTED_VERSION) {
            errors.add(new Issue("incompatible_version", "schema_version",
                    version + " (this build reads " + OLDEST_SUPPORTED_VERSION + "–" + CURRENT_VERSION + ")"));
            return new Parsed(null, errors, warnings);
        }
        for (String field : object.keySet()) {
            if (!KNOWN_FIELDS.contains(field)) {
                warnings.add(new Issue("unknown_field", clip(field), ""));
            }
        }

        String modVersion = orEmpty(string(object, "mod_version"));
        ProfileDocument.Meta meta = meta(object, errors);
        ProfileDocument.Mode mode = ProfileDocument.Mode.REPLACE;
        String modeText = string(object, "mode");
        if (modeText != null) {
            try {
                mode = ProfileDocument.Mode.valueOf(modeText.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add(new Issue("bad_field", "mode", clip(modeText)));
            }
        }
        Map<String, String> settings = map(object, "settings", errors);
        Map<String, String> modules = map(object, "modules", errors);
        if (settings.size() + modules.size() > MAX_ENTRIES) {
            errors.add(new Issue("too_large", "settings", Integer.toString(MAX_ENTRIES)));
        }
        if (meta == null || !errors.isEmpty()) {
            return new Parsed(null, errors, warnings);
        }
        return new Parsed(new ProfileDocument(version, modVersion, meta, mode, settings, modules), errors, warnings);
    }

    private static ProfileDocument.@Nullable Meta meta(JsonObject object, List<Issue> errors) {
        JsonElement element = object.get("profile");
        if (element == null || !element.isJsonObject()) {
            errors.add(new Issue("bad_field", "profile", "missing or not an object"));
            return null;
        }
        JsonObject profile = element.getAsJsonObject();
        String id = string(profile, "id");
        String name = string(profile, "name");
        if (id == null || !ProfileIds.validId(id)) {
            errors.add(new Issue("bad_field", "profile.id", id == null ? "missing" : clip(id)));
            return null;
        }
        if (name == null || name.isBlank() || name.length() > MAX_NAME) {
            errors.add(new Issue("bad_field", "profile.name", name == null ? "missing" : clip(name)));
            return null;
        }
        Long created = longValue(profile, "created");
        ProfileDocument.Kind kind = ProfileDocument.Kind.CUSTOM;
        String kindText = string(profile, "kind");
        if (kindText != null) {
            try {
                kind = ProfileDocument.Kind.valueOf(kindText.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add(new Issue("bad_field", "profile.kind", clip(kindText)));
            }
        }
        String description = orEmpty(string(profile, "description"));
        return new ProfileDocument.Meta(id, name.trim(), description.length() > MAX_DESCRIPTION
                ? description.substring(0, MAX_DESCRIPTION) : description,
                clip(orEmpty(string(profile, "author"))), created == null ? 0L : created, kind);
    }

    /** A string → string map; non-string values, oversized keys or values are errors naming the entry. */
    private static Map<String, String> map(JsonObject object, String field, List<Issue> errors) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonElement element = object.get(field);
        if (element == null) {
            return out;
        }
        if (!element.isJsonObject()) {
            errors.add(new Issue("bad_field", field, "not an object"));
            return out;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (key.length() > MAX_KEY) {
                errors.add(new Issue("bad_entry", clip(key), "key too long"));
                continue;
            }
            if (!value.isJsonPrimitive()) {
                errors.add(new Issue("bad_entry", key, "value is not text, a number or true/false"));
                continue;
            }
            String text = value.getAsJsonPrimitive().getAsString();
            if (text.length() > MAX_VALUE) {
                errors.add(new Issue("bad_entry", key, "value too long"));
                continue;
            }
            out.put(key, text);
        }
        return out;
    }

    public static String write(ProfileDocument document) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_ID);
        root.addProperty("schema_version", CURRENT_VERSION);
        root.addProperty("mod_version", document.modVersion());
        JsonObject profile = new JsonObject();
        ProfileDocument.Meta meta = document.meta();
        profile.addProperty("id", meta.id());
        profile.addProperty("name", meta.name());
        profile.addProperty("description", meta.description());
        profile.addProperty("author", meta.author());
        profile.addProperty("created", meta.createdMillis());
        profile.addProperty("kind", meta.kind().id());
        root.add("profile", profile);
        root.addProperty("mode", document.mode().id());
        root.add("settings", object(document.settings()));
        root.add("modules", object(document.modules()));
        return GSON.toJson(root);
    }

    private static JsonObject object(Map<String, String> values) {
        JsonObject out = new JsonObject();
        new TreeMap<>(values).forEach((key, value) -> out.add(key, new JsonPrimitive(value)));
        return out;
    }

    private static @Nullable String string(JsonObject object, String field) {
        JsonElement element = object.get(field);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }

    private static @Nullable Integer integer(JsonObject object, String field) {
        Long value = longValue(object, field);
        return value == null || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE ? null : value.intValue();
    }

    private static @Nullable Long longValue(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            double number = element.getAsDouble();
            return number == Math.rint(number) && Double.isFinite(number) ? (long) number : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String orEmpty(@Nullable String text) {
        return text == null ? "" : text;
    }

    private static String clip(String text) {
        return text.length() <= 120 ? text : text.substring(0, 120) + "…";
    }
}
