package at.koopro.wizardsandbeasts.spell.core;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.impl.*;
import at.koopro.wizardsandbeasts.util.NamespaceMigration;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

    /**
     * The live table, swapped rather than mutated in place.
     *
     * <p>Copy-on-write because two threads write to it. The server writes on a datapack reload; the
     * client writes when {@code SpellDefinitionsSyncS2CPayload} arrives. In single-player those are
     * the server thread and the render thread inside one JVM writing the same static field, so an
     * in-place {@code LinkedHashMap} mutation was a data race that could be observed as a half-built
     * map by anything calling {@link #byId(String)} — which the HUD does every frame. Publishing an
     * immutable map through a volatile reference means a reader sees the table before the reload or
     * the table after it, never a table mid-rebuild.
     *
     * <p>Insertion order is part of the contract: {@link #all()} feeds the spell menu's category
     * grouping and every command suggestion list, and both should be stable between sessions.
     */
    private static volatile Map<String, Spell> BY_ID = Map.of();
    /** Ids contributed by JSON spells; tracked so {@link #clearJsonSpells()} only touches those. */
    private static volatile Set<String> JSON_SPELL_IDS = Set.of();
    /** True once {@link #init()} has run; subsequent registrations must self-init. */
    private static boolean bootstrapped = false;

    // Combat
    // Step 3/4/5: stupefy, incendio, diffindo, bombarda, confringo, flipendo, glacius, depulso,
    // expelliarmus migrated to JSON (data/wizards_and_beasts/spells/*.json) — no Java classes.

    // Utility
    // Step 3/4/5 + F2: lumos, nox, reparo, liberacorpus, arresto_momentum, accio, alohomora,
    // colloportus, aguamenti, wingardium_leviosa migrated to JSON — no Java classes.
    // F6: riddikulus migrated to JSON; its cast burst rides a particle_burst effect component.

    // Defense
    public static final Protego PROTEGO = register(new Protego());
    public static final ExpectoPatronum EXPECTO_PATRONUM = register(new ExpectoPatronum());

    // Dark Arts
    // F2: crucio migrated to JSON — no Java class.
    public static final AvadaKedavra AVADA_KEDAVRA = register(new AvadaKedavra());
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
    public static synchronized <T extends Spell> T register(T spell) {
        if (BY_ID.containsKey(spell.getId())) {
            LOGGER.warn("Spell id collision: '{}' is already registered; replacing.", spell.getId());
        }
        Map<String, Spell> next = new LinkedHashMap<>(BY_ID);
        next.put(spell.getId(), spell);
        BY_ID = Collections.unmodifiableMap(next);
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
    public static synchronized <T extends Spell> T registerJson(T spell) {
        replaceJsonSpells(concatJsonSlice(spell));
        return spell;
    }

    /** The current JSON slice with {@code spell} appended (or replacing its own earlier entry). */
    private static List<Spell> concatJsonSlice(Spell spell) {
        List<Spell> slice = new ArrayList<>(JSON_SPELL_IDS.size() + 1);
        for (String id : JSON_SPELL_IDS) {
            if (!id.equals(spell.getId())) {
                Spell existing = BY_ID.get(id);
                if (existing != null) {
                    slice.add(existing);
                }
            }
        }
        slice.add(spell);
        return slice;
    }

    /**
     * Swaps the whole JSON-contributed slice of the registry in one publish: the previous slice is
     * dropped, every spell in {@code spells} is registered and initialized, and the new table goes
     * live as a single volatile write.
     *
     * <p>This exists instead of {@code clearJsonSpells()} followed by 150-odd
     * {@link #registerJson(Spell)} calls because that sequence was observable. Between the clear and
     * the last registration the table genuinely did not contain most of the mod's spells, and
     * anything reading it in that window — a HUD frame, another player's cast — saw a registry with
     * holes in it. One swap has no such window.
     *
     * <p>Called by the datapack reload listener on the server and by
     * {@code SpellDefinitionsSyncS2CPayload} on the client, which is why it is synchronized: in
     * single-player both are the same static field written from two different threads.
     */
    public static synchronized void replaceJsonSpells(Collection<? extends Spell> spells) {
        Map<String, Spell> next = new LinkedHashMap<>(BY_ID);
        for (String id : JSON_SPELL_IDS) {
            next.remove(id);
        }
        Set<String> nextJsonIds = new LinkedHashSet<>(spells.size());
        for (Spell spell : spells) {
            Spell previous = next.get(spell.getId());
            if (previous != null) {
                LOGGER.warn("JSON spell id collision: '{}' already registered as {}; replacing with {}.",
                        spell.getId(), previous.getClass().getSimpleName(), spell.getClass().getSimpleName());
            }
            // Before the swap: init() reads cross-references out of the registry, and a half-built
            // spell must never be reachable through the published table.
            spell.init();
            nextJsonIds.add(spell.getId());
            next.put(spell.getId(), spell);
        }
        JSON_SPELL_IDS = Collections.unmodifiableSet(nextJsonIds);
        BY_ID = Collections.unmodifiableMap(next);
    }

    /** Removes every spell previously contributed by {@link #registerJson(Spell)}. */
    public static synchronized void clearJsonSpells() {
        replaceJsonSpells(List.of());
    }

    /** Ids currently contributed by JSON, in registration order. */
    public static Set<String> jsonSpellIds() {
        return JSON_SPELL_IDS;
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
