package at.koopro.wizardsandbeasts.client.bestiary.gui;

import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.CreatureProfile;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.data.EntryHistory;
import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.bestiary.journal.JournalFacts;
import at.koopro.wizardsandbeasts.bestiary.journal.JournalLayout;
import at.koopro.wizardsandbeasts.bestiary.journal.JournalReveal;
import at.koopro.wizardsandbeasts.bestiary.journal.JournalSection;
import at.koopro.wizardsandbeasts.bestiary.journal.ResearchProgress;
import at.koopro.wizardsandbeasts.bestiary.journal.ResearchRewards;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Writes one page of an entry: for every section {@link JournalReveal} puts on that page, a heading and what the
 * player may read under it. The same code writes every creature's page; what differs is the data.
 *
 * <p>A locked section is a struck pencil line and the way to earn it. A section with nothing to say — a creature that
 * yields no material — says "None recorded" only once it is open, so a locked heading never gives away whether
 * there is something under it.
 */
@NullMarked
final class JournalPageWriter {

    /** One block of text and how to ink it. */
    enum Ink { HEADING, BODY, QUIET, STRUCK, DONE }

    record Block(Component text, Ink ink) {}

    private static final String K = "bestiary.wizards_and_beasts.journal.";
    private static final String FIELD = "bestiary.wizards_and_beasts.field.";

    private JournalPageWriter() {}

    static List<Block> write(JournalLayout.Page page, BestiaryEntry entry, DiscoveryTier tier, PlayerBestiaryData data,
                             JournalFacts facts, JournalLayout layout) {
        List<Block> out = new ArrayList<>();
        for (JournalReveal.SectionView view : JournalReveal.reveal(layout, tier)) {
            if (view.page() != page) {
                continue;
            }
            out.add(new Block(Component.translatable(view.section().headingKey()), Ink.HEADING));
            if (view.state() == JournalReveal.State.LOCKED) {
                out.add(new Block(Component.translatable(K + "struck"), Ink.STRUCK));
                out.add(new Block(view.nextReveal().unlockHint(), Ink.QUIET));
                continue;
            }
            List<Block> body = section(view.section(), view.state() == JournalReveal.State.PARTIAL, entry, tier, data, facts);
            out.addAll(body.isEmpty() ? List.of(new Block(Component.translatable(K + "none"), Ink.QUIET)) : body);
        }
        return out;
    }

