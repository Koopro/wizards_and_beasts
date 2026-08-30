package at.koopro.wizardsandbeasts.item;

import at.koopro.wizardsandbeasts.network.ClientScreenHooksInvoker;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.floo.FlooRegistrationService;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.network.floo.OpenFlooRegistrationS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.UseOnContext;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * Ministry of Magic handbook. Right-click opens the datapack-driven handbook screen client-side.
 * Registration is unconditional; opening the screen is gated on {@link Module#HANDBOOK}.
 *
 * <p>The screen is opened reflectively (same pattern as {@code BestiaryItem}) so the client-only
 * {@code HandbookScreen} class never loads on a dedicated server.
 */
public final class MinistryHandbookItem extends Item {

    public MinistryHandbookItem(@NonNull Properties properties) {
        super(properties);
    }

    /**
     * On a cold Floo hearth, the handbook is Ministry paperwork rather than a book to read.
     *
     * <p>{@code useOn} rather than {@code use}, so the ordinary right-click that opens the handbook
     * is untouched everywhere else — including on a hearth that is already lit, which is a travel
     * prompt and not a form.
     *
     * <p>Falls through to {@code super} whenever this is not a registration, which is what lets the
     * book still open when a player happens to be facing a fireplace.
     */
    @Override
    public @NonNull InteractionResult useOn(@NonNull UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !ModuleManager.isEnabled(Module.FLOO_NETWORK)) {
            return super.useOn(context);
        }
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(ModBlocks.FLOO_FIREPLACE.get())) {
            return super.useOn(context);
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!FlooRegistrationService.canOpenAt(sp, serverLevel, pos)) {
            // Two different refusals wear one sentence on purpose here: a lit hearth and somebody
            // else's hearth are both "not something you may file", and the specific reason is given
            // by the paths that actually attempt it.
            sp.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.cannot_register"), true);
            return InteractionResult.SUCCESS;
        }
        String current = serverLevel.getBlockEntity(pos) instanceof FlooFireplaceBlockEntity be
                ? be.getNetworkAddress() : "";
        OpenFlooRegistrationS2CPayload.send(sp, pos, current, FlooRegistrationService.feeFor(sp));
        return InteractionResult.SUCCESS;
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand usedHand) {
        if (!ModuleManager.isEnabled(Module.HANDBOOK)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            ClientScreenHooksInvoker.invoke("openHandbookScreen");
        }
        return InteractionResult.SUCCESS;
    }
}
