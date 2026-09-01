package at.koopro.wizardsandbeasts.entity.frog;

import at.koopro.wizardsandbeasts.chocolate.ChocolateFrog;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/**
 * A Chocolate Frog that got away.
 *
 * <p><b>An {@link net.minecraft.world.entity.item.ItemEntity}, deliberately.</b> A frog you can chase
 * and catch is exactly a dropped item with a mind of its own: it already renders as the chocolate
 * frog, already syncs, already despawns, and — the part that matters — catching it is already
 * implemented, because walking into a dropped item is how you pick one up. Building a {@code Mob}
 * for this would have meant a model, a renderer, an AI goal and a pickup interaction, all to
 * reimplement what {@code ItemEntity} does for free.
 *
 * <p>What it adds is the hop, a short life, and a pickup delay long enough that the frog gets a
 * head start rather than being reabsorbed the instant it leaves your hand.
 */
@NullMarked
public class ChocolateFrogEntity extends net.minecraft.world.entity.item.ItemEntity {

    /** Upward kick of a hop. */
    private static final double HOP_HEIGHT = 0.42;
    /** Horizontal shove of a hop. */
    private static final double HOP_DISTANCE = 0.34;
    /** Ticks between hops. Frequent enough to be hard to corner, slow enough to be catchable. */
    private static final int HOP_INTERVAL = 11;
    /** Ticks before it can be picked up, so it clears the thrower's feet first. */
    private static final int HEAD_START_TICKS = 12;

    public ChocolateFrogEntity(EntityType<? extends net.minecraft.world.entity.item.ItemEntity> type,
                               Level level) {
        super(type, level);
    }

    public ChocolateFrogEntity(Level level, double x, double y, double z, ItemStack stack) {
        super(ModEntities.CHOCOLATE_FROG.get(), level);
        setPos(x, y, z);
        setItem(stack);
        setPickUpDelay(HEAD_START_TICKS);
        // 5-8s, rolled per frog. lifespan is ItemEntity's own despawn clock, so nothing extra has to
        // tick to make the snack expire.
        this.lifespan = ChocolateFrog.escapeLifetime(level.getRandom());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || !onGround() || tickCount % HOP_INTERVAL != 0) {
            return;
        }
        hop();
    }

    /** One bound, in a random direction. */
    private void hop() {
        double angle = random.nextDouble() * Math.PI * 2.0;
        setDeltaMovement(new Vec3(
                Math.cos(angle) * HOP_DISTANCE,
                HOP_HEIGHT,
                Math.sin(angle) * HOP_DISTANCE));
        level().playSound(null, getX(), getY(), getZ(),
                SoundEvents.SLIME_JUMP_SMALL, SoundSource.NEUTRAL, 0.3f, 1.6f);
    }

    /**
     * Item entities merge with nearby stacks of the same thing, and {@code isMergable} is private on
     * the superclass so that cannot be switched off. It costs nothing here: merging needs another
     * dropped chocolate frog within half a block, and the merged stack is still catchable.
     */
}
