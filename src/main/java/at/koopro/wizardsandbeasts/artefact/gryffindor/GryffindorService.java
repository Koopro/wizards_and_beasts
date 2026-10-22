package at.koopro.wizardsandbeasts.artefact.gryffindor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.equipment.EquipmentAPI;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The Sorting Hat and the Sword of Gryffindor, server side: noticing a wearer standing their ground, presenting the
 * sword, the sword drinking a basilisk's venom, and the world forgetting a sword that was destroyed.
 * {@link GryffindorEvents} routes the game's events here; the rule of when is {@link SwordPresentation}'s.
 */
@NullMarked
public final class GryffindorService {

    public static final TagKey<EntityType<?>> FOES = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sword_of_gryffindor_foes"));
    static final Identifier BASILISK = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "basilisk");
    private static final String KEY = "artefact.wizards_and_beasts.sword_of_gryffindor.";

    /** The last foe each player struck, and when (that level's game time). */
    record Strike(UUID foe, long tick) {}

    private static final PlayerScopedState<Strike> LAST_STRIKE = PlayerScopedState.create("gryffindor_last_strike");

    private GryffindorService() {}

    /** A player struck a foe worth the sword. */
    public static void struck(ServerPlayer striker, LivingEntity foe) {
        if (foe.getType().is(FOES)) {
            LAST_STRIKE.put(striker, new Strike(foe.getUUID(), striker.level().getGameTime()));
        }
    }

    /**
     * A player was just hurt by {@code attacker}. If they wear the Hat, are in mortal danger from a foe worth the sword
     * and were fighting it, the sword falls out of the Hat. Returns the dropped sword, or null.
     */
    public static @Nullable ItemEntity hurtBy(ServerPlayer wearer, Entity attacker) {
        ServerLevel level = wearer.level();
        Strike strike = LAST_STRIKE.get(wearer);
        long since = strike != null && strike.foe().equals(attacker.getUUID())
                ? level.getGameTime() - strike.tick() : -1L;
        boolean may = SwordPresentation.mayPresent(ModuleManager.isEnabled(Module.ARTEFACTS), wearsHat(wearer),
                GryffindorSword.get(level).present() != null, attacker.getType().is(FOES),
                wearer.getHealth(), wearer.getMaxHealth(), since);
        return may ? present(wearer) : null;
    }

    /** The sword falls out of the Hat at the wearer's head, ready to take. */
    public static ItemEntity present(ServerPlayer wearer) {
        ServerLevel level = wearer.level();
        UUID id = UUID.randomUUID();
        ItemStack sword = new ItemStack(CanonItemRegistry.SWORD_OF_GRYFFINDOR.get());
        sword.set(ModDataComponents.GRYFFINDOR_SWORD_ID.get(), id);
        ItemEntity drop = new ItemEntity(level, wearer.getX(), wearer.getEyeY() + 0.4, wearer.getZ(), sword, 0, -0.1, 0);
        drop.setNoPickUpDelay();
        // It does not lie about for five minutes and vanish in the middle of the fight it was given for.
        drop.setUnlimitedLifetime();
        level.addFreshEntity(drop);
        GryffindorSword.get(level).presented(id);
        level.playSound(null, wearer.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.6f);
        wearer.displayClientMessage(Component.translatable(KEY + "presented"), false);
        return drop;
    }

    /** {@code killer} killed {@code victim}, wielding {@code weapon}: a basilisk's venom goes into the blade. */
    public static boolean killed(ServerPlayer killer, LivingEntity victim, ItemStack weapon) {
        if (!weapon.is(CanonItemRegistry.SWORD_OF_GRYFFINDOR.get()) || SwordOfGryffindorItem.imbued(weapon)
                || !BASILISK.equals(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()))) {
            return false;
        }
        weapon.set(ModDataComponents.VENOM_IMBUED.get(), true);
        killer.displayClientMessage(Component.translatable(KEY + "imbued"), false);
        killer.level().playSound(null, killer.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS,
                1.0f, 0.6f);
        return true;
    }

    /** A dropped stack was destroyed. If it was the world's sword, the Hat may present it again. */
    public static boolean destroyed(ServerLevel level, ItemStack stack) {
        UUID id = stack.get(ModDataComponents.GRYFFINDOR_SWORD_ID.get());
        return id != null && GryffindorSword.get(level).lost(id);
    }

    public static boolean wearsHat(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(CanonItemRegistry.SORTING_HAT.get())
                || EquipmentAPI.getEquippedHat(entity).is(CanonItemRegistry.SORTING_HAT.get());
    }
}