    private static List<Block> section(JournalSection section, boolean partial, BestiaryEntry entry, DiscoveryTier tier,
                                       PlayerBestiaryData data, JournalFacts facts) {
        List<Block> out = new ArrayList<>();
        Optional<CreatureProfile> profile = entry.profile();
        EntryHistory history = data.historyOf(entry.id());
        switch (section) {
            case CLASSIFICATION -> {
                profile.ifPresent(p -> out.add(body(p.classification().displayName())));
                out.add(body(Component.translatable(FIELD + "rating", ministryGrade(entry.mmRating()))));
            }
            case SIZE -> out.add(body(Component.translatable("bestiary.wizards_and_beasts.size."
                    + entry.size().name().toLowerCase(Locale.ROOT))));
            case HABITAT -> {
                if (!partial) {
                    out.add(body(entry.habitat()));
                }
                if (!history.firstSeenBiome().isEmpty()) {
                    out.add(quiet(Component.translatable(K + "first_seen_in", biomeName(history.firstSeenBiome()))));
                }
            }
            case TEMPERAMENT -> out.add(body(Component.translatable(K + "temperament." + facts.temperament())));
            case DANGER -> entry.threatKeys().forEach(key -> out.add(body(Component.translatable(key))));
            case KNOWN_BEHAVIOUR -> profile.ifPresent(p -> {
                out.add(body(Component.translatable(p.behaviour())));
                out.add(quiet(Component.translatable(p.society())));
            });
            case OBSERVED_BEHAVIOUR -> {
                for (String note : data.notesOn(entry.id())) {
                    EcologyCapability capability = EcologyCapability.fromNote(note);
                    if (capability != null) {
                        out.add(body(Component.translatable(capability.noteKey())));
                    }
                }
                if (out.isEmpty()) {
                    out.add(quiet(Component.translatable(K + "nothing_observed")));
                }
            }
            case DIET -> profile.ifPresent(p -> out.add(body(Component.translatable(p.diet()))));
            case FEEDING_BONDING -> {
                if (partial) {
                    out.add(quiet(Component.translatable(K + (facts.feeds().isEmpty() ? "feeds_none_seen" : "feeds_seen"))));
                } else {
                    for (Identifier item : facts.feeds()) {
                        out.add(body(Component.translatable(K + "takes", itemName(item))));
                    }
                    if (facts.bondable()) {
                        out.add(body(Component.translatable(K + "bondable")));
                    }
                    facts.gift().ifPresent(gift -> out.add(body(Component.translatable(K + "gift", itemName(gift)))));
                    profile.ifPresent(p -> p.interactions().forEach(key -> out.add(quiet(Component.translatable(key)))));
                }
            }
            case MAGIC -> entry.magicAbilityKeys().forEach(key -> out.add(body(Component.translatable(key))));
            case WEAKNESSES -> entry.weaknessKeys().forEach(key -> out.add(body(Component.translatable(key))));
            case HARVESTING -> {
                boolean researched = history.complete();
                for (JournalFacts.Harvest harvest : facts.harvests()) {
                    int seconds = researched ? harvest.cooldownSeconds() / 2 : harvest.cooldownSeconds();
                    out.add(body(Component.translatable(K + "harvest", itemName(harvest.item()),
                            harvest.minTier().displayName(), duration(seconds))));
                }
                if (researched && !facts.harvests().isEmpty()) {
                    out.add(quiet(Component.translatable(K + "harvest_researched")));
                }
            }
            case MATERIALS -> profile.ifPresent(p -> {
                for (CreatureProfile.Material material : p.materials()) {
                    out.add(body(Component.translatable(FIELD + "material", itemName(material.item()),
                            material.how().displayName())));
                }
            });
            case LORE -> {
                out.add(body(entry.fullLore()));
                profile.ifPresent(p -> out.add(quiet(Component.translatable(FIELD + "basis", p.basis().displayName()))));
            }
            case SIGNATURE -> {
                boolean has = profile.map(CreatureProfile::hasSignature).orElse(false);
                if (partial) {
                    out.add(quiet(Component.translatable(K + (has ? "signature_unseen" : "signature_none_pending"))));
                } else if (has) {
                    out.add(body(Component.translatable(profile.get().signature().get())));
                    entry.journal().signatureCaption().ifPresent(key -> out.add(quiet(Component.translatable(key))));
                } else {
                    out.add(quiet(Component.translatable(K + "signature_none")));
                }
            }
            case HISTORY -> {
                if (history.firstSeenDay() >= 0) {
                    out.add(body(Component.translatable(K + "history.first", history.firstSeenDay() + 1,
                            biomeName(history.firstSeenBiome()))));
                }
                for (DiscoveryTier t : DiscoveryTier.values()) {
                    long day = history.dayReached(t);
                    if (t != DiscoveryTier.UNKNOWN && t != DiscoveryTier.ENCOUNTERED && day >= 0) {
                        out.add(quiet(Component.translatable(K + "history.reached", t.displayName(), day + 1)));
                    }
                }
            }
            case RESEARCH -> {
                ResearchProgress progress = ResearchRewards.progress(entry, data, facts);
                out.add(body(Component.translatable(K + "research.count", progress.done(), progress.total())));
                for (ResearchProgress.Step step : progress.steps()) {
                    Component label = step.arg().isEmpty() ? Component.translatable(step.key())
                            : step.key().endsWith("act")
                            ? Component.translatable(step.key(), Component.translatable("bestiary.wizards_and_beasts.study." + step.arg()))
                            : Component.translatable(step.key(), step.arg());
                    out.add(new Block(Component.translatable(K + (step.done() ? "check.done" : "check.open"), label),
                            step.done() ? Ink.DONE : Ink.QUIET));
                }
                if (history.complete()) {
                    out.add(new Block(Component.translatable(K + "research.rewarded"), Ink.DONE));
                }
            }
        }
        return out;
    }

    private static Block body(Component text) {
        return new Block(text, Ink.BODY);
    }

    private static Block quiet(Component text) {
        return new Block(text, Ink.QUIET);
    }

    static Component itemName(Identifier item) {
        return BuiltInRegistries.ITEM.getOptional(item).map(found -> found.getName())
                .orElse(Component.literal(item.toString()));
    }

    static Component biomeName(String biome) {
        Identifier id = Identifier.tryParse(biome);
        return id == null ? Component.literal(biome)
                : Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
    }

    static Component ministryGrade(Optional<Integer> rating) {
        return Component.literal(rating.map(r -> "X".repeat(Math.max(1, Math.min(5, r)))).orElse("?"));
    }

    static Component duration(int seconds) {
        return seconds >= 60 ? Component.translatable(K + "minutes", (seconds + 59) / 60)
                : Component.translatable(K + "seconds", seconds);
    }
}
