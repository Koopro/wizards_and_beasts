package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.client.skill.state.ClientSkillDataState;
import at.koopro.wizardsandbeasts.client.skill.state.ClientVocationCache;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.skill.build.BuildProfile;
import at.koopro.wizardsandbeasts.skill.build.Builds;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the sheet derives from synced state, worked out once per change rather than once per frame.
 *
 * <p>Two pages read the same derivations — the Identity page's cards and the page that holds the detail — and
 * both used to redo them every frame: the Skills tab rebuilt the whole {@link BuildProfile} (a walk over every
 * archetype's pathway) sixty times a second, and the Spells tab looked up, resolved and sorted every known spell
 * just as often. Each is now keyed on the client state's own change counter, so it is rebuilt when a sync lands
 * and read for free in between.
 *
 * <p>Nothing here is authoritative. Every input is the server's synced copy; this only decides how often the
 * client re-reads it.
 */
@NullMarked
public final class SheetCaches {

    private SheetCaches() {}

    // ── build ─────────────────────────────────────────────────────────────

    private static long buildVersion = -1;
    private static Optional<Identifier> buildVocation = Optional.empty();
    private static @Nullable BuildProfile build;

    /** The build the synced allocation describes, as {@code /wandb skill build} would print it. */
    public static BuildProfile build() {
        long version = ClientSkillDataState.version();
        Optional<Identifier> vocation = ClientVocationCache.primary();
        BuildProfile cached = build;
        if (cached == null || version != buildVersion || !vocation.equals(buildVocation)) {
            cached = Builds.clientProfile(ClientSkillDataState.get().getUnlockedSkills(), vocation);
            build = cached;
            buildVersion = version;
            buildVocation = vocation;
        }
        return cached;
    }

    // ── spells ────────────────────────────────────────────────────────────

    /** One known spell and how far into it the wizard is. */
    public record KnownSpell(Spell spell, Proficiency proficiency, int hits, String name) {
        /** Successful casts the next tier asks for; -1 once mastered. */
        public int nextAt() {
            return switch (proficiency) {
                case NOVICE -> Proficiency.PROFICIENT.getCastsRequired();
                case PROFICIENT -> Proficiency.MASTERED.getCastsRequired();
                case MASTERED -> -1;
            };
        }

        /** Progress from this tier's floor to the next tier, 0..1; 1 once mastered. */
        public float tierProgress() {
            int next = nextAt();
            if (next < 0) {
                return 1.0f;
            }
            int floor = proficiency.getCastsRequired();
            return Math.max(0.0f, Math.min(1.0f, (hits - floor) / (float) Math.max(1, next - floor)));
        }
    }

    /**
     * The spellbook, read once: every known spell sorted mastered first and then by name, grouped by category, and
     * the tier counts.
     */
    public record Spellbook(List<KnownSpell> all, Map<SpellCategory, List<KnownSpell>> byCategory,
                            Map<Proficiency, Integer> tierCounts) {
        public int count(Proficiency tier) {
            return tierCounts.getOrDefault(tier, 0);
        }

        /** Overall mastery: every spell's casts toward Mastered, averaged. 0 with nothing known. */
        public float mastery() {
            if (all.isEmpty()) {
                return 0.0f;
            }
            int target = Proficiency.MASTERED.getCastsRequired();
            float sum = 0.0f;
            for (KnownSpell s : all) {
                sum += Math.min(s.hits(), target) / (float) target;
            }
            return sum / all.size();
        }

        /** The spell closest to its next tier, for "what to practise". Null when everything is mastered. */
        public @Nullable KnownSpell closestToNextTier() {
            KnownSpell best = null;
            int bestLeft = Integer.MAX_VALUE;
            for (KnownSpell s : all) {
                int next = s.nextAt();
                if (next < 0) {
                    continue;
                }
                int left = next - s.hits();
                if (left < bestLeft) {
                    best = s;
                    bestLeft = left;
                }
            }
            return best;
        }
    }

    private static long spellVersion = -1;
    private static @Nullable Spellbook spellbook;

    public static Spellbook spellbook() {
        long version = ClientSpellDataState.version();
        Spellbook cached = spellbook;
        if (cached == null || version != spellVersion) {
            cached = buildSpellbook();
            spellbook = cached;
            spellVersion = version;
        }
        return cached;
    }

    private static Spellbook buildSpellbook() {
        var data = ClientSpellDataState.get();
        List<KnownSpell> list = new ArrayList<>();
        for (String id : data.getKnownSpells()) {
            Spell spell = Spells.byId(id);
            if (spell == null) {
                continue;
            }
            int hits = data.getSuccessfulHits(spell.getId());
            list.add(new KnownSpell(spell, Proficiency.fromCastCount(hits), hits, GuiText.resolve(spell.getDisplayName())));
        }
        list.sort(Comparator.<KnownSpell>comparingInt(s -> -s.proficiency().ordinal())
                .thenComparing(KnownSpell::name, String.CASE_INSENSITIVE_ORDER));
        Map<SpellCategory, List<KnownSpell>> byCategory = new EnumMap<>(SpellCategory.class);
        Map<Proficiency, Integer> counts = new EnumMap<>(Proficiency.class);
        for (KnownSpell s : list) {
            byCategory.computeIfAbsent(s.spell().getCategory(), c -> new ArrayList<>()).add(s);
            counts.merge(s.proficiency(), 1, Integer::sum);
        }
        return new Spellbook(List.copyOf(list), byCategory, counts);
    }
}
