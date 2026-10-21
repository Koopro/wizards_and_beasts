package at.koopro.wizardsandbeasts.owl.career.track;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * Words for activities, goals and contracts. Built on the server and sent to the Career tab ready to draw,
 * so the client never needs the track definitions and the two can never disagree about what a line says.
 */
@NullMarked
public final class CareerText {

    private static final String KEY = "career.wizards_and_beasts.";

    private CareerText() {}

    /** "Bestiary pages taken to Studied — Unicorn, XXXX or more, different creatures". */
    public static Component activity(ActivityFilter filter, boolean distinct) {
        MutableComponent line = Component.translatable(KEY + "activity." + filter.activity().getSerializedName());
        filter.minTier().ifPresent(tier -> line.append(Component.literal(" ")).append(Component.translatable(
                KEY + "qualifier.tier", Component.translatable(KEY + "tier." + tier.name().toLowerCase(Locale.ROOT)))));
        if (!filter.species().isEmpty()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < filter.species().size(); i++) {
                if (i > 0) {
                    names.append(Component.literal(", "));
                }
                names.append(subject(filter.activity(), filter.species().get(i)));
            }
            line.append(Component.literal(" — ")).append(names);
        }
        if (filter.minRating() > 0) {
            line.append(Component.literal(" ")).append(Component.translatable(KEY + "qualifier.rating",
                    "X".repeat(filter.minRating())));
        }
        if (distinct) {
            line.append(Component.literal(" ")).append(Component.translatable(KEY + "qualifier.distinct"));
        }
        return line;
    }

    /** A creature's name, from its entity type's translation. */
    public static Component creature(Identifier species) {
        return Component.translatable("entity." + species.getNamespace() + "." + species.getPath());
    }

    /**
     * The name of what an activity involved. Creature work names a creature; curse-breaking names a curse kind and
     * treasure work a treasure site, both from {@code subject.<ns>.<path>}; trade names the location.
     */
    public static Component subject(ActivityType type, Identifier subject) {
        return switch (type) {
            case CURSE_BROKEN, TREASURE_RETURNED, TREASURE_SIGHTED ->
                    Component.translatable("subject." + subject.getNamespace() + "." + subject.getPath());
            case AERIAL_SURVEY ->
                    Component.translatable("biome." + subject.getNamespace() + "." + subject.getPath());
            case GOODS_TRADED, COURIER_DELIVERED, COURIER_EXPRESS, TIME_TRIAL_FINISHED, TIME_TRIAL_PAR ->
                    Component.translatable("location." + subject.getNamespace() + "." + subject.getPath() + ".name");
            default -> creature(subject);
        };
    }

    public static Component rank(String profession, String rankId) {
        return Component.translatable(KEY + profession + ".rank." + rankId);
    }

    public static Component contractTitle(String profession, String contractId) {
        return Component.translatable(KEY + profession + ".contract." + contractId);
    }

    public static Component contractBrief(String profession, String contractId) {
        return Component.translatable(KEY + profession + ".contract." + contractId + ".brief");
    }
}
