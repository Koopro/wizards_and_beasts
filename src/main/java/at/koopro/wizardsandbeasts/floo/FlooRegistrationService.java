package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * Filing a hearth with the Ministry: the paperwork path onto the network.
 *
 * <h2>Why a second way in</h2>
 * <p>A name tag already registers a hearth, and it stays. But it is a strange thing to be the
 * <em>only</em> way: it costs an anvil level to write on, it is invisible to a player who has never
 * thought to try it, and nothing in the fiction says the Floo Network is administered with luggage
 * labels. Doing it through the Ministry Handbook, for a fee, is the version a player can guess at —
 * and it is the only one that can charge money, because a name tag has no room to ask a question
 * before it is consumed.
 *
 * <p>The two paths differ in price and in nothing else. Both set an owner, both validate through
 * {@link FlooAddress}, both refuse an address somebody else has taken.
 *
 * <h2>Charged where it is charged</h2>
 * <p>The fee is taken in {@link #submit}, after every other refusal has passed and immediately before
 * the entry goes in — so a player refused for a taken address, an invalid one, or a hearth that is
 * not theirs pays nothing. All-or-nothing on the withdrawal: a partial payment is refunded rather
 * than pocketed, which matters because {@code withdrawSmartKnuts} will happily take what it can.
 *
 * <p>Free for creative and for this mod's admins, and free entirely when the Gringotts module is off.
 * A fee that cannot be paid is not a cost, it is a wall.
 */
@NullMarked
public final class FlooRegistrationService {

    private FlooRegistrationService() {
    }

    /** Why a registration attempt was refused, or that it took. */
    public enum Outcome {
        REGISTERED,
        NOT_A_HEARTH,
        NOT_OWNER,
        ADDRESS_TAKEN,
        INVALID_ADDRESS,
        CANNOT_AFFORD
    }

    /** The fee this player would actually pay, in knuts. Zero when they pay nothing. */
    public static int feeFor(ServerPlayer player) {
        if (player.getAbilities().instabuild || FlooAccess.isAdmin(player)) {
            return 0;
        }
        if (!ModuleManager.isEnabled(Module.GRINGOTTS)) {
            return 0;
        }
        return Math.max(0, Config.flooRegistrationFeeKnuts);
    }

    /**
     * Whether {@code player} may open the registration form at {@code pos}.
     *
     * <p>Checked before the screen is sent as well as on submit. The screen check is a courtesy so a
     * player is not asked to type a name they will not be allowed to use; the submit check is the
     * boundary, because a client can send the packet whether or not it was ever shown a screen.
     */
    public static boolean canOpenAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.FLOO_FIREPLACE.get())) {
            return false;
        }
        // A lit hearth is a travel prompt. Registering one would mean writing an address onto a fire
        // somebody might be standing in, and the name-tag path already refuses it for the same reason.
        if (state.getValue(FlooFireplaceBlock.LIT)) {
            return false;
        }
        FlooRegistryEntry existing = FlooNetworkManager.get(level)
                .findByPos(level.dimension().identifier(), pos);
        return existing == null || FlooAccess.mayAdminister(player, existing);
    }

    /**
     * Take a filled-in registration form.
     *
     * <p>{@code pos} comes from the client, so it is re-validated here from scratch rather than
     * trusted: that it is a hearth, that it is in reach, and that the player is allowed to name it.
     * A position sent by a client is a request, not a fact.
     */
    public static Outcome submit(ServerPlayer player, BlockPos pos, String rawAddress, boolean isPublic) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            return Outcome.NOT_A_HEARTH;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return Outcome.NOT_A_HEARTH;
        }
        // Reach check. Without it the packet is a rename tool for any hearth whose coordinates a
        // player can learn, anywhere in the world, from anywhere else in it.
        if (player.blockPosition().distSqr(pos) > MAX_REACH_SQ) {
            return Outcome.NOT_A_HEARTH;
        }
        if (!canOpenAt(player, level, pos)) {
            BlockState state = level.getBlockState(pos);
            return state.is(ModBlocks.FLOO_FIREPLACE.get()) ? Outcome.NOT_OWNER : Outcome.NOT_A_HEARTH;
        }
        if (!FlooAddress.isValid(rawAddress)) {
            return Outcome.INVALID_ADDRESS;
        }
        if (!(level.getBlockEntity(pos) instanceof FlooFireplaceBlockEntity be)) {
            return Outcome.NOT_A_HEARTH;
        }

        FlooNetworkManager manager = FlooNetworkManager.get(level);
        // Asked before the money is taken. The register call below would refuse the same clash, but
        // by then the fee is gone, and refunding it is a second failure path that does not need to
        // exist.
        FlooRegistryEntry clash = manager.getEntry(rawAddress);
        if (clash != null && !clash.blockPos().equals(pos)) {
            return Outcome.ADDRESS_TAKEN;
        }

        int fee = feeFor(player);
        PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
        if (fee > 0) {
            long withdrawn = vault.withdrawSmartKnuts(fee);
            if (withdrawn < fee) {
                if (withdrawn > 0) {
                    vault.depositKnuts(withdrawn); // never pocket a partial payment
                }
                return Outcome.CANNOT_AFFORD;
            }
        }

        FlooNetworkManager.RegisterResult result = manager.register(
                rawAddress, level.dimension().identifier(), pos, isPublic, Optional.of(player.getUUID()));
        if (!result.ok()) {
            // Should be unreachable - both refusals are checked above - but the fee has already been
            // taken, so the refund is not optional. A registration that fails silently and costs a
            // Galleon is the worst outcome this method has.
            if (fee > 0) {
                vault.depositKnuts(fee);
            }
            return result == FlooNetworkManager.RegisterResult.ADDRESS_TAKEN
                    ? Outcome.ADDRESS_TAKEN
                    : Outcome.INVALID_ADDRESS;
        }

        be.setNetworkAddress(FlooAddress.display(rawAddress));
        be.setRegistered(true);
        be.setEnabled(true);

        level.playSound(null, pos, ModSounds.FLOO_IGNITE.get(), SoundSource.BLOCKS, 0.5f, 1.6f);
        PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                Component.translatable("floo.wizards_and_beasts.registered.title",
                        FlooAddress.display(rawAddress)),
                fee > 0
                        ? Component.translatable("floo.wizards_and_beasts.registered.paid",
                                CurrencyHelper.formatFromKnuts(fee))
                        : Component.translatable("floo.wizards_and_beasts.registered.free"));
        return Outcome.REGISTERED;
    }

    /**
     * How far a player may be from the hearth they are filing.
     *
     * <p>Six blocks squared over the usual reach, so leaning back from a fireplace while typing does
     * not invalidate the form the moment it is submitted.
     */
    private static final double MAX_REACH_SQ = 8.0 * 8.0;

    /** The line a player is shown for each refusal. */
    public static Component message(Outcome outcome, String address) {
        return switch (outcome) {
            case REGISTERED -> Component.translatable("floo.wizards_and_beasts.registered", address);
            case NOT_A_HEARTH -> Component.translatable("floo.wizards_and_beasts.fail.not_a_hearth");
            case NOT_OWNER -> Component.translatable("floo.wizards_and_beasts.fail.not_owner");
            case ADDRESS_TAKEN -> Component.translatable("floo.wizards_and_beasts.fail.address_taken", address);
            case INVALID_ADDRESS -> FlooAddress.validate(address).message();
            case CANNOT_AFFORD -> Component.translatable("floo.wizards_and_beasts.fail.cannot_afford");
        };
    }
}
