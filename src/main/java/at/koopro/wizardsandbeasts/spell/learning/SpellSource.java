package at.koopro.wizardsandbeasts.spell.learning;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The written record of one spell, carried on an item stack.
 *
 * <p>A wizard does not buy an incantation across a counter. They read it — out of a textbook, off a
 * torn page, out of somebody else's notes — and then practise it until it works. This class is the
 * "read it" half: the {@code spell_source} data component that turns an otherwise ordinary book into
 * <em>that</em> book, the one with Lumos written in it.
 *
 * <h2>Why a component and not an item per spell</h2>
 * There are 128 registered spells. A registered item for each would be 128 ids, 128 sprites and 128
 * lang keys for what is one object with one variable field. Keeping the spell in a component means a
 * loot table, a recipe, a command, a creative-tab stack and {@code SpellScriptoriumBlock}'s scribing
 * path all write the same field, and the item itself stays a single registered thing.
 *
 * <p>A stack with no component is a <em>blank</em> book — legal, purchasable, and the input to
 * scribing. {@link #spellOf} returning {@code null} means blank, not broken.
 */
@NullMarked
public final class SpellSource {

    private SpellSource() {
    }

    /** The raw id written on this stack, or {@code null} if the pages are blank. */
    public static @Nullable String spellIdOf(ItemStack stack) {
        String id = stack.get(ModDataComponents.SPELL_SOURCE.get());
        return id == null || id.isBlank() ? null : id;
    }

    /**
     * The spell this stack teaches, or {@code null} if the pages are blank <em>or</em> name a spell
     * that no longer exists. Both cases read the same way to the player — nothing usable is written
     * here — so they are deliberately not distinguished.
     */
    public static @Nullable Spell spellOf(ItemStack stack) {
        String id = spellIdOf(stack);
        return id == null ? null : Spells.byId(id);
    }

    public static boolean isBlank(ItemStack stack) {
        return spellIdOf(stack) == null;
    }

    /**
     * The written spell's name, resolved from the id alone rather than through {@code Spells}.
     *
     * <p>Not a workaround for an empty client registry — {@code SpellDefinitionsSyncS2CPayload}
     * mirrors the datapack table to every client on login and on {@code /reload}, so
     * {@code Spells.byId} does answer here. It is registry-free because a book's <em>title</em> should
     * not depend on the spell still existing: an id whose definition a pack has since removed, or a
     * legacy id from an old save, resolves to {@code null}, and a title built that way would silently
     * become a plain untitled textbook. The id is the only thing the stack actually carries, so the
     * name is built from that and nothing else.
     *
     * <p>The lang key is a convention both halves of the corpus keep: a JSON spell's
     * {@code displayName} <em>is</em> {@code spell.<namespace>.<path>.name}, and the Java spells have
     * the same keys. The fallback covers an id with no key at all — a pack's own spell — by
     * prettifying the path, so a book is never nameless.
     */
    public static Component writtenName(String spellId) {
        String path = spellId.substring(spellId.indexOf(':') + 1);
        return Component.translatableWithFallback("spell.wizards_and_beasts." + path + ".name", prettify(path));
    }

    /** {@code wingardium_leviosa} → {@code Wingardium Leviosa}. Last resort, for an id with no lang key. */
    private static String prettify(String path) {
        StringBuilder out = new StringBuilder(path.length());
        boolean capitalise = true;
        for (char c : path.toCharArray()) {
            if (c == '_') {
                out.append(' ');
                capitalise = true;
            } else {
                out.append(capitalise ? Character.toUpperCase(c) : c);
                capitalise = false;
            }
        }
        return out.toString();
    }

    /**
     * Writes a spell onto a stack, storing the canonical id rather than whatever alias was passed.
     * Storing the alias would leave two stacks that teach the same spell looking different to every
     * comparison the game makes on components, including stacking.
     */
    public static void write(ItemStack stack, Spell spell) {
        stack.set(ModDataComponents.SPELL_SOURCE.get(), spell.getId());
    }
}
