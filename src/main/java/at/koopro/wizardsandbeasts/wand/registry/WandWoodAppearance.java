package at.koopro.wizardsandbeasts.wand.registry;

import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What a wand of this wood looks like: the colour of the timber and the shape it is turned into.
 *
 * <p>The wand rig carries every handle, shaft and tip the author modelled as a variant bone
 * ({@code WandModuleRegistry}); which one a wand shows used to be chosen only by a command, so every
 * wand made in play rendered the same classic handle, straight shaft and pointed tip, and ten woods
 * differed by a tint alone. A wood now names its own handle, shaft and tip here, so an ash wand and
 * a vine wand are different silhouettes in the hand. The tint lives here too, next to the silhouette
 * it colours, instead of in a Java table beside a registry that already knew every wood.
 *
 * <p>Every field is optional. A wood with no tint gets a colour derived from its id; a wood with no
 * module for a slot keeps the base wand's choice for that slot. A module a stack sets explicitly
 * (the wand configuration component) always wins over the wood's.
 *
 * @param tint   ARGB multiplier for the wand sheet, written {@code "#RRGGBB"} or {@code "#AARRGGBB"}
 * @param handle module id for the handle slot, e.g. {@code wizards_and_beasts:gnarled}
 * @param shaft  module id for the shaft slot
 * @param tip    module id for the tip slot
 */
public record WandWoodAppearance(Optional<Integer> tint,
                                 Optional<Identifier> handle,
                                 Optional<Identifier> shaft,
                                 Optional<Identifier> tip) {

    public static final WandWoodAppearance NONE =
            new WandWoodAppearance(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    private static final Codec<Integer> HEX_COLOUR = Codec.STRING.comapFlatMap(
            WandWoodAppearance::parseHex,
            argb -> String.format(Locale.ROOT, "#%08X", argb));

    public static final Codec<WandWoodAppearance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            HEX_COLOUR.optionalFieldOf("tint").forGetter(WandWoodAppearance::tint),
            Identifier.CODEC.optionalFieldOf("handle").forGetter(WandWoodAppearance::handle),
            Identifier.CODEC.optionalFieldOf("shaft").forGetter(WandWoodAppearance::shaft),
            Identifier.CODEC.optionalFieldOf("tip").forGetter(WandWoodAppearance::tip)
    ).apply(instance, WandWoodAppearance::new));

    /** The slots this wood decides, in slot order; slots it leaves open are absent. */
    public Map<WandSlot, Identifier> modules() {
        Map<WandSlot, Identifier> modules = new EnumMap<>(WandSlot.class);
        handle.ifPresent(id -> modules.put(WandSlot.HANDLE, id));
        shaft.ifPresent(id -> modules.put(WandSlot.SHAFT, id));
        tip.ifPresent(id -> modules.put(WandSlot.TIP, id));
        return modules;
    }

    /** {@code #RRGGBB} is taken as opaque; {@code #AARRGGBB} as written. */
    static DataResult<Integer> parseHex(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6 && hex.length() != 8) {
            return DataResult.error(() -> "Wand wood tint must be #RRGGBB or #AARRGGBB: " + text);
        }
        try {
            long value = Long.parseLong(hex, 16);
            return DataResult.success((int) (hex.length() == 6 ? value | 0xFF000000L : value));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Wand wood tint is not hexadecimal: " + text);
        }
    }
}
