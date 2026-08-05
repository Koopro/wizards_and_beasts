package at.koopro.wizardsandbeasts.animagus;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * Translates between the form id persisted on {@code PlayerAbilityData} and the datapack registry.
 * <p>
 * The stored value is a plain {@code String} and stays one. It predates this registry, every live
 * reader keys on it ({@code AnimagusForms}, {@code FormRegistry}, {@code SizeProfileRegistry},
 * {@code TransitionManager}, the renderer's form switch), and it sits in every existing save.
 * Widening the column would have meant a migration plus a sweep through all of those for no gain
 * the players would notice, so the two vocabularies are reconciled here instead.
 * <p>
 * Legacy ids are prefixed ({@code "animagus_cat"}); datapack ids are namespaced
 * ({@code "wizards_and_beasts:cat"}). A legacy id with no datapack counterpart resolves to empty
 * rather than to a guess — {@code animagus_stag} and friends have no definition yet, and inventing
 * one would let a form load with someone else's physics.
 */
@NullMarked
public final class AnimagusFormBinding {

    /** Prefix on the pre-registry form ids stored in player data. */
    private static final String LEGACY_PREFIX = "animagus_";

    private AnimagusFormBinding() {}

    /**
     * Resolves a stored form id to its datapack key. Accepts both vocabularies, so a save written
     * before or after this registry existed reads the same way.
     */
    public static Optional<Identifier> toFormKey(@Nullable String storedFormId) {
        if (storedFormId == null || storedFormId.isBlank()) {
            return Optional.empty();
        }
        String value = storedFormId.trim().toLowerCase(Locale.ROOT);

        if (value.indexOf(':') >= 0) {
            String[] split = value.split(":", 2);
            return Optional.of(Identifier.fromNamespaceAndPath(split[0], split[1]));
        }

        String beast = value.startsWith(LEGACY_PREFIX) ? value.substring(LEGACY_PREFIX.length()) : value;
        if (beast.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, beast));
    }

    /**
     * The stored-id form of a datapack key, so a form chosen through the new registry is written
     * back in the vocabulary the existing transform path reads.
     */
    public static String toStoredId(Identifier formKey) {
        return LEGACY_PREFIX + formKey.getPath();
    }

    /** Resolves a stored form id straight to its definition, or empty if it has none. */
    public static Optional<AnimagusFormDefinition> resolve(@Nullable String storedFormId) {
        return toFormKey(storedFormId).map(AnimagusFormRegistry::get);
    }

    /** Client-side counterpart of {@link #resolve}, reading the synced cache. */
    public static Optional<AnimagusFormDefinition> resolveClient(@Nullable String storedFormId) {
        return toFormKey(storedFormId).map(AnimagusFormRegistry::clientGet);
    }

    /** True when the stored id names a form the datapack registry actually defines. */
    public static boolean isDefined(@Nullable String storedFormId) {
        return resolve(storedFormId).isPresent();
    }
}
