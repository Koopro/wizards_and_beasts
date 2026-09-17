package at.koopro.wizardsandbeasts.bestiary;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The parts of a creature's page that describe it as a living animal rather than as a threat: what the Ministry
 * classifies it as, what it eats, how it behaves, how to approach it, what it yields and how, where it stands in
 * wizarding society, how a naturalist learns more, and the one thing it does that makes it itself.
 *
 * <p>Held as an optional {@code profile} object on every bestiary entry. {@code basis} says how much of it is
 * canon: a page never presents a gameplay invention as something Newt Scamander wrote.
 *
 * @param classification Ministry division the creature falls under
 * @param basis          how far the profile rests on canon
 * @param diet           lang key
 * @param behaviour      lang key: temperament and habits
 * @param society        lang key: its relationship with wizarding society
 * @param interactions   lang keys: how to approach it, and what it does in return
 * @param materials      what it yields, and how a wizard comes by it
 * @param study          how a naturalist learns more than can be seen; {@link StudyAct#WATCH} alone means watching
 *                       long enough is the whole of it
 * @param signature      lang key for the behaviour that completes the page, if the creature has one worth waiting for
 */
@NullMarked
public record CreatureProfile(
        Classification classification,
        Basis basis,
        String diet,
        String behaviour,
        String society,
        List<String> interactions,
        List<Material> materials,
        List<StudyAct> study,
        Optional<String> signature) {

    public static final Codec<CreatureProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Classification.CODEC.fieldOf("classification").forGetter(CreatureProfile::classification),
            Basis.CODEC.fieldOf("basis").forGetter(CreatureProfile::basis),
            Codec.STRING.fieldOf("diet").forGetter(CreatureProfile::diet),
            Codec.STRING.fieldOf("behaviour").forGetter(CreatureProfile::behaviour),
            Codec.STRING.fieldOf("society").forGetter(CreatureProfile::society),
            Codec.STRING.listOf().optionalFieldOf("interactions", List.of()).forGetter(CreatureProfile::interactions),
            Material.CODEC.listOf().optionalFieldOf("materials", List.of()).forGetter(CreatureProfile::materials),
            StudyAct.CODEC.listOf().optionalFieldOf("study", List.of(StudyAct.WATCH)).forGetter(CreatureProfile::study),
            Codec.STRING.optionalFieldOf("signature").forGetter(CreatureProfile::signature)
    ).apply(instance, CreatureProfile::new));

    public CreatureProfile {
        interactions = List.copyOf(interactions);
        materials = List.copyOf(materials);
        study = study.isEmpty() ? List.of(StudyAct.WATCH) : List.copyOf(study);
    }

    /** True when studying the creature takes more than watching it: feeding, handling, harvesting or bonding. */
    public boolean studiedByHand() {
        return study.stream().anyMatch(act -> act != StudyAct.WATCH);
    }

    public boolean hasSignature() {
        return signature.isPresent();
    }

    /**
     * The Ministry's divisions (<i>Fantastic Beasts and Where to Find Them</i>, introduction).
     */
    public enum Classification implements StringRepresentable {
        /** Care of Magical Creatures' Beast Division. */
        BEAST,
        /**
         * Offered Being status and chose Beast Division instead: centaurs and merpeople, who would not share a
         * classification with hags and vampires.
         */
        BEAST_BY_CHOICE,
        /** Being Division: intelligent enough to bear part of the responsibility for magical law. */
        BEING,
        /** Spirit Division: ghosts. */
        SPIRIT,
        /** Canon gives no division — Dementors, Boggarts, the Obscurus. */
        UNCLASSIFIED;

        public static final Codec<Classification> CODEC = StringRepresentable.fromEnum(Classification::values);

        public Component displayName() {
            return Component.translatable("bestiary.wizards_and_beasts.classification." + getSerializedName());
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How far a profile rests on canon. Shown on the page. */
    public enum Basis implements StringRepresentable {
        /** Everything stated is in Rowling's writing. */
        CANON,
        /** Grounded in canon, with gameplay filling gaps canon leaves. */
        PARTIAL,
        /** Canon says little; this is the mod's reading. */
        GAMEPLAY;

        public static final Codec<Basis> CODEC = StringRepresentable.fromEnum(Basis::values);

        public Component displayName() {
            return Component.translatable("bestiary.wizards_and_beasts.basis." + getSerializedName());
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How a naturalist learns more than can be seen from a distance. */
    public enum StudyAct implements StringRepresentable {
        WATCH, FEED, HANDLE, HARVEST, BOND;

        public static final Codec<StudyAct> CODEC = StringRepresentable.fromEnum(StudyAct::values);

        public Component displayName() {
            return Component.translatable("bestiary.wizards_and_beasts.study." + getSerializedName());
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How a material comes to a wizard. */
    public enum Acquisition implements StringRepresentable {
        /** Shed naturally, found where the creature has been. */
        SHED,
        /** Given up by a creature that trusts you enough to let you near. */
        GROOMING,
        /** Offered by a bonded creature. */
        GIFT,
        /** Produced by a creature kept and cared for. */
        HUSBANDRY,
        /** Taken from a creature killed by one who has studied it, and only sometimes. */
        STUDIED_KILL,
        /** Ordinary remains. */
        REMAINS;

        public static final Codec<Acquisition> CODEC = StringRepresentable.fromEnum(Acquisition::values);

        public Component displayName() {
            return Component.translatable("bestiary.wizards_and_beasts.acquisition." + getSerializedName());
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One material and the way it is had. */
    public record Material(Identifier item, Acquisition how) {

        public static final Codec<Material> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Material::item),
                Acquisition.CODEC.fieldOf("how").forGetter(Material::how)
        ).apply(instance, Material::new));
    }
}
