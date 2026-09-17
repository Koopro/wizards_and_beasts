package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;

/**
 * Spell law as the datapack authored it: {@code data/<namespace>/spell_law/<spell>.json}, keyed by the spell's
 * id. A spell with no file is read from its category by {@link SpellLaw#defaultFor}.
 *
 * <p>The three Unforgivables keep a floor in code: a datapack that deletes their files, or never loads, leaves
 * them Unforgivable rather than quietly legal. A pack may still author them differently on purpose.
 */
@NullMarked
public final class SpellLawRegistry {

    private static volatile Map<Identifier, SpellLaw> LAWS = Map.of();

    private SpellLawRegistry() {}

    public static void replaceAll(Map<Identifier, SpellLaw> laws) {
        LAWS = Map.copyOf(laws);
    }

    public static int count() {
        return LAWS.size();
    }

    /** The law for a spell id, bare ({@code crucio}) or namespaced. */
    public static SpellLaw lawFor(String spellId, @Nullable SpellCategory category) {
        Identifier id = spellId.indexOf(':') < 0
                ? Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, spellId)
                : Identifier.tryParse(spellId);
        SpellLaw authored = id == null ? null : LAWS.get(id);
        if (authored != null) {
            return authored;
        }
        MagicalOffence unforgivable = MagicalOffence.forSpell(spellId);
        if (unforgivable != null) {
            return new SpellLaw(LegalClass.UNFORGIVABLE, 2, Optional.of(unforgivable));
        }
        return SpellLaw.defaultFor(category);
    }

    /** Loads {@code spell_law/}. Wired from the mod's server reload listeners. */
    public static final class Loader extends SimpleJsonResourceReloadListener<SpellLaw> {

        private static final Logger LOGGER = LogUtils.getLogger();

        public static final String DIRECTORY = "spell_law";

        public Loader() {
            super(SpellLaw.CODEC, FileToIdConverter.json(DIRECTORY));
        }

        @Override
        protected void apply(Map<Identifier, SpellLaw> entries, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            replaceAll(entries);
            LOGGER.info("[W&B] Loaded {} spell law entries.", count());
        }
    }
}
