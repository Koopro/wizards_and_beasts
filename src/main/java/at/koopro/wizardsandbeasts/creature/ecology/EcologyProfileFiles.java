package at.koopro.wizardsandbeasts.creature.ecology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Turns the raw files of {@code creature_ecology/} into profiles, resolving archetypes. Pure: a map of file ids to
 * JSON in, a map of creature ids to parse results out — shared by the reload listener and the data test, so what the
 * test checks is what the game loads.
 *
 * <p>An archetype lives at {@code _archetypes/<name>.json} and is never a creature itself. A creature file may say
 * {@code "extends": "<name>"}; the archetype is its base and the file's own fields win, except:
 * <ul>
 *   <li>{@code capabilities} are added to the archetype's, and {@code "without": [...]} removes some of them;</li>
 *   <li>{@code needs} and {@code personality} are merged key by key.</li>
 * </ul>
 * Archetypes may extend archetypes, four deep at most.
 */
@NullMarked
public final class EcologyProfileFiles {

    public static final String ARCHETYPES = "_archetypes/";
    private static final int MAX_DEPTH = 4;

    private EcologyProfileFiles() {}

    /** Every creature profile in {@code files}, keyed by creature id (the file path without directory). */
    public static Map<String, DataResult<EcologyProfile>> resolve(Map<String, JsonElement> files) {
        Map<String, DataResult<EcologyProfile>> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : files.entrySet()) {
            if (e.getKey().startsWith(ARCHETYPES)) {
                continue;
            }
            DataResult<JsonObject> merged = flatten(e.getKey(), e.getValue(), files, 0);
            out.put(e.getKey(), merged.flatMap(json -> EcologyProfile.CODEC.parse(JsonOps.INSTANCE, json)));
        }
        return out;
    }

    private static DataResult<JsonObject> flatten(String id, JsonElement element, Map<String, JsonElement> files,
                                                  int depth) {
        if (!element.isJsonObject()) {
            return DataResult.error(() -> id + " is not a JSON object");
        }
        JsonObject own = element.getAsJsonObject();
        if (!own.has("extends")) {
            return DataResult.success(own);
        }
        if (depth >= MAX_DEPTH) {
            return DataResult.error(() -> id + ": archetypes extend each other more than " + MAX_DEPTH + " deep");
        }
        String parentId = ARCHETYPES + own.get("extends").getAsString();
        JsonElement parent = files.get(parentId);
        if (parent == null) {
            return DataResult.error(() -> id + " extends " + parentId + ", which does not exist");
        }
        return flatten(parentId, parent, files, depth + 1).map(base -> merge(base, own));
    }

    /** {@code own} laid over {@code base}. Neither argument is changed. */
    static JsonObject merge(JsonObject base, JsonObject own) {
        JsonObject out = base.deepCopy();
        out.remove("extends");
        out.remove("without");
        for (Map.Entry<String, JsonElement> e : own.entrySet()) {
            String key = e.getKey();
            switch (key) {
                case "extends", "without" -> { }
                case "capabilities" -> out.add(key, union(out.get(key), e.getValue()));
                case "needs", "personality" -> {
                    JsonObject into = out.has(key) && out.get(key).isJsonObject()
                            ? out.getAsJsonObject(key) : new JsonObject();
                    e.getValue().getAsJsonObject().entrySet().forEach(n -> into.add(n.getKey(), n.getValue().deepCopy()));
                    out.add(key, into);
                }
                default -> out.add(key, e.getValue().deepCopy());
            }
        }
        if (own.has("without") && out.has("capabilities")) {
            Set<String> drop = new LinkedHashSet<>();
            own.getAsJsonArray("without").forEach(j -> drop.add(j.getAsString()));
            JsonArray kept = new JsonArray();
            out.getAsJsonArray("capabilities").forEach(j -> {
                if (!drop.contains(j.getAsString())) {
                    kept.add(j);
                }
            });
            out.add("capabilities", kept);
        }
        return out;
    }

    private static JsonArray union(JsonElement base, JsonElement own) {
        Set<String> names = new LinkedHashSet<>();
        if (base != null && base.isJsonArray()) {
            base.getAsJsonArray().forEach(j -> names.add(j.getAsString()));
        }
        own.getAsJsonArray().forEach(j -> names.add(j.getAsString()));
        JsonArray out = new JsonArray();
        names.forEach(out::add);
        return out;
    }
}
