package at.koopro.wizardsandbeasts.item.darkartefact;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

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
        return destroy(level, new Vec3(by.getX(), by.getEyeY() - 0.4, by.getZ()), horcrux);
    }

    /**
     * Releases the fragment in {@code horcrux} where it lies: Fiendfyre takes a vessel nobody is holding (the diadem
     * on its shelf in the Room of Requirement, DH ch.31). Server-side.
     *
     * @return whether a fragment was bound and is now gone
     */
    public static boolean destroy(ServerLevel level, Vec3 at, ItemStack horcrux) {
        if (!isIntact(horcrux)) {
            return false;
        }
        horcrux.set(ModDataComponents.SOUL_FRAGMENT_INTACT.get(), false);
        level.playSound(null, at.x, at.y, at.z, ModSounds.HORCRUX_SHATTER.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 24, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.02);
        return true;
    }
}
