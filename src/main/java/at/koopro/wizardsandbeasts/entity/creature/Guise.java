package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.util.AnimHelper;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Another creature's shape, worn by a shapeshifter: the creature whose model, skin and clips it borrows, and which of
 * that creature's clips it moves with ({@code walk} for most, {@code float} for a Dementor).
 *
 * <p>Carried to the client as one synced string, {@code form} or {@code form/walkClip}, so the renderer and the
 * animation controller both read it from the entity without a second field. Decoded values are cached: the controller
 * asks every frame.
 *
 * @param form     asset name of the borrowed creature ({@code dementor} for {@code geckolib/models/entity/dementor})
 * @param walkClip the borrowed creature's locomotion clip
 */
public record Guise(String form, String walkClip) {

    private static final Map<String, Guise> DECODED = new ConcurrentHashMap<>();
    private static final Map<String, RawAnimation> LOOPS = new ConcurrentHashMap<>();

    public Guise {
        if (form.isBlank() || form.contains("/") || walkClip.isBlank() || walkClip.contains("/")) {
            throw new IllegalArgumentException("bad guise " + form + "/" + walkClip);
        }
    }

    /** The synced form of this guise. */
    public String encode() {
        return "walk".equals(walkClip) ? form : form + "/" + walkClip;
    }

    /** The guise a synced string stands for, or {@code null} for the creature's own shape ({@code ""}). */
    public static @Nullable Guise decode(String encoded) {
        if (encoded.isEmpty()) {
            return null;
        }
        return DECODED.computeIfAbsent(encoded, s -> {
            int slash = s.indexOf('/');
            return slash < 0 ? new Guise(s, "walk") : new Guise(s.substring(0, slash), s.substring(slash + 1));
        });
    }

    public RawAnimation idle() {
        return loop(form, "idle");
    }

    public RawAnimation moving() {
        return loop(form, walkClip);
    }

    private static RawAnimation loop(String form, String clip) {
        return LOOPS.computeIfAbsent(form + "." + clip, key -> AnimHelper.loop(form, clip));
    }
}
