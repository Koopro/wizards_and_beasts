package at.koopro.wizardsandbeasts.event.skill;

import java.util.List;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Consumers for the House-elf "Binding Threads" ability flags:
 * <ul>
 *   <li>{@code elf_nimble_fingers} — deft hands draw nearby loose items to the elf.</li>
 *   <li>{@code free_elf} — unbound: immune to Slowness and Weakness.</li>
 * </ul>
 * (The third elf flag, {@code elf_apparition}, is consumed in
 * {@link at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic}.)
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ElfAbilityHandler {

    private static final double MAGNET_RADIUS = 3.0;
    private static final double MAGNET_PULL = 0.12;

    private ElfAbilityHandler() {}

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (SkillSystemAPI.hasAbility(player, "free_elf")) {
            // Unbound: shed binding effects each tick.
            player.removeEffect(MobEffects.SLOWNESS);
            player.removeEffect(MobEffects.WEAKNESS);
        }

        if (SkillSystemAPI.hasAbility(player, "elf_nimble_fingers")) {
            pullNearbyItems(player);
        }
    }

    /** Nudges loose, pickup-ready items toward the elf — deft gathering hands. */
    private static void pullNearbyItems(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(MAGNET_RADIUS);
        List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class, area,
                item -> !item.hasPickUpDelay() && item.isAlive());
        if (items.isEmpty()) return;

        Vec3 center = player.position().add(0.0, 0.5, 0.0);
        for (ItemEntity item : items) {
            Vec3 toPlayer = center.subtract(item.position());
            if (toPlayer.lengthSqr() < 0.25) continue; // close enough; let vanilla pickup take it
            item.setDeltaMovement(item.getDeltaMovement().add(toPlayer.normalize().scale(MAGNET_PULL)));
        }
    }
}
