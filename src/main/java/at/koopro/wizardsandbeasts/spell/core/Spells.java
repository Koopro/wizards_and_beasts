package at.koopro.wizardsandbeasts.spell.core;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.impl.*;
import at.koopro.wizardsandbeasts.util.NamespaceMigration;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of all spells. Follows the Minecraft Items/Blocks pattern.
 * Call {@link #init()} during mod initialization to finalize all spells.
 *
 * <p><b>Three registration paths:</b>
 * <ul>
 *   <li><b>Java spells</b>: declared as static fields below; finalized by
 *       {@link #init()} during mod construction. Use bare ids (e.g. {@code "lumos"}).</li>
 *   <li><b>Addon Java spells</b>: registered by third-party mods via the
 *       {@link at.koopro.wizardsandbeasts.event.RegisterSpellsEvent} during common setup.
 *       Use {@code register(Spell)} from inside the event handler.</li>
 *   <li><b>JSON / datapack spells</b>: loaded by
 *       {@link at.koopro.wizardsandbeasts.spell.def.SpellReloadListener} on every reload via
 *       {@link #registerJson(Spell)} (which initializes immediately because the
 *       registry phase is already past). {@link #clearJsonSpells()} runs first
 *       so the reload is idempotent.</li>
 * </ul>
 */
public final class Spells {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, Spell> BY_ID = new LinkedHashMap<>();
    /** Ids contributed by JSON spells; tracked so {@link #clearJsonSpells()} only touches those. */
    private static final Set<String> JSON_SPELL_IDS = new HashSet<>();
    /** True once {@link #init()} has run; subsequent registrations must self-init. */
    private static boolean bootstrapped = false;

    // Combat
    // Step 3/4: stupefy, incendio, diffindo, bombarda, confringo, flipendo, glacius, depulso
    // migrated to JSON (data/wizards_and_beasts/spells/*.json) — no Java classes.
    public static final Expelliarmus EXPELLIARMUS = register(new Expelliarmus());

    // Utility
    // Step 3/4: lumos, nox, reparo, liberacorpus, arresto_momentum migrated to JSON — no Java classes.
    public static final Accio ACCIO = register(new Accio());
    public static final WingardiumLeviosa WINGARDIUM_LEVIOSA = register(new WingardiumLeviosa());
    public static final Alohomora ALOHOMORA = register(new Alohomora());
    public static final Colloportus COLLOPORTUS = register(new Colloportus());
    public static final Riddikulus RIDDIKULUS = register(new Riddikulus());
    public static final Aguamenti AGUAMENTI = register(new Aguamenti());

    // Defense
    public static final Protego PROTEGO = register(new Protego());
    public static final ExpectoPatronum EXPECTO_PATRONUM = register(new ExpectoPatronum());

    // Dark Arts
    public static final AvadaKedavra AVADA_KEDAVRA = register(new AvadaKedavra());
    public static final Crucio CRUCIO = register(new Crucio());
    public static final Imperio IMPERIO = register(new Imperio());
    public static final ObscurusSurge OBSCURUS_SURGE = register(new ObscurusSurge());
    public static final ObscurusGrasp OBSCURUS_GRASP = register(new ObscurusGrasp());

    private Spells() {}

    /**
     * Registers a Java spell. Called from this class's static field initializers
     * (before {@link #init()}) and from
     * {@link at.koopro.wizardsandbeasts.event.RegisterSpellsEvent} handlers.
     *
     * <p>If the registry is already bootstrapped (i.e. {@link #init()} ran),
     * the spell is initialized immediately so it is usable straight away;
     * otherwise it will be initialized as part of the normal {@link #init()}
     * sweep. JSON spells should use {@link #registerJson(Spell)} instead so
     * that {@code /reload} can clear them.
     */
    public static <T extends Spell> T register(T spell) {
        if (BY_ID.containsKey(spell.getId())) {
            LOGGER.warn("Spell id collision: '{}' is already registered; replacing.", spell.getId());
        }
        BY_ID.put(spell.getId(), spell);
        if (bootstrapped) {
            spell.init();
        }
        return spell;
    }

    /**
     * Registers a JSON-loaded spell. Tracked separately so {@link #clearJsonSpells()}
     * can remove only datapack-contributed spells on reload. Always self-inits
     * because reloads happen long after the {@link #init()} bootstrap.
     */
    public static <T extends Spell> T registerJson(T spell) {
        Spell previous = BY_ID.get(spell.getId());
        if (previous != null) {
            LOGGER.warn("JSON spell id collision: '{}' already registered as {}; replacing with {}.",
                    spell.getId(), previous.getClass().getSimpleName(), spell.getClass().getSimpleName());
        }
        JSON_SPELL_IDS.add(spell.getId());
        BY_ID.put(spell.getId(), spell);
        spell.init();
        return spell;
    }

    /** Removes every spell previously contributed by {@link #registerJson(Spell)}. */
    public static void clearJsonSpells() {
        for (String id : JSON_SPELL_IDS) {
            BY_ID.remove(id);
        }
        JSON_SPELL_IDS.clear();
    }

    /**
     * Two-phase init: all Java spells are constructed first (populating BY_ID),
     * then properties and requirements are built (so cross-references are safe).
     */
    public static void init() {
        for (Spell spell : BY_ID.values()) {
            spell.init();
        }
        bootstrapped = true;
    }

    /**
     * Looks up a spell by id. Accepts both bare ids (e.g. {@code "lumos"}) and
     * full namespaced ids (e.g. {@code "wizards_and_beasts:lumos"} or {@code "othermod:firebolt"}).
     * For bare-id input, falls back to the mod namespace plus {@code id} if the bare lookup
     * misses, so JSON spells whose registry id is namespaced remain reachable
     * by short name from existing code paths.
     */
    @Nullable
    public static Spell byId(String id) {
        if (id == null) return null;
        id = NamespaceMigration.remapLegacyId(id);
        Spell direct = BY_ID.get(id);
        if (direct != null) return direct;
        if (!id.contains(":")) {
            return BY_ID.get(WizardsAndBeastsMod.MODID + ":" + id);
        }
        return null;
    }

    public static Collection<Spell> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static int count() {
        return BY_ID.size();
    }
}
