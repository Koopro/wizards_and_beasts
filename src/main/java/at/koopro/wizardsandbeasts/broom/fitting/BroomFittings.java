package at.koopro.wizardsandbeasts.broom.fitting;

import at.koopro.wizardsandbeasts.broom.BroomTier;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The fittings on one broom, one per {@link FittingSlot}. Lives on the broom's stack as a data component and is
 * mirrored onto the flying entity as a synced string ({@link #encode()}), because the flight is simulated on the
 * rider's client and checked on the server and both have to fly the same broom.
 *
 * <p>Stored as a list of ids, not a slot map: the slot is a property of the fitting, so a map would be a second
 * place for it to disagree. Unknown ids are dropped on read, so retiring a fitting cannot fault a saved broom.
 */
@NullMarked
public record BroomFittings(Map<FittingSlot, BroomFitting> bySlot) {

    public static final BroomFittings NONE = new BroomFittings(Map.of());

    public static final Codec<BroomFittings> CODEC = Codec.STRING.listOf()
            .xmap(BroomFittings::fromIds, BroomFittings::ids);

    public static final StreamCodec<ByteBuf, BroomFittings> STREAM_CODEC = ByteBufCodecs.STRING_UTF8
            .apply(ByteBufCodecs.list(FittingSlot.values().length))
            .map(BroomFittings::fromIds, BroomFittings::ids);

    public BroomFittings {
        EnumMap<FittingSlot, BroomFitting> copy = new EnumMap<>(FittingSlot.class);
        copy.putAll(bySlot);
        bySlot = Collections.unmodifiableMap(copy);
    }

    public static BroomFittings fromIds(List<String> ids) {
        EnumMap<FittingSlot, BroomFitting> map = new EnumMap<>(FittingSlot.class);
        for (String id : ids) {
            BroomFitting fitting = BroomFitting.byId(id);
            if (fitting != null) {
                map.put(fitting.slot(), fitting);
            }
        }
        return map.isEmpty() ? NONE : new BroomFittings(map);
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>(bySlot.size());
        for (BroomFitting fitting : bySlot.values()) {
            ids.add(fitting.id());
        }
        return ids;
    }

    /** The form synced to clients on the broom entity: ids in slot order, comma-separated; empty for none. */
    public String encode() {
        return String.join(",", ids());
    }

    public static BroomFittings decode(String encoded) {
        return encoded.isEmpty() ? NONE : fromIds(List.of(encoded.split(",")));
    }

    public boolean isEmpty() {
        return bySlot.isEmpty();
    }

    public int count() {
        return bySlot.size();
    }

    public Optional<BroomFitting> in(FittingSlot slot) {
        return Optional.ofNullable(bySlot.get(slot));
    }

    public java.util.Collection<BroomFitting> all() {
        return bySlot.values();
    }

    /** Why a fitting cannot go on a broom; {@link #OK} when it can. */
    public enum Refusal { OK, TIER_TOO_LOW, NO_ROOM, ALREADY_FITTED }

    /**
     * Whether {@code fitting} can go on a broom of {@code tier} carrying these fittings. A fitting for an occupied
     * slot <em>replaces</em> the one there, so it never needs room; one for an empty slot needs a free place within
     * {@link BroomFitting#capacity}.
     */
    public Refusal check(BroomFitting fitting, BroomTier tier) {
        if (!fitting.fits(tier)) {
            return Refusal.TIER_TOO_LOW;
        }
        BroomFitting there = bySlot.get(fitting.slot());
        if (there == fitting) {
            return Refusal.ALREADY_FITTED;
        }
        if (there == null && count() >= BroomFitting.capacity(tier)) {
            return Refusal.NO_ROOM;
        }
        return Refusal.OK;
    }

    /** These fittings with {@code fitting} in its slot (replacing whatever was there). */
    public BroomFittings with(BroomFitting fitting) {
        EnumMap<FittingSlot, BroomFitting> map = new EnumMap<>(FittingSlot.class);
        map.putAll(bySlot);
        map.put(fitting.slot(), fitting);
        return new BroomFittings(map);
    }

    /** The product of every fitting's multiplier for {@code stat}; 1 when none touches it. */
    public float multiplier(FittingStat stat) {
        float product = 1.0f;
        for (BroomFitting fitting : bySlot.values()) {
            product *= fitting.modifiers().getOrDefault(stat, 1.0f);
        }
        if (stat == FittingStat.MAX_SPEED) {
            product = Math.min(product, BroomFitting.MAX_SPEED_GAIN);
        }
        return product;
    }
}
