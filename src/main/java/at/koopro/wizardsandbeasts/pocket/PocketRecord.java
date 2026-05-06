package at.koopro.wizardsandbeasts.pocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.HashSet;
import java.util.UUID;

public record PocketRecord(
        UUID pocketId,
        UUID owner,
        String pocketName,
        PocketAccessMode accessMode,
        PocketArchetype archetype,
        String templateId,
        long createdAtEpochSeconds,
        long seed,
        BlockPos spawnPos,
        List<UUID> members) {

    public static final Codec<PocketRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("pocketId").forGetter(PocketRecord::pocketId),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(PocketRecord::owner),
            Codec.STRING.optionalFieldOf("pocketName", "Unnamed Pocket").forGetter(PocketRecord::pocketName),
            PocketAccessMode.CODEC.optionalFieldOf("accessMode", PocketAccessMode.PRIVATE).forGetter(PocketRecord::accessMode),
            PocketArchetype.CODEC.optionalFieldOf("archetype", PocketArchetype.CUSTOM_PLAYER_TEMPLATE).forGetter(PocketRecord::archetype),
            Codec.STRING.optionalFieldOf("templateId", "blank_shell").forGetter(PocketRecord::templateId),
            Codec.LONG.optionalFieldOf("createdAtEpochSeconds", 0L).forGetter(PocketRecord::createdAtEpochSeconds),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(PocketRecord::seed),
            BlockPos.CODEC.optionalFieldOf("spawnPos", BlockPos.ZERO).forGetter(PocketRecord::spawnPos),
            UUIDUtil.CODEC.listOf().optionalFieldOf("members", java.util.List.of()).forGetter(PocketRecord::members)
    ).apply(instance, PocketRecord::new));

    public static PocketRecord create(UUID owner, PocketArchetype archetype, String templateId, BlockPos spawnPos) {
        UUID pocketId = UUID.randomUUID();
        HashSet<UUID> members = new HashSet<>();
        members.add(owner);
        long nowSeconds = System.currentTimeMillis() / 1000L;
        return new PocketRecord(
                pocketId,
                owner,
                "Pocket " + pocketId.toString().substring(0, 8),
                PocketAccessMode.PRIVATE,
                archetype,
                templateId,
                nowSeconds,
                pocketId.getMostSignificantBits() ^ pocketId.getLeastSignificantBits(),
                spawnPos,
                List.copyOf(members));
    }

    public boolean canAccess(UUID playerId) {
        if (owner.equals(playerId)) {
            return true;
        }
        return switch (accessMode) {
            case PUBLIC_ACCESS -> true;
            case SHARED_WHITELIST -> members.contains(playerId);
            case PRIVATE -> false;
        };
    }

    public PocketRecord withAccessMode(PocketAccessMode mode) {
        return new PocketRecord(pocketId, owner, pocketName, mode, archetype, templateId, createdAtEpochSeconds, seed, spawnPos, members);
    }

    public PocketRecord withArchetype(PocketArchetype nextArchetype) {
        return new PocketRecord(pocketId, owner, pocketName, accessMode, nextArchetype, templateId, createdAtEpochSeconds, seed, spawnPos, members);
    }

    public PocketRecord withTemplateId(String nextTemplateId) {
        return new PocketRecord(pocketId, owner, pocketName, accessMode, archetype, nextTemplateId, createdAtEpochSeconds, seed, spawnPos, members);
    }

    public PocketRecord withMember(UUID playerId) {
        HashSet<UUID> next = new HashSet<>(members);
        next.add(playerId);
        return new PocketRecord(pocketId, owner, pocketName, accessMode, archetype, templateId, createdAtEpochSeconds, seed, spawnPos, List.copyOf(next));
    }
}
