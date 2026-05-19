package at.koopro.wizardsandbeasts.event.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class MuffliatoSoundHandler {
    private MuffliatoSoundHandler() {
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null || !localPlayer.hasEffect(ModEffects.MUFFLIATO)) {
            return;
        }
        if (event.getSound() == null) {
            return;
        }
        double maxAudibleDistance = 8.0D;
        double dx = event.getSound().getX() - localPlayer.getX();
        double dy = event.getSound().getY() - localPlayer.getY();
        double dz = event.getSound().getZ() - localPlayer.getZ();
        if ((dx * dx) + (dy * dy) + (dz * dz) > (maxAudibleDistance * maxAudibleDistance)) {
            event.setSound(null);
        }
    }
}
