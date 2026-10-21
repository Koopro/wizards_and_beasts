package at.koopro.wizardsandbeasts.bestiary.journal;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The shape of every journal page: which sections appear, on which page of the spread, in what order, and from which
 * depth of knowledge each one opens. One layout for the whole book — no creature gets a hand-made screen — loaded from
 * {@code data/wizards_and_beasts/bestiary_journal/layout.json} and synced to clients with the entries. {@link #DEFAULT}
 * is the shipped file's content, used when no file loads.
 *
 * @param slots the sections, in reading order within each page
 */
@NullMarked
public record JournalLayout(List<Slot> slots) {

    /** The two pages of an open entry. */
    public enum Page implements StringRepresentable {
        /** Left: the illustrated plate and the facts at a glance. */
        PLATE,
        /** Right: the notes. */
        NOTES;

        public static final Codec<Page> CODEC = StringRepresentable.fromEnum(Page::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One section's place on the page.
     *
     * @param reveal  the depth at which it opens in full
     * @param partial an earlier depth at which it shows what the player could only partly know — where they saw it,
     *                that something is still unseen — or empty
     */
    public record Slot(JournalSection section, Page page, DiscoveryTier reveal, Optional<DiscoveryTier> partial) {
        public static final Codec<Slot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                JournalSection.CODEC.fieldOf("section").forGetter(Slot::section),
                Page.CODEC.fieldOf("page").forGetter(Slot::page),
                DiscoveryTier.CODEC.fieldOf("reveal").forGetter(Slot::reveal),
                DiscoveryTier.CODEC.optionalFieldOf("partial").forGetter(Slot::partial)
        ).apply(instance, Slot::new));
    }

    public static final Codec<JournalLayout> CODEC = Slot.CODEC.listOf().fieldOf("sections").codec()
            .xmap(JournalLayout::new, JournalLayout::slots).validate(JournalLayout::validate);

    private static DataResult<JournalLayout> validate(JournalLayout layout) {
        Set<JournalSection> seen = EnumSet.noneOf(JournalSection.class);
        for (Slot slot : layout.slots()) {
            if (!seen.add(slot.section())) {
                return DataResult.error(() -> "section " + slot.section().getSerializedName() + " appears twice");
            }
            if (slot.reveal() == DiscoveryTier.UNKNOWN) {
                return DataResult.error(() -> slot.section().getSerializedName()
                        + " opens at UNKNOWN: an undiscovered creature's page says nothing");
            }
            if (slot.partial().isPresent() && (slot.partial().get() == DiscoveryTier.UNKNOWN
                    || !slot.reveal().atLeast(slot.partial().get()) || slot.partial().get() == slot.reveal())) {
                return DataResult.error(() -> slot.section().getSerializedName()
                        + ": a partial view must come after UNKNOWN and before the full reveal");
            }
        }
        return DataResult.success(layout);
    }

    private static Slot slot(JournalSection section, Page page, DiscoveryTier reveal) {
        return new Slot(section, page, reveal, Optional.empty());
    }

    private static Slot slot(JournalSection section, Page page, DiscoveryTier partial, DiscoveryTier reveal) {
        return new Slot(section, page, reveal, Optional.of(partial));
    }

    /** Must match {@code bestiary_journal/layout.json} — {@code JournalLayoutDataTest} holds them together. */
    public static final JournalLayout DEFAULT = new JournalLayout(List.of(
            slot(JournalSection.CLASSIFICATION, Page.PLATE, DiscoveryTier.ENCOUNTERED),
            slot(JournalSection.SIZE, Page.PLATE, DiscoveryTier.ENCOUNTERED),
            slot(JournalSection.HABITAT, Page.PLATE, DiscoveryTier.ENCOUNTERED, DiscoveryTier.OBSERVED),
            slot(JournalSection.TEMPERAMENT, Page.PLATE, DiscoveryTier.OBSERVED),
            slot(JournalSection.DANGER, Page.PLATE, DiscoveryTier.OBSERVED),
            slot(JournalSection.RESEARCH, Page.PLATE, DiscoveryTier.ENCOUNTERED),
            slot(JournalSection.HISTORY, Page.PLATE, DiscoveryTier.ENCOUNTERED),
            slot(JournalSection.OBSERVED_BEHAVIOUR, Page.NOTES, DiscoveryTier.ENCOUNTERED),
            slot(JournalSection.KNOWN_BEHAVIOUR, Page.NOTES, DiscoveryTier.OBSERVED),
            slot(JournalSection.DIET, Page.NOTES, DiscoveryTier.OBSERVED),
            slot(JournalSection.FEEDING_BONDING, Page.NOTES, DiscoveryTier.OBSERVED, DiscoveryTier.STUDIED),
            slot(JournalSection.MAGIC, Page.NOTES, DiscoveryTier.STUDIED),
            slot(JournalSection.WEAKNESSES, Page.NOTES, DiscoveryTier.STUDIED),
            slot(JournalSection.HARVESTING, Page.NOTES, DiscoveryTier.STUDIED),
            slot(JournalSection.MATERIALS, Page.NOTES, DiscoveryTier.STUDIED),
            slot(JournalSection.LORE, Page.NOTES, DiscoveryTier.STUDIED),
            slot(JournalSection.SIGNATURE, Page.NOTES, DiscoveryTier.STUDIED, DiscoveryTier.KNOWN)));
}
