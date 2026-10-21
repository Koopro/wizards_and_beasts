package at.koopro.wizardsandbeasts.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the mod needs to know about one wearable item, as data.
 *
 * <p>Lives in the {@code wizards_and_beasts:wearables} item data map
 * ({@code data/<namespace>/data_maps/item/wearables.json}), so a new robe, ring or mask is a JSON
 * entry rather than a code change — the Curios slot it may enter, whether it covers armour, and what
 * it grants all follow from this record. Example:
 *
 * <pre>{@code
 * "wizards_and_beasts:death_eater_robe_chest": {
 *   "equipment_type": "body_clothing",
 *   "visual_layer": "body_clothing",
 *   "hides": ["chest"],
 *   "hides_with_hood_up": ["head"],
 *   "faction": "death_eaters",
 *   "attributes": [{ "attribute": "minecraft:armor", "amount": 6, "operation": "add_value" }]
 * }
 * }</pre>
 *
 * @param type            the semantic category, which also fixes the Curios slot
 * @param layer           the draw layer, defaulting to the type's
 * @param hides           vanilla armour slots this piece covers <em>visually</em>; the armour stays
 *                        equipped and keeps its stats
 * @param hidesWithHoodUp extra slots covered while the stack's hood is raised
 * @param concealsWearer  the wearer's whole outfit — armour, held items, every W&amp;B piece — is
 *                        withheld from rendering (the Deathly Hallow)
 * @param faction         optional allegiance id a uniform signals, for future Ministry/house systems
 * @param attributes      modifiers granted while worn in a non-cosmetic slot, through Curios
 */
@NullMarked
public record WearableDefinition(
        EquipmentType type,
        VisualLayer layer,
        Set<EquipmentSlot> hides,
        Set<EquipmentSlot> hidesWithHoodUp,
        boolean concealsWearer,
        Optional<String> faction,
        List<Modifier> attributes) {

    /** Only the four humanoid armour slots can be hidden; hands and the wolf/horse BODY slot cannot. */
    private static final Codec<EquipmentSlot> ARMOR_SLOT = EquipmentSlot.CODEC.validate(slot ->
            slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                    ? DataResult.success(slot)
                    : DataResult.error(() -> "Not a humanoid armour slot: " + slot.getName()));

    private static final Codec<Set<EquipmentSlot>> ARMOR_SLOTS = ARMOR_SLOT.listOf()
            .xmap(WearableDefinition::slotSet, List::copyOf);

    public static final Codec<WearableDefinition> CODEC = RecordCodecBuilder.<Partial>create(i -> i.group(
            EquipmentType.CODEC.fieldOf("equipment_type").forGetter(Partial::type),
            VisualLayer.CODEC.optionalFieldOf("visual_layer").forGetter(Partial::layer),
            ARMOR_SLOTS.optionalFieldOf("hides", Set.of()).forGetter(Partial::hides),
            ARMOR_SLOTS.optionalFieldOf("hides_with_hood_up", Set.of()).forGetter(Partial::hidesWithHoodUp),
            Codec.BOOL.optionalFieldOf("conceals_wearer", false).forGetter(Partial::concealsWearer),
            Codec.STRING.optionalFieldOf("faction").forGetter(Partial::faction),
            Modifier.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(Partial::attributes)
    ).apply(i, Partial::new)).xmap(Partial::resolve, Partial::of);

    public WearableDefinition {
        hides = Set.copyOf(hides);
        hidesWithHoodUp = Set.copyOf(hidesWithHoodUp);
        attributes = List.copyOf(attributes);
    }

    /** A bare definition of the given type: no hiding, no stats. */
    public static WearableDefinition of(EquipmentType type) {
        return new WearableDefinition(type, type.defaultLayer(), Set.of(), Set.of(), false,
                Optional.empty(), List.of());
    }

    /** Armour slots covered for a stack whose hood is (or is not) raised. */
    public Set<EquipmentSlot> hiddenSlots(boolean hoodUp) {
        if (!hoodUp || hidesWithHoodUp.isEmpty()) {
            return hides;
        }
        EnumSet<EquipmentSlot> union = slotSet(List.copyOf(hides));
        union.addAll(hidesWithHoodUp);
        return union;
    }

    private static EnumSet<EquipmentSlot> slotSet(List<EquipmentSlot> slots) {
        EnumSet<EquipmentSlot> set = EnumSet.noneOf(EquipmentSlot.class);
        set.addAll(slots);
        return set;
    }

    /**
     * One attribute modifier granted while worn.
     *
     * <p>The id defaults to {@code wizards_and_beasts:wearable/<attribute path>}; Curios suffixes it
     * with the slot and index, so two rings carrying the same entry never collide.
     */
    public record Modifier(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation,
                           Optional<Identifier> id) {
        public static final Codec<Modifier> CODEC = RecordCodecBuilder.create(i -> i.group(
                Attribute.CODEC.fieldOf("attribute").forGetter(Modifier::attribute),
                Codec.DOUBLE.fieldOf("amount").forGetter(Modifier::amount),
                AttributeModifier.Operation.CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE)
                        .forGetter(Modifier::operation),
                Identifier.CODEC.optionalFieldOf("id").forGetter(Modifier::id)
        ).apply(i, Modifier::new));

        public AttributeModifier toModifier() {
            Identifier modifierId = id.orElseGet(() -> Identifier.fromNamespaceAndPath(
                    "wizards_and_beasts", "wearable/" + attribute.unwrapKey()
                            .map(key -> key.identifier().getPath())
                            .orElse("unknown")));
            return new AttributeModifier(modifierId, amount, operation);
        }
    }

    /** Decoded shape with the layer still optional, so the default can come from the type. */
    private record Partial(EquipmentType type, Optional<VisualLayer> layer, Set<EquipmentSlot> hides,
                           Set<EquipmentSlot> hidesWithHoodUp, boolean concealsWearer,
                           Optional<String> faction, List<Modifier> attributes) {
        WearableDefinition resolve() {
            return new WearableDefinition(type, layer.orElse(type.defaultLayer()), hides, hidesWithHoodUp,
                    concealsWearer, faction, attributes);
        }

        static Partial of(WearableDefinition d) {
            return new Partial(d.type(), Optional.of(d.layer()), d.hides(), d.hidesWithHoodUp(),
                    d.concealsWearer(), d.faction(), d.attributes());
        }
    }
}
