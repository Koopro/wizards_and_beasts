package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * Signature ability (Occamy): choranaptyxis — it grows or shrinks to fill the available space. Render-only:
 * it eases its synced model scale ({@link GenericBeastEntity#setRenderScale}) toward {@code maxScale} when
 * roused (has an attack target) or roomy, and toward {@code minScale} when calm or boxed in by a low
 * ceiling. The hitbox never changes (registry-frozen); only the rendered model swells/shrinks.
 */
public record OccamyChoranaptyxis(float minScale, float maxScale, float rate) implements CreatureAbility {

    public static final MapCodec<OccamyChoranaptyxis> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("min_scale", 0.6f).forGetter(OccamyChoranaptyxis::minScale),
            Codec.FLOAT.optionalFieldOf("max_scale", 1.6f).forGetter(OccamyChoranaptyxis::maxScale),
            Codec.FLOAT.optionalFieldOf("rate", 0.05f).forGetter(OccamyChoranaptyxis::rate)
    ).apply(instance, OccamyChoranaptyxis::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.OCCAMY_CHORANAPTYXIS;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (entity.level().isClientSide() || entity.tickCount % 4 != 0) {
            return;
        }
        float headroom = ceilingHeadroom(entity);
        boolean roused = entity.getTarget() != null;
        // Want to be big when roused and there is room; small when calm or confined.
        float desired = (roused && headroom >= 3) ? maxScale : (headroom < 2 ? minScale : 1.0f);
        float current = entity.getRenderScale();
        float next = approach(current, desired, rate);
        if (Math.abs(next - current) > 1.0e-4f) {
            entity.setRenderScale(next);
        }
    }

    /** Open blocks directly above the entity, up to 4 (proxy for "available space"). */
    private static int ceilingHeadroom(GenericBeastEntity entity) {
        Level level = entity.level();
        BlockPos head = entity.blockPosition().above((int) Math.ceil(entity.getBbHeight()));
        int room = 0;
        for (int i = 0; i < 4; i++) {
            if (!level.getBlockState(head.above(i)).isAir()) {
                break;
            }
            room++;
        }
        return room;
    }

    private static float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }
}
