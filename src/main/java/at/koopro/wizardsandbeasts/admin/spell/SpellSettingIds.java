package at.koopro.wizardsandbeasts.admin.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The id scheme of per-spell settings: {@code wizards_and_beasts:spell/<spell namespace>/<spell path>/<property>}.
 *
 * <p>An ordinary admin setting id, so a spell value travels through the same change/reset payloads, history and
 * undo as any other setting. The spell's own id is embedded whole (its path may contain slashes), with the
 * property as the last segment.
 */
@NullMarked
public final class SpellSettingIds {

    public static final String PREFIX = "spell/";

    private SpellSettingIds() {}

    public record Parsed(String spellId, SpellProperty property) {}

    public static Identifier of(String spellId, SpellProperty property) {
        Identifier spell = Identifier.parse(spellId.indexOf(':') >= 0 ? spellId : WizardsAndBeastsMod.MODID + ":" + spellId);
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                PREFIX + spell.getNamespace() + "/" + spell.getPath() + "/" + property.id());
    }

    public static boolean isSpellSetting(Identifier id) {
        return id.getNamespace().equals(WizardsAndBeastsMod.MODID) && id.getPath().startsWith(PREFIX);
    }

    /**
     * The spell a parsed id names. Java spells register under bare ids ({@code avada_kedavra}) while setting ids
     * always carry a namespace, so this mod's namespace falls back to the bare form.
     */
    public static at.koopro.wizardsandbeasts.spell.core.@Nullable Spell resolveSpell(String spellId) {
        at.koopro.wizardsandbeasts.spell.core.Spell spell = at.koopro.wizardsandbeasts.spell.core.Spells.byId(spellId);
        String ownPrefix = WizardsAndBeastsMod.MODID + ":";
        if (spell == null && spellId.startsWith(ownPrefix)) {
            spell = at.koopro.wizardsandbeasts.spell.core.Spells.byId(spellId.substring(ownPrefix.length()));
        }
        return spell;
    }

    /** Splits a setting id back into spell id and property, or null when it is not a well-formed spell setting id. */
    public static @Nullable Parsed parse(Identifier id) {
        if (!isSpellSetting(id)) {
            return null;
        }
        String rest = id.getPath().substring(PREFIX.length());
        int firstSlash = rest.indexOf('/');
        int lastSlash = rest.lastIndexOf('/');
        if (firstSlash <= 0 || lastSlash <= firstSlash + 1 || lastSlash == rest.length() - 1) {
            return null;
        }
        SpellProperty property = SpellProperty.byId(rest.substring(lastSlash + 1));
        if (property == null) {
            return null;
        }
        return new Parsed(rest.substring(0, firstSlash) + ":" + rest.substring(firstSlash + 1, lastSlash), property);
    }
}
