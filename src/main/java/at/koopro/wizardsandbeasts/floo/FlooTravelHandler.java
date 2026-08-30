package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.block.floo.FlooFlamesBlock;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.floo.call.FlooCallService;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.floo.FlooArrivalEffectsS2CPayload;
import at.koopro.wizardsandbeasts.network.floo.FlooBlockSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.floo.FlooTransitS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FlooTravelHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Game tick each player last arrived somewhere. Cleared with the player.
     *
     * <p>Arrival rather than departure, because the thing being paced is how often somebody can come
     * out of a grate. Timing from the departure would let a player with a long windup configured hop
     * more often than one with a short one, which is backwards.
     */
    private static final PlayerScopedState<Long> LAST_ARRIVAL =
            PlayerScopedState.create("floo_travel_cooldown");

    /** How long after arriving the Network refuses to take you again. */
    public static int cooldownTicks() {
        return Math.max(0, at.koopro.wizardsandbeasts.Config.flooTravelCooldownTicks);
    }

    /** Ticks left on {@code player}'s arrival cooldown, or 0 when they may travel. */
    public static long cooldownRemaining(@NonNull ServerPlayer player) {
        int cooldown = cooldownTicks();
        if (cooldown <= 0) {
            return 0L;
        }
        Long last = LAST_ARRIVAL.get(player.getUUID());
        if (last == null) {
            return 0L;
        }
        long elapsed = player.level().getGameTime() - last;
        // Guarded against a negative elapsed time. Game time can move backwards across a world
        // restore, and an unguarded subtraction would leave a player unable to travel for however
        // many ticks the world went back by.
        if (elapsed < 0) {
            LAST_ARRIVAL.remove(player.getUUID());
            return 0L;
        }
        return Math.max(0L, cooldown - elapsed);
    }

    private FlooTravelHandler() {
    }

    /**
     * Everything that must be true before a wizard may step into the fire, and nothing that happens
     * afterwards.
     *
     * <h2>Refusal order</h2>
     * Checks run cheapest-and-most-likely first, and <b>every one of them says why</b>. Two used to
     * return in silence — no fireplace in reach, and a destination whose dimension the server no
     * longer loads — which produced the worst possible failure: the player presses Travel, nothing
     * happens, and there is nothing on screen to distinguish "unlit grate" from "unregistered" from
     * "the mod that owned that dimension is gone".
     *
     * <h2>Nothing is spent here</h2>
     * <p>Powder is <b>checked</b> last and <b>consumed</b> nowhere in this method — it is paid in
     * {@link #commitTravel}, at the far end of the departure window. The ordering still means a
     * player told they are out of powder can trust that powder is the only thing missing, and it now
     * also means a departure that is cancelled halfway costs nothing at all.
     *
     * <h2>Where a misfire is decided</h2>
     * <p>Here, before the window opens, not at the far end of it — so the fire that builds under a
     * traveller is already the fire taking them to the wrong grate, and nothing has to be re-rolled
     * at a point where the player has stopped being able to affect it.
     *
     * <p>How it is decided depends on how the address was given. A destination <em>picked from the
     * list</em> misfires on {@link at.koopro.wizardsandbeasts.Config#flooMisfireChancePercent},
     * because pointing at a name is not something a wizard can do badly and the risk has to come
     * from somewhere. A destination <em>spoken</em> misfires when it was mumbled — when what was
     * typed is close enough to be understood but is not actually an address. That is the whole of the
     * difference between the two modes, and it is why speaking is worth doing: say it properly and
     * nothing can go wrong.
     *
     * <h2>Edge cases, and what this does about them</h2>
     * <ul>
     *   <li><b>Renamed fireplace</b> — a re-registration retires the old address; see
     *       {@link FlooNetworkManager#register}. One block holds one address, so a renamed hearth
     *       stops answering to what it used to be called.</li>
     *   <li><b>Broken fireplace</b> — {@code FlooFireplaceBlock.affectNeighborsAfterRemoval} purges
     *       the entry by position, so the network never lists a hearth that is not there. A player
     *       who breaks their own destination loses it, which is the honest outcome.</li>
     *   <li><b>Unloaded destination chunk</b> — nothing is pre-loaded and no chunk ticket is held.
     *       The teleport itself loads the destination chunk on arrival, the way vanilla does, and it
     *       is released again by the ordinary chunk lifecycle. A Floo address is deliberately
     *       <em>not</em> a chunk loader: brooms and Floo are travel, not infrastructure, and a
     *       network of a hundred registered hearths must not become a hundred forced chunks.</li>
     *   <li><b>Missing dimension</b> — refused with a reason rather than silently, because the
     *       address survives in saved data after the dimension that held it stops existing.</li>
     *   <li><b>Blocked far end</b> — refused before the window opens. {@link #findSafeArrival} needs
     *       two clear blocks within three above the grate, so arriving inside a wall is impossible
     *       rather than merely unlikely.</li>
     * </ul>
     *
     * @param spoken true when the player typed the address rather than picking it from the list
     */
    public static void beginTravel(@NonNull ServerPlayer player, @NonNull String requestedAddress,
                                   boolean spoken) {
        // Reachable without the module: the travel packet is sent by an already-open screen, and a
        // module switched off between opening the GUI and pressing Travel would otherwise swallow the
        // click in silence. Every other refusal below says why; this one has to as well.
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.module_off"));
            return;
        }

        if (FlooCallService.isInCall(player.getUUID())) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.in_call"));
            return;
        }

        // A second Travel press while the fire is already building. Refused rather than restarted, so
        // a player cannot hold the window open indefinitely by re-confirming, and cannot quietly
        // change destination after the grate has begun to roar.
        if (FlooDeparture.isDeparting(player.getUUID())) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("floo.wizards_and_beasts.fail.already_departing"));
            return;
        }

        long cooling = cooldownRemaining(player);
        if (cooling > 0) {
            PlayerFeedback.actionBar(player, Component.translatable(
                    "floo.wizards_and_beasts.fail.too_soon", Math.max(1L, cooling / 20L)));
            return;
        }

        // You cannot take a broom through the Floo.
        //
        // Refused rather than silently dismounting, which was the tempting alternative. A wizard who
        // has just been tipped off their broom by a menu has been surprised by their own travel; one
        // who is told to get off first has been informed. It also avoids the genuinely bad outcome
        // of the vehicle being left in a fireplace nobody can now reach past.
        if (player.isPassenger()) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("floo.wizards_and_beasts.fail.riding"));
            return;
        }

        // Was a bare `return`. A player standing at an unlit, unregistered or out-of-reach grate
        // pressed Travel and nothing happened at all — no message, no sound, nothing to tell them
        // which of the three it was. This is the commonest way to fail to travel, so it is the one
        // that most needed a reason.
        FlooFireplaceBlockEntity origin = findNearbyLitFireplace(player);
        if (origin == null) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.no_fireplace"));
            return;
        }

        FlooNetworkManager manager = FlooNetworkManager.get((ServerLevel) player.level());

        FlooRegistryEntry heard = spoken
                ? FlooAccess.resolveSpoken(player, manager, requestedAddress)
                : pickedFromList(player, manager, requestedAddress);
        if (heard == null) {
            PlayerFeedback.actionBar(player, Component.translatable(
                    "floo.wizards_and_beasts.fail.unknown_address", FlooAddress.display(requestedAddress)));
            return;
        }

        String intended = FlooAddress.display(requestedAddress);
        // A spoken address the network had to guess at is a mumble; an exact one is not. Compared
        // here, where both halves are in hand, rather than inside the roll.
        boolean mumbled = spoken && !FlooAddress.sameAddress(heard.networkAddress(), requestedAddress);
        String actualAddress = rollMisfire(player, manager, origin,
                heard.networkAddress(), spoken, mumbled);

        FlooRegistryEntry dest = manager.getEntry(actualAddress);
        if (dest == null) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("floo.wizards_and_beasts.fail.unknown_address", actualAddress));
            return;
        }
        if (!dest.isEnabled()) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.sealed"));
            FlooCues.sealed(player);
            return;
        }

        // Was a bare `return` too. A destination in a dimension the server no longer loads — a
        // datapack dimension removed, a mod uninstalled — left the grate roaring and the player
        // standing in it. The warning went to the server log, where no player can read it.
        ServerLevel destLevel = resolveLevel(player, dest.dimension());
        if (destLevel == null) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.no_dimension"));
            return;
        }

        if (findSafeArrival(destLevel, dest.blockPos()) == null) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.blocked"));
            return;
        }

        // Looked for, not taken. See the note above on why the fare is paid at the other end.
        if (!hasPowder(player)) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.no_powder"));
            return;
        }

        FlooDeparture.begin(player, origin.getBlockPos(), actualAddress, intended);
    }

    /**
     * Resolve an address that was clicked rather than typed.
     *
     * <p>Still permission-checked, and deliberately: the list a client holds was built when the
     * screen opened, and between then and the click a hearth can have been sealed, unregistered or
     * made private. Trusting the address in the packet because "it came from our own list" is how a
     * client that keeps an old screen open travels somewhere it is no longer allowed to see.
     */
    @Nullable
    private static FlooRegistryEntry pickedFromList(@NonNull ServerPlayer player,
                                                     @NonNull FlooNetworkManager manager,
                                                     @NonNull String address) {
        FlooRegistryEntry entry = manager.getEntry(address);
        return entry != null && FlooAccess.mayReach(player, entry) ? entry : null;
    }

    /**
     * The far end of the ritual: the fare, the fire, the hop, and the ash.
     *
     * <p>Everything here is irreversible, which is exactly why none of it lives in
     * {@link #beginTravel}. By the time this runs the traveller has stood in a roaring grate for the
     * whole departure window without stepping out and without the fire dying, and that is what a
     * commitment is.
     *
     * <p>Re-checked rather than trusted: the destination is looked up again, the arrival spot found
     * again, and the powder counted again. The window is seconds long and a lot can happen in it —
     * the far end can be built over, the line can be sealed, the powder can be dropped — and a commit
     * that assumed the checks from before the window would teleport a player into a wall for the sake
     * of not repeating three cheap lookups.
     */
    public static void commitTravel(@NonNull ServerPlayer player, @NonNull BlockPos originPos,
                                    @NonNull String actualAddress, @NonNull String intended) {
        ServerLevel originLevel = (ServerLevel) player.level();
        FlooNetworkManager manager = FlooNetworkManager.get(originLevel);

        // Two different things can have happened during the departure window, and they are told
        // apart because they mean different things to the player. A sealed line is somebody's
        // decision and may be reversed; a destination that has stopped existing is somebody having
        // broken the fireplace, and no amount of waiting will bring it back.
        FlooRegistryEntry dest = manager.getEntry(actualAddress);
        if (dest == null) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("floo.wizards_and_beasts.fail.destination_gone", actualAddress));
            FlooCues.sputter(originLevel, originPos);
            return;
        }
        if (!dest.isEnabled()) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.sealed"));
            FlooCues.sealed(player);
            FlooCues.sputter(originLevel, originPos);
            return;
        }
        ServerLevel destLevel = resolveLevel(player, dest.dimension());
        if (destLevel == null) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.no_dimension"));
            return;
        }
        BlockPos destPos = findSafeArrival(destLevel, dest.blockPos());
        if (destPos == null) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.blocked"));
            return;
        }
        if (!consumeOnePowder(player)) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.no_powder"));
            FlooCues.sputter(originLevel, originPos);
            return;
        }

        String originAddress = originLevel.getBlockEntity(originPos)
                instanceof FlooFireplaceBlockEntity originBe ? originBe.getNetworkAddress() : "(unknown)";

        // The exit whoosh, heard by the room being left rather than by the traveller. They are about
        // to be somewhere else; the people watching them go are who this is for.
        originLevel.playSound(null, originPos, ModSounds.FLOO_WHOOSH.get(), SoundSource.BLOCKS, 1.0f, 0.85f);
        originLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                originPos.getX() + 0.5, originPos.getY() + 0.6, originPos.getZ() + 0.5,
                20, 0.35, 0.5, 0.35, 0.06);

        resetOriginFireplace(originLevel, originPos);

        // Anything riding the traveller is put down before they go. A passenger is not carried
        // through the Network - the grate takes one person - and leaving them attached across a
        // dimension change is how an entity ends up riding somebody who is no longer in its world.
        // The traveller's own mount was refused back in beginTravel, so this is only the other
        // direction: a pet, a passenger, anything sitting on them.
        if (!player.getPassengers().isEmpty()) {
            player.ejectPassengers();
        }

        TeleportTransition transition = new TeleportTransition(
                destLevel,
                Vec3.atCenterOf(destPos),
                Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                Set.of(),
                entity -> {});
        player.teleport(transition);

        player.addEffect(new MobEffectInstance(ModEffects.DISORIENTED, 100, 0, false, true, true));

        boolean wasMisfire = !FlooAddress.sameAddress(actualAddress, intended);
        applyTravelSickness(player, destLevel, destPos, wasMisfire);

        lightArrivalHearth(player, destLevel, dest.blockPos());

        // A toast, not chat. Arriving somewhere is a discrete event worth a few seconds and worth
        // nothing afterwards, and a chat line for every hop buries whatever the player was reading
        // — which is the "chat spam confusion" this was asked to fix. A misfire is a FAIL toast
        // even though the travel succeeded: the player did not get what they asked for, and that
        // is the thing they need to notice.
        if (wasMisfire) {
            PlayerFeedback.toast(player, NoticeKind.FAIL,
                    Component.translatable("floo.wizards_and_beasts.arrive.misfire.title", actualAddress),
                    Component.translatable("floo.wizards_and_beasts.arrive.misfire.body", intended));
        } else {
            PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                    Component.translatable("floo.wizards_and_beasts.arrive.title", actualAddress),
                    Component.translatable("floo.wizards_and_beasts.arrive.body"));
        }

        // The grate flares straight up; the traveller thuds onto the floor. Two events, two
        // sounds - FLOO_ARRIVAL belongs to the fireplace and FLOO_LAND to the person.
        FlooCues.arrivalBurst(destLevel,
                destPos.getX() + 0.5, destPos.getY() + 0.5, destPos.getZ() + 0.5);
        destLevel.playSound(null, destPos, ModSounds.FLOO_ARRIVAL.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        destLevel.playSound(null, destPos, ModSounds.FLOO_LAND.get(), SoundSource.BLOCKS, 0.7f, 1.0f);

        manager.logTravel(player.getName().getString(), originAddress, actualAddress);
        FlooArrivalEffectsS2CPayload.sendToNear(destLevel, dest.blockPos());

        // Stamped on arrival, and only on a hop that actually happened. A refused commit leaves the
        // player free to try again immediately, which is right: they have not been anywhere.
        LAST_ARRIVAL.put(player.getUUID(), destLevel.getGameTime());

        Set<String> visited = new HashSet<>(player.getData(ModAttachments.FLOO_VISITED_DESTINATIONS.get()));
        // FlooAddress.key, not a hand-rolled strip-and-lowercase. FlooAccess.hasVisited reads this
        // set back through that method, so a second spelling of "the same address" here is a hearth
        // the player has visited and can never see again.
        visited.add(FlooAddress.key(actualAddress));
        player.setData(ModAttachments.FLOO_VISITED_DESTINATIONS.get(), visited);
    }

    public static void forceTeleport(@NonNull ServerPlayer player, @NonNull String address) {
        FlooNetworkManager manager = FlooNetworkManager.get((ServerLevel) player.level());
        FlooRegistryEntry dest = manager.getEntry(address);
        if (dest == null) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("floo.wizards_and_beasts.fail.unknown_address", address));
            return;
        }
        ServerLevel destLevel = resolveLevel(player, dest.dimension());
        if (destLevel == null) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.no_dimension"));
            return;
        }

        // findSafeArrival, not blockPos().above(). The raw offset put the target inside whatever had
        // been built over the grate — an admin teleport that suffocates the player it was meant to
        // move — and this is the same two-clear-blocks search every ordinary hop already uses.
        BlockPos destPos = findSafeArrival(destLevel, dest.blockPos());
        if (destPos == null) {
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.fail.blocked"));
            return;
        }
        TeleportTransition transition = new TeleportTransition(
                destLevel,
                Vec3.atCenterOf(destPos),
                Vec3.ZERO,
                player.getYRot(),
                player.getXRot(),
                Set.of(),
                entity -> {});
        player.teleport(transition);
        player.addEffect(new MobEffectInstance(ModEffects.DISORIENTED, 100, 0, false, true, true));
        destLevel.playSound(null, destPos, ModSounds.FLOO_LAND.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        manager.logTravel(player, "(forced)", address);
    }

    /**
     * Floo travel is famously unpleasant: spinning, dizzy, and out covered in soot.
     * Sends the spin overlay, dusts the arrival in ash, and rolls a stumble.
     */
    private static void applyTravelSickness(@NonNull ServerPlayer player, @NonNull ServerLevel destLevel,
                                            @NonNull BlockPos destPos, boolean wasMisfire) {
        // Spin for the journey, then soot on the lens for a couple of seconds after it. The soot is
        // longer on a misfire for the same reason the ash is thicker.
        FlooTransitS2CPayload.send(player, wasMisfire ? 26 : 18, wasMisfire ? 60 : 40);

        int slowTicks = wasMisfire ? 80 : 50;
        player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS,
                slowTicks, 0, false, false, true));

        // Soot/ash cloud around the arriving traveller. A misfire brings more of it: you have been
        // through more of the Network than you meant to.
        FlooCues.sootFall(destLevel, player.getX(), player.getY(), player.getZ(), wasMisfire);
        if (wasMisfire) {
            FlooCues.sputter(destLevel, destPos);
        }

        // Stumble: clumsier travellers (and misfires) lurch out of the grate.
        float stumbleChance = wasMisfire ? 0.6f : 0.2f;
        if (player.getRandom().nextFloat() < stumbleChance) {
            double yaw = Math.toRadians(player.getYRot());
            player.push(-Math.sin(yaw) * 0.35, 0.05, Math.cos(yaw) * 0.35);
            player.hurtMarked = true;
            // Action bar: a stumble is worth saying now and worthless a second later, and it lands
            // in the same breath as the arrival toast rather than as a second chat line under it.
            PlayerFeedback.actionBar(player, Component.translatable("floo.wizards_and_beasts.arrive.stumble"));
        }
    }

    /**
     * Finds the lowest unobstructed 2-block-tall standing spot above the destination grate.
     *
     * <h2>The chunk is loaded on purpose</h2>
     * <p>{@code getChunk(x, z, true)} before the scan, rather than relying on {@code getBlockState}
     * to pull the chunk in as a side effect of being asked. It did work by accident — every
     * {@code getBlockState} on an unloaded chunk loads it — but "the destination loads because we
     * happen to read four blocks there" is a guarantee that disappears the moment somebody
     * reorders the checks. Doing it once, first, also means one chunk load instead of up to six.
     *
     * <p>No ticket is held. The chunk is loaded for the read and released by the ordinary lifecycle,
     * because a Floo address must not be a chunk loader — a network of a hundred hearths would
     * otherwise be a hundred forced chunks.
     *
     * <h2>The search is clamped to the world</h2>
     * <p>A hearth built near the build limit — the Nether roof is the obvious one — used to be able
     * to land a traveller <em>above</em> it. Out-of-bounds reads answer air, air is passable, and the
     * scan happily returned a position outside the world for the player to fall out of. Both the feet
     * and the block above them have to be inside build height, which is why the loop stops at
     * {@code getMaxY() - 1} rather than at {@code getMaxY()}.
     */
    @Nullable
    private static BlockPos findSafeArrival(@NonNull ServerLevel level, @NonNull BlockPos firePos) {
        level.getChunk(firePos);
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos feet = firePos.above(dy);
            // The head slot has to be inside the world too, so the ceiling is one below the maximum.
            if (feet.getY() < level.getMinY() || feet.getY() >= level.getMaxY()) {
                continue;
            }
            if (isPassable(level, feet) && isPassable(level, feet.above())) {
                return feet;
            }
        }
        return null;
    }

    private static boolean isPassable(@NonNull ServerLevel level, @NonNull BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Whether the player could pay the fare, without taking it.
     *
     * <p>Split from {@link #consumeOnePowder} because the two questions are now asked at opposite
     * ends of the departure window: the refusal that says "you have no Floo Powder" has to happen
     * before the fire builds, and the deduction has to happen after it, or a cancelled hop would
     * silently eat a pinch.
     */
    private static boolean hasPowder(@NonNull ServerPlayer player) {
        if (player.getAbilities().instabuild) return true;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(MiscItemRegistry.FLOO_POWDER.get())) {
                return true;
            }
        }
        return false;
    }

    /** Consumes one Floo Powder from the player's inventory; true if paid (or creative). */
    private static boolean consumeOnePowder(@NonNull ServerPlayer player) {
        if (player.getAbilities().instabuild) return true;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.is(MiscItemRegistry.FLOO_POWDER.get())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static FlooFireplaceBlockEntity findNearbyLitFireplace(@NonNull ServerPlayer player) {
        BlockPos center = player.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos check = center.offset(dx, dy, dz);
                    BlockState state = player.level().getBlockState(check);
                    if (state.is(ModBlocks.FLOO_FIREPLACE.get()) && state.getValue(FlooFireplaceBlock.LIT)) {
                        if (player.level().getBlockEntity(check) instanceof FlooFireplaceBlockEntity be
                                && be.isRegistered() && be.isEnabled()) {
                            return be;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Where the traveller actually ends up.
     *
     * <h2>Two ways to get it wrong, one table for the consequence</h2>
     * <p>A <b>spoken</b> address misfires when the network had to guess — when
     * {@link FlooAccess#resolveSpoken} matched something within the typo tolerance that was not the
     * address itself. That is a mumble, and it is the player's own doing: say it correctly and this
     * branch cannot fire at all, however unlucky they are.
     *
     * <p>A <b>picked</b> address misfires on
     * {@link at.koopro.wizardsandbeasts.Config#flooMisfireChancePercent}. There is nothing to mumble
     * when you click a row, so the risk has to be a die — and it is the one knob a server owner can
     * turn to zero if they want list travel to be dependable.
     *
     * <p>Either way the wrong destination comes from the same weighted table, because the interesting
     * part of a misfire is not why it happened but where it puts you.
     *
     * @param mumbled whether a spoken address had to be guessed at; ignored when {@code spoken} is
     *                false, since a click cannot be mispronounced
     */
    @NonNull
    private static String rollMisfire(@NonNull ServerPlayer player,
                                       @NonNull FlooNetworkManager manager,
                                       @NonNull FlooFireplaceBlockEntity origin,
                                       @NonNull String heardAddress,
                                       boolean spoken, boolean mumbled) {
        boolean misfires;
        if (spoken) {
            misfires = mumbled;
        } else {
            int chance = Math.max(0, Math.min(100,
                    at.koopro.wizardsandbeasts.Config.flooMisfireChancePercent));
            misfires = chance > 0 && player.getRandom().nextInt(100) < chance;
        }
        if (!misfires) {
            return heardAddress;
        }

        List<FlooRegistryEntry> alternatives = FlooAccess.visibleTo(player, manager).stream()
                .filter(FlooRegistryEntry::isEnabled)
                .filter(e -> !FlooAddress.sameAddress(e.networkAddress(), heardAddress))
                .filter(e -> !FlooAddress.sameAddress(e.networkAddress(), origin.getNetworkAddress()))
                .toList();

        if (alternatives.isEmpty()) return heardAddress;

        // LORE: a mumbled destination tends to spit you out somewhere unsavoury
        // (Harry's "Diagonally" → Knockturn Alley). Weight dark addresses heavier.
        FlooRegistryEntry misfireTarget = pickWeightedMisfire(player, alternatives);
        LOGGER.info("[FlooNetwork] {} misfired — heard \"{}\" landed at \"{}\"",
                player.getName().getString(), heardAddress, misfireTarget.networkAddress());
        return misfireTarget.networkAddress();
    }

    private static final List<String> DARK_ADDRESS_KEYWORDS =
            List.of("knockturn", "borgin", "burkes", "azkaban", "riddle", "malfoy", "spinner");

    private static boolean isDarkAddress(@NonNull FlooRegistryEntry entry) {
        String addr = entry.networkAddress().toLowerCase(Locale.ROOT);
        return DARK_ADDRESS_KEYWORDS.stream().anyMatch(addr::contains);
    }

    @NonNull
    private static FlooRegistryEntry pickWeightedMisfire(@NonNull ServerPlayer player,
                                                         @NonNull List<FlooRegistryEntry> alternatives) {
        int totalWeight = 0;
        for (FlooRegistryEntry e : alternatives) {
            totalWeight += isDarkAddress(e) ? 4 : 1;
        }
        int roll = player.getRandom().nextInt(totalWeight);
        for (FlooRegistryEntry e : alternatives) {
            roll -= isDarkAddress(e) ? 4 : 1;
            if (roll < 0) return e;
        }
        return alternatives.getLast();
    }

    /**
     * What departing costs the hearth you left from.
     *
     * <p>A pinch of Floo Powder buys {@link FlooFlamesBlock#MAX_CHARGES} hops, so a departure spends
     * one charge rather than putting the whole fire out — three people can leave one hearth on one
     * pinch, which is what makes a lit hearth worth lighting in a shared base. The fire darkens the
     * hearth itself when the last charge goes ({@link FlooFlamesBlock#extinguish}), so nothing here
     * has to decide when {@code LIT} ends.
     *
     * <p>The unconditional darken survives as the fallback for a hearth that is lit with no flames in
     * front of it — a world saved before flames existed, or a {@code /setblock}. Without it such a
     * hearth would stay lit through every hop, since there is no fire to spend.
     */
    private static void resetOriginFireplace(@NonNull ServerLevel level, @NonNull BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(FlooFireplaceBlock.FACING)) {
            Direction facing = state.getValue(FlooFireplaceBlock.FACING);
            if (FlooFlamesBlock.isLitAt(level, pos, facing)) {
                FlooFlamesBlock.consumeChargeAt(level, pos, facing);
                return;
            }
        }
        if (state.hasProperty(FlooFireplaceBlock.LIT)) {
            level.setBlock(pos, state.setValue(FlooFireplaceBlock.LIT, false), 3);
        }
        // Looked up rather than passed in: the commit runs at the far end of a departure window, and
        // a block entity captured before it could belong to a hearth that has since been mined.
        if (level.getBlockEntity(pos) instanceof FlooFireplaceBlockEntity be) {
            be.setLitTicksRemaining(0);
        }
        FlooBlockSyncS2CPayload.sendToNear(level, pos, false, 0);
    }

    /**
     * The far end of the hop, seen from the far end.
     *
     * <p>A traveller comes <em>out</em> of green fire, so arriving lights the destination hearth and
     * stands flames in its opening — the same block the departure was made from, so the arrival can be
     * hopped onward from immediately without hunting for powder to light the hearth again. Powder is
     * still spent per hop in {@link #consumeOnePowder}, so this gives away the fire, never the fare.
     *
     * <p>{@link FlooFlamesBlock#suppressOfferFor} is the reason it is safe: without it a traveller who
     * took one step forward out of the grate would land in the flames they just arrived through and be
     * handed the destination screen again before they had looked at the room.
     *
     * <p>If the opening is blocked the flames cannot stand, and the hearth falls back to the brief
     * hundred-tick flare it always had — arrival still looks like arrival even where fire will not fit.
     */
    private static void lightArrivalHearth(@NonNull ServerPlayer player, @NonNull ServerLevel destLevel,
                                           @NonNull BlockPos hearthPos) {
        BlockState hearth = destLevel.getBlockState(hearthPos);
        if (!hearth.is(ModBlocks.FLOO_FIREPLACE.get())
                || !(destLevel.getBlockEntity(hearthPos) instanceof FlooFireplaceBlockEntity destBe)) {
            return;
        }
        boolean flamesUp = FlooFlamesBlock.light(destLevel, hearthPos, hearth.getValue(FlooFireplaceBlock.FACING));
        int litTicks = flamesUp ? FlooFireplaceBlockEntity.litTimeoutTicks() : 100;
        if (!hearth.getValue(FlooFireplaceBlock.LIT)) {
            destLevel.setBlock(hearthPos, hearth.setValue(FlooFireplaceBlock.LIT, true), 3);
        }
        destBe.setLitTicksRemaining(litTicks);
        FlooBlockSyncS2CPayload.sendToNear(destLevel, hearthPos, true, litTicks);
        if (flamesUp) {
            FlooFlamesBlock.suppressOfferFor(player.getUUID(), destLevel.getGameTime());
        }
    }

    @Nullable
    private static ServerLevel resolveLevel(@NonNull ServerPlayer player, @NonNull Identifier dimensionId) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel result = ((ServerLevel) player.level()).getServer().getLevel(key);
        if (result == null) {
            LOGGER.warn("[FlooNetwork] Destination dimension not found: {}", dimensionId);
        }
        return result;
    }
}
