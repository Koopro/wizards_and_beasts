package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The Control Center's header and dashboard: who the viewer is to this server, and the server's state at the
 * moment the snapshot was taken. Not live — the panel asks for a refresh.
 *
 * @param capabilities        what this viewer may change; the client greys out the rest
 * @param modVersion          this mod's version on the server
 * @param dedicated           dedicated server rather than an integrated one
 * @param onlinePlayers       players connected
 * @param maxPlayers          player limit
 * @param averageTickMillis   mean tick time
 * @param allowListRestricted whether the {@code adminUuids} allow-list is in force
 * @param modulesEnabled      modules currently ENABLED
 * @param modulesTotal        all modules this build knows
 * @param restartPending      an applied change is waiting for a restart
 * @param recentChanges       newest first, at most {@link #MAX_RECENT}
 * @param counts              what the server has registered, and how many debug tools are switched on
 */
@NullMarked
public record AdminSessionInfo(Set<AdminCapability> capabilities,
                               String modVersion,
                               boolean dedicated,
                               int onlinePlayers,
                               int maxPlayers,
                               float averageTickMillis,
                               boolean allowListRestricted,
                               int modulesEnabled,
                               int modulesTotal,
                               boolean restartPending,
                               List<RecentChange> recentChanges,
                               Counts counts) {

    public static final int MAX_RECENT = 8;
    private static final int MAX_CAPABILITIES = 32;

    /**
     * One line of the dashboard's recent-changes list.
     *
     * @param sequence   the history record's number, for undo
     * @param settingId  the full setting id
     * @param nameKey    the setting's name key; empty when the setting no longer exists
     * @param revertible applied and not undone yet, so the dashboard may offer Undo
     */
    public record RecentChange(long sequence, String settingId, String nameKey, String actorName, String oldValue,
                               String newValue, long timestampMillis, boolean applied, boolean revertible) {}

    /** The dashboard's tiles: registered content, and debug tools currently on (held by how many administrators). */
    public record Counts(int spells, int brews, int creatures, int heritages, int debugTools, int debugHolders) {
        public static final Counts NONE = new Counts(0, 0, 0, 0, 0, 0);
    }

    public static void write(ByteBuf buf, AdminSessionInfo info) {
        buf.writeInt(info.capabilities.size());
        for (AdminCapability capability : info.capabilities) {
            PacketCodecUtils.writeString(buf, capability.name());
        }
        PacketCodecUtils.writeString(buf, info.modVersion);
        buf.writeBoolean(info.dedicated);
        buf.writeInt(info.onlinePlayers);
        buf.writeInt(info.maxPlayers);
        buf.writeFloat(info.averageTickMillis);
        buf.writeBoolean(info.allowListRestricted);
        buf.writeInt(info.modulesEnabled);
        buf.writeInt(info.modulesTotal);
        buf.writeBoolean(info.restartPending);
        int recent = Math.min(info.recentChanges.size(), MAX_RECENT);
        buf.writeInt(recent);
        for (int i = 0; i < recent; i++) {
            RecentChange change = info.recentChanges.get(i);
            buf.writeLong(change.sequence());
            PacketCodecUtils.writeString(buf, change.settingId());
            PacketCodecUtils.writeString(buf, change.nameKey());
            PacketCodecUtils.writeString(buf, change.actorName());
            PacketCodecUtils.writeString(buf, change.oldValue());
            PacketCodecUtils.writeString(buf, change.newValue());
            buf.writeLong(change.timestampMillis());
            buf.writeBoolean(change.applied());
            buf.writeBoolean(change.revertible());
        }
        Counts counts = info.counts;
        buf.writeInt(counts.spells());
        buf.writeInt(counts.brews());
        buf.writeInt(counts.creatures());
        buf.writeInt(counts.heritages());
        buf.writeInt(counts.debugTools());
        buf.writeInt(counts.debugHolders());
    }

    public static AdminSessionInfo read(ByteBuf buf) {
        int capabilityCount = PacketCodecUtils.readBoundedCount(buf, MAX_CAPABILITIES, "admin-capabilities");
        Set<AdminCapability> capabilities = EnumSet.noneOf(AdminCapability.class);
        for (int i = 0; i < capabilityCount; i++) {
            AdminCapability capability = AdminCapability.byName(PacketCodecUtils.readString(buf));
            if (capability != null) {
                capabilities.add(capability);
            }
        }
        String version = PacketCodecUtils.readString(buf);
        boolean dedicated = buf.readBoolean();
        int online = buf.readInt();
        int max = buf.readInt();
        float tick = buf.readFloat();
        boolean restricted = buf.readBoolean();
        int enabled = buf.readInt();
        int total = buf.readInt();
        boolean restart = buf.readBoolean();
        int recentCount = PacketCodecUtils.readBoundedCount(buf, MAX_RECENT, "admin-recent-changes");
        List<RecentChange> recent = new ArrayList<>(recentCount);
        for (int i = 0; i < recentCount; i++) {
            recent.add(new RecentChange(buf.readLong(), PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                    PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf),
                    buf.readLong(), buf.readBoolean(), buf.readBoolean()));
        }
        Counts counts = new Counts(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        return new AdminSessionInfo(capabilities, version, dedicated, online, max, tick, restricted,
                enabled, total, restart, List.copyOf(recent), counts);
    }
}
