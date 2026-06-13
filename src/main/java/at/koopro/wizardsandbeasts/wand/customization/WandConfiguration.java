package at.koopro.wizardsandbeasts.wand.customization;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-stack visual configuration for a wand. Stored as a DataComponentType.
 * Maps each WandSlot to the registry id of the chosen WandModule.
 * Optional slots (CORE, ORNAMENT) may be absent from the map.
 *
 * Two wands with different configurations do not stack (each holds this component).
 */
public record WandConfiguration(@NonNull Map<WandSlot, Identifier> modules) {

    /** Default configuration: classic handle, straight shaft, pointed tip. No core or ornament. */
    public static final WandConfiguration DEFAULT = new WandConfiguration(Map.of(
            WandSlot.HANDLE, Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "classic"),
            WandSlot.SHAFT,  Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "straight"),
            WandSlot.TIP,    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pointed")
    ));

    /** Canonical constructor — ensures immutable copy of the provided map. */
    public WandConfiguration {
        modules = Map.copyOf(modules);
    }

    // ── Codec ────────────────────────────────────────────────────────────────

    private static final Codec<WandSlot> SLOT_KEY_CODEC = Codec.STRING.comapFlatMap(
            id -> WandSlot.byId(id)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown wand slot: " + id)),
            WandSlot::slotId
    );

    private static final Codec<Map<WandSlot, Identifier>> SLOT_MAP_CODEC =
            Codec.unboundedMap(SLOT_KEY_CODEC, Identifier.CODEC);

    public static final Codec<WandConfiguration> CODEC =
            SLOT_MAP_CODEC.xmap(WandConfiguration::new, WandConfiguration::modules);

    // ── StreamCodec ──────────────────────────────────────────────────────────

    public static final StreamCodec<ByteBuf, WandConfiguration> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WandConfiguration decode(ByteBuf buf) {
            int size = ByteBufCodecs.VAR_INT.decode(buf);
            EnumMap<WandSlot, Identifier> map = new EnumMap<>(WandSlot.class);
            WandSlot[] slots = WandSlot.values();
            for (int i = 0; i < size; i++) {
                int ordinal = ByteBufCodecs.VAR_INT.decode(buf);
                if (ordinal >= 0 && ordinal < slots.length) {
                    Identifier id = Identifier.STREAM_CODEC.decode(buf);
                    map.put(slots[ordinal], id);
                }
            }
            return new WandConfiguration(map);
        }

        @Override
        public void encode(ByteBuf buf, WandConfiguration config) {
            Map<WandSlot, Identifier> m = config.modules();
            ByteBufCodecs.VAR_INT.encode(buf, m.size());
            m.forEach((slot, id) -> {
                ByteBufCodecs.VAR_INT.encode(buf, slot.ordinal());
                Identifier.STREAM_CODEC.encode(buf, id);
            });
        }
    };

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns the configured module id for the given slot, or empty if not set. */
    public Optional<Identifier> getModule(WandSlot slot) {
        return Optional.ofNullable(modules.get(slot));
    }

    /** Returns a new WandConfiguration with the given slot set to moduleId. */
    public WandConfiguration withModule(@NonNull WandSlot slot, @NonNull Identifier moduleId) {
        EnumMap<WandSlot, Identifier> copy = new EnumMap<>(modules);
        copy.put(slot, moduleId);
        return new WandConfiguration(copy);
    }

    /** Returns a new WandConfiguration with the given optional slot cleared. */
    public WandConfiguration withoutModule(@NonNull WandSlot slot) {
        EnumMap<WandSlot, Identifier> copy = new EnumMap<>(modules);
        copy.remove(slot);
        return new WandConfiguration(copy);
    }
}
