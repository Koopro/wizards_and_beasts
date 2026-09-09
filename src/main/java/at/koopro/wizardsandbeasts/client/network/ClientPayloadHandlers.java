package at.koopro.wizardsandbeasts.client.network;

import at.koopro.wizardsandbeasts.azkaban.attachment.AzkabanTrespasserData;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity;
import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilityCache;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionWardState;
import at.koopro.wizardsandbeasts.client.bestiary.ClientBestiaryCache;
import at.koopro.wizardsandbeasts.client.currency.state.ClientVaultDataState;
import at.koopro.wizardsandbeasts.client.form.SizeLerpTracker;
import at.koopro.wizardsandbeasts.client.form.state.ClientFormDataState;
import at.koopro.wizardsandbeasts.client.form.state.ClientTransitionTracker;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientBloodState;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.legilimency.state.ClientLegilimencyVisionState;
import at.koopro.wizardsandbeasts.client.map.MapClientHandler;
import at.koopro.wizardsandbeasts.client.ministry.state.ClientMinistryRecordState;
import at.koopro.wizardsandbeasts.client.standing.state.ClientStandingState;
import at.koopro.wizardsandbeasts.client.owl.ClientOWLCache;
import at.koopro.wizardsandbeasts.client.petrify.state.ClientPetrifyState;
import at.koopro.wizardsandbeasts.client.pose.ClientPoseState;
import at.koopro.wizardsandbeasts.network.petrify.PetrifiedStateSyncS2CPayload;
import at.koopro.wizardsandbeasts.client.skill.state.ClientSkillBonusCache;
import at.koopro.wizardsandbeasts.client.skill.state.ClientSkillDataState;
import at.koopro.wizardsandbeasts.client.skill.state.ClientVocationCache;
import at.koopro.wizardsandbeasts.client.stats.ClientStatsState;
import at.koopro.wizardsandbeasts.entity.niffler.CarriedNifflerAttachment;
import at.koopro.wizardsandbeasts.form.RenderFlag;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.TransformationState;
import at.koopro.wizardsandbeasts.item.bloodpact.BloodPactVialItem;
import at.koopro.wizardsandbeasts.network.ClientScreenHooksInvoker;
import at.koopro.wizardsandbeasts.network.ability.AbilityDataSyncPayload;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionWardsSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.azkaban.AzkabanTrespasserSyncPayload;
import at.koopro.wizardsandbeasts.network.ministry.MinistryRecordSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.standing.StandingSyncS2CPayload;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import at.koopro.wizardsandbeasts.network.bestiary.BestiaryDataSyncPayload;
import at.koopro.wizardsandbeasts.network.bestiary.SyncBestiaryEntriesPayload;
import at.koopro.wizardsandbeasts.network.bestiary.niffler.NifflerCarrySyncS2CPayload;
import at.koopro.wizardsandbeasts.network.handbook.SyncHandbookPayload;
import at.koopro.wizardsandbeasts.handbook.HandbookChapterManager;
import at.koopro.wizardsandbeasts.network.bloodpact.SBreakPactPayload;
import at.koopro.wizardsandbeasts.network.bloodpact.SUpdateVialPayload;
import at.koopro.wizardsandbeasts.network.currency.GringottsOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.currency.VaultSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.floo.FlooArrivalEffectsS2CPayload;
import at.koopro.wizardsandbeasts.network.floo.FlooBlockSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.floo.OpenFlooGuiS2CPayload;
import at.koopro.wizardsandbeasts.network.form.DebugOverlayToggleS2CPayload;
import at.koopro.wizardsandbeasts.network.form.FormSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.form.TransitionEndS2CPayload;
import at.koopro.wizardsandbeasts.network.form.TransitionStartS2CPayload;
import at.koopro.wizardsandbeasts.network.heritage.BloodDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.legilimency.LegilimencyVisionS2CPayload;
import at.koopro.wizardsandbeasts.network.map.MapOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.map.MapSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.owl.OWLDataSyncPayload;
import at.koopro.wizardsandbeasts.network.owl.ProfessionSyncPayload;
import at.koopro.wizardsandbeasts.network.skill.SkillBonusSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.VocationDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import at.koopro.wizardsandbeasts.network.trunk.PocketStatusS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import at.koopro.wizardsandbeasts.network.pose.PoseOverrideSyncS2CPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.client.gui.toast.WizardsToasts;

