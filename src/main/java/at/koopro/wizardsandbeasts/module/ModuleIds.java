package at.koopro.wizardsandbeasts.module;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The stable {@link Identifier} for each {@link Module}, used as the persisted key for module state and
 * settings and as the wire key for sync.
 *
 * <p>The id is the lowercased enum constant name under the mod namespace — matching what
 * {@code AbilityResolver} already does when resolving an ability's {@code module} field, so a datapack that
 * writes {@code wizards_and_beasts:dark_arts} means the same module here.
 *
 * <p><b>Renaming a {@link Module} constant changes its id and orphans any state stored under the old one.</b>
 * Stored state for an unknown id is dropped with a warning rather than guessed at; if a rename is ever
 * needed, migrate the saved data deliberately.
 */
@NullMarked
public final class ModuleIds {

    private static final Map<Module, Identifier> TO_ID = new HashMap<>();
    private static final Map<Identifier, Module> FROM_ID = new HashMap<>();

    static {
        for (Module module : Module.values()) {
            Identifier id = Identifier.fromNamespaceAndPath(
                    WizardsAndBeastsMod.MODID, module.name().toLowerCase(Locale.ROOT));
            TO_ID.put(module, id);
            FROM_ID.put(id, module);
        }
    }

    /** Serializes a module as its identifier; unknown ids fail the codec rather than defaulting. */
    public static final Codec<Module> CODEC = Identifier.CODEC.comapFlatMap(
            id -> {
                Module module = FROM_ID.get(id);
                return module == null
                        ? com.mojang.serialization.DataResult.error(() -> "Unknown module id: " + id)
                        : com.mojang.serialization.DataResult.success(module);
            },
            ModuleIds::of);

    private ModuleIds() {}

    public static Identifier of(Module module) {
        return TO_ID.get(module);
    }

    @Nullable
    public static Module byId(Identifier id) {
        return FROM_ID.get(id);
    }

    /**
     * The module's translated name, from {@code module.<namespace>.<path>.name}.
     *
     * <p>Datagen writes one of these for every module. Callers were building the key by hand from
     * {@code module.name().toLowerCase(ROOT)}, which is the same derivation as {@link #of} and drifts the
     * moment either changes; deriving it from the id keeps one answer.
     */
    public static Component displayName(Module module) {
        Identifier id = of(module);
        return Component.translatable("module." + id.getNamespace() + "." + id.getPath() + ".name");
    }

    /** Accepts a bare path ({@code dark_arts}) or a full id ({@code wizards_and_beasts:dark_arts}). */
    @Nullable
    public static Module parse(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return null;
        }
        Identifier id = trimmed.contains(":")
                ? Identifier.tryParse(trimmed)
                : Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, trimmed);
        return id == null ? null : FROM_ID.get(id);
    }
}
