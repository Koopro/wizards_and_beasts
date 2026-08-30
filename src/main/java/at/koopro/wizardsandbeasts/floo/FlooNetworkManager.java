package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.*;

public final class FlooNetworkManager extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_LOG_ENTRIES = 100;

    public static final SavedDataType<FlooNetworkManager> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_floo_network",
            FlooNetworkManager::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.unboundedMap(Codec.STRING, FlooRegistryEntry.CODEC)
                            .optionalFieldOf("entries", Map.of())
                            .forGetter(d -> Map.copyOf(d.entries)),
                    Codec.STRING.listOf()
                            .optionalFieldOf("travelLog", List.of())
                            .forGetter(d -> List.copyOf(d.travelLog))
            ).apply(instance, FlooNetworkManager::ofCodec)));

    private final LinkedHashMap<String, FlooRegistryEntry> entries;
    private final LinkedList<String> travelLog;

    public FlooNetworkManager() {
        this(new LinkedHashMap<>(), new LinkedList<>());
    }

    private FlooNetworkManager(@NonNull LinkedHashMap<String, FlooRegistryEntry> entries,
                                @NonNull LinkedList<String> travelLog) {
        this.entries = entries;
        this.travelLog = travelLog;
    }

    private static FlooNetworkManager ofCodec(@NonNull Map<String, FlooRegistryEntry> entries,
                                               @NonNull List<String> log) {
        return new FlooNetworkManager(new LinkedHashMap<>(entries), new LinkedList<>(log));
    }

    public static FlooNetworkManager get(@NonNull ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Registers {@code pos} under {@code address}, replacing whatever that fireplace was called before.
     *
     * <p><b>One block, one address.</b> Re-registering a fireplace under a new name used to leave the
     * old entry in place, still pointing at the same block — so a renamed fireplace kept answering to
     * its old name, showed up twice in every destination list, and the only way to remove the ghost
     * was to know a name the player had already replaced. The block entity's own
     * {@code networkAddress} was updated, which is what made it invisible: the fireplace agreed it had
     * been renamed and the network did not.
     *
     * <p>Purging by position first is what makes a re-register a rename. It is deliberately keyed on
     * position rather than on the old name, because the caller renaming a fireplace is looking at the
     * block and does not necessarily know what it used to be called.
     *
     * @return false when {@code address} is already taken by a <em>different</em> fireplace, in which
     *         case nothing is changed
     */
    /** Why a registration was refused, or that it took. */
    public enum RegisterResult {
        REGISTERED,
        ADDRESS_TAKEN,
        INVALID_ADDRESS;

        public boolean ok() {
            return this == REGISTERED;
        }
    }

    public RegisterResult register(@NonNull String address, @NonNull Identifier dimension,
                                   @NonNull BlockPos pos, boolean isPublic) {
        return register(address, dimension, pos, isPublic, Optional.empty());
    }

    /**
     * Register {@code pos} under {@code address}, recording who it belongs to.
     *
     * <p>The owner is carried through a re-registration of the same block rather than re-read from
     * the caller, so renaming your own hearth does not silently hand it to whoever happened to hold
     * the name tag. Only a registration at a position that was previously unregistered takes the
     * caller's owner.
     */
    public RegisterResult register(@NonNull String address, @NonNull Identifier dimension,
                                   @NonNull BlockPos pos, boolean isPublic,
                                   @NonNull Optional<UUID> owner) {
        // Validated here rather than only at the command, because this is the one door into the
        // network and a caller that skips the check would put an unremovable blank row in the list.
        if (!FlooAddress.isValid(address)) {
            LOGGER.warn("[FlooNetwork] Refused invalid address: \"{}\"", address);
            return RegisterResult.INVALID_ADDRESS;
        }
        String key = FlooAddress.key(address);
        FlooRegistryEntry existing = entries.get(key);
        if (existing != null && !(existing.dimension().equals(dimension) && existing.blockPos().equals(pos))) {
            LOGGER.warn("[FlooNetwork] Address already taken: {}", key);
            return RegisterResult.ADDRESS_TAKEN;
        }
        // Re-registering this exact block under the same name is a no-op refresh, not a clash, so the
        // check above lets it through and this drops the old row before the new one goes in.
        FlooRegistryEntry previous = findByPos(dimension, pos);
        unregisterByPos(dimension, pos);
        Optional<UUID> effectiveOwner = previous != null && previous.owner().isPresent()
                ? previous.owner()
                : owner;
        entries.put(key, new FlooRegistryEntry(FlooAddress.display(address), dimension, pos,
                true, isPublic, effectiveOwner));
        setDirty();
        return RegisterResult.REGISTERED;
    }

    public void unregister(@NonNull String address) {
        String key = FlooAddress.key(address);
        if (entries.remove(key) != null) {
            setDirty();
        }
    }

    /**
     * Removes any entry registered at the given block position, returning its address.
     * Used to purge the network when a registered fireplace is destroyed.
     */
    @Nullable
    public String unregisterByPos(@NonNull Identifier dimension, @NonNull BlockPos pos) {
        var it = entries.entrySet().iterator();
        while (it.hasNext()) {
            FlooRegistryEntry entry = it.next().getValue();
            if (entry.dimension().equals(dimension) && entry.blockPos().equals(pos)) {
                String address = entry.networkAddress();
                it.remove();
                setDirty();
                return address;
            }
        }
        return null;
    }

    /** The entry registered at a block position, or null. */
    @Nullable
    public FlooRegistryEntry findByPos(@NonNull Identifier dimension, @NonNull BlockPos pos) {
        for (FlooRegistryEntry entry : entries.values()) {
            if (entry.dimension().equals(dimension) && entry.blockPos().equals(pos)) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    public FlooRegistryEntry getEntry(@NonNull String address) {
        return entries.get(FlooAddress.key(address));
    }

    // getDestinations(Player) lived here and has been deleted rather than left unused.
    //
    // It was a second copy of the visibility rule — public-or-visited — and that rule has since grown
    // two more clauses: a hearth you own, and a hearth you administer. The copy could not grow with
    // them, because it takes a Player rather than a ServerPlayer and has no way to ask about
    // ownership. Left in place it would have been a working method that quietly answered an outdated
    // question, and the failure it produced would have been a player unable to see their own private
    // hearth in their own destination list.
    //
    // FlooAccess.visibleTo is the one answer now, built on FlooAccess.mayReach.

    @NonNull
    public List<FlooRegistryEntry> getAllEntries() {
        return new ArrayList<>(entries.values());
    }

    /** Move a hearth on or off the public directory. */
    public void setPublic(@NonNull String address, boolean isPublic) {
        String key = FlooAddress.key(address);
        FlooRegistryEntry entry = entries.get(key);
        if (entry != null) {
            entries.put(key, entry.withPublic(isPublic));
            setDirty();
        }
    }

    public void setEnabled(@NonNull String address, boolean enabled) {
        String key = FlooAddress.key(address);
        FlooRegistryEntry entry = entries.get(key);
        if (entry != null) {
            entries.put(key, entry.withEnabled(enabled));
            setDirty();
        }
    }

    public void logTravel(@NonNull Player player, @NonNull String fromAddress, @NonNull String toAddress) {
        logTravel(player.getName().getString(), fromAddress, toAddress);
    }

    /**
     * Record one hop.
     *
     * <p>Takes a name rather than a {@link Player} so the ring buffer can be tested. The cap is the
     * only interesting behaviour here — an uncapped log is a save file that grows without bound for
     * the life of a world — and a {@code Player} cannot be constructed without a level, which would
     * have left the one thing worth checking unreachable.
     */
    public void logTravel(@NonNull String playerName, @NonNull String fromAddress, @NonNull String toAddress) {
        String message = String.format("[FlooNetwork] %s travelled from \"%s\" to \"%s\" at %s",
                playerName, fromAddress, toAddress, Instant.now());
        LOGGER.info(message);
        travelLog.addLast(message);
        while (travelLog.size() > MAX_LOG_ENTRIES) {
            travelLog.removeFirst();
        }
        setDirty();
    }

    @NonNull
    public List<String> getRecentLog(int count) {
        List<String> all = new ArrayList<>(travelLog);
        int start = Math.max(0, all.size() - count);
        return all.subList(start, all.size());
    }
}
