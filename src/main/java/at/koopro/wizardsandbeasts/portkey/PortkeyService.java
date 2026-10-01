package at.koopro.wizardsandbeasts.portkey;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Portkey is for: one journey, to one place, for everyone holding on.
 *
 * <p>Canon (<i>Goblet of Fire</i> ch. 6): an ordinary object set to carry whoever is touching it to a fixed place — the
 * Weasleys, the Diggorys and Harry all at once on one old boot, "a jerk just behind the navel". So a Portkey's one
 * thing, which nothing else in the mod does, is taking a <em>group</em> somewhere set in advance. Apparition takes one
 * wizard (and a passenger) wherever they can picture; the Floo needs a grate at both ends.
 *
 * <p>It stores a destination and never travelled until 2026-09-29. Now the holder uses it, everyone within
 * {@link #TOUCH_RADIUS} blocks of them is carried along after the tug, and the Portkey is spent: a Portkey is made
 * for a journey, not kept as a way home. Only within the world it was set in.
 */
@NullMarked
public final class PortkeyService {

    /** How close another player must be to count as holding on. */
    public static final double TOUCH_RADIUS = 1.5;

    private PortkeyService() {}

    /** The outcome of one activation, for the item and for tests. */
    public enum Result { TRAVELLED, UNSET, OTHER_WORLD, OCCUPIED }

    /** Sets the Portkey to carry its travellers to the top of {@code pos} in {@code level}. */
    public static void set(ItemStack portkey, ServerLevel level, BlockPos pos) {
        portkey.set(ModDataComponents.PORTKEY_TARGET.get(), pos.immutable());
        portkey.set(ModDataComponents.PORTKEY_DIMENSION.get(), level.dimension().identifier());
    }

    /**
     * Carries {@code holder} and everyone touching them to the Portkey's destination, then spends it. Nothing
     * happens, and nothing is spent, when it is unset, set in another world, or the destination is blocked.
     */
    public static Result travel(ServerPlayer holder, ItemStack portkey) {
        BlockPos target = portkey.get(ModDataComponents.PORTKEY_TARGET.get());
        if (target == null) {
            tell(holder, "item.wizards_and_beasts.portkey.unset");
            return Result.UNSET;
        }
        ServerLevel level = holder.level();
        Identifier setIn = portkey.get(ModDataComponents.PORTKEY_DIMENSION.get());
        // A stack set before the dimension was recorded is taken to belong to the world it is used in.
        if (setIn != null && !setIn.equals(level.dimension().identifier())) {
            tell(holder, "item.wizards_and_beasts.portkey.other_world");
            return Result.OTHER_WORLD;
        }
        Vec3 arrival = Vec3.atBottomCenterOf(target.above());

        List<ServerPlayer> travellers = new ArrayList<>();
        travellers.add(holder);
        for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class,
                holder.getBoundingBox().inflate(TOUCH_RADIUS), p -> p != holder && p.isAlive() && !p.isSpectator())) {
            travellers.add(other);
        }
        for (ServerPlayer traveller : travellers) {
            if (!level.noCollision(traveller, traveller.getBoundingBox().move(arrival.subtract(traveller.position())))) {
                tell(holder, "item.wizards_and_beasts.portkey.occupied");
                return Result.OCCUPIED;
            }
        }

        for (ServerPlayer traveller : travellers) {
            Vec3 from = traveller.position();
            level.sendParticles(ParticleTypes.CLOUD, from.x, from.y + 0.5, from.z, 16, 0.3, 0.3, 0.3, 0.1);
            traveller.stopRiding();
            traveller.teleportTo(arrival.x, arrival.y, arrival.z);
            traveller.resetFallDistance();
            traveller.displayClientMessage(Component.translatable("item.wizards_and_beasts.portkey.jerk")
                    .withStyle(ChatFormatting.AQUA), true);
        }
        level.sendParticles(ParticleTypes.CLOUD, arrival.x, arrival.y + 0.5, arrival.z, 24, 0.5, 0.3, 0.5, 0.1);
        level.playSound(null, target.above(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.6f);

        // Spent: the journey it was made for is made.
        portkey.shrink(1);
        tell(holder, "item.wizards_and_beasts.portkey.spent");
        return Result.TRAVELLED;
    }

    private static void tell(ServerPlayer player, String key) {
        PlayerFeedback.actionBar(player, Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
