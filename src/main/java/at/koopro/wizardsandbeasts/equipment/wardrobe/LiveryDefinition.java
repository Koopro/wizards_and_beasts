package at.koopro.wizardsandbeasts.equipment.wardrobe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/**
 * One livery: a colourway a set of cuts can be stitched in. Datapack registry
 * {@code wizards_and_beasts:livery}, files at {@code data/<ns>/wizards_and_beasts/livery/<id>.json}.
 *
 * <pre>
 * { "cuts": ["wizards_and_beasts:student_robe_chest"], "faction": "school_house_lion" }
 * </pre>
 *
 * <p>The look itself is a baked sheet, not data: a livery {@code ns:path} worn on a cut whose
 * model is {@code <asset>} draws {@code ns:textures/armor/<asset>/<path>.png} (the cut's default
 * livery draws the base {@code <asset>.png}). Sheets come from {@code tools/wardrobe_model.py},
 * so a new colour is a resource-pack change; this entry decides which cuts may carry it and which
 * faction it speaks for (for issuing uniforms on promotion, WARDROBE_RENAISSANCE §B7).
 *
 * @param cuts    item ids of the cuts that may be stitched in this livery; any other is refused
 * @param faction allegiance the livery displays, matched against career tracks when issuing
 */
@NullMarked
public record LiveryDefinition(List<Identifier> cuts, Optional<String> faction) {

    public static final Codec<LiveryDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.listOf().fieldOf("cuts").forGetter(LiveryDefinition::cuts),
            Codec.STRING.optionalFieldOf("faction").forGetter(LiveryDefinition::faction)
    ).apply(i, LiveryDefinition::new));

    public boolean admits(Identifier itemId) {
        return cuts.contains(itemId);
    }
}
