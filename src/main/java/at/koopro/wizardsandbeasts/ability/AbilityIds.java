package at.koopro.wizardsandbeasts.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * Ids of the abilities that ship with real behaviors, shared by the datapack definitions, the behavior
 * registrations and the grant source. Keeping them in one place is what makes the grant source and the
 * {@code data/wizards_and_beasts/abilities/*.json} file names verifiably the same key.
 */
@NullMarked
public final class AbilityIds {

    public static final Identifier APPARITION = id("apparition");
    public static final Identifier LEGILIMENCY = id("legilimency");
    public static final Identifier ANIMAGUS_FORM = id("animagus_form");

    private AbilityIds() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }
}
