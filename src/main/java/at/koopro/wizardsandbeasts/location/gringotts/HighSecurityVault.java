package at.koopro.wizardsandbeasts.location.gringotts;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.LocationPresence;
import at.koopro.wizardsandbeasts.location.LocationService;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * A high-security Gringotts vault: 27 slots of item storage behind the goblins' counter, rented by the in-game week.
 *
 * <p>What it is for is what Gringotts is for — keeping valuables where death and thieves cannot reach them. The
 * contents are an attachment with {@code copyOnDeath}; the vault opens only at the counter, closes when its owner
 * walks away ({@link VaultContainer#stillValid}), and is locked — never emptied — while the rent is unpaid.
 *
 * @param items          the stored stacks, by slot
 * @param paidThroughDay the last in-game day the rent covers; -1 never rented
 */
@NullMarked
public record HighSecurityVault(List<Slot> items, long paidThroughDay) {

    public static final int SIZE = 27;
    /** In-game days one rent payment covers. */
    public static final int RENT_DAYS = 7;

    public record Slot(int slot, ItemStack stack) {
        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, SIZE - 1).fieldOf("slot").forGetter(Slot::slot),
                ItemStack.CODEC.fieldOf("stack").forGetter(Slot::stack)
        ).apply(i, Slot::new));
    }

    public static final Codec<HighSecurityVault> CODEC = RecordCodecBuilder.create(i -> i.group(
            Slot.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(HighSecurityVault::items),
            Codec.LONG.optionalFieldOf("paid_through", -1L).forGetter(HighSecurityVault::paidThroughDay)
    ).apply(i, HighSecurityVault::new));

    public static final HighSecurityVault EMPTY = new HighSecurityVault(List.of(), -1L);

    public HighSecurityVault {
        items = List.copyOf(items);
    }

    public boolean rentedOn(long day) {
        return day <= paidThroughDay;
    }

    public HighSecurityVault paidFrom(long day) {
        return new HighSecurityVault(items, Math.max(paidThroughDay, day - 1) + RENT_DAYS);
    }

    public int stored() {
        return items.size();
    }

    static HighSecurityVault of(SimpleContainer container, long paidThroughDay) {
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                slots.add(new Slot(i, stack.copy()));
            }
        }
        return new HighSecurityVault(slots, paidThroughDay);
    }

    /** The container a vault menu edits; every change is written straight back to the attachment. */
    static final class VaultContainer extends SimpleContainer {
        private final ServerPlayer owner;
        private final Identifier location;
        private boolean loading = true;

        VaultContainer(ServerPlayer owner, Identifier location, HighSecurityVault vault) {
            super(SIZE);
            this.owner = owner;
            this.location = location;
            for (Slot slot : vault.items()) {
                setItem(slot.slot(), slot.stack().copy());
            }
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!loading) {
                save();
            }
        }

        @Override
        public void stopOpen(ContainerUser user) {
            super.stopOpen(user);
            save();
        }

        @Override
        public boolean stillValid(Player player) {
            LocationDefinition def = Locations.open(location);
            return player == owner && owner.isAlive() && def != null && LocationPresence.isAt(owner, def);
        }

        private void save() {
            HighSecurityVault current = owner.getData(ModAttachments.HIGH_SECURITY_VAULT.get());
            owner.setData(ModAttachments.HIGH_SECURITY_VAULT.get(), of(this, current.paidThroughDay()));
        }
    }

    /** The service: pay the week's rent if it has lapsed, then open the vault. */
    static final class Service implements LocationService {

        @Override
        public Quote quote(ServerPlayer player, Use use) {
            HighSecurityVault vault = player.getData(ModAttachments.HIGH_SECURITY_VAULT.get());
            long day = SpellPractice.day(player.level().getGameTime());
            if (vault.rentedOn(day) || !Use.billed()) {
                return Quote.yes(Component.translatable("location.wizards_and_beasts.gringotts.vault_paid",
                        vault.stored(), SIZE, vault.paidThroughDay() - day + 1));
            }
            return Quote.yes(Component.translatable("location.wizards_and_beasts.gringotts.vault_rent",
                    CurrencyHelper.formatFromKnuts(use.fee()), RENT_DAYS, vault.stored()));
        }

        @Override
        public boolean perform(ServerPlayer player, Use use) {
            long day = SpellPractice.day(player.level().getGameTime());
            HighSecurityVault vault = player.getData(ModAttachments.HIGH_SECURITY_VAULT.get());
            if (!vault.rentedOn(day)) {
                if (!use.charge(player, at.koopro.wizardsandbeasts.currency.billing.MoneyFlow.VAULT_RENT, use.fee())) {
                    PlayerFeedback.actionBar(player,
                            Component.translatable("location.wizards_and_beasts.gringotts.vault_locked"));
                    return false;
                }
                vault = vault.paidFrom(day);
                player.setData(ModAttachments.HIGH_SECURITY_VAULT.get(), vault);
            }
            open(player, use.location(), vault);
            return true;
        }
    }

    static void open(ServerPlayer player, Identifier location, HighSecurityVault vault) {
        VaultContainer container = new VaultContainer(player, location, vault);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, container),
                Component.translatable("location.wizards_and_beasts.gringotts.vault_title")));
    }
}
