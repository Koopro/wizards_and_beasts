package at.koopro.wizardsandbeasts.portkey;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
            // A Portkey left for travellers (Stoatshead Hill's boot) is set for a place, not a block: the first use
            // finds where that place stands in this world and sets it there.
            java.util.Optional<Identifier> landmark = landmark(portkey);
            BlockPos found = landmark.isPresent() ? resolveLandmark(holder.level(), landmark.get()) : null;
            if (found != null) {
                set(portkey, holder.level(), found);
                target = found;
            }
        }
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
            swirl(level, from);
            traveller.stopRiding();
            traveller.teleportTo(arrival.x, arrival.y, arrival.z);
            traveller.resetFallDistance();
            traveller.displayClientMessage(Component.translatable("item.wizards_and_beasts.portkey.jerk")
                    .withStyle(ChatFormatting.AQUA), true);
        }
        // Set down: a puff of the mod's own smoke where the travellers land, and the swirl dying out round them.
        at.koopro.wizardsandbeasts.particle.MagicSmoke.burst(level, arrival.x, arrival.y + 0.4, arrival.z,
                SMOKE, 10, 0.5, 0.2);
        swirl(level, arrival);
        level.playSound(null, target.above(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.6f);

        // Spent: the journey it was made for is made.
        portkey.shrink(1);
        tell(holder, "item.wizards_and_beasts.portkey.spent");
        return Result.TRAVELLED;
    }

    /** The pale grey of dust kicked up by a landing, not vanilla's cloud. */
    private static final int SMOKE = 0xFFB8B4AC;

    /**
     * "Spinning in a howl of wind and swirling colour" (GoF ch. 6): three rising rings of motes in the charm colour
     * round where a traveller stands. Was vanilla's cloud puff, which is not ours to borrow and read as a mob
     * despawning.
     */
    private static void swirl(ServerLevel level, Vec3 where) {
        var motes = at.koopro.wizardsandbeasts.registry.ModParticles.tinted(
                at.koopro.wizardsandbeasts.spell.core.SpellFamily.ARCANE,
                at.koopro.wizardsandbeasts.spell.core.MagicColours.MAGIC);
        for (int ring = 0; ring < 3; ring++) {
            at.koopro.wizardsandbeasts.network.spell.SpellVfxShapeS2CPayload.ring(level, motes,
                    new Vec3(where.x, where.y + 0.3 + ring * 0.6, where.z), new Vec3(0, 1, 0),
                    0.9 - ring * 0.2, 10, (float) (ring * 0.15 * Math.PI * 2));
        }
    }

    /** The custom-data key a placed Portkey carries instead of a target: the id of the structure it is set for. */
    public static final String LANDMARK_KEY = "portkey_landmark";

    /** The structure this Portkey is set for, when it was left set for a place rather than a block. */
    public static java.util.Optional<Identifier> landmark(ItemStack portkey) {
        net.minecraft.world.item.component.CustomData data =
                portkey.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) {
            return java.util.Optional.empty();
        }
        return data.copyTag().getString(LANDMARK_KEY).map(Identifier::tryParse);
    }

    /**
     * The ground to set down on beside {@code structureId} in this world: a one-per-world place's own chunk, any other
     * structure's nearest start. Lands a few blocks off the template's south side, on the surface, so travellers
     * arrive outside the building rather than on its roof. {@code null} when the world has none.
     */
    public static @org.jspecify.annotations.Nullable BlockPos resolveLandmark(ServerLevel level, Identifier structureId) {
        var structures = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        var holder = structures.get(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.STRUCTURE, structureId)).orElse(null);
        if (holder == null) {
            return null;
        }
        var state = level.getChunkSource().getGeneratorState();
        net.minecraft.world.level.ChunkPos chunk = null;
        for (var placement : state.getPlacementsForStructure(holder)) {
            if (placement instanceof at.koopro.wizardsandbeasts.azkaban.structure.OnePerWorldPlacement one) {
                chunk = one.target(state);
            }
        }
        if (chunk == null) {
            var found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
                    net.minecraft.core.HolderSet.direct(holder), level.getRespawnData().pos(), 100, false);
            if (found == null) {
                return null;
            }
            chunk = new net.minecraft.world.level.ChunkPos(found.getFirst());
        }
        var start = level.getChunk(chunk.x, chunk.z).getStartForStructure(holder.value());
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        if (start != null && start.isValid() && !start.getPieces().isEmpty()) {
            var box = start.getPieces().get(0).getBoundingBox();
            x = box.getCenter().getX();
            z = box.maxZ() + 3;
        }
        // The landing column can lie in the next chunk over; an unloaded chunk reads its height as the world's floor.
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y - 1, z);
    }

    private static void tell(ServerPlayer player, String key) {
        PlayerFeedback.actionBar(player, Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
