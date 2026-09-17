package at.koopro.wizardsandbeasts.heritage.appearance;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.MagicalCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * How one heritage — or one variant of it — expresses itself on the player's body in third person.
 *
 * <p><b>An override layer, not a replacement.</b> A full form system already ships: {@code
 * FormRegistry}, {@code SizeProfileRegistry} and {@code HeritageFormBridge} are hardcoded static
 * registries, and {@code LivingEntityRendererMixin} already swaps the model for every non-humanoid
 * form. This registry sits <em>in front</em> of them:
 *
 * <pre>
 *   HeritageAppearance datapack entry   (may be absent)
 *           ↓
 *   HeritageFormBridge + FormRegistry + SizeProfileRegistry   (always present)
 *           ↓
 *   nothing rendered
 * </pre>
 *
 * <p>Nothing is deleted by its existence. What it buys is that a mechanism assignment can be changed,
 * an asset swapped and a provenance corrected by editing one JSON and running {@code /reload}, rather
 * than by recompiling — which is also what makes the in-game verification matrix testable at all.
 *
 * <p><b>Resolution is by heritage and variant; rendering keys off the active form id.</b> The server
 * syncs {@code activeFormId} to every tracking client ({@code FormSyncS2CPayload}), which is what makes
 * a remote player's shape renderable at all.
 *
 * <p>{@code transformationState} now reaches every tracking client too, on
 * {@code HeritageIdentitySyncS2CPayload}, and is readable per-UUID from
 * {@code ClientHeritageIdentityState}. It previously travelled only to the player it belonged to, so
 * anything keyed off it worked in single-player and silently failed for every remote player. Read it
 * from the per-UUID map, never from {@code ClientHeritageDataState} — that still holds a single
 * instance and still describes only the local player.
 *
 * @param id          the entry's own identifier, taken from its file path, mirroring
 *                    {@code BestiaryEntry.id}
 * <p><b>Conditions.</b> An entry may describe a {@link MagicalCondition} instead of a heritage — a werewolf's change
 * belongs to lycanthropy, not to whatever lineage the wizard came from. Such an entry leaves {@code heritage} empty and
 * names the {@code condition}. It is resolved server-side only ({@link HeritageAppearanceRegistry#resolveCondition}):
 * a condition is not public knowledge — Lupin kept his from the school for a year — so it is never broadcast, and its
 * visible expression on other clients is the form it puts the player in, which already syncs.
 *
 * @param heritage    the {@link Heritage#getId()} this describes, or empty for a condition entry
 * @param variant     optional {@link HeritageVariant#getId()}; present only where variants of one
 *                    heritage look different from each other. A variant entry wins over the
 *                    heritage-wide entry for that variant, and the heritage-wide entry covers the rest
 * @param mechanisms  at most one body arm ({@code proportion} or {@code form}) plus at most one
 *                    {@code overlay}. Empty is legal and renders nothing
 * @param provenance  canon citation, or an explicit fan-extrapolation admission
 * @param condition   optional {@link MagicalCondition#getId()}; present exactly when {@code heritage} is empty
 */
public record HeritageAppearance(
        Identifier id,
        String heritage,
        Optional<String> variant,
        List<AppearanceMechanism> mechanisms,
        Provenance provenance,
        Optional<String> condition
) {

    /** A heritage entry, which is every entry but a condition's. */
    public HeritageAppearance(Identifier id, String heritage, Optional<String> variant,
                              List<AppearanceMechanism> mechanisms, Provenance provenance) {
        this(id, heritage, variant, mechanisms, provenance, Optional.empty());
    }

    /** Whether this entry describes a {@link MagicalCondition} rather than a heritage. */
    public boolean isCondition() {
        return condition.isPresent();
    }

    public static final Codec<HeritageAppearance> CODEC =
            RecordCodecBuilder.<HeritageAppearance>create(instance -> instance.group(
                    Identifier.CODEC.fieldOf("id").forGetter(HeritageAppearance::id),
                    Codec.STRING.optionalFieldOf("heritage", "").forGetter(HeritageAppearance::heritage),
                    Codec.STRING.optionalFieldOf("variant").forGetter(HeritageAppearance::variant),
                    AppearanceMechanism.CODEC.listOf()
                            .optionalFieldOf("mechanisms", List.of()).forGetter(HeritageAppearance::mechanisms),
                    Provenance.CODEC.fieldOf("provenance").forGetter(HeritageAppearance::provenance),
                    Codec.STRING.optionalFieldOf("condition").forGetter(HeritageAppearance::condition)
            ).apply(instance, HeritageAppearance::new)).validate(HeritageAppearance::validate);

    /** The {@code proportion} arm, or null. Mutually exclusive with {@link #form()}. */
    public @Nullable Proportion proportion() {
        return firstOf(Proportion.class);
    }

    /** The {@code form} arm, or null. Mutually exclusive with {@link #proportion()}. */
    public @Nullable FormAppearance form() {
        return firstOf(FormAppearance.class);
    }

    /** The {@code overlay} arm, or null. May accompany either body arm. */
    public @Nullable OverlayAppearance overlay() {
        return firstOf(OverlayAppearance.class);
    }

    /** True when this entry would draw nothing, so a resolver can fall through to the shipped defaults. */
    public boolean isEmpty() {
        return mechanisms.isEmpty();
    }

    private <T extends AppearanceMechanism> @Nullable T firstOf(Class<T> arm) {
        for (AppearanceMechanism mechanism : mechanisms) {
            if (arm.isInstance(mechanism)) {
                return arm.cast(mechanism);
            }
        }
        return null;
    }

    /**
     * Cross-field invariants. Each of these parses fine field-by-field and only misbehaves once a
     * player wears the heritage, which is exactly the class of bug that has to fail at datapack load.
     */
    private static DataResult<HeritageAppearance> validate(HeritageAppearance entry) {
        if (entry.condition.isPresent()) {
            if (!entry.heritage.isEmpty()) {
                return DataResult.error(() -> "entry names both heritage '" + entry.heritage + "' and condition '"
                        + entry.condition.get() + "'; a condition is carried by any heritage, so name only one");
            }
            if (MagicalCondition.byId(entry.condition.get()) == null) {
                return DataResult.error(() -> "unknown condition '" + entry.condition.get() + "'");
            }
            if (entry.variant.isPresent()) {
                return DataResult.error(() -> "a condition entry cannot name a variant");
            }
            return validateMechanisms(entry);
        }
        Heritage heritage = Heritage.byId(entry.heritage);
        if (heritage == null) {
            return DataResult.error(() -> entry.heritage.isEmpty()
                    ? "entry names neither a heritage nor a condition"
                    : "unknown heritage '" + entry.heritage + "'");
        }

        if (entry.variant.isPresent()) {
            HeritageVariant variant = HeritageVariant.byId(entry.variant.get());
            if (variant == null) {
                return DataResult.error(() -> "unknown heritage variant '" + entry.variant.get() + "'");
            }
            if (variant.getParentHeritage() != heritage) {
                return DataResult.error(() -> "variant '" + entry.variant.get() + "' belongs to heritage '"
                        + variant.getParentHeritage().getId() + "', not '" + entry.heritage + "'");
            }
        }

        return validateMechanisms(entry);
    }

    private static DataResult<HeritageAppearance> validateMechanisms(HeritageAppearance entry) {
        int bodyArms = 0;
        int overlays = 0;
        for (AppearanceMechanism mechanism : entry.mechanisms) {
            if (mechanism.isBodyArm()) {
                bodyArms++;
            } else {
                overlays++;
            }
        }
        final int declaredBodyArms = bodyArms;
        final int declaredOverlays = overlays;
        if (declaredBodyArms > 1) {
            return DataResult.error(() -> "entry declares " + declaredBodyArms
                    + " body mechanisms; proportion and form are mutually exclusive and at most one may appear");
        }
        if (declaredOverlays > 1) {
            return DataResult.error(() -> "entry declares " + declaredOverlays
                    + " overlay mechanisms; at most one may appear");
        }
        return DataResult.success(entry);
    }
}
