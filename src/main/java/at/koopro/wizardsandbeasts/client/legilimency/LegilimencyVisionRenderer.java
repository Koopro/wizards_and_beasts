package at.koopro.wizardsandbeasts.client.legilimency;

import at.koopro.wizardsandbeasts.client.legilimency.state.ClientLegilimencyVisionState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class LegilimencyVisionRenderer {
    private LegilimencyVisionRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        BlockPos pos = ClientLegilimencyVisionState.markerPos();
        if (pos == null) {
            return;
        }
        int red = 0xFF3333;
        for (int i = 0; i < 2; i++) {
            double y = pos.getY() + 1.4 + (i * 0.5);
            mc.level.addParticle(new DustParticleOptions(red, 1.2f), pos.getX() + 0.5, y, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
        }
    }
}
