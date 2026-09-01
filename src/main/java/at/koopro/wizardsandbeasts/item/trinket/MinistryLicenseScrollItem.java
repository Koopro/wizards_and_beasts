package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.ministry.licence.LicenceUpgrade;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseData;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicences;
import at.koopro.wizardsandbeasts.network.ministry.LicenceOpenS2CPayload;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * A Ministry of Magic licence, on parchment, with a seal.
 *
 * <p>The scroll itself does almost nothing; it is the <em>document</em> that matters, and the document
 * is one data component ({@link LicenseData}) that {@link MinistryLicences} reads on behalf of every
 * system that answers to paperwork. Apparition, restricted brooms, Ministry premises, controlled
 * ingredients and the goblins at the counter all ask the same class the same question, which is why
 * adding a sixth thing that needs papers costs one call and not a sixth idea of what "revoked" means.
 *
 * <h2>Right-click does one of two things</h2>
 * With an ink bottle in the other hand it is a pen: {@link LicenceUpgrade} tries to write the next
 * endorsement onto it. Otherwise it is a document and opens the licence screen. Ink in hand is an
 * unmistakable statement of intent, so the two never race each other for the same click.
 *
 * <h2>Never stacks</h2>
 * Each scroll names one wizard, one type and one rank. Two of them are two different documents and
 * merging them would have to throw one away.
 */
@NullMarked
public class MinistryLicenseScrollItem extends Item {

    public MinistryLicenseScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        ItemStack scroll = player.getItemInHand(hand);

        if (isPenInOtherHand(player, hand)) {
            LicenceUpgrade.Outcome outcome = LicenceUpgrade.attempt(serverPlayer, scroll);
            PlayerFeedback.actionBar(serverPlayer, outcome.message()
                    .copy().withStyle(outcome.upgraded() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            return outcome.upgraded() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }

        LicenceOpenS2CPayload.open(serverPlayer, hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        LicenseData data = MinistryLicences.read(stack);
        if (data == null) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.ministry_license_scroll.blank")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return;
        }

        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.ministry_license_scroll.type",
                data.type().displayName()).withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.ministry_license_scroll.rank",
                data.rank(), LicenseType.MAX_RANK).withStyle(ChatFormatting.GRAY));

        if (data.revoked()) {
            tooltipAdder.accept(Component.translatable("ministry.wizards_and_beasts.licence.revoked_banner")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            return;
        }
        if (!data.isPermanent()) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.ministry_license_scroll.expires")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        if (data.rank() < LicenseType.MAX_RANK) {
            var required = LicenceUpgrade.requirementFor(data.rank() + 1);
            if (required != null) {
                tooltipAdder.accept(Component.translatable(
                        "item.wizards_and_beasts.ministry_license_scroll.next_rank",
                        Component.translatable(data.type().examinedSubject().translationKey()),
                        Component.translatable(required.translationKey()))
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    /**
     * A licence in force still catches the light. A revoked one is a piece of paper — dropping the
     * enchantment glint is the tell you get without opening anything.
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        LicenseData data = MinistryLicences.read(stack);
        return data != null && !data.revoked() && !data.hasExpired(0L);
    }

    /**
     * Whether the hand that is <em>not</em> holding the scroll is holding ink.
     *
     * <p>Checked against the opposite hand specifically, rather than the inventory, so picking up the
     * scroll with ink in your pack never turns a click meant to read the document into a click that
     * spends parchment. {@link LicenceUpgrade} still takes the parchment from anywhere.
     */
    private static boolean isPenInOtherHand(Player player, InteractionHand hand) {
        ItemStack other = hand == InteractionHand.MAIN_HAND
                ? player.getOffhandItem()
                : player.getMainHandItem();
        return other.is(MiscItemRegistry.INK_BOTTLE.get());
    }

    /** Convenience for commands and loot: a fresh rank-0 licence made out to {@code holder}. */
    public static ItemStack issue(ItemStack scroll, LicenseType type, java.util.UUID holder) {
        scroll.set(at.koopro.wizardsandbeasts.registry.ModDataComponents.LICENSE_DATA.get(),
                LicenseData.issue(type, holder));
        return scroll;
    }
}
