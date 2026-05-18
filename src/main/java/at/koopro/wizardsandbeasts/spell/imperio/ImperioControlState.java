package at.koopro.wizardsandbeasts.spell.imperio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * Server-serialised Imperius control metadata for a living entity.
 */
public record ImperioControlState(
        boolean isControlled,
        UUID controllerUUID,
        int remainingTicks,
        float resistanceProgress,
        int imperioCommandOrdinal,
        boolean sneakAttemptSinceLastResistTick) {

    public static final ImperioControlState DEFAULT =
            new ImperioControlState(false, UUID.fromString("00000000-0000-0000-0000-000000000000"), 0, 0f, 0, false);

    public static final Codec<ImperioControlState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.BOOL.fieldOf("is_controlled").forGetter(ImperioControlState::isControlled),
            UUIDUtil.CODEC.fieldOf("controller_uuid").forGetter(ImperioControlState::controllerUUID),
            Codec.INT.fieldOf("remaining_ticks").forGetter(ImperioControlState::remainingTicks),
            Codec.FLOAT.fieldOf("resistance_progress").forGetter(ImperioControlState::resistanceProgress),
            Codec.INT.optionalFieldOf("imperio_command", 0).forGetter(ImperioControlState::imperioCommandOrdinal),
            Codec.BOOL.optionalFieldOf("sneak_attempt", false).forGetter(ImperioControlState::sneakAttemptSinceLastResistTick)
    ).apply(inst, ImperioControlState::new));

    public ImperioControlState withSneakAttempt(boolean v) {
        return new ImperioControlState(isControlled, controllerUUID, remainingTicks, resistanceProgress, imperioCommandOrdinal, v);
    }

    public ImperioControlState withCommandOrdinal(int ord) {
        return new ImperioControlState(isControlled, controllerUUID, remainingTicks, resistanceProgress, ord, sneakAttemptSinceLastResistTick);
    }

    public ImperioControlState tickDown() {
        if (!isControlled) {
            return this;
        }
        int next = Math.max(0, remainingTicks - 1);
        boolean stillControlled = next > 0;
        // Preserve sneakAttemptUntil processed by ImperioServerLogic.tickVictim (resistance roll).
        return new ImperioControlState(stillControlled, controllerUUID, next, resistanceProgress,
                imperioCommandOrdinal, sneakAttemptSinceLastResistTick);
    }
}
