package at.koopro.wizardsandbeasts.heritage.elf;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.identity.HeritagePractice;
import at.koopro.wizardsandbeasts.heritage.identity.PracticeKind;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import at.koopro.wizardsandbeasts.util.InventoryHandover;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A house-elf's service to a master, and the one way out of it.
 *
 * <ul>
 *   <li><b>Pledge and accept.</b> A bound elf (lineage trait {@code bound_service}) offers service by sneak-using a
 *       player with an empty hand; that player accepts by sneak-using the elf back within {@link #PLEDGE_TICKS}. Two
 *       consenting players, both on the server — nothing binds anyone who did not ask.</li>
 *   <li><b>Summons.</b> The master's wheel gains <i>Call house-elf</i>; every elf bound to them in the same world
 *       Apparates to their side with the elf's crack (Kreacher and Dobby answer wherever they are).</li>
 *   <li><b>No harm to the master.</b> A blow a bound elf aims at its master never lands, and the elf punishes
 *       itself for it (Dobby's ironed hands, CoS ch.2) — {@link HouseElfEvents}.</li>
 *   <li><b>Freedom.</b> The master hands the elf a garment — uses any wearable on them — and the elf is free:
 *       lineage Free, bond gone, for good (CoS ch.18). Nothing else frees an elf; a free elf serves no one.</li>
 * </ul>
 *
 * <p>State lives on the elf ({@link ElfBondData}); the pending pledge is transient. Server-side only.
 */
@NullMarked
public final class HouseElfBond {

    /** How long a pledge waits for its answer: ten seconds. */
    public static final int PLEDGE_TICKS = 200;

    private record Pledge(UUID elf, long expiresAt) {}

    /** Pending pledges, keyed by the would-be master. */
    private static final PlayerScopedState<Pledge> PLEDGES = PlayerScopedState.create("house_elf_pledge");

    private HouseElfBond() {}

    // -- who is what --------------------------------------------------------------------------------------

    public static boolean isElf(ServerPlayer player) {
        return HeritageAPI.getData(player).getSelectedHeritage() == Heritage.HOUSE_ELF;
    }

    public static ElfBondData bondOf(ServerPlayer elf) {
        return elf.getData(ModAttachments.ELF_BOND.get());
    }

    /** Whether this elf is of a serving lineage and serves no one yet. */
    public static boolean mayPledge(ServerPlayer elf) {
        return enabled() && isElf(elf) && HeritageAPI.getData(elf).hasTrait("bound_service") && !bondOf(elf).bound();
    }

    /** Whether {@code elf} is bound to {@code master}. */
    public static boolean serves(ServerPlayer elf, ServerPlayer master) {
        return isElf(elf) && bondOf(elf).servedBy(master.getUUID());
    }

    /** Every elf bound to {@code master} that is online now. */
    public static List<ServerPlayer> elvesOf(ServerPlayer master) {
        List<ServerPlayer> out = new ArrayList<>();
        if (master.level().getServer() == null) {
            return out;
        }
        for (ServerPlayer player : master.level().getServer().getPlayerList().getPlayers()) {
            if (player != master && serves(player, master)) {
                out.add(player);
            }
        }
        return out;
    }

    /** Whether the master's wheel shows the call: they have an elf bound to them. */
    public static boolean canCall(ServerPlayer master) {
        return enabled() && !elvesOf(master).isEmpty();
    }

    static boolean enabled() {
        return ModuleManager.isEnabled(Module.HERITAGE);
    }

    // -- binding ------------------------------------------------------------------------------------------

    /** The elf offers service to {@code master}. Returns whether a pledge now stands. */
    public static boolean pledge(ServerPlayer elf, ServerPlayer master) {
        if (!mayPledge(elf) || isElf(master)) {
            return false;
        }
        PLEDGES.put(master, new Pledge(elf.getUUID(), master.level().getGameTime() + PLEDGE_TICKS));
        PlayerFeedback.toast(master, NoticeKind.DISCOVERY, Component.translatable("heritage.wizards_and_beasts.elf.pledge.title"),
                Component.translatable("heritage.wizards_and_beasts.elf.pledge.body", elf.getDisplayName()));
        PlayerFeedback.actionBar(elf, Component.translatable("heritage.wizards_and_beasts.elf.pledge.sent",
                master.getDisplayName()));
        return true;
    }

    /** {@code master} accepts {@code elf}'s standing pledge. Returns whether they are now bound. */
    public static boolean accept(ServerPlayer master, ServerPlayer elf) {
        Pledge pledge = PLEDGES.get(master);
        if (pledge == null || !pledge.elf().equals(elf.getUUID())
                || master.level().getGameTime() > pledge.expiresAt() || !mayPledge(elf)) {
            return false;
        }
        PLEDGES.remove(master);
        bind(elf, master);
        return true;
    }

    /** Binds without the handshake: the accepted pledge, and admin tools and tests. */
    public static void bind(ServerPlayer elf, ServerPlayer master) {
        elf.setData(ModAttachments.ELF_BOND.get(), ElfBondData.to(master.getUUID(), master.getName().getString(),
                SpellPractice.day(elf.level().getGameTime())));
        PlayerFeedback.toast(elf, NoticeKind.UNLOCK, Component.translatable("heritage.wizards_and_beasts.elf.bound.title"),
                Component.translatable("heritage.wizards_and_beasts.elf.bound.elf", master.getDisplayName()));
        PlayerFeedback.toast(master, NoticeKind.UNLOCK, Component.translatable("heritage.wizards_and_beasts.elf.bound.title"),
                Component.translatable("heritage.wizards_and_beasts.elf.bound.master", elf.getDisplayName()));
        at.koopro.wizardsandbeasts.sync.PlayerStateSyncService.syncAbilityGrants(master);
    }

    // -- the summons --------------------------------------------------------------------------------------

    /**
     * {@code master} calls their elves. Each bound elf in the master's world Apparates to their side.
     *
     * @return how many answered; {@code -1} when the master has no elf to call
     */
    public static int call(ServerPlayer master) {
        List<ServerPlayer> elves = elvesOf(master);
        if (elves.isEmpty() || !(master.level() instanceof ServerLevel level)) {
            return -1;
        }
        int answered = 0;
        for (ServerPlayer elf : elves) {
            if (elf.level() != level || !elf.isAlive() || elf.isSpectator()) {
                PlayerFeedback.actionBar(elf, Component.translatable("heritage.wizards_and_beasts.elf.called_far",
                        master.getDisplayName()));
                continue;
            }
            crack(level, elf.position());
            Vec3 beside = master.position().add(master.getLookAngle().multiply(1.2, 0.0, 1.2));
            elf.teleportTo(level, beside.x, master.getY(), beside.z, java.util.Set.of(), elf.getYRot(), elf.getXRot(),
                    true);
            elf.resetFallDistance();
            crack(level, elf.position());
            elf.setData(ModAttachments.ELF_BOND.get(), bondOf(elf).answered());
            HeritagePractice.record(elf, PracticeKind.ELF_SUMMONS);
            PlayerFeedback.actionBar(elf, Component.translatable("heritage.wizards_and_beasts.elf.answered",
                    master.getDisplayName()));
            answered++;
        }
        PlayerFeedback.actionBar(master, Component.translatable("heritage.wizards_and_beasts.elf.call", answered));
        return answered;
    }

    static void crack(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8f, 1.6f);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, 8, 0.2, 0.3, 0.2, 0.01);
    }

    // -- freedom -------------------------------------------------------------------------------------------

    /** Whether a stack is a garment: anything worn on the body (head, chest, legs, feet). */
    public static boolean isGarment(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    /**
     * The master hands {@code elf} the garment in their hand. If they are this elf's master, the elf is free.
     *
     * @return whether the elf was freed
     */
    public static boolean present(ServerPlayer master, ServerPlayer elf, ItemStack garment) {
        if (!enabled() || !serves(elf, master) || !isGarment(garment)) {
            return false;
        }
        ItemStack given = garment.split(1);
        InventoryHandover.giveOrDrop(elf, given);
        free(elf, master.getDisplayName());
        return true;
    }

    /** Sets {@code elf} free: Free lineage (nothing re-rolled), no master. {@code by} names who gave the clothes. */
    public static void free(ServerPlayer elf, @Nullable Component by) {
        PlayerHeritageData data = HeritageAPI.getData(elf);
        UUID former = bondOf(elf).master().orElse(null);
        elf.setData(ModAttachments.ELF_BOND.get(), ElfBondData.NONE);
        if (data.getSelectedHeritageVariant() != HeritageVariant.ELF_FREE) {
            HeritageAPI.shiftLineage(elf, HeritageVariant.ELF_FREE);
        }
        PlayerFeedback.toast(elf, NoticeKind.UNLOCK, Component.translatable("heritage.wizards_and_beasts.elf.freed.title"),
                by == null ? Component.translatable("heritage.wizards_and_beasts.elf.freed.body")
                        : Component.translatable("heritage.wizards_and_beasts.elf.freed.by", by));
        if (former != null && elf.level().getServer() != null) {
            ServerPlayer master = elf.level().getServer().getPlayerList().getPlayer(former);
            if (master != null) {
                PlayerFeedback.toast(master, NoticeKind.DISCOVERY,
                        Component.translatable("heritage.wizards_and_beasts.elf.freed.title"),
                        Component.translatable("heritage.wizards_and_beasts.elf.freed.master", elf.getDisplayName()));
                at.koopro.wizardsandbeasts.sync.PlayerStateSyncService.syncAbilityGrants(master);
            }
        }
    }
}
