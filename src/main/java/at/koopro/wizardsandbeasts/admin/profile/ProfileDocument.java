package at.koopro.wizardsandbeasts.admin.profile;

import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * One server configuration profile as plain data — exactly what the export file holds, nothing else. Values are the
 * settings' own canonical text (what a command argument carries), so a profile is readable, diffable and never a
 * serialized Java object.
 *
 * @param schemaVersion the {@link ProfileCodec} schema it was written in
 * @param modVersion    the mod version that wrote it ("" when unknown); informational
 * @param settings      setting id → value text; module switches live in {@link #modules}
 * @param modules       module id path → state ({@code enabled}, {@code disabled}, {@code preview})
 */
@NullMarked
public record ProfileDocument(int schemaVersion, String modVersion, Meta meta, Mode mode,
                              Map<String, String> settings, Map<String, String> modules) {

    /**
     * How a profile meets the settings it does not name. {@link #REPLACE}: they return to their defaults, so applying
     * the profile always lands on the same configuration (snapshots, presets). {@link #MERGE}: they are left alone.
     */
    public enum Mode {
        REPLACE, MERGE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Where a profile came from. Built-in presets ship with the mod and cannot be renamed, changed or deleted. */
    public enum Kind {
        PRESET, CUSTOM, SNAPSHOT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Meta(String id, String name, String description, String author, long createdMillis, Kind kind) {
        public Meta withId(String newId) {
            return new Meta(newId, name, description, author, createdMillis, kind);
        }

        public Meta withName(String newName) {
            return new Meta(id, newName, description, author, createdMillis, kind);
        }

        public Meta withKind(Kind newKind) {
            return new Meta(id, name, description, author, createdMillis, newKind);
        }
    }

    public ProfileDocument {
        settings = Map.copyOf(new TreeMap<>(settings));
        modules = Map.copyOf(new TreeMap<>(modules));
    }

    public ProfileDocument withMeta(Meta newMeta) {
        return new ProfileDocument(schemaVersion, modVersion, newMeta, mode, settings, modules);
    }

    /** How many values the profile names. */
    public int size() {
        return settings.size() + modules.size();
    }
}
