package at.koopro.wizardsandbeasts.client.trinket;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.trinket.SneakoscopeItem;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.sneakoscope.SneakoscopeFocus;
import at.koopro.wizardsandbeasts.sneakoscope.SneakoscopeTier;
import at.koopro.wizardsandbeasts.sneakoscope.SneakoscopeTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The silver light that orbits a working Sneakoscope, and the arrow it draws in focused mode.
 *
 * <p><b>Nothing here is on the wire.</b> Every input is already on the held stack — the threat count
 * and the focus/bearing components replicate with the item — so the whole presentation is derived
 * client-side from data that was being sent anyway. That is also why it is drawn with
 * {@code ClientLevel#addParticle} rather than {@code ServerLevel#sendParticles}: the arrow is the
 * holder's private reading, and a server-spawned particle would hand it to everyone standing nearby,
 * including the person it is pointing at.
 *
 * <p>Motes start at {@link SneakoscopeTier#MEDIUM}. One or two suspects get the spin and the whirr
 * and nothing else, because a Sneakoscope that throws light at the smallest provocation cannot be
 * carried anywhere quietly, and being carriable is the point of it.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
@NullMarked
public final class SneakoscopeFx {

    /** Ticks between mote emissions. */
    private static final int ORBIT_INTERVAL_TICKS = 4;
    /** Ticks between arrow redraws. Slower than the motes: it is a pointer, not a sparkle. */
    private static final int ARROW_INTERVAL_TICKS = 6;

    /** Cold silver — the colour of the glass, not of a spell. */
    private static final int SILVER = 0xC9D6E8;
    /** Slightly warmer for the arrow, so it separates from the orbit at a glance. */
    private static final int ARROW_SILVER = 0xE4ECF7;

    /** Orbit radius around the item, in blocks. */
    private static final double ORBIT_RADIUS = 0.22;
    /** Orbit revolutions per second. Independent of the model spin: this is the light, not the top. */
    private static final double ORBIT_HZ = 0.8;
    /** Vertical wander of the orbit, in blocks, so it reads as a sphere rather than a ring. */
    private static final double ORBIT_BOB = 0.06;

    /** How far in front of the eyes the item is taken to be, for placing the motes. */
    private static final double ITEM_FORWARD = 0.55;
    /** Sideways offset to the holding hand. */
    private static final double ITEM_SIDE = 0.34;
    /** Drop from eye level to roughly where the hand is. */
    private static final double ITEM_DROP = 0.30;

    /** Where the arrow starts and ends, in blocks from the holder. */
    private static final double ARROW_NEAR = 0.9;
    private static final double ARROW_FAR = 1.9;
    /** Motes along the shaft. Four is enough to read as a line and few enough to stay subtle. */
    private static final int ARROW_SHAFT_MOTES = 4;
    /** How far back and out each barb sits from the tip, in blocks. */
    private static final double ARROW_BARB_BACK = 0.32;
    private static final double ARROW_BARB_OUT = 0.22;

    private SneakoscopeFx() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            return;
        }
        ItemStack stack = HeldSneakoscope.find(player);
        if (stack == null) {
            return;
        }

        int threats = SneakoscopeItem.threatCount(stack);
        SneakoscopeTier tier = SneakoscopeTuning.tier(threats);
        SneakoscopeFocus focus = SneakoscopeItem.focusOf(stack);
        long tick = level.getGameTime();

        if (tick % ORBIT_INTERVAL_TICKS == 0L) {
            drawOrbit(level, player, tier, HeldSneakoscope.isOffHand(player, stack));
        }
        if (focus.hasArrow() && tick % ARROW_INTERVAL_TICKS == 0L) {
            drawArrow(level, player, focus.bearing());
        }
    }

    /** Silver light circling the glass, seeded evenly around the ring so it never looks like a stream. */
    private static void drawOrbit(ClientLevel level, LocalPlayer player, SneakoscopeTier tier, boolean offHand) {
        int motes = SneakoscopeTuning.orbitParticles(tier);
        if (motes <= 0) {
            return;
        }
        Vec3 centre = itemPosition(player, offHand);
        double seconds = level.getGameTime() / 20.0;
        double base = seconds * ORBIT_HZ * 2.0 * Math.PI;

        for (int i = 0; i < motes; i++) {
            double angle = base + (2.0 * Math.PI * i) / motes;
            double x = centre.x + Math.cos(angle) * ORBIT_RADIUS;
            double z = centre.z + Math.sin(angle) * ORBIT_RADIUS;
            double y = centre.y + Math.sin(angle * 2.0) * ORBIT_BOB;
            level.addParticle(new SpellTintParticleOptions(ModParticles.LIGHT_GLOW.get(), SILVER),
                    x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /**
     * A short arrow of motes laid along the bearing sector.
     *
     * <p>Drawn from the sector's mid-angle rather than from the suspect's real position: the bearing
     * on the wire is already quantised to 22.5°, and drawing it any more precisely than it was
     * measured would promise an accuracy the item does not have.
     */
    private static void drawArrow(ClientLevel level, LocalPlayer player, int sector) {
        double dx = SneakoscopeTuning.sectorDirectionX(sector);
        double dz = SneakoscopeTuning.sectorDirectionZ(sector);
        Vec3 origin = player.position().add(0.0, player.getEyeHeight() * 0.75, 0.0);
        SpellTintParticleOptions mote =
                new SpellTintParticleOptions(ModParticles.ARCANE_MOTE.get(), ARROW_SILVER);

        for (int i = 0; i < ARROW_SHAFT_MOTES; i++) {
            double along = ARROW_NEAR + (ARROW_FAR - ARROW_NEAR) * i / (ARROW_SHAFT_MOTES - 1.0);
            level.addParticle(mote, origin.x + dx * along, origin.y, origin.z + dz * along, 0.0, 0.0, 0.0);
        }

        // Two barbs, one either side of the shaft, set back from the tip — the difference between a
        // line of sparks and something the eye reads as pointing.
        double tipX = origin.x + dx * ARROW_FAR;
        double tipZ = origin.z + dz * ARROW_FAR;
        double perpX = -dz;
        double perpZ = dx;
        for (int side = -1; side <= 1; side += 2) {
            level.addParticle(mote,
                    tipX - dx * ARROW_BARB_BACK + perpX * ARROW_BARB_OUT * side,
                    origin.y,
                    tipZ - dz * ARROW_BARB_BACK + perpZ * ARROW_BARB_OUT * side,
                    0.0, 0.0, 0.0);
        }
    }

    /** Roughly where the held item sits in the world, for a first-person orbit that tracks the hand. */
    private static Vec3 itemPosition(LocalPlayer player, boolean offHand) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        Vec3 forward = flat.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        boolean leftHanded = offHand == (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
        return player.getEyePosition()
                .add(forward.scale(ITEM_FORWARD))
                .add(right.scale(leftHanded ? -ITEM_SIDE : ITEM_SIDE))
                .subtract(0.0, ITEM_DROP, 0.0);
    }
}
