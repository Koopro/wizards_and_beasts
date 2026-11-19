package at.koopro.wizardsandbeasts.client.atmosphere;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A place's fog: drawn a little toward its colour and pulled in a little, both by the place's eased strength, so it
 * thickens over seconds as one walks into an old wood at night and thins again on the way out.
 *
 * <p>Only the open-air fog is touched. Under water, in lava or powder snow, and under Blindness or Darkness the
 * vanilla fog has a job to do and is left alone. Off with {@code Config.atmosphereFog}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
@NullMarked
public final class AtmosphereFog {

    private AtmosphereFog() {}

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!applies(event.getCamera().getFluidInCamera())) {
            return;
        }
        float scale = 1f;
        for (PlaceSense.Place place : PlaceSense.places()) {
            var fog = place.profile().fog().orElse(null);
            float s = place.strength();
            if (fog == null || s <= 0f || !place.gated || !fog.when().test(AtmosphereDirector.ambience())) {
                continue;
            }
            scale = Math.min(scale, 1f - (1f - fog.far()) * s);
        }
        if (scale < 1f) {
            event.scaleFarPlaneDistance(scale);
            event.scaleNearPlaneDistance(scale);
        }
    }

    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        if (!applies(event.getCamera().getFluidInCamera())) {
            return;
        }
        float r = event.getRed();
        float g = event.getGreen();
        float b = event.getBlue();
        boolean changed = false;
        for (PlaceSense.Place place : PlaceSense.places()) {
            var fog = place.profile().fog().orElse(null);
            float s = place.strength();
            if (fog == null || s <= 0f || !place.gated || !fog.when().test(AtmosphereDirector.ambience())) {
                continue;
            }
            float t = fog.tint() * s;
            int c = fog.colour();
            // The place's colour, scaled by how bright the vanilla fog is now: a tint, never a light source.
            float lum = Math.max(r, Math.max(g, b));
            r += (((c >> 16) & 0xFF) / 255f * lum - r) * t;
            g += (((c >> 8) & 0xFF) / 255f * lum - g) * t;
            b += ((c & 0xFF) / 255f * lum - b) * t;
            changed = true;
        }
        if (changed) {
            event.setRed(r);
            event.setGreen(g);
            event.setBlue(b);
        }
    }

    private static boolean applies(FogType type) {
        if (!Config.atmosphereFog || type != FogType.ATMOSPHERIC) {
            return false;
        }
        Entity camera = Minecraft.getInstance().getCameraEntity();
        return !(camera instanceof LivingEntity living)
                || !(living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }
}
