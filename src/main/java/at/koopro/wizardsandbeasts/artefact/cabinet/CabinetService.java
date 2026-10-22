package at.koopro.wizardsandbeasts.artefact.cabinet;

import at.koopro.wizardsandbeasts.apparition.ApparitionWard;
import at.koopro.wizardsandbeasts.apparition.ApparitionWardRegistry;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionLanding;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Everything a Vanishing Cabinet does, server side: linking two with a wand, unlinking, sharing a pair, stepping
 * through, and what happens when one is broken. The block and its event handler only route clicks here; every
 * decision -- who may, whether the far end still stands, where you come out -- is made in this class against
 * {@link CabinetPairs}, the single record of which cabinets are joined.
 *
 * <p>Rules (user decisions, 2026-10-22): link by wand gesture (sneak + wand on one cabinet, then on another within
 * a minute); a linked cabinet asks before it unlinks; any dimension except the pocket realm; Apparition wards stop
 * an arrival; a pair is private to the player who linked it until they share it.
 */
@NullMarked
public final class CabinetService {

    /** How long the first cabinet hums, waiting for its partner. */
    static final long LINK_WINDOW_TICKS = 20L * 60L;
    /** How long a "really unlink?" stands before a second gesture no longer counts as yes. */
    static final long UNLINK_CONFIRM_TICKS = 20L * 5L;
    /** Between two trips, so a held click cannot bounce a player back and forth. */
    static final long TRAVEL_COOLDOWN_TICKS = 40L;

    private static final String KEY = "artefact.wizards_and_beasts.vanishing_cabinet.";

    /** A gesture waiting for its second half: the first cabinet of a link, or a cabinet asked to unlink. */
    record Pending(GlobalPos pos, long expiresAt, boolean unlink) {}

    private static final PlayerScopedState<Pending> PENDING = PlayerScopedState.create("vanishing_cabinet_pending");
    private static final PlayerScopedState<Long> LAST_TRIP = PlayerScopedState.create("vanishing_cabinet_trip");

    /** What a gesture or a step came to. Each carries the line the player is shown. */
    public enum Result {
        ATTUNED("attuned"),
        LINKED("linked"),
        CONFIRM_UNLINK("confirm_unlink"),
        UNLINKED("unlinked"),
        SHARED("shared"),
        PRIVATE("private"),
        TRAVELLED(null),
        NOT_OWNER("not_owner"),
        NO_PERMISSION("no_permission"),
        POCKET("pocket"),
        NOT_LINKED("not_linked"),
        PARTNER_GONE("partner_gone"),
        PARTNER_TAKEN("partner_taken"),
        PRIVATE_PAIR("private_pair"),
        OBSTRUCTED("obstructed"),
        WARDED(null),
        COOLDOWN(null),
        DISABLED("disabled");

        private final @Nullable String key;

        Result(@Nullable String key) {
            this.key = key;
        }

        public @Nullable Component message() {
            return key == null ? null : Component.translatable(KEY + key);
        }
    }

    private CabinetService() {}

    // ── linking ───────────────────────────────────────────────────────────

