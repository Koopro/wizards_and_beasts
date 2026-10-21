package at.koopro.wizardsandbeasts.heritage.elf;

import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageTransformService;
import at.koopro.wizardsandbeasts.heritage.identity.HeritagePractice;
import at.koopro.wizardsandbeasts.heritage.identity.PracticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.util.InventoryHandover;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * House-elf magic: wandless, small, and stronger than it looks. Two arts on the wheel, both for any elf with the
 * {@code household_magic} trait in their own shape. No wand is involved, so the Code of Wand Use is not broken and
 * the Trace does not see it — the Ministry has never been able to tell elf magic from the household's (CoS ch.2).
 *
 * <ul>
 *   <li><b>Snap</b> ({@code elf_snap}): one snap of the fingers at a living thing within {@link #SNAP_REACH} —
 *       it is thrown back and drops what it holds. Dobby threw Lucius Malfoy down a flight of stairs this way (CoS
 *       ch.18). A bound elf cannot raise it against its master, and against another player it needs PvP on.</li>
 *   <li><b>Kitchen charm</b> ({@code elf_kitchen}): up to {@link #KITCHEN_ITEMS} raw foods the elf carries are
 *       cooked at once, without fire or fuel — the Hogwarts kitchens (GoF ch.21). Only food is cooked; ore stays
 *       ore.</li>
 * </ul>
 *
 * <p>Cooldowns are the ability framework's ({@code abilities/elf_snap.json}, {@code elf_kitchen.json}). Each success
 * counts as practice ({@link HeritagePractice}).
 */
@NullMarked
public final class ElfMagic {

    public static final double SNAP_REACH = 8.0;
    public static final double SNAP_PUSH = 1.4;
    public static final int KITCHEN_ITEMS = 8;

    private ElfMagic() {}

    /** Whether this player may use elf magic now: an elf of a household lineage, in their own shape. */
    public static boolean canUse(ServerPlayer player) {
        return HouseElfBond.enabled() && HouseElfBond.isElf(player)
                && HeritageAPI.getData(player).hasTrait("household_magic")
                && !HeritageTransformService.isTransformed(HeritageAPI.getData(player));
    }

    // -- snap ----------------------------------------------------------------------------------------------

    /** Snaps at whatever the elf is looking at. Returns whether anything was struck. */
    public static boolean snap(ServerPlayer elf) {
        if (!canUse(elf) || !(elf.level() instanceof ServerLevel level)) {
            return false;
        }
        LivingEntity target = lookedAt(elf);
        if (target == null) {
            PlayerFeedback.actionBar(elf, Component.translatable("heritage.wizards_and_beasts.elf.snap.nothing")
                    .withStyle(ChatFormatting.GRAY));
            return false;
        }
        return snapAt(elf, target, level);
    }

    /** The snap against one target, for the ability and for tests. */
    public static boolean snapAt(ServerPlayer elf, LivingEntity target, ServerLevel level) {
        if (target instanceof ServerPlayer other) {
            if (HouseElfBond.serves(elf, other)) {
                PlayerFeedback.refuse(elf, Component.translatable("heritage.wizards_and_beasts.elf.snap"),
                        Component.translatable("heritage.wizards_and_beasts.elf.snap.master"));
                return false;
            }
            if (!level.isPvpAllowed()) {
                return false;
            }
        }
        Vec3 away = target.position().subtract(elf.position()).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() < 1.0E-4) {
            away = elf.getLookAngle().multiply(1.0, 0.0, 1.0);
        }
        away = away.normalize().scale(SNAP_PUSH);
        target.push(away.x, 0.45, away.z);
        target.hurtMarked = true;
        disarm(target);
        level.playSound(null, elf.getX(), elf.getY(), elf.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 0.6f, 1.9f);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6,
                target.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
        HeritagePractice.record(elf, PracticeKind.ELF_SNAP);
        return true;
    }

    /** Whatever is in the target's main hand falls at its feet. */
    static void disarm(LivingEntity target) {
        ItemStack held = target.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        ItemStack dropped = held.copy();
        target.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (target instanceof ServerPlayer player) {
            player.drop(dropped, true);
            player.containerMenu.broadcastChanges();
        } else if (target instanceof Mob mob) {
            mob.spawnAtLocation((ServerLevel) mob.level(), dropped);
        }
    }

    private static @Nullable LivingEntity lookedAt(ServerPlayer elf) {
        Vec3 eye = elf.getEyePosition();
        Vec3 look = elf.getLookAngle();
        Vec3 end = eye.add(look.scale(SNAP_REACH));
        AABB sweep = elf.getBoundingBox().expandTowards(look.scale(SNAP_REACH)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(elf, eye, end, sweep,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), SNAP_REACH * SNAP_REACH);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity living) || !elf.hasLineOfSight(living)) {
            return null;
        }
        return living;
    }

    // -- kitchen -------------------------------------------------------------------------------------------

    /**
     * Cooks up to {@link #KITCHEN_ITEMS} raw foods from the elf's inventory.
     *
     * @return how many were cooked; 0 when there was nothing to cook (the ability then spends no cooldown)
     */
    public static int kitchen(ServerPlayer elf) {
        if (!canUse(elf) || !(elf.level() instanceof ServerLevel level)) {
            return 0;
        }
        int cooked = 0;
        var inventory = elf.getInventory();
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size() && cooked < KITCHEN_ITEMS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            ItemStack result = cookedFood(level, stack);
            if (result.isEmpty()) {
                continue;
            }
            int take = Math.min(stack.getCount(), KITCHEN_ITEMS - cooked);
            stack.shrink(take);
            for (int i = 0; i < take; i++) {
                InventoryHandover.giveOrDrop(elf, result.copy());
            }
            cooked += take;
        }
        if (cooked == 0) {
            PlayerFeedback.actionBar(elf, Component.translatable("heritage.wizards_and_beasts.elf.kitchen.nothing")
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }
        elf.containerMenu.broadcastChanges();
        level.playSound(null, elf.getX(), elf.getY(), elf.getZ(), SoundEvents.FURNACE_FIRE_CRACKLE,
                SoundSource.PLAYERS, 1.0f, 1.2f);
        level.sendParticles(ParticleTypes.SMOKE, elf.getX(), elf.getY() + 1.0, elf.getZ(), 10, 0.3, 0.3, 0.3, 0.01);
        PlayerFeedback.actionBar(elf, Component.translatable("heritage.wizards_and_beasts.elf.kitchen.done", cooked));
        HeritagePractice.record(elf, PracticeKind.ELF_KITCHEN);
        return cooked;
    }

    /** What the furnace would make of one {@code raw}, if that is food from food; empty otherwise. */
    static ItemStack cookedFood(ServerLevel level, ItemStack raw) {
        if (raw.isEmpty() || !raw.has(DataComponents.FOOD)) {
            return ItemStack.EMPTY;
        }
        SingleRecipeInput input = new SingleRecipeInput(raw.copyWithCount(1));
        Optional<RecipeHolder<SmeltingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level);
        if (recipe.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
        return result.has(DataComponents.FOOD) ? result.copyWithCount(1) : ItemStack.EMPTY;
    }
}
