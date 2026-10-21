package at.koopro.wizardsandbeasts.bestiary.journal;

import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.harvest.HarvestRule;
import at.koopro.wizardsandbeasts.bestiary.harvest.HarvestTable;
import at.koopro.wizardsandbeasts.creature.CreatureDefinition;
import at.koopro.wizardsandbeasts.creature.CreatureDefinitionRegistry;
import at.koopro.wizardsandbeasts.creature.Temperament;
import at.koopro.wizardsandbeasts.creature.bond.BondFeed;
import at.koopro.wizardsandbeasts.creature.bond.BondProfile;
import at.koopro.wizardsandbeasts.creature.bond.BondProfileRegistry;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyProfile;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyProfileRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What the journal says about a creature that lives in other datapacks — its disposition, its ecology, what it eats
 * from the hand, what it can yield — derived on the server, where those registries are, and synced with the entry.
 *
 * <p>Nothing here is per-player: these are facts about the species, and {@link JournalReveal} decides when a player
 * sees each. Deriving them rather than copying them into every bestiary file is what keeps one source per fact — a
 * bond profile changed is a journal page changed.
 *
 * @param temperament one of {@link #TEMPERAMENTS}
 * @param ecology     the creature's ecology capabilities, serialized
 * @param feeds       what it takes from a hand (bond profile)
 * @param bondable    whether it can come to trust a person
 * @param gift        what a bonded one gives, if anything
 * @param harvests    what careful hands can take, from which depth, how often
 */
@NullMarked
public record JournalFacts(String temperament, List<String> ecology, List<Identifier> feeds, boolean bondable,
                           Optional<Identifier> gift, List<Harvest> harvests) {

    /** The temperaments a page can name, mildest first. */
    public static final List<String> TEMPERAMENTS = List.of("docile", "curious", "wary", "neutral", "territorial", "dangerous");

    public static final JournalFacts NONE = new JournalFacts("neutral", List.of(), List.of(), false, Optional.empty(), List.of());

    public record Harvest(Identifier item, DiscoveryTier minTier, int cooldownSeconds) {
        public static final Codec<Harvest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Harvest::item),
                DiscoveryTier.CODEC.fieldOf("min_tier").forGetter(Harvest::minTier),
                Codec.INT.fieldOf("cooldown_seconds").forGetter(Harvest::cooldownSeconds)
        ).apply(instance, Harvest::new));
    }

    public static final Codec<JournalFacts> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("temperament").forGetter(JournalFacts::temperament),
            Codec.STRING.listOf().fieldOf("ecology").forGetter(JournalFacts::ecology),
            Identifier.CODEC.listOf().fieldOf("feeds").forGetter(JournalFacts::feeds),
            Codec.BOOL.fieldOf("bondable").forGetter(JournalFacts::bondable),
            Identifier.CODEC.optionalFieldOf("gift").forGetter(JournalFacts::gift),
            Harvest.CODEC.listOf().fieldOf("harvests").forGetter(JournalFacts::harvests)
    ).apply(instance, JournalFacts::new));

    /** The facts for an entry, from the registries loaded on this server. */
    public static JournalFacts compute(BestiaryEntry entry) {
        if (entry.entityType().isEmpty()) {
            return NONE;
        }
        Identifier type = entry.entityType().get();
        CreatureDefinition definition = CreatureDefinitionRegistry.get(type);
        EcologyProfile ecology = EcologyProfileRegistry.get(type);
        List<String> caps = ecology.capabilities().stream().map(EcologyCapability::getSerializedName).toList();
        BondProfile bond = BondProfileRegistry.get(type);
        List<Identifier> feeds = bond == null ? List.of()
                : bond.feeds().stream().map(BondFeed::item).map(BuiltInRegistries.ITEM::getKey).toList();
        Optional<Identifier> gift = bond == null ? Optional.empty()
                : bond.gift().map(g -> BuiltInRegistries.ITEM.getKey(g.item()));
        List<Harvest> harvests = new ArrayList<>();
        for (HarvestRule rule : HarvestTable.forEntityType(type)) {
            harvests.add(new Harvest(BuiltInRegistries.ITEM.getKey(rule.item()), rule.minTier(), rule.cooldownSeconds()));
        }
        return new JournalFacts(temperament(definition == null ? null : definition.temperament(), ecology.capabilities()),
                caps, feeds, bond != null, gift, List.copyOf(harvests));
    }

    /**
     * A disposition a naturalist would write down, from the creature's temperament (datapack creatures) and its
     * ecology (every profiled creature). Pure: the most telling trait wins — anything that attacks unprovoked is
     * dangerous whatever else it is, and a creature with no stated disposition is neutral.
     */
    public static String temperament(@Nullable Temperament temperament, Collection<EcologyCapability> ecology) {
        Set<EcologyCapability> caps = ecology.isEmpty() ? Set.of() : Set.copyOf(ecology);
        if (temperament == Temperament.HOSTILE || caps.contains(EcologyCapability.AGGRESSIVE)) {
            return "dangerous";
        }
        if (caps.contains(EcologyCapability.TERRITORIAL)) {
            return "territorial";
        }
        if (caps.contains(EcologyCapability.HUMAN_AVOIDANT) || caps.contains(EcologyCapability.COWARDLY)) {
            return "wary";
        }
        if (caps.contains(EcologyCapability.CURIOUS) || caps.contains(EcologyCapability.HUMAN_FRIENDLY)) {
            return "curious";
        }
        if (temperament == Temperament.PASSIVE) {
            return "docile";
        }
        return "neutral";
    }
}
