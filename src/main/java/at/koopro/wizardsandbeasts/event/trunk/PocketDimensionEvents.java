package at.koopro.wizardsandbeasts.event.trunk;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.trunk.ExtensionCharmService;
import at.koopro.wizardsandbeasts.trunk.TrunkRecord;
import at.koopro.wizardsandbeasts.trunk.TrunkArchetype;
import at.koopro.wizardsandbeasts.trunk.TrunkRegistryData;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public class PocketDimensionEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    // playerId → ticks remaining before LADDER_STEP plays after entry
    private static final Map<UUID, Integer> pendingLadderSounds = new HashMap<>();

    // Forward-compat entity tag for mod creatures (inert until creatures are added and tagged).
    private static final TagKey<EntityType<?>> MAGICAL_CREATURE_TAG =
            TagKey.create(Registries.ENTITY_TYPE,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "magical_creature"));

    private PocketDimensionEvents() {}

    public static void scheduleLadderSound(UUID playerId, int delayTicks) {
        pendingLadderSounds.put(playerId, delayTicks);
    }

    public static void cancelLadderSound(UUID playerId) {
        pendingLadderSounds.remove(playerId);
    }

    // -------------------------------------------------------------------------
    // Monster suppression + latch-open detection on item spawn
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Block monsters from spawning inside the extension realm.
        if (event.getLevel() instanceof ServerLevel sl && sl.dimension().equals(ModDimensions.EXTENSION_REALM)
                && event.getEntity() instanceof Mob mob
                && mob.getType().getCategory() == MobCategory.MONSTER) {
            event.setCanceled(true);
            return;
        }

        // Mark trunk items as latch-unsecured when dropped into the world.
        if (!ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        ItemStack stack = itemEntity.getItem();
        if (!stack.has(ModDataComponents.POCKET_CASE_ID.get())) return;

        stack.set(ModDataComponents.POCKET_LATCH_SECURED.get(), false);
    }

    // -------------------------------------------------------------------------
    // Per-player tick: void fall safety + delayed ladder sound + latch warning
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        // Void fall safety net — external lock does NOT prevent this escape.
        if (serverLevel.dimension().equals(ModDimensions.EXTENSION_REALM) && player.getY() < 50.0) {
            ExtensionCharmService.exitPocket(player);
            return;
        }

        // Delayed ladder creak 2 ticks after trapdoor-open on entry.
        UUID id = player.getUUID();
        if (pendingLadderSounds.containsKey(id)) {
            int remaining = pendingLadderSounds.get(id) - 1;
            if (remaining <= 0) {
                pendingLadderSounds.remove(id);
                if (serverLevel.dimension().equals(ModDimensions.EXTENSION_REALM)) {
                    serverLevel.playSound(null, player.blockPosition(),
                            SoundEvents.LADDER_STEP, SoundSource.BLOCKS, 1.0f, 1.0f);
                }
            } else {
                pendingLadderSounds.put(id, remaining);
            }
        }

        // Latch warning: warn player if they are within 4 blocks of an unsecured trunk on the ground.
        if (!ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) return;
        List<ItemEntity> nearby = serverLevel.getEntitiesOfClass(
                ItemEntity.class,
                player.getBoundingBox().inflate(4.0),
                ie -> ie.getItem().has(ModDataComponents.POCKET_CASE_ID.get())
                        && !ie.getItem().getOrDefault(ModDataComponents.POCKET_LATCH_SECURED.get(), true));
        if (!nearby.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("A latch on the suitcase is open.").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    // -------------------------------------------------------------------------
    // Per-level tick: creature escape risk check
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) return;
        // Creature escape is suppressed in PREVIEW mode (requires actual creature entities).
        if (ModuleManager.isPreview(Module.POCKET_DIMENSIONS)) return;

        var border = level.getWorldBorder();
        AABB borderBox = new AABB(border.getMinX(), -64, border.getMinZ(), border.getMaxX(), 320, border.getMaxZ());
        List<ItemEntity> unsecured = level.getEntitiesOfClass(
                ItemEntity.class,
                borderBox,
                ie -> ie.getItem().has(ModDataComponents.POCKET_CASE_ID.get())
                        && !ie.getItem().getOrDefault(ModDataComponents.POCKET_LATCH_SECURED.get(), true));

        for (ItemEntity trunkEntity : unsecured) {
            UUID caseId = trunkEntity.getItem().get(ModDataComponents.POCKET_CASE_ID.get());
            if (caseId == null) continue;

            TrunkRegistryData data = TrunkRegistryData.get(level);
            TrunkRecord record = data.getCasePocket(caseId).orElse(null);
            if (record == null) continue;

            // 1-in-200 chance per tick that a nearby magical creature escapes into the trunk.
            if (level.random.nextInt(200) != 0) continue;

            List<Entity> creatures = level.getEntitiesOfClass(
                    Entity.class,
                    trunkEntity.getBoundingBox().inflate(3.0),
                    e -> e.getType().is(MAGICAL_CREATURE_TAG));

            if (creatures.isEmpty()) continue;

            Entity creature = creatures.get(0);
            // Teleport wired when creature entities are implemented.
            LOGGER.info("Creature {} eligible to enter unsecured trunk {} (teleport pending implementation)",
                    creature.getName().getString(), record.pocketId());
        }
    }

    // -------------------------------------------------------------------------
    // Right-click block: exit via ceiling trapdoor or exit door
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(ModDimensions.EXTENSION_REALM)) return;

        BlockPos clickedPos = event.getPos();
        TrunkRegistryData data = TrunkRegistryData.get(serverLevel);
        TrunkRecord record = data.getPocketAtPos(clickedPos).orElse(null);
        if (record == null) return;

        BlockPos center  = record.spawnPos();
        int wallTop      = center.getY() + 8;

        // SCAMANDER: exit via shed roof trapdoor (cy+5) or shed south door (cy, cz+2)
        final BlockPos ceilingHatch;
        final BlockPos doorLower;
        if (record.archetype() == TrunkArchetype.SCAMANDER_SANCTUARY) {
            ceilingHatch = new BlockPos(center.getX(), center.getY() + 5, center.getZ());
            doorLower    = new BlockPos(center.getX(), center.getY(), center.getZ() + 2);
        } else {
            ceilingHatch = new BlockPos(center.getX(), wallTop, center.getZ());
            doorLower    = new BlockPos(center.getX(), center.getY() - 1, center.getZ() - 1);
        }
        boolean isCeilingExit = clickedPos.equals(ceilingHatch)
                && serverLevel.getBlockState(clickedPos).is(Blocks.IRON_TRAPDOOR);

        boolean isDoorExit = (clickedPos.equals(doorLower) || clickedPos.equals(doorLower.above()))
                && serverLevel.getBlockState(clickedPos).is(Blocks.SPRUCE_DOOR);

        if (isCeilingExit || isDoorExit) {
            event.setCanceled(true);
            // Priority 3: external lock blocks hatch/door exit (void fall safety net bypasses this).
            if (record.lockedExternally()) {
                player.displayClientMessage(
                        Component.literal("The case has been locked from outside.").withStyle(ChatFormatting.RED), true);
                return;
            }
            ExtensionCharmService.exitPocket(player);
        }
    }

    // -------------------------------------------------------------------------
    // Right-click entity: re-secure an unsecured trunk item on the ground
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof ItemEntity itemEntity)) return;

        ItemStack stack = itemEntity.getItem();
        if (!stack.has(ModDataComponents.POCKET_CASE_ID.get())) return;
        if (stack.getOrDefault(ModDataComponents.POCKET_LATCH_SECURED.get(), true)) return; // already secured

        event.setCanceled(true);
        stack.set(ModDataComponents.POCKET_LATCH_SECURED.get(), true);

        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.blockPosition(),
                    SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        player.displayClientMessage(
                Component.literal("Latch secured.").withStyle(ChatFormatting.GREEN), true);
    }
}
