package at.koopro.wizardsandbeasts.creature.variant;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.CreatureDefinition;
import at.koopro.wizardsandbeasts.creature.CreatureDefinitionRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Coats a creature can be born with, declared in its definition JSON instead of in a Java enum:
 *
 * <pre>{@code "coats": {"base_weight": 98, "variants": [
 *     {"id": "smoke", "weight": 2, "origin": "design", "note": "..."}]}}</pre>
 *
 * <p>Cosmetic only: a coat is a texture, {@code textures/entity/<creature>/<id>.png}, nothing else. The base coat is
 * the creature's own texture and always exists. {@code origin} says where a coat comes from — {@code canon} (the
 * Fwooper's four colours, the Pygmy Puff's purple, the Salamander's fire-heat colours) or {@code design} (a rare
 * coat the mod adds) — so the bestiary and the reviewer can tell the two apart.
 *
 * <p>Definitions are synced to clients ({@code CreatureDefinitionSyncEvents}), so both sides read the same list;
 * the synced entity carries only the index.
 */
@NullMarked
public record Coats(int baseWeight, List<Coat> variants) {

    public static final Coats NONE = new Coats(100, List.of());

    public record Coat(String id, int weight, String origin, Optional<String> note) {
        static final Codec<Coat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Coat::id),
                Codec.INT.optionalFieldOf("weight", 2).forGetter(Coat::weight),
                Codec.STRING.optionalFieldOf("origin", "design").forGetter(Coat::origin),
                Codec.STRING.optionalFieldOf("note").forGetter(Coat::note)
        ).apply(i, Coat::new));
    }

    public static final Codec<Coats> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("base_weight", 100).forGetter(Coats::baseWeight),
            Coat.CODEC.listOf().optionalFieldOf("variants", List.of()).forGetter(Coats::variants)
    ).apply(i, Coats::new));

    /** One coat as the shared variant machinery sees it. Index 0 is the base coat (the creature's own texture). */
    public record DataCoat(String id, int index, int weight, @Nullable String texture) implements CreatureVariant {
        @Override
        public String name() {
            return id.toUpperCase(Locale.ROOT);
        }

        @Override
        public int ordinal() {
            return index;
        }

        @Override
        public String variantId() {
            return id;
        }

        @Override
        public @Nullable String variantTexture() {
            return texture;
        }

        @Override
        public int authoredWeight() {
            return weight;
        }
    }

    /** The coats as variants, base first; empty when the creature declares none (or is not a data creature). */
    public List<DataCoat> asVariants(String creatureId) {
        if (variants.isEmpty()) {
            return List.of();
        }
        List<DataCoat> out = new ArrayList<>(variants.size() + 1);
        out.add(new DataCoat("base", 0, baseWeight, null));
        for (int i = 0; i < variants.size(); i++) {
            Coat c = variants.get(i);
            out.add(new DataCoat(c.id(), i + 1, c.weight(), creatureId + "/" + c.id()));
        }
        return List.copyOf(out);
    }

    /** The data coats of a creature by its path id ({@code kneazle}), or an empty list. */
    public static List<DataCoat> of(String creatureId) {
        CreatureDefinition def = CreatureDefinitionRegistry.get(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, creatureId));
        return def == null ? List.of() : def.coats().asVariants(creatureId);
    }
}
