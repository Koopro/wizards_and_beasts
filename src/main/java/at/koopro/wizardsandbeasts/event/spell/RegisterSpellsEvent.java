package at.koopro.wizardsandbeasts.event.spell;

import at.koopro.wizardsandbeasts.spell.core.JsonSpell;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.def.SpellDefinition;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import javax.annotation.Nullable;

/**
 * Mod-bus event fired during common setup that lets <strong>third-party mods</strong>
 * contribute spells without touching WizardsAndBeastsMod source. Subscribers receive a thin
 * facade over the {@link Spells} registry — they cannot reach in and mutate
 * other state.
 *
 * <p>Fire order: this event is dispatched <em>before</em> {@link Spells#init()},
 * so registered spells go through the normal two-phase finalization and may
 * declare requirements that reference other spells (including yours).
 *
 * <p>Usage from an addon mod:
 * <pre>{@code
 * @SubscribeEvent
 * public static void onRegisterSpells(RegisterSpellsEvent event) {
 *     event.register(new MyCustomSpell());           // Java spell
 *     event.registerFromDefinition("foo", myDefinition); // data-driven spell
 * }
 * }</pre>
 */
public final class RegisterSpellsEvent extends Event implements IModBusEvent {

    public RegisterSpellsEvent() {
    }

    /**
     * Registers a Java {@link Spell}. The spell must have a unique id; if it
     * collides with an existing spell the previous registration is replaced
     * (with a warning).
     *
     * @return the spell, for fluent chaining.
     */
    public <T extends Spell> T register(T spell) {
        return Spells.register(spell);
    }

    /**
     * Convenience: builds and registers a {@link JsonSpell} from a code-defined
     * {@link SpellDefinition}. Useful for addons that want data-driven spells
     * without ship-and-load JSON files.
     *
     * @param namespace mod id used as the spell-id namespace
     * @param path bare path part of the spell id
     * @param def the spell definition
     * @return the registered spell, for fluent chaining
     */
    @Nullable
    public JsonSpell registerFromDefinition(String namespace, String path, SpellDefinition def) {
        if (namespace == null || namespace.isEmpty() || path == null || path.isEmpty()) {
            return null;
        }
        JsonSpell spell = new JsonSpell(namespace + ":" + path, def);
        return Spells.register(spell);
    }
}
