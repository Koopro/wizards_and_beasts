package at.koopro.wizardsandbeasts.equipment;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

/**
 * What a worn piece <em>is</em>, independent of where it is stored.
 *
 * <p>Gameplay asks semantic questions — "is a cloak worn", "which rings" — and never names a Curios
 * slot. The slot a type lives in is decided here and nowhere else: {@link #slotId()} is the single
 * table of Curios slot strings in the mod. Two types may share a slot (a cloak and a backpack both
 * hang on the back, a pouch and a trinket both go in the charm slot), which is why the type is read
 * from the item's {@link WearableDefinition} rather than inferred from the slot it sits in.
 *
 * <p>Vanilla armour is deliberately absent. Helmets, chestplates, leggings and boots stay in their
 * {@link net.minecraft.world.entity.EquipmentSlot}s and keep every vanilla mechanic; the W&amp;B layer
 * only reads them.
 */
@NullMarked
public enum EquipmentType implements StringRepresentable {
    /** Wizard and witch hats, crowns, the Sorting Hat. */
    HEADWEAR("headwear", "head", VisualLayer.HEADWEAR),
    /** Masks, magical spectacles, face-worn disguises. */
    FACE("face", "face", VisualLayer.FACE),
    /** Robes, uniforms, ceremonial and travelling clothes. */
    BODY_CLOTHING("body_clothing", "body", VisualLayer.BODY_CLOTHING),
    /** Cloaks — including the Invisibility Cloak and the Deathly Hallow. */
    CLOAK("cloak", "back", VisualLayer.CLOAK),
    /** Anything else carried on the back: packs, future back artefacts. Shares the cloak's slot. */
    BACK("back", "back", VisualLayer.CLOAK),
    /** Time-Turner, amulets, pendants, Ministry badges. */
    NECKLACE("necklace", "necklace", VisualLayer.NECK),
    /** Rings. The slot count is data (Curios {@code ring} slot size), never assumed here. */
    RING("ring", "ring", VisualLayer.ACCESSORY),
    /** Utility, potion and dragonhide belts. */
    BELT("belt", "belt", VisualLayer.ACCESSORY),
    /** Gloves. The wand is <em>not</em> hand equipment — the wand system stays authoritative. */
    HANDS("hands", "hands", VisualLayer.HANDS),
    /** Wizard and ingredient pouches. */
    POUCH("pouch", "charm", VisualLayer.ACCESSORY),
    /** Small artefacts and charms with no body part of their own. */
    TRINKET("trinket", "charm", VisualLayer.ACCESSORY);

    public static final Codec<EquipmentType> CODEC = StringRepresentable.fromEnum(EquipmentType::values);

    private final String serializedName;
    private final String slotId;
    private final VisualLayer defaultLayer;

    EquipmentType(String serializedName, String slotId, VisualLayer defaultLayer) {
        this.serializedName = serializedName;
        this.slotId = slotId;
        this.defaultLayer = defaultLayer;
    }

    /**
     * The Curios slot identifier this type is stored in.
     *
     * <p>Kept in step with {@code data/wizards_and_beasts/curios/slots/*.json} and
     * {@code curios/entities/player.json} — {@code EquipmentSlotDataTest} fails if a type names a
     * slot that no data file declares.
     */
    public String slotId() {
        return slotId;
    }

    /** The draw layer a definition gets when it does not name one. */
    public VisualLayer defaultLayer() {
        return defaultLayer;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
