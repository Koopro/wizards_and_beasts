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
        int sortOrder,
        /**
         * The creature as a living animal — classification, diet, behaviour, society, materials, how to study it.
         * Optional so a datapack entry written before profiles existed still loads; every shipped entry has one.
         */
        Optional<CreatureProfile> profile) {

    /**
     * The sixteen original fields. {@code RecordCodecBuilder.group} stops at sixteen, so the profile is a second
     * half merged with {@link Codec#mapPair} — both halves read the same flat JSON object, so no shipped entry
     * changes shape.
     */
    private record Core(Identifier id, String displayNameKey, BestiaryCategory category, Optional<Integer> mmRating,
                        String habitatKey, BestiarySize size, String shortLoreKey, String fullLoreKey,
                        List<String> magicAbilityKeys, List<String> threatKeys, List<String> weaknessKeys,
                        EncounterTrigger encounterTrigger, Optional<Identifier> entityType, Identifier iconTexture,
                        Identifier silhouetteTexture, int sortOrder) {}

    private static final com.mojang.serialization.MapCodec<Core> CORE = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(Core::id),
            Codec.STRING.fieldOf("displayName").forGetter(Core::displayNameKey),
            BestiaryCategory.CODEC.fieldOf("category").forGetter(Core::category),
            Codec.intRange(1, 5).optionalFieldOf("mmRating").forGetter(Core::mmRating),
            Codec.STRING.fieldOf("habitat").forGetter(Core::habitatKey),
            BestiarySize.CODEC.fieldOf("size").forGetter(Core::size),
            Codec.STRING.fieldOf("shortLore").forGetter(Core::shortLoreKey),
            Codec.STRING.fieldOf("fullLore").forGetter(Core::fullLoreKey),
            Codec.STRING.listOf().fieldOf("magicAbilities").forGetter(Core::magicAbilityKeys),
            Codec.STRING.listOf().fieldOf("threats").forGetter(Core::threatKeys),
            Codec.STRING.listOf().fieldOf("weaknesses").forGetter(Core::weaknessKeys),
            EncounterTrigger.CODEC.fieldOf("encounterTrigger").forGetter(Core::encounterTrigger),
            Identifier.CODEC.optionalFieldOf("entityType").forGetter(Core::entityType),
            Identifier.CODEC.fieldOf("iconTexture").forGetter(Core::iconTexture),
            Identifier.CODEC.fieldOf("silhouetteTexture").forGetter(Core::silhouetteTexture),
            Codec.INT.fieldOf("sortOrder").forGetter(Core::sortOrder)
    ).apply(instance, Core::new));

    public static final Codec<BestiaryEntry> CODEC = Codec.mapPair(CORE,
                    CreatureProfile.CODEC.optionalFieldOf("profile"))
            .codec()
            .xmap(pair -> {
                Core c = pair.getFirst();
                return new BestiaryEntry(c.id(), c.displayNameKey(), c.category(), c.mmRating(), c.habitatKey(),
                        c.size(), c.shortLoreKey(), c.fullLoreKey(), c.magicAbilityKeys(), c.threatKeys(),
                        c.weaknessKeys(), c.encounterTrigger(), c.entityType(), c.iconTexture(),
                        c.silhouetteTexture(), c.sortOrder(), pair.getSecond());
            }, entry -> com.mojang.datafixers.util.Pair.of(new Core(entry.id(), entry.displayNameKey(),
                    entry.category(), entry.mmRating(), entry.habitatKey(), entry.size(), entry.shortLoreKey(),
                    entry.fullLoreKey(), entry.magicAbilityKeys(), entry.threatKeys(), entry.weaknessKeys(),
                    entry.encounterTrigger(), entry.entityType(), entry.iconTexture(), entry.silhouetteTexture(),
                    entry.sortOrder()), entry.profile()));

    public Component displayName() { return Component.translatable(displayNameKey); }
    public Component habitat() { return Component.translatable(habitatKey); }
    public Component shortLore() { return Component.translatable(shortLoreKey); }
    public Component fullLore() { return Component.translatable(fullLoreKey); }
}
