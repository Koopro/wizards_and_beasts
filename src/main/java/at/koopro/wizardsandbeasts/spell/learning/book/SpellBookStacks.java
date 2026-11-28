package at.koopro.wizardsandbeasts.spell.learning.book;

import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import at.koopro.wizardsandbeasts.spell.learning.SpellSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/** Makes textbook stacks: a {@code standard_book_of_spells} carrying the {@code spell_book} id. */
@NullMarked
public final class SpellBookStacks {

    private SpellBookStacks() {
    }

    /** One copy of the textbook {@code bookId} (bare or namespaced). */
    public static ItemStack of(String bookId) {
        ItemStack stack = new ItemStack(CanonItemRegistry.STANDARD_BOOK_OF_SPELLS.get());
        SpellSource.writeBook(stack, bookId);
        return stack;
    }
}
