package at.koopro.wizardsandbeasts.brew.knowledge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The brewing recipes one player has learned: from a textbook, a loose page, a lesson, or a successful experiment.
 * The basics everyone knows ({@code brew_recipe_sources/basic.json}) are not written here -- they are known by
 * everyone, so a datapack can change them without touching saves.
 */
@NullMarked
public record BrewRecipeBook(Set<String> known) {

    public static final BrewRecipeBook EMPTY = new BrewRecipeBook(Set.of());

    public static final Codec<BrewRecipeBook> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("known", List.of()).forGetter(b -> List.copyOf(b.known()))
    ).apply(i, list -> new BrewRecipeBook(Set.copyOf(list))));

    public BrewRecipeBook {
        known = Set.copyOf(new TreeSet<>(known));
    }

    public boolean has(String recipeId) {
        return known.contains(recipeId);
    }

    public BrewRecipeBook with(Collection<String> recipeIds) {
        TreeSet<String> next = new TreeSet<>(known);
        next.addAll(recipeIds);
        return new BrewRecipeBook(next);
    }
}
