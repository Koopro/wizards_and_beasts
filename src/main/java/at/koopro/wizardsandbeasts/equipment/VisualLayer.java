package at.koopro.wizardsandbeasts.equipment;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

/**
 * The outside-in order a wearer is dressed in.
 *
 * <p>Minecraft draws opaque cutout geometry depth-tested, so submission order alone cannot put a
 * robe "over" a chestplate — whichever surface is further out wins. The order is therefore realised
 * two ways, and this enum is the contract both follow:
 * <ol>
 *   <li><b>Hiding.</b> A piece on an outer layer may hide the vanilla armour beneath it
 *       ({@link WearableDefinition#hides()}); the armour stays equipped and keeps its stats, it is
 *       only left out of that frame's render state. That is what keeps a netherite chestplate
 *       (inflated a full pixel) from poking through a robe (inflated about half a pixel).</li>
 *   <li><b>Inflation ladder.</b> Garment models are authored to sit just outside the layer below
 *       them ({@code tools/armor_model.py}), so two worn pieces never share a surface and cannot
 *       z-fight.</li>
 * </ol>
 * The ordinal is also the listing order for the Character Sheet and the debug report.
 */
@NullMarked
public enum VisualLayer implements StringRepresentable {
    SKIN("skin"),
    ARMOR("armor"),
    BODY_CLOTHING("body_clothing"),
    CLOAK("cloak"),
    HEADWEAR("headwear"),
    FACE("face"),
    NECK("neck"),
    HANDS("hands"),
    ACCESSORY("accessory"),
    EFFECT("effect");

    public static final Codec<VisualLayer> CODEC = StringRepresentable.fromEnum(VisualLayer::values);

    private final String serializedName;

    VisualLayer(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
