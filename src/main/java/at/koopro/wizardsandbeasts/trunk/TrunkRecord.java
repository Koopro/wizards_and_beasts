package at.koopro.wizardsandbeasts.trunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.HashSet;
import java.util.UUID;

public record TrunkRecord(
        UUID pocketId,
        UUID owner,
        String pocketName,
        TrunkAccessMode accessMode,
        TrunkArchetype archetype,
        String templateId,
        long createdAtEpochSeconds,
        long seed,
        BlockPos spawnPos,
        List<UUID> members,
        int pocketRadius,
        List<String> biomeZones,
        boolean muggleWorthy,
        boolean lockedExternally) {

    public static final Codec<TrunkRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("pocketId").forGetter(TrunkRecord::pocketId),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(TrunkRecord::owner),
            Codec.STRING.optionalFieldOf("pocketName", "Unnamed Pocket").forGetter(TrunkRecord::pocketName),
            TrunkAccessMode.CODEC.optionalFieldOf("accessMode", TrunkAccessMode.SEALED).forGetter(TrunkRecord::accessMode),
            TrunkArchetype.CODEC.optionalFieldOf("archetype", TrunkArchetype.FIELD_CAMP).forGetter(TrunkRecord::archetype),
            Codec.STRING.optionalFieldOf("templateId", "blank_shell").forGetter(TrunkRecord::templateId),
            Codec.LONG.optionalFieldOf("createdAtEpochSeconds", 0L).forGetter(TrunkRecord::createdAtEpochSeconds),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(TrunkRecord::seed),
            BlockPos.CODEC.optionalFieldOf("spawnPos", BlockPos.ZERO).forGetter(TrunkRecord::spawnPos),
            UUIDUtil.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(TrunkRecord::members),
            Codec.INT.optionalFieldOf("pocketRadius", 12).forGetter(TrunkRecord::pocketRadius),
            Codec.STRING.listOf().optionalFieldOf("biomeZones", List.of()).forGetter(TrunkRecord::biomeZones),
            Codec.BOOL.optionalFieldOf("muggleWorthy", false).forGetter(TrunkRecord::muggleWorthy),
            Codec.BOOL.optionalFieldOf("lockedExternally", false).forGetter(TrunkRecord::lockedExternally)
    ).apply(instance, TrunkRecord::new));

    public static TrunkRecord create(UUID owner, TrunkArchetype archetype, String templateId, BlockPos spawnPos, int maxRadius) {
        UUID pocketId = UUID.randomUUID();
        HashSet<UUID> members = new HashSet<>();
        members.add(owner);
        long nowSeconds = System.currentTimeMillis() / 1000L;
        return new TrunkRecord(
                pocketId,
                owner,
                "Pocket " + pocketId.toString().substring(0, 8),
                TrunkAccessMode.SEALED,
                archetype,
                templateId,
                nowSeconds,
                pocketId.getMostSignificantBits() ^ pocketId.getLeastSignificantBits(),
                spawnPos,
                List.copyOf(members),
                maxRadius,
                archetype.defaultBiomeZones(),
                false,
                false);
    }

    public boolean canAccess(UUID playerId) {
        if (owner.equals(playerId)) {
            return true;
        }
        return switch (accessMode) {
            case OPEN -> true;
            case KEYED -> members.contains(playerId);
            case SEALED -> false;
        };
    }

    public TrunkRecord withAccessMode(TrunkAccessMode mode) {
        return new TrunkRecord(pocketId, owner, pocketName, mode, archetype, templateId,
                createdAtEpochSeconds, seed, spawnPos, members, pocketRadius, biomeZones, muggleWorthy, lockedExternally);
    }

    public TrunkRecord withArchetype(TrunkArchetype nextArchetype) {
        return new TrunkRecord(pocketId, owner, pocketName, accessMode, nextArchetype, templateId,
                createdAtEpochSeconds, seed, spawnPos, members, pocketRadius, biomeZones, muggleWorthy, lockedExternally);
    }

    public TrunkRecord withTemplateId(String nextTemplateId) {
        return new TrunkRecord(pocketId, owner, pocketName, accessMode, archetype, nextTemplateId,
                createdAtEpochSeconds, seed, spawnPos, members, pocketRadius, biomeZones, muggleWorthy, lockedExternally);
    }

    public TrunkRecord withMember(UUID playerId) {
        HashSet<UUID> next = new HashSet<>(members);
        next.add(playerId);
        return new TrunkRecord(pocketId, owner, pocketName, accessMode, archetype, templateId,
                createdAtEpochSeconds, seed, spawnPos, List.copyOf(next), pocketRadius, biomeZones, muggleWorthy, lockedExternally);
    }

    public TrunkRecord withRadius(int newRadius) {
        return new TrunkRecord(pocketId, owner, pocketName, accessMode, archetype, templateId,
                createdAtEpochSeconds, seed, spawnPos, members, newRadius, biomeZones, muggleWorthy, lockedExternally);
    }

    public TrunkRecord withBiomeZones(List<String> newZones) {
        return new TrunkRecord(pocketId, owner, pocketName, accessMode, archetype, templateId,
                createdAtEpochSeconds, seed, spawnPos, members, pocketRadius, newZones, muggleWorthy, lockedExternally);
    }

    public TrunkRecord withMuggleWorthy(boolean value) {
        return new TrunkRecord(pocketId, owner, pocketName, accessMode, archetype, templateId,
                createdAtEpochSeconds, seed, spawnPos, members, pocketRadius, biomeZones, value, lockedExternally);
    }

    public TrunkRecord withLockedExternally(boolean value) {
        return new TrunkRecord(pocketId, owner, pocketName, accessMode, archetype, templateId,
                createdAtEpochSeconds, seed, spawnPos, members, pocketRadius, biomeZones, muggleWorthy, value);
    }
}
