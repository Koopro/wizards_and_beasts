package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.beam.WandTipTracker;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.visual.WandBondLook;
import at.koopro.wizardsandbeasts.wand.visual.WandLook;
import at.koopro.wizardsandbeasts.wand.visual.WandMetal;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A wand at rest in a hand is not quite still: now and then a mote of its core's light slips off the point
 * and drifts down, and a legendary or gilt wand catches a glint along its metal. Strong cores shed more,
 * a wand that does not answer to its holder sheds nothing.
 *
 * <p>Purely client-side and read from what every client already has -- the stack's wood, core and allegiance
 * and the drawn tip ({@link WandTipTracker}) -- so other players' wands show it too with nothing sent. A wand
 * mid-cast is left to {@code CastChargeMotes}, which gathers the spell's own colour at the tip instead.
 * {@link Config#wandEffects} sets how much shows (off, subtle, full); the motes also thin with the vanilla
 * particle setting.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class WandIdleEffects {

    /** Beyond this, a wand's light is a pixel nobody can see. */
    private static final double RANGE_SQ = 24.0 * 24.0;
    /** Below this bond the core shows no light at all ({@link WandBondLook}), so it sheds none. */
    private static final float MIN_BOND = 0.35f;

    private WandIdleEffects() {}

    /**
     * Ticks between motes for a core showing this bright (its strength times the bond's), or 0 for none. A
     * strong core in its master's hand sheds one about every two seconds at subtle; a troll whisker hardly ever.
     */
    static int moteInterval(Config.WandEffects setting, float glintStrength) {
        if (setting == Config.WandEffects.OFF || glintStrength <= 0.05f) {
            return 0;
        }
        float base = setting == Config.WandEffects.FULL ? 12.0f : 32.0f;
        return Math.max(4, Math.round(base / Math.max(0.2f, glintStrength)));
    }

    /** Ticks between glints on gilt metal, or 0 for none. */
    static int glintInterval(Config.WandEffects setting, WandMetal metal, boolean elder) {
        if (setting == Config.WandEffects.OFF || (metal != WandMetal.GOLD && !elder)) {
            return 0;
        }
        return setting == Config.WandEffects.FULL ? 14 : 40;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Config.WandEffects setting = Config.wandEffects;
        if (mc.level == null || mc.isPaused() || setting == Config.WandEffects.OFF || mc.player == null) {
            return;
        }
        long tick = mc.level.getGameTime();
        RandomSource random = mc.level.random;
        for (Player player : mc.level.players()) {
            if (player.isInvisible() || player.distanceToSqr(mc.player) > RANGE_SQ || player.isUsingItem()) {
                continue;
            }
            ItemStack stack = wandIn(player);
            if (stack.isEmpty()) {
                continue;
            }
            WandLook look = WandLookCache.look(mc.level.registryAccess(), stack);
            WandBondLook bond = WandBondLook.of(WandComponents.getMaster(stack),
                    WandComponents.getAllegianceScore(stack), player.getUUID());
            if (bond.strength() < MIN_BOND) {
                continue;
            }
            // Each holder on their own beat, so a room of wizards does not shed in unison.
            long beat = tick + player.getId() * 7L;
            int motes = moteInterval(setting, look.glintStrength() * bond.strength());
            int glints = glintInterval(setting, look.metal(), look.elder());
            boolean mote = motes > 0 && beat % motes == 0 && (look.glint() & 0xFFFFFF) != 0;
            boolean glint = glints > 0 && beat % glints == 0;
            if (!mote && !glint) {
                continue;
            }
            Vec3 tip = WandTipTracker.resolve(player, 1.0f);
            if (mote) {
                mc.level.addParticle(new SpellTintParticleOptions(ModParticles.MOTE.get(), look.glint()),
                        tip.x + (random.nextDouble() - 0.5) * 0.06,
                        tip.y + (random.nextDouble() - 0.5) * 0.06,
                        tip.z + (random.nextDouble() - 0.5) * 0.06, 0.0, 0.0, 0.0);
            }
            if (glint) {
                // Somewhere down the shaft towards the hand, where the fittings are.
                Vec3 hand = player.getEyePosition().add(0.0, -0.5, 0.0);
                double along = 0.15 + random.nextDouble() * 0.5;
                Vec3 at = tip.lerp(hand, along);
                int colour = look.elder() ? 0xD8E4F2 : 0xFFE8A8;
                mc.level.addParticle(new SpellTintParticleOptions(ModParticles.GLINT.get(), colour),
                        at.x, at.y, at.z, 0.0, 0.0, 0.0);
            }
        }
    }

    /** The wand in this player's main hand, else their off hand, else nothing. */
    private static ItemStack wandIn(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WandItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
