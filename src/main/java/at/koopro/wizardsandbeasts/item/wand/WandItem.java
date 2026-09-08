package at.koopro.wizardsandbeasts.item.wand;

import at.koopro.wizardsandbeasts.item.GeoItemBase;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.WandLoreNames;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import at.koopro.wizardsandbeasts.spell.cast.WandCastSessions;
import at.koopro.wizardsandbeasts.spell.cast.WandCastTiming;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic;
import at.koopro.wizardsandbeasts.util.ClientClassBridge;
import at.koopro.wizardsandbeasts.wand.WandCastLines;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;

public class WandItem extends GeoItemBase {

    /**
     * How long a single wand hold may run before vanilla force-releases it. Named because
     * {@link at.koopro.wizardsandbeasts.spell.cast.WandCastSessions} bounds a cast session by exactly
     * this, and the two must not be able to drift apart.
     */
    public static final int USE_DURATION_TICKS = 72000;

    public WandItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = ClientClassBridge.instantiate(
                            "at.koopro.wizardsandbeasts.client.wand.WandRenderer",
                            GeoItemRenderer.class,
                            new Class<?>[0],
                            new Object[0]);
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<WandItem>("wand_controller", 0, state -> PlayState.STOP));
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return 1;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!WandModuleHooks.isWandsEnabled()) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            Optional<UUID> master = WandComponents.getMaster(stack);
            if (master.isEmpty()) {
                float score = WandResonanceSystem.computeResonance(player, stack, level.registryAccess());
                WandResonanceSystem.applyResonance(player, stack, score, level.registryAccess());
                // Falls through to startUsingItem rather than returning here. The client runs this same
                // method with the server branch skipped, so an early return made the client believe it
                // was holding a wand the server did not think was in use at all — the exact client/server
                // divergence the cast session exists to rule out. Holding an unbonded wand is harmless:
                // the beam tick refuses it (isWandBondedTo) and the release still reports WAND_NOT_BONDED.
            } else if (!master.get().equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("wandcraft.resonance.notYourWand"), true);
            }
        }
        player.startUsingItem(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            // The hold has begun on the server. This is the only place a cast session is opened, so a
            // release packet that does not correspond to a hold the server itself saw start has nothing
            // to land on.
            WandCastSessions.begin(sp, WandCastSessions.gameTickOf(sp));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        super.onUseTick(level, entity, stack, remainingUseDuration);
        if (!level.isClientSide() && entity instanceof ServerPlayer sp) {
            WandBeamChannelLogic.tick(sp, stack);
        }
    }

    /**
     * Every way a hold can end <em>without</em> a release, funnelled to the same teardown.
     *
     * <p>{@code releaseUsing} is not the only exit from a wand hold, and it used to be the only one
     * wired up. Vanilla's {@code LivingEntity.updatingUsingItem} compares the stack in hand against
     * the one being used and, when they differ, calls {@code stopUsingItem()} — which never reaches
     * {@code releaseUsing}. Switching hotbar slot mid-channel therefore ended the use with the beam
     * session still open on the server: no {@code sendEnd} went out, and the client's beam entity
     * has no timeout of its own, so the beam hung in the world until something else happened to
     * clear it. Dropping the wand or having it moved out of the hand by a hopper is the same path.
     *
     * <p>Only the beam channel is torn down here. The cast session is deliberately left alone: the
     * ordinary release also passes through {@code stopUsingItem()} (at the tail of
     * {@code releaseUsingItem()}), and the client's cast packet arrives <em>after</em> it, so
     * aborting the session here would refuse every cast with {@code NO_CAST_SESSION}. A hold that
     * ends this way leaves a session that no release will ever be offered to, and it expires on its
     * own at {@link WandCastSessions#MAX_SESSION_TICKS}.
     *
     * <p>Idempotent: {@code endChannel} returns immediately when there is no session, which is the
     * case on the normal path where {@code releaseUsing} has already run.
     */
    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (!entity.level().isClientSide() && entity instanceof ServerPlayer sp) {
            WandBeamChannelLogic.endChannel(sp);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide() && entity instanceof ServerPlayer sp) {
            int holdTicks = Math.max(0, getUseDuration(stack, entity) - timeLeft);
            WandCastTiming.recordRelease(sp, holdTicks);
            WandBeamChannelLogic.endChannel(sp);
        }
        if (level.isClientSide()) {
            ClientClassBridge.callStaticBoolean(
                    "at.koopro.wizardsandbeasts.client.wand.WandCastClient",
                    "castOrOpenImperioMenu");
        }
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return WandComponents.getMaster(stack).isPresent();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag flag) {
        Identifier wood = WandComponents.getWood(stack);
        Identifier core = WandComponents.getCore(stack);
        WandFlexibility flexibility = WandComponents.getFlexibility(stack);
        Optional<UUID> master = WandComponents.getMaster(stack);

        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.wood",
                WandLoreNames.wood(context.registries(), wood)).withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.core",
                WandLoreNames.core(context.registries(), core)).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.flexibility",
                flexibility == null ? "?" : flexibility.getSerializedName()).withStyle(ChatFormatting.GRAY));
        if (master.isPresent()) {
            // A raw UUID told a player nothing and looked like a bug. The identity that matters in
            // play is "is this mine"; the UUID stays available under advanced tooltips for anyone
            // debugging a transfer.
            tooltipAdder.accept(Component.translatable("wandcraft.tooltip.master_other")
                    .withStyle(ChatFormatting.AQUA));
            if (flag.isAdvanced()) {
                tooltipAdder.accept(Component.translatable("wandcraft.tooltip.master", master.get().toString())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.integrity", WandComponents.getIntegrity(stack))
                .withStyle(ChatFormatting.GREEN));
        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.corruption", WandComponents.getCorruption(stack))
                .withStyle(ChatFormatting.DARK_RED));
        Float len = WandComponents.getLength(stack);
        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.length_in", len == null ? 0.0f : len)
                .withStyle(ChatFormatting.BLUE));
        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.allegiance", WandComponents.getAllegianceScore(stack))
                .withStyle(ChatFormatting.DARK_GREEN));

        // What the wand is worth, under what it is. Resolved through the same call the cast path makes,
        // so the stated contribution cannot drift from the applied one. Silent when the wand contributes
        // nothing, and silent when the registries are unavailable — resolve() answers NEUTRAL rather
        // than throwing, and a neutral set produces no lines.
        WandCastLines.append(WandStatsResolver.resolve(stack, context.registries()), tooltipAdder);
    }

    /**
     * A wand from the four enums, for the paths that still speak them — {@code /wandb wand give}, the
     * dev kit and the game-test fixtures.
     *
     * <p>Note which name each component is written with. These are the <em>modern</em> id components,
     * so the core has to be its {@link WandCore#getDefinitionPath()} and not the
     * {@code getSerializedName()} that backs the legacy enum component. The two differ for Thestral,
     * and taking the serialized form here wrote {@code wizards_and_beasts:thestral_tail} — an id no
     * {@code wand_cores} definition has. Nothing failed: the wand was built, named and bonded
     * correctly, and simply contributed no core modifier to any cast. Wood needs no such care; every
     * {@code WandWood} constant is spelled the same in both places, which {@code WandIdParityTest}
     * holds it to.
     */
    public static ItemStack createWand(WandWood wood, WandCore core, WandLength length, WandFlexibility flexibility) {
        ItemStack stack = new ItemStack(WandItemRegistry.WAND.get());
        if (wood != null) {
            stack.set(WandComponents.WAND_WOOD.get(), Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, wood.getSerializedName()));
        }
        if (core != null) {
            stack.set(WandComponents.WAND_CORE.get(), Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, core.getDefinitionPath()));
        }
        if (flexibility != null) {
            stack.set(WandComponents.WAND_FLEXIBILITY.get(), flexibility);
        }
        if (length != null) {
            float inches = 13.0f;
            switch (length) {
                case SHORT:
                    inches = 9.0f;
                    break;
                case MEDIUM:
                    inches = 11.0f;
                    break;
                case LONG:
                    inches = 15.0f;
                    break;
                case STANDARD:
                default:
                    inches = 13.0f;
                    break;
            }
            stack.set(WandComponents.WAND_LENGTH.get(), inches);
        }
        ModDataComponents.refreshElderWandMarker(stack);
        return stack;
    }
}
