package at.koopro.wizardsandbeasts.admin.creature;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariant;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariants;
import at.koopro.wizardsandbeasts.creature.variant.VariantHolder;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test creatures an administrator spawns from the Creature Lab.
 *
 * <p><b>The client names a creature, never a position.</b> The server picks the spot: a few blocks in front of the
 * administrator, in their own level, on solid ground, in a loaded chunk, with room for the creature's hitbox. The
 * creature id must be on the roster and a requested variant must be one of that creature's variants; anything
 * else is refused. Spawning needs the {@link AdminCapability#WORLD} capability.
 *
 * <p><b>Cleanup.</b> Every test creature carries {@link #TAG}, its owner's tag and this server session's tag. They
 * are removed on request, when their owner logs out, and — because a crash or a stop skips both — any test creature
 * that loads into a later session is refused entry to the level. Test creatures never outlive the session that made
 * them, and are capped per administrator.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class CreatureTestSpawns {

    public static final String TAG = "wb_admin_test";
    private static final String OWNER_PREFIX = "wb_admin_test_owner:";
    private static final String SESSION_PREFIX = "wb_admin_test_session:";
    public static final int MAX_PER_ADMIN = 8;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "admin.wizards_and_beasts.creature_action.";

    /** New per JVM run of a server; a test creature from any other value is stale. */
    private static String session = UUID.randomUUID().toString();

    private CreatureTestSpawns() {}

    public record Outcome(boolean success, String messageKey, String detail) {
        static Outcome refused(String reason, String detail) {
            return new Outcome(false, KEY + reason, detail);
        }
    }

    public static Outcome spawn(ServerPlayer admin, String creatureId, @Nullable String variantId, boolean noAi) {
        AdminContext actor = AdminContext.of(admin);
        if (!actor.canRead() || !actor.canModify(AdminCapability.WORLD)) {
            LOGGER.warn("[Admin] Refused test spawn of {} from unauthorised {} ({})", creatureId,
                    admin.getName().getString(), admin.getUUID());
            return Outcome.refused("unauthorized", creatureId);
        }
        if (!ModCreatures.ROSTER.contains(creatureId)) {
            return Outcome.refused("unknown_creature", creatureId);
        }
        EntityType<?> type = CreatureAdminService.typeOf(creatureId);
        if (type == null) {
            return Outcome.refused("unknown_creature", creatureId);
        }
        CreatureVariant variant = null;
        if (variantId != null && !variantId.isEmpty()) {
            variant = CreatureVariants.byId(creatureId, variantId);
            if (variant == null) {
                return Outcome.refused("unknown_variant", creatureId + "/" + variantId);
            }
        }
        ServerLevel level = admin.level();
        if (owned(level.getServer(), admin.getUUID()).size() >= MAX_PER_ADMIN) {
            return Outcome.refused("too_many", Integer.toString(MAX_PER_ADMIN));
        }
        Vec3 spot = safeSpot(admin, level, type);
        if (spot == null) {
            return Outcome.refused("no_space", creatureId);
        }
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            return Outcome.refused("failed", creatureId);
        }
        entity.snapTo(spot.x, spot.y, spot.z, admin.getYRot() + 180.0f, 0.0f);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.COMMAND, null);
            mob.setPersistenceRequired();
            mob.setNoAi(noAi);
        }
        if (variant != null && entity instanceof VariantHolder holder) {
            holder.applyVariant(variant.variantId());
        }
        entity.addTag(TAG);
        entity.addTag(OWNER_PREFIX + admin.getUUID());
        entity.addTag(SESSION_PREFIX + session);
        entity.setCustomName(Component.translatable(KEY + "name", type.getDescription()));
        if (!level.addFreshEntity(entity)) {
            return Outcome.refused("failed", creatureId);
        }
        LOGGER.info("[Admin] {} spawned test {} at {}", admin.getName().getString(), creatureId, entity.blockPosition());
        return new Outcome(true, KEY + "spawned", creatureId);
    }

    /** Removes this administrator's test creatures in every level. */
    public static Outcome cleanup(ServerPlayer admin) {
        AdminContext actor = AdminContext.of(admin);
        if (!actor.canRead() || !actor.canModify(AdminCapability.WORLD)) {
            return Outcome.refused("unauthorized", "");
        }
        int removed = removeOwned(admin.level().getServer(), admin.getUUID());
        return new Outcome(true, KEY + "cleaned", Integer.toString(removed));
    }

    public static int removeOwned(MinecraftServer server, UUID owner) {
        List<Entity> mine = owned(server, owner);
        mine.forEach(Entity::discard);
        return mine.size();
    }

    public static List<Entity> owned(MinecraftServer server, UUID owner) {
        String ownerTag = OWNER_PREFIX + owner;
        List<Entity> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.isAlive() && entity.getTags().contains(ownerTag)) {
                    out.add(entity);
                }
            }
        }
        return out;
    }

    /**
     * A standing spot 2–6 blocks ahead of the administrator, in plain sight: solid top face below, the creature's box
     * free of blocks and liquids (water allowed for a creature that lives in it), chunk loaded, and nothing solid
     * between the administrator's eyes and the spot — the search stops at the first wall rather than reaching past
     * it. Null when there is none.
     */
    static @Nullable Vec3 safeSpot(ServerPlayer admin, ServerLevel level, EntityType<?> type) {
        Vec3 look = admin.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0e-4) {
            look = Vec3.directionFromRotation(0, admin.getYRot());
        }
        look = look.normalize();
        at.koopro.wizardsandbeasts.creature.CreatureDefinition def = CreatureAdminService.definitionOf(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
        boolean aquatic = def != null && def.locomotion() == at.koopro.wizardsandbeasts.creature.Locomotion.AQUATIC;
        Vec3 eye = admin.getEyePosition();
        for (int distance = 2; distance <= 6; distance++) {
            Vec3 ahead = admin.position().add(look.scale(distance));
            Vec3 sight = new Vec3(ahead.x, admin.getY() + 0.5, ahead.z);
            if (level.clip(new net.minecraft.world.level.ClipContext(eye, sight,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                    admin)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                // A wall: everything further is behind it.
                return null;
            }
            BlockPos column = BlockPos.containing(ahead);
            for (int dy = 2; dy >= -4; dy--) {
                BlockPos feet = column.above(dy);
                if (!level.isLoaded(feet)) {
                    continue;
                }
                BlockPos below = feet.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    continue;
                }
                Vec3 spot = Vec3.atBottomCenterOf(feet);
                AABB box = type.getSpawnAABB(spot.x, spot.y, spot.z);
                if (level.noCollision(box) && (aquatic || !level.containsAnyLiquid(box))) {
                    return spot;
                }
            }
        }
        return null;
    }

    /** A test creature from an earlier session never re-enters a level. */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!event.getLevel().isClientSide() && entity.getTags().contains(TAG)
                && !entity.getTags().contains(SESSION_PREFIX + session)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            int removed = removeOwned(player.level().getServer(), player.getUUID());
            if (removed > 0) {
                LOGGER.info("[Admin] Removed {} test creature(s) of {} on logout", removed, player.getName().getString());
            }
        }
    }
}
