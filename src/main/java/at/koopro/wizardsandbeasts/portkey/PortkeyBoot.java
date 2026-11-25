package at.koopro.wizardsandbeasts.portkey;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Stoatshead Hill's boot (Goblet of Fire ch.6): "a manky old boot" on the hilltop that is a Portkey. Everyone who wants
 * to go takes hold of it; here, whoever touches it (uses the boot) is carried, with everyone within reach of them, to
 * the Leaky Cauldron, and the boot goes with them: it was set for one journey.
 *
 * <p>The place template (structure {@code places/portkey_hill}) carries the boot as an item display and an invisible
 * vanilla interaction entity over it, both tagged {@link #TAG}. The journey itself is {@link PortkeyService#travel}, the
 * one every Portkey makes.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class PortkeyBoot {

    /** The entity tag on the boot's display and its touch box. */
    public static final String TAG = "wizards_and_beasts.portkey_boot";
    /** Where a hilltop boot goes: the one place every world has where travellers are expected. */
    public static final Identifier LANDMARK = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "leaky_cauldron");

    private PortkeyBoot() {}

    /** A Portkey set for {@link #LANDMARK}, as the boot is. */
    public static ItemStack portkey() {
        ItemStack stack = new ItemStack(TrinketItemRegistry.PORTKEY.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(PortkeyService.LANDMARK_KEY, LANDMARK.toString());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @SubscribeEvent
    public static void onTouch(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!(target instanceof Interaction) || !target.getTags().contains(TAG)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player) || !ModuleManager.isEnabled(Module.ARTEFACTS)) {
            return;
        }
        var level = player.level();
        var boot = target.getBoundingBox().inflate(1.0);
        if (PortkeyService.travel(player, portkey()) == PortkeyService.Result.TRAVELLED) {
            for (Entity part : level.getEntities((Entity) null, boot, e -> e.getTags().contains(TAG))) {
                part.discard();
            }
        }
    }
}