/**
 * Client-side S2C payload handlers.
 *
 * <p>Method bodies here may reference client-only classes ({@code net.minecraft.client.*},
 * {@code at.koopro.wizardsandbeasts.client.*}). Common-side network registrars reference these
 * handlers via method references only — the NeoForge-documented dist-safe pattern: client class
 * references live exclusively inside method bodies of a class that is never loaded on the
 * dedicated server.</p>
 */
@NullMarked
public final class ClientPayloadHandlers {

    private ClientPayloadHandlers() {
    }

    // --- ability ---

    public static void handleAbilityDataSync(AbilityDataSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientAbilityCache.set(payload.data()));
    }

    // --- apparition ---

    public static void handleApparitionWardsSync(ApparitionWardsSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            List<ClientApparitionWardState.WardBox> mapped = payload.wards().stream()
                    .map(w -> new ClientApparitionWardState.WardBox(w.dimensionId(), w.bounds(), w.message()))
                    .toList();
            ClientApparitionWardState.setAll(mapped);
        });
    }

    // --- azkaban ---

    public static void handleAzkabanTrespasserSync(AzkabanTrespasserSyncPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            player.setData(ModAttachments.AZKABAN_TRESPASSER_TAG.get(),
                    new AzkabanTrespasserData(pkt.tagged()));
        });
    }

    // --- ministry ---

    public static void handleMinistryRecordSync(MinistryRecordSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientMinistryRecordState.set(pkt.record(), pkt.traceActive(), pkt.finesActive()));
    }

    // --- standing ---

    public static void handleStandingSync(StandingSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientStandingState.set(pkt.values(), pkt.bands(), pkt.bound()));
    }

    // --- bestiary ---

    public static void handleBestiaryDataSync(BestiaryDataSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBestiaryCache.set(payload.data()));
    }

    public static void handleSyncBestiaryEntries(SyncBestiaryEntriesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BestiaryEntryRegistry.setClientEntries(payload.entries()));
    }

    public static void handleHeritageIdentitySync(
            at.koopro.wizardsandbeasts.network.heritage.HeritageIdentitySyncS2CPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() ->
                at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageIdentityState.update(
                        payload.playerUUID(), payload.heritageId(), payload.variantId(),
                        payload.transformationState()));
    }

    public static void handleSyncHeritageAppearance(
            at.koopro.wizardsandbeasts.network.heritage.SyncHeritageAppearancePayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> at.koopro.wizardsandbeasts.heritage.appearance.HeritageAppearanceRegistry
                .setClientEntries(payload.entries()));
    }

    public static void handleSyncAnimagusForms(
            at.koopro.wizardsandbeasts.network.animagus.SyncAnimagusFormsPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                at.koopro.wizardsandbeasts.animagus.AnimagusFormRegistry.setClientForms(payload.forms()));
    }

    public static void handleSyncSkillDefinitions(
            at.koopro.wizardsandbeasts.network.skill.SyncSkillDefinitionsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> at.koopro.wizardsandbeasts.skill.SkillTrees.setClientDefinitions(payload.skills()));
    }

    public static void handleSyncHandbook(SyncHandbookPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HandbookChapterManager.setClientChapters(payload.chapters()));
    }

    public static void handleNifflerCarrySync(NifflerCarrySyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.setData(ModAttachments.CARRIED_NIFFLER.get(),
                        new CarriedNifflerAttachment(payload.nifflerUUID()));
            }
        });
    }

    // --- blood pact ---

    public static void handleSBreakPact(SBreakPactPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            WizardsToasts.show(NoticeKind.WARN,
                    Component.translatable("bloodpact.wizards_and_beasts.broken.title"),
                    Component.translatable("bloodpact.wizards_and_beasts.broken.body"));
            mc.level.playLocalSound(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    ModSounds.BLOOD_PACT_SHATTER.get(), SoundSource.PLAYERS, 1.2f, 0.8f, false);
        });
    }

    public static void handleSUpdateVial(SUpdateVialPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ItemStack mainHand = mc.player.getMainHandItem();
            if (mainHand.getItem() instanceof BloodPactVialItem) {
                mainHand.set(ModDataComponents.PACT_UUID.get(), Optional.of(pkt.pactUUID()));
                mainHand.set(ModDataComponents.PACT_PARTNER_UUID.get(), Optional.of(pkt.partnerUUID()));
            }
        });
    }

    // --- currency ---

    public static void handleGringottsOpen(GringottsOpenS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientVaultDataState.load(pkt.knuts(), pkt.sickles(), pkt.galleons());
            openGringottsScreenSafe();
        });
    }

    public static void handleDragotQuote(at.koopro.wizardsandbeasts.network.currency.DragotQuoteS2CPayload pkt,
                                         IPayloadContext ctx) {
        ctx.enqueueWork(() -> at.koopro.wizardsandbeasts.client.currency.state.ClientDragotQuoteState
                .load(pkt.rate(), pkt.drift(), pkt.purse()));
    }

    private static void openGringottsScreenSafe() {
        ClientScreenHooksInvoker.invoke("openGringottsScreen");
    }

    public static void handleVaultSync(VaultSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientVaultDataState.load(pkt.syncVersion(), pkt.knuts(), pkt.sickles(), pkt.galleons()));
    }

    // --- floo ---

    public static void handleFlooArrivalEffects(FlooArrivalEffectsS2CPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            double cx = packet.pos().getX() + 0.5;
            double cy = packet.pos().getY() + 0.5;
            double cz = packet.pos().getZ() + 0.5;
            mc.level.playLocalSound(packet.pos(), ModSounds.FLOO_WHOOSH.get(), SoundSource.BLOCKS, 0.9f, 1.0f, false);
            for (int i = 0; i < 24; i++) {
                double ox = (mc.level.random.nextDouble() - 0.5) * 0.8;
                double oz = (mc.level.random.nextDouble() - 0.5) * 0.8;
                mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, cx + ox, cy, cz + oz, 0, 0.05, 0);
            }
        });
    }

    public static void handleFlooTransit(at.koopro.wizardsandbeasts.network.floo.FlooTransitS2CPayload packet,
                                         IPayloadContext context) {
        context.enqueueWork(() ->
                at.koopro.wizardsandbeasts.client.floo.FlooTransitOverlay.trigger(
                        packet.durationTicks(), packet.sootTicks()));
    }

    public static void handleFlooBlockSync(FlooBlockSyncS2CPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            if (mc.level.getBlockEntity(packet.pos()) instanceof FlooFireplaceBlockEntity be) {
                be.setLitTicksRemaining(packet.litTicksRemaining());
            }
            BlockState state = mc.level.getBlockState(packet.pos());
            if (state.hasProperty(FlooFireplaceBlock.LIT) && state.getValue(FlooFireplaceBlock.LIT) != packet.isLit()) {
                mc.level.setBlock(packet.pos(), state.setValue(FlooFireplaceBlock.LIT, packet.isLit()), 11);
            }
        });
    }

    public static void handleOpenFlooRegistration(
            at.koopro.wizardsandbeasts.network.floo.OpenFlooRegistrationS2CPayload packet,
            IPayloadContext context) {
        // Same reflective route as the network screen, for the same reason: the client-only screen
        // type must not load during server-side verification.
        context.enqueueWork(() ->
                ClientScreenHooksInvoker.invoke("openFlooRegistrationScreen",
                        net.minecraft.core.BlockPos.class, packet.hearthPos(),
                        String.class, packet.currentAddress(),
                        int.class, packet.feeKnuts()));
    }

    /**
     * Who a player now looks like, or that they look like themselves again.
     *
     * <p>An empty target UUID is the revert, so a set and a clear are one packet shape and cannot
     * arrive out of order relative to each other.
     */
    public static void handlePolyjuiceSync(
            at.koopro.wizardsandbeasts.network.polyjuice.PolyjuiceSyncS2CPayload packet) {
        if (packet.targetId().equals(
                at.koopro.wizardsandbeasts.network.polyjuice.PolyjuiceSyncS2CPayload.NONE)
                || packet.targetName().isBlank()) {
            at.koopro.wizardsandbeasts.client.polyjuice.ClientPolyjuiceState.set(packet.playerUUID(), null);
            return;
        }
        at.koopro.wizardsandbeasts.client.polyjuice.ClientPolyjuiceState.set(packet.playerUUID(),
                new at.koopro.wizardsandbeasts.client.polyjuice.ClientPolyjuiceState.Disguise(
                        packet.targetId(), packet.targetName()));
    }

    public static void handleOpenFlooGui(OpenFlooGuiS2CPayload packet, IPayloadContext context) {
        // Routed through the reflection invoker (not a direct `new FlooNetworkScreen`) so this
        // class — loaded server-side when the registrar resolves the method ref — never forces
        // verification-time loading of the client-only FlooNetworkScreen / Screen types.
        context.enqueueWork(() ->
                ClientScreenHooksInvoker.invoke("openFlooNetworkScreen",
                        List.class, packet.destinations(),
                        String.class, packet.originAddress(),
                        boolean.class, packet.headInFire()));
    }

    // --- form ---

    public static void handleDebugOverlayToggle(DebugOverlayToggleS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientFormDataState.setDebugOverlay(pkt.enabled());
        });
    }

    public static void handleFormSync(FormSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            SizeProfile profile = new SizeProfile(
                    pkt.formId(),
                    pkt.hitboxWidth(), pkt.hitboxHeight(),
                    pkt.modelScale(), pkt.modelAspectX(), pkt.modelAspectZ(),
                    pkt.reachBonus(), pkt.knockbackResistance(), pkt.stepHeight());
            ClientFormDataState.update(pkt.playerUUID(), pkt.formId(), profile,
                    RenderFlag.fromBitmask(pkt.renderFlagMask()));
            SizeLerpTracker.onScaleChanged(pkt.playerUUID(), pkt.modelScale());
        });
    }

    /**
     * A player's flight attitude changed: store it, then recompute their collision box.
     *
     * <p>The refresh is the point. {@code FlightHitboxHandler} flattens the box while a player is
     * drawn lying flat, but a box is only recomputed when something calls
     * {@code refreshDimensions()} — the server does it in {@code PoseOverrideService.set} and this
     * is the client half. Skipping it leaves the local player colliding as a standing 1.8-tall
     * column while the server has them at 0.6, so the client refuses moves the server would have
     * allowed and the player rubber-bands off ceilings they actually fit under.
     */
    public static void handlePoseOverrideSync(PoseOverrideSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientPoseState.apply(pkt.playerUuid(), pkt.override());
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                Player player = level.getPlayerByUUID(pkt.playerUuid());
                if (player != null) {
                    player.refreshDimensions();
                }
            }
        });
    }

    public static void handlePetrifiedStateSync(PetrifiedStateSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPetrifyState.set(pkt.playerUUID(), pkt.isPetrified()));
    }

    public static void handleTransitionEnd(TransitionEndS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientTransitionTracker.endTransition(pkt.playerUUID());
        });
    }

    public static void handleTransitionStart(TransitionStartS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientTransitionTracker.startTransition(
                    pkt.playerUUID(), pkt.fromFormId(), pkt.toFormId(),
                    pkt.durationTicks(), pkt.screenEffectOrdinal());
        });
    }

    // --- heritage ---

    public static void handleHeritageDataSync(HeritageDataSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Heritage heritage = resolveHeritage(pkt.heritageId());
            HeritageVariant variant = resolveVariant(pkt.variantId());
            TransformationState state = resolveState(pkt.transformationState());
            String activeForm = pkt.activeFormId() == null || pkt.activeFormId().isBlank() ? null : pkt.activeFormId();
            String selectedProfessionId = pkt.selectedProfessionId() == null || pkt.selectedProfessionId().isBlank()
                    ? null : pkt.selectedProfessionId();
            ClientHeritageDataState.applySync(pkt.syncVersion(), heritage, variant, pkt.locked(), state, activeForm, pkt.debugOverlay(), pkt.customFlags(),
                    pkt.professionPoints(), pkt.totalProfessionPointsEarned(), pkt.unlockedProfessions(), selectedProfessionId);
            if (pkt.openSelector()) {
                openHeritageSelectionScreenSafe();
            }
        });
    }

    /**
     * The blood pool and the policy that owns the hunger slot.
     *
     * <p>Applied unconditionally rather than being gated on "is this player a vampire" here: the payload
     * carries the policy, so a vampire who loses their heritage is corrected by the same packet that a
     * vampire who gains one is set up by. A client that decided for itself would have no packet at all to
     * tell it the bar had gone away.
     */
    public static void handleBloodDataSync(BloodDataSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientBloodState.applySync(
                pkt.policy(), pkt.stage(), pkt.blood(), pkt.maxBlood()));
    }

    @Nullable
    private static Heritage resolveHeritage(@Nullable String heritageId) {
        return heritageId == null || heritageId.isBlank() ? null : Heritage.byId(heritageId);
    }

    @Nullable
    private static HeritageVariant resolveVariant(@Nullable String variantId) {
        return variantId == null || variantId.isBlank() ? null : HeritageVariant.byId(variantId);
    }

    private static TransformationState resolveState(@Nullable String state) {
        if (state == null || state.isBlank()) {
            return TransformationState.NORMAL;
        }
        try {
            return TransformationState.valueOf(state);
        } catch (IllegalArgumentException ignored) {
            return TransformationState.NORMAL;
        }
    }

    private static void openHeritageSelectionScreenSafe() {
        ClientScreenHooksInvoker.invoke("openHeritageSelectionScreen");
    }

    // --- legilimency ---

    public static void handleLegilimencyVision(LegilimencyVisionS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientLegilimencyVisionState.set(payload.markerPos(), payload.durationTicks()));
    }

    // --- map ---

    public static void handleMapOpen(MapOpenS2CPayload pkt, IPayloadContext ctx) {
        MapClientHandler.handleMapOpen(pkt, ctx);
    }

    public static void handleMapSync(MapSyncS2CPayload pkt, IPayloadContext ctx) {
        MapClientHandler.handleMapSync(pkt, ctx);
    }

    public static void handleMapRegion(
            at.koopro.wizardsandbeasts.network.map.MapRegionS2CPayload pkt, IPayloadContext ctx) {
        MapClientHandler.handleMapRegion(pkt, ctx);
    }

    public static void handleMapMarkers(
            at.koopro.wizardsandbeasts.network.map.MapMarkersS2CPayload pkt, IPayloadContext ctx) {
        MapClientHandler.handleMapMarkers(pkt, ctx);
    }

    // --- owl ---

    public static void handleOWLDataSync(OWLDataSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientOWLCache.setOWLData(payload.grades(), payload.examTaken()));
    }

    public static void handleProfessionSync(ProfessionSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientOWLCache.setProfession(payload.profession()));
    }

    // --- skill ---

    public static void handleSkillBonusSync(SkillBonusSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSkillBonusCache.set(payload.bonusData()));
    }

    public static void handleSkillDataSync(SkillDataSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSkillDataState.applySync(
                pkt.syncVersion(), pkt.skillPoints(), pkt.totalPointsEarned(), pkt.unlockedSkills()));
    }

    public static void handleVocationDataSync(VocationDataSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientVocationCache.applySync(pkt.syncVersion(), pkt.primary()));
    }

    public static void handleSyncVocationDefinitions(
            at.koopro.wizardsandbeasts.network.skill.SyncVocationDefinitionsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> at.koopro.wizardsandbeasts.skill.vocation.VocationRegistry
                .setClientDefinitions(payload.vocations()));
    }

    // --- stats ---

    public static void handlePlayerStatsSync(PlayerStatsSyncPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientStatsState.applySync(pkt.data()));
    }

    public static void handleStatLevelUp(
            at.koopro.wizardsandbeasts.network.stats.StatLevelUpS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            at.koopro.wizardsandbeasts.stats.PlayerStat stat =
                    at.koopro.wizardsandbeasts.stats.PlayerStat.fromId(pkt.statId());
            // An id this build does not have: skipped rather than rejected, matching how the stat
            // sync payload treats an unknown stat. A missing celebration beats a dropped connection.
            if (stat != null) {
                at.koopro.wizardsandbeasts.client.stats.ClientStatLevelUps
                        .onLevelUp(stat, pkt.from(), pkt.to(), pkt.sourceKey());
            }
        });
    }

    // --- trinket ---

    public static void handlePensieveOpen(at.koopro.wizardsandbeasts.network.trinket.PensieveOpenS2CPayload pkt,
                                          IPayloadContext ctx) {
        // Routed through the reflection invoker so this common-side class never forces verification-time
        // loading of the client-only PensieveScreen.
        ctx.enqueueWork(() ->
                ClientScreenHooksInvoker.invoke("openPensieveScreen", List.class, pkt.memories()));
    }

    public static void handleLicenceOpen(at.koopro.wizardsandbeasts.network.ministry.LicenceOpenS2CPayload pkt,
                                         IPayloadContext ctx) {
        // Reflection invoker, like the Pensieve: keeps this common-side class from forcing
        // verification-time loading of the client-only MinistryLicenceScreen.
        ctx.enqueueWork(() ->
                ClientScreenHooksInvoker.invoke("openLicenceScreen", boolean.class, pkt.offHand()));
    }

    public static void handleMirrorOpen(at.koopro.wizardsandbeasts.network.trinket.MirrorOpenS2CPayload pkt,
                                        IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientScreenHooksInvoker.invoke("openMirrorViewScreen",
                java.util.UUID.class, pkt.otherUuid(), String.class, pkt.otherName()));
    }

    public static void handleMirrorPresence(at.koopro.wizardsandbeasts.network.trinket.MirrorPresenceS2CPayload pkt,
                                            IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientScreenHooksInvoker.invoke("updateMirrorPresence",
                float.class, pkt.yaw(), float.class, pkt.pitch()));
    }

    public static void handleMirrorClose(at.koopro.wizardsandbeasts.network.trinket.MirrorCloseS2CPayload pkt,
                                         IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientScreenHooksInvoker.invoke("closeMirror", String.class, pkt.reason()));
    }

    public static void handleDiaryReply(at.koopro.wizardsandbeasts.network.trinket.DiaryReplyS2CPayload pkt,
                                        IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (pkt.possess()) {
                ClientScreenHooksInvoker.invoke("openDiaryPossession", String.class, pkt.line());
            } else {
                ClientScreenHooksInvoker.invoke("appendDiaryReply", String.class, pkt.line(), int.class, pkt.tier());
            }
        });
    }

    // --- trunk ---

    public static void handlePocketStatus(PocketStatusS2CPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(packet.message()), true);
            }
        });
    }
}
