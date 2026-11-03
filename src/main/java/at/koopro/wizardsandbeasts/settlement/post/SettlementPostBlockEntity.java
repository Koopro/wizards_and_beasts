package at.koopro.wizardsandbeasts.settlement.post;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlockEntities;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.settlement.PostRef;
import at.koopro.wizardsandbeasts.settlement.SettlementData;
import at.koopro.wizardsandbeasts.settlement.SettlementDiscovery;
import at.koopro.wizardsandbeasts.settlement.npc.NpcRole;
import at.koopro.wizardsandbeasts.settlement.npc.NpcRoles;
import at.koopro.wizardsandbeasts.settlement.npc.SettlementNpcEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * A post's one job: see that its person is here. It looks once a second while its chunk ticks, and only acts on what
 * {@link PostRules} decides. The truth lives in {@link SettlementData}; this holds the role from the template and how
 * long it has gone without finding anyone.
 *
 * <p>People are spawned here and never stored in a template, so a generated hamlet cannot hold a stale copy, and a site
 * nobody has visited holds no entities at all.
 */
@NullMarked
public class SettlementPostBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TagKey<Structure> SETTLEMENTS = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, "settlement"));
    private static final int LOOK_TICKS = 20;
    /** How close a player must be to see someone jump home, or to have reached the place. */
    private static final double WATCH_RANGE = 32.0;
    private static final double ARRIVAL_RANGE = 16.0;

    private String role = "";
    private @Nullable String site;
    private int looks;

    public SettlementPostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SETTLEMENT_POST.get(), pos, state);
    }

    public String role() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("role", role);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        role = input.getStringOr("role", "");
    }

    void serverTick(Level level, BlockPos pos) {
        if (level.getGameTime() % LOOK_TICKS == Math.floorMod(pos.hashCode(), LOOK_TICKS)
                && level instanceof ServerLevel server) {
            look(server, pos);
        }
    }

    /** One look. Public for game tests, which cannot wait out a day. */
    public void look(ServerLevel level, BlockPos pos) {
        if (!ModuleManager.isEnabled(Module.SETTLEMENTS)) {
            return;
        }
        if (!entitiesLoaded(level, pos)) {
            // What is not loaded cannot be judged. Starts the count again: lost means lost in plain view.
            looks = 0;
            return;
        }
        lookLoaded(level, pos);
    }

    /** A look with everything around the post known to be loaded. A game test's plot forces only one chunk. */
    public void lookLoaded(ServerLevel level, BlockPos pos) {
        NpcRole definition = NpcRoles.get(role);
        if (definition == null) {
            return;
        }
        PostRef ref = reference(level, pos);
        SettlementData data = SettlementData.get(level);
        SettlementData.Post post = data.post(ref);
        if (post == null) {
            long seed = data.site(ref.site()) == null ? 0 : data.site(ref.site()).seed();
            post = SettlementData.Post.vacant(role, Math.floorMod(java.util.Objects.hash(seed, role), 4096));
            data.setPost(ref, post);
        }
        Entity person = post.npc().map(level::getEntity).orElse(null);
        boolean watched = level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, WATCH_RANGE, false) != null;
        looks = person != null ? 0 : looks + 1;
        double distance = person == null ? 0.0 : person.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        switch (PostRules.decide(post.status(), person != null, distance, watched, looks,
                post.due(level.getGameTime(), level.getDayTime()))) {
            case SPAWN -> spawn(level, pos, ref, post, definition);
            case RETURN -> person.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            case NOTHING -> { }
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= ARRIVAL_RANGE * ARRIVAL_RANGE) {
                SettlementDiscovery.arrive(player, ref.site());
            }
        }
    }

    /** Whether every chunk their person could be standing in has its entities in memory. */
    private static boolean entitiesLoaded(ServerLevel level, BlockPos pos) {
        ChunkPos centre = new ChunkPos(pos);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(centre.x + x, centre.z + z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The post's place in its settlement, found once. A post outside any settlement structure (an operator's own, or a
     * test's) is a site of its own chunk.
     */
    private PostRef reference(ServerLevel level, BlockPos pos) {
        if (site == null) {
            StructureStart start = level.structureManager().getStructureWithPieceAt(pos, SETTLEMENTS);
            ChunkPos chunk = start.isValid() ? start.getChunkPos() : new ChunkPos(pos);
            site = SettlementData.siteKey(level, chunk);
            SettlementData.get(level).site(site, level.getSeed() ^ chunk.toLong() * 0x9E3779B97F4A7C15L);
        }
        return new PostRef(site, pos.asLong());
    }

    /** The record is written before the entity joins, so the join check sees the real one and nothing else. */
    private void spawn(ServerLevel level, BlockPos pos, PostRef ref, SettlementData.Post post, NpcRole definition) {
        EntityType<?> type = definition.entity().isPresent()
                ? BuiltInRegistries.ENTITY_TYPE.getValue(definition.entity().get())
                : ModEntities.SETTLEMENT_NPC.get();
        Entity person = type.create(level, EntitySpawnReason.STRUCTURE);
        if (!(person instanceof Mob mob)) {
            LOGGER.error("[Settlement] Role {} names {}, which is not a mob", role, type);
            return;
        }
        mob.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0f, 0.0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        mob.setPersistenceRequired();
        mob.setHomeTo(pos, SettlementNpcEntity.HOME_RADIUS);
        if (mob instanceof SettlementNpcEntity npc) {
            npc.setRoleId(role);
        }
        if (!definition.names().isEmpty()) {
            mob.setCustomName(Component.translatable(definition.names().get(post.name() % definition.names().size())));
        }
        mob.setData(ModAttachments.SETTLEMENT_POST.get(), ref);
        UUID id = mob.getUUID();
        SettlementData data = SettlementData.get(level);
        data.setPost(ref, post.present(id));
        if (!level.addFreshEntity(mob)) {
            data.setPost(ref, post);
            return;
        }
        looks = 0;
    }
}