    /**
     * The wand gesture on a cabinet. Unlinked: the first cabinet hums for a minute, the second joins it. Linked:
     * the first gesture asks, a second within five seconds unlinks.
     */
    public static Result gesture(ServerPlayer player, ServerLevel level, BlockPos lower) {
        if (!ModuleManager.isEnabled(Module.ARTEFACTS)) {
            return Result.DISABLED;
        }
        if (inPocket(level)) {
            return Result.POCKET;
        }
        VanishingCabinetBlockEntity cabinet = cabinetAt(level, lower);
        if (cabinet == null) {
            return Result.NOT_LINKED;
        }
        CabinetPairs pairs = CabinetPairs.get(level);
        GlobalPos here = GlobalPos.of(level.dimension(), lower);
        long now = now(level);
        Pending pending = PENDING.get(player);
        if (pending != null && pending.expiresAt() < now) {
            PENDING.remove(player);
            pending = null;
        }

        CabinetPairs.Pair pair = pairs.pairAt(here);
        if (pair != null) {
            if (!isAdmin(player) && !pair.owner().equals(player.getUUID())) {
                return Result.NOT_OWNER;
            }
            if (pending != null && pending.unlink() && pending.pos().equals(here)) {
                PENDING.remove(player);
                pairs.sever(here);
                level.playSound(null, lower, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8f, 0.8f);
                return Result.UNLINKED;
            }
            PENDING.put(player, new Pending(here, now + UNLINK_CONFIRM_TICKS, true));
            return Result.CONFIRM_UNLINK;
        }

        if (!mayAlter(player, level, lower, cabinet)) {
            return cabinet.owner() != null && !cabinet.owner().equals(player.getUUID()) && !isAdmin(player)
                    ? Result.NOT_OWNER : Result.NO_PERMISSION;
        }
        if (pending == null || pending.unlink() || pending.pos().equals(here)) {
            PENDING.put(player, new Pending(here, now + LINK_WINDOW_TICKS, false));
            level.playSound(null, lower, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8f, 0.7f);
            return Result.ATTUNED;
        }

        // The second cabinet: the first must still stand, still be free, and still be this player's to link.
        PENDING.remove(player);
        ServerLevel firstLevel = level.getServer().getLevel(pending.pos().dimension());
        VanishingCabinetBlockEntity first = firstLevel == null ? null : cabinetAt(firstLevel, pending.pos().pos());
        if (firstLevel == null || first == null) {
            // The first cabinet was broken meanwhile: this one starts a fresh link instead.
            PENDING.put(player, new Pending(here, now + LINK_WINDOW_TICKS, false));
            return Result.ATTUNED;
        }
        if (pairs.pairAt(pending.pos()) != null) {
            PENDING.put(player, new Pending(here, now + LINK_WINDOW_TICKS, false));
            return Result.PARTNER_TAKEN;
        }
        if (!mayAlter(player, firstLevel, pending.pos().pos(), first)) {
            return Result.NOT_OWNER;
        }
        if (pairs.link(pending.pos(), here, player.getUUID()) == null) {
            return Result.PARTNER_TAKEN;
        }
        level.playSound(null, lower, SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 0.8f, 1.2f);
        firstLevel.playSound(null, pending.pos().pos(), SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 0.8f, 1.2f);
        return Result.LINKED;
    }

    /** Sneak with an empty hand: the pair's owner switches it between private and shared. */
    public static Result toggleShared(ServerPlayer player, ServerLevel level, BlockPos lower) {
        if (!ModuleManager.isEnabled(Module.ARTEFACTS)) {
            return Result.DISABLED;
        }
        CabinetPairs pairs = CabinetPairs.get(level);
        GlobalPos here = GlobalPos.of(level.dimension(), lower);
        CabinetPairs.Pair pair = pairs.pairAt(here);
        if (pair == null) {
            return Result.NOT_LINKED;
        }
        if (!isAdmin(player) && !pair.owner().equals(player.getUUID())) {
            return Result.NOT_OWNER;
        }
        CabinetPairs.Pair updated = pairs.setShared(here, !pair.shared());
        return updated != null && updated.shared() ? Result.SHARED : Result.PRIVATE;
    }

    // ── travelling ────────────────────────────────────────────────────────

