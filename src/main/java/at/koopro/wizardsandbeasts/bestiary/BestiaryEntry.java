package at.koopro.wizardsandbeasts.bestiary;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public record BestiaryEntry(
        Identifier id,
        String displayNameKey,
        BestiaryCategory category,
        /**
         * Ministry of Magic classification, 1–5 (X…XXXXX). Absent means <em>unclassified</em>:
         * Scamander's A–Z lists only creatures that exist exclusively in the magical world, so an
         * ordinary animal with magical uses — a toad, an owl, a cat — holds no grade at all rather
         * than a low one. {@code X} is a real grade held by real magical beasts (Flobberworm,
         * Horklump); asserting it for a toad would invent a classification canon does not make.
         */
        Optional<Integer> mmRating,
        String habitatKey,
        BestiarySize size,
        String shortLoreKey,
        String fullLoreKey,
        List<String> magicAbilityKeys,
        List<String> threatKeys,
        List<String> weaknessKeys,
        EncounterTrigger encounterTrigger,
        Optional<Identifier> entityType,
        Identifier iconTexture,
        Identifier silhouetteTexture,
        int sortOrder) {

    public static final Codec<BestiaryEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(BestiaryEntry::id),
            Codec.STRING.fieldOf("displayName").forGetter(BestiaryEntry::displayNameKey),
            BestiaryCategory.CODEC.fieldOf("category").forGetter(BestiaryEntry::category),
            Codec.intRange(1, 5).optionalFieldOf("mmRating").forGetter(BestiaryEntry::mmRating),
            Codec.STRING.fieldOf("habitat").forGetter(BestiaryEntry::habitatKey),
            BestiarySize.CODEC.fieldOf("size").forGetter(BestiaryEntry::size),
            Codec.STRING.fieldOf("shortLore").forGetter(BestiaryEntry::shortLoreKey),
            Codec.STRING.fieldOf("fullLore").forGetter(BestiaryEntry::fullLoreKey),
            Codec.STRING.listOf().fieldOf("magicAbilities").forGetter(BestiaryEntry::magicAbilityKeys),
            Codec.STRING.listOf().fieldOf("threats").forGetter(BestiaryEntry::threatKeys),
            Codec.STRING.listOf().fieldOf("weaknesses").forGetter(BestiaryEntry::weaknessKeys),
            EncounterTrigger.CODEC.fieldOf("encounterTrigger").forGetter(BestiaryEntry::encounterTrigger),
            Identifier.CODEC.optionalFieldOf("entityType").forGetter(BestiaryEntry::entityType),
            Identifier.CODEC.fieldOf("iconTexture").forGetter(BestiaryEntry::iconTexture),
            Identifier.CODEC.fieldOf("silhouetteTexture").forGetter(BestiaryEntry::silhouetteTexture),
            Codec.INT.fieldOf("sortOrder").forGetter(BestiaryEntry::sortOrder)
    ).apply(instance, BestiaryEntry::new));

    public Component displayName() { return Component.translatable(displayNameKey); }
    public Component habitat() { return Component.translatable(habitatKey); }
    public Component shortLore() { return Component.translatable(shortLoreKey); }
    public Component fullLore() { return Component.translatable(fullLoreKey); }
}
