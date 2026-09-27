package at.koopro.wizardsandbeasts.item.darkartefact;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Ending a Horcrux: the one place its soul fragment is released.
 *
 * <p>Canon: only a handful of things are destructive enough — basilisk venom (Harry and the diary, Hermione and the
 * cup, both with a fang), the venom-impregnated Sword of Gryffindor, Fiendfyre. Before this nothing in the mod ever
 * set {@code SOUL_FRAGMENT_INTACT} to false; every Horcrux's "Destroyed" tooltip was unreachable. Whatever destroys
 * one calls here, so there is one release and one spectacle.
 */
public final class HorcruxDestruction {

    private HorcruxDestruction() {}

    /** Whether {@code stack} is a Horcrux with its fragment still bound. */
    public static boolean isIntact(ItemStack stack) {
        return stack.getItem() instanceof IHorcruxVessel vessel && vessel.isSoulIntact(stack);
    }

    /**
     * Releases the fragment in {@code horcrux}. Server-side.
     *
     * @return whether a fragment was bound and is now gone
     */
    public static boolean destroy(ServerLevel level, Player by, ItemStack horcrux) {
        if (!isIntact(horcrux)) {
            return false;
        }
        horcrux.set(ModDataComponents.SOUL_FRAGMENT_INTACT.get(), false);
        level.playSound(null, by.blockPosition(), SoundEvents.GHAST_SCREAM, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.sendParticles(ParticleTypes.SQUID_INK, by.getX(), by.getEyeY() - 0.4, by.getZ(), 24, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, by.getX(), by.getEyeY() - 0.4, by.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        return true;
    }
}
