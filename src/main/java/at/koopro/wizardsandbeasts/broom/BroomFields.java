package at.koopro.wizardsandbeasts.broom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads one broom definition's flat JSON keys, collecting every problem instead of stopping at the
 * first.
 *
 * <p>This replaces a nested {@code flatMap} pyramid that had reached 22 levels and would have
 * reached 38 with this pass's fields. The pyramid was not a style choice — {@code RecordCodecBuilder}
 * caps at 16 fields, so the codec was hand-written — but it had two costs that grew with every field.
 * The deepest line carried about 150 characters of leading whitespace, and it short-circuited: a
 * definition with four bad values reported one, so fixing a datapack meant four edit-and-reload
 * cycles. Collecting into a list gives a flat method body and one message naming every fault.
 *
 * <p>Every reader records a fault and returns a usable placeholder rather than throwing, so decoding
 * always runs to the end and always has the full picture. {@link #failure()} is what the caller
 * checks before building the record; nothing else should look at the values when it is non-empty.
 *
 * <p><b>Unknown keys are ignored,</b> which is deliberate and is what makes a definition written for
 * a later version of the mod load in an earlier one, minus the fields it does not understand yet.
 */
final class BroomFields<T> {

    private final Dynamic<T> dynamic;
    private final List<String> faults = new ArrayList<>();

    BroomFields(Dynamic<T> dynamic) {
        this.dynamic = dynamic;
    }

    /** Every fault found, joined, or empty when the definition is sound. */
    Optional<String> failure() {
        return faults.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", faults));
    }

    void fault(String message) {
        faults.add(message);
    }

    /** Whether the JSON carries this key at all — used to tell "absent" from "authored". */
    boolean has(String key) {
        return dynamic.get(key).result().isPresent();
    }

    /** A key that must be present and must parse. Records a fault and returns null if not. */
    <A> A required(String key, Codec<A> codec) {
        Optional<Dynamic<T>> node = dynamic.get(key).result();
        if (node.isEmpty()) {
            faults.add("Missing required field: " + key);
            return null;
        }
        return parse(key, codec, node.get(), null);
    }

    /** A key that may be absent. An absent key is not a fault; an unparseable one is. */
    <A> A optional(String key, Codec<A> codec, A fallback) {
        Optional<Dynamic<T>> node = dynamic.get(key).result();
        return node.isEmpty() ? fallback : parse(key, codec, node.get(), fallback);
    }

    /** As {@link #optional}, wrapped: absent and unparseable both come back empty. */
    <A> Optional<A> maybe(String key, Codec<A> codec) {
        return Optional.ofNullable(optional(key, codec, null));
    }

    /**
     * The first of {@code keys} that is present, so a renamed key can keep loading old datapacks.
     * Absence of all of them is not a fault.
     */
    <A> Optional<A> maybeAny(Codec<A> codec, String... keys) {
        for (String key : keys) {
            if (has(key)) return maybe(key, codec);
        }
        return Optional.empty();
    }

    /** A required number inside an inclusive range. Out of range is a fault, not a clamp. */
    float rangedFloat(String key, float min, float max) {
        Optional<Dynamic<T>> node = dynamic.get(key).result();
        if (node.isEmpty()) {
            faults.add("Missing required field: " + key);
            return min;
        }
        return checkedFloat(key, node.get(), min, max, min);
    }

    /** An optional number inside an inclusive range, falling back when the key is absent. */
    float rangedFloat(String key, float min, float max, float fallback) {
        Optional<Dynamic<T>> node = dynamic.get(key).result();
        return node.isEmpty() ? fallback : checkedFloat(key, node.get(), min, max, fallback);
    }

    int rangedInt(String key, int min, int max) {
        Optional<Dynamic<T>> node = dynamic.get(key).result();
        if (node.isEmpty()) {
            faults.add("Missing required field: " + key);
            return min;
        }
        return checkedInt(key, node.get(), min, max, min);
    }

    int rangedInt(String key, int min, int max, int fallback) {
        Optional<Dynamic<T>> node = dynamic.get(key).result();
        return node.isEmpty() ? fallback : checkedInt(key, node.get(), min, max, fallback);
    }

    private float checkedFloat(String key, Dynamic<T> node, float min, float max, float fallback) {
        float value = node.asFloat(Float.NaN);
        if (Float.isNaN(value)) {
            faults.add("Invalid float for field: " + key);
            return fallback;
        }
        if (value < min || value > max) {
            faults.add(key + " out of range [" + min + ", " + max + "]: " + value);
            return fallback;
        }
        return value;
    }

    private int checkedInt(String key, Dynamic<T> node, int min, int max, int fallback) {
        int value = node.asInt(Integer.MIN_VALUE);
        if (value == Integer.MIN_VALUE) {
            faults.add("Invalid int for field: " + key);
            return fallback;
        }
        if (value < min || value > max) {
            faults.add(key + " out of range [" + min + ", " + max + "]: " + value);
            return fallback;
        }
        return value;
    }

    private <A> A parse(String key, Codec<A> codec, Dynamic<T> node, A fallback) {
        DataResult<A> parsed = codec.parse(node);
        Optional<A> value = parsed.result();
        if (value.isPresent()) return value.get();
        faults.add(key + ": " + parsed.error().map(DataResult.Error::message).orElse("could not be read"));
        return fallback;
    }
}
