package at.koopro.wizardsandbeasts.spell.beam;

import at.koopro.wizardsandbeasts.spell.core.*;

import net.minecraft.core.BlockPos;

import org.jspecify.annotations.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Per-player state for a held wand-beam channel. */
final class WandBeamSession {

    @Nullable String spellId;
    int beamTicks;
    boolean avadaConsumed;
    @Nullable UUID lastCrucioTarget;
    @Nullable UUID lastLeviosaTarget;
    @Nullable Boolean lastLeviosaHadNoGravity;
    int leviosaMissTicks;
    float leviosaHoldDistance = 6.0f;
    float leviosaLastCommandedSpeed;
    int lastLeviosaSlamTick = -9999;
    int lastProficiencyHitTick;
    @Nullable UUID cachedTarget;
    @Nullable BlockPos aguamentiWaterAim;
    int aguamentiWaterHold;

    void syncSpell(String id) {
        if (!Objects.equals(spellId, id)) {
            spellId = id;
            beamTicks = 0;
            avadaConsumed = false;
            lastCrucioTarget = null;
            lastLeviosaTarget = null;
            lastLeviosaHadNoGravity = null;
            leviosaMissTicks = 0;
            leviosaHoldDistance = 6.0f;
            leviosaLastCommandedSpeed = 0f;
            lastLeviosaSlamTick = -9999;
            lastProficiencyHitTick = -9999;
            cachedTarget = null;
            aguamentiWaterAim = null;
            aguamentiWaterHold = 0;
        }
    }
}