    /**
     * Steps through the cabinet at {@code lower} and out of its partner. Refused before anything moves when the
     * player may not use the pair, the partner is gone (the pair is then dissolved), the way out is blocked, a ward
     * stands over the far side, or the player stepped through a moment ago.
     */
    public static Result travel(ServerPlayer player, ServerLevel level, BlockPos lower) {
        if (!ModuleManager.isEnabled(Module.ARTEFACTS)) {
            return Result.DISABLED;
        }
        if (inPocket(level)) {
            return Result.POCKET;
        }
        CabinetPairs pairs = CabinetPairs.get(level);
        GlobalPos here = GlobalPos.of(level.dimension(), lower);
        CabinetPairs.Pair pair = pairs.pairAt(here);
        if (pair == null) {
            return Result.NOT_LINKED;
        }
        if (!mayUse(pair.owner().equals(player.getUUID()), pair.shared(), isAdmin(player))) {
            return Result.PRIVATE_PAIR;
        }
        long now = now(level);
        Long last = LAST_TRIP.get(player);
        if (last != null && now - last < TRAVEL_COOLDOWN_TICKS) {
            return Result.COOLDOWN;
        }

        GlobalPos far = pair.partnerOf(here);
        ServerLevel farLevel = far == null ? null : level.getServer().getLevel(far.dimension());
        if (far == null || farLevel == null) {
            pairs.sever(here);
            return Result.PARTNER_GONE;
        }
        // Loads the partner's chunk if nobody is near it: the far side has to be looked at to be stepped into.
        BlockState farState = farLevel.getChunk(far.pos()).getBlockState(far.pos());
        if (!(farState.getBlock() instanceof VanishingCabinetBlock)
                || farState.getValue(VanishingCabinetBlock.HALF) != DoubleBlockHalf.LOWER) {
            pairs.sever(here);
            return Result.PARTNER_GONE;
        }

        Direction facing = farState.getValue(VanishingCabinetBlock.FACING);
        BlockPos door = far.pos().relative(facing);
        farLevel.getChunk(door);
        Vec3 out = ApparitionLanding.resolve(farLevel, player, Vec3.atBottomCenterOf(door));
        // Apparition looks a few blocks down for footing; a cabinet's door is where it is, so a landing found below
        // it -- under the floor of a walled-up doorway -- is no way out.
        if (out == null || BlockPos.containing(out).getY() != door.getY()) {
            return Result.OBSTRUCTED;
        }
        AABB arrivalBox = player.getBoundingBox().move(out.subtract(player.position()));
        ApparitionWard ward = ApparitionWardRegistry.findBlockingWard(farLevel, player.createCommandSourceStack(), arrivalBox);
        if (ward != null) {
            player.displayClientMessage(ward.blockMessage(), true);
            return Result.WARDED;
        }

        LAST_TRIP.put(player, now);
        PENDING.remove(player);
        level.playSound(null, lower, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.9f, 0.8f);
        level.sendParticles(ParticleTypes.SMOKE, lower.getX() + 0.5, lower.getY() + 1.0, lower.getZ() + 0.5,
                12, 0.3, 0.6, 0.3, 0.01);
        if (!player.getPassengers().isEmpty()) {
            player.ejectPassengers();
        }
        player.stopRiding();
        player.teleport(new TeleportTransition(farLevel, out, Vec3.ZERO, facing.toYRot(), player.getXRot(),
                Set.<Relative>of(), TeleportTransition.DO_NOTHING));
        farLevel.playSound(null, far.pos(), SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.9f, 0.8f);
        farLevel.sendParticles(ParticleTypes.SMOKE, far.pos().getX() + 0.5, far.pos().getY() + 1.0,
                far.pos().getZ() + 0.5, 12, 0.3, 0.6, 0.3, 0.01);
        return Result.TRAVELLED;
    }

    // ── breaking ──────────────────────────────────────────────────────────

    /**
     * A cabinet left the world (mined, blown up, replaced). Its pair is dissolved at once, so the partner is a
     * plain cabinet again and can be linked anew; nothing else about either block changes.
     */
    public static void onRemoved(ServerLevel level, BlockPos lower) {
        CabinetPairs pairs = CabinetPairs.get(level);
        CabinetPairs.Pair pair = pairs.sever(GlobalPos.of(level.dimension(), lower));
        if (pair == null) {
            return;
        }
        GlobalPos far = pair.partnerOf(GlobalPos.of(level.dimension(), lower));
        ServerLevel farLevel = far == null ? null : level.getServer().getLevel(far.dimension());
        if (far != null && farLevel != null && farLevel.isLoaded(far.pos())) {
            farLevel.playSound(null, far.pos(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8f, 0.6f);
        }
    }

    // ── rules ─────────────────────────────────────────────────────────────

    /** Who may step through a pair: whoever linked it, anyone once it is shared, and operators. */
    public static boolean mayUse(boolean isOwner, boolean shared, boolean isAdmin) {
        return isOwner || shared || isAdmin;
    }

    /**
     * Whether this player may link or unlink this cabinet: they placed it (or nobody did, as for one set by a
     * command) and they could build where it stands -- so nobody can tie a cabinet into ground they could not
     * build in.
     */
    private static boolean mayAlter(ServerPlayer player, ServerLevel level, BlockPos pos, VanishingCabinetBlockEntity cabinet) {
        if (isAdmin(player)) {
            return true;
        }
        UUID owner = cabinet.owner();
        if (owner != null && !owner.equals(player.getUUID())) {
            return false;
        }
        return player.mayBuild() && level.mayInteract(player, pos);
    }

    private static boolean isAdmin(ServerPlayer player) {
        return player.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static boolean inPocket(ServerLevel level) {
        return refusesDimension(level.dimension());
    }

    /** The one dimension a cabinet will not work in: the pocket realm, whose spaces belong to trunks and tents. */
    public static boolean refusesDimension(ResourceKey<Level> dimension) {
        return dimension.equals(ModDimensions.EXTENSION_REALM);
    }

    private static long now(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    private static @Nullable VanishingCabinetBlockEntity cabinetAt(ServerLevel level, BlockPos lower) {
        return level.getChunk(lower).getBlockEntity(lower) instanceof VanishingCabinetBlockEntity be ? be : null;
    }

    /** Shows the player what their click came to, on the action bar. */
    public static void tell(ServerPlayer player, Result result) {
        Component message = result.message();
        if (message != null) {
            player.displayClientMessage(message, true);
        }
    }
}
