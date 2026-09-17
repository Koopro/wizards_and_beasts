package at.koopro.wizardsandbeasts.wand.allegiance;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.entity.beast.ThestralEntity;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.wand.elder.ElderWandSavedData;
import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.registry.WandTemperament;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The wand–wizard relationship, applied to the world. Decisions live in {@link WandAllegianceRules}; this
 * class reads stacks, players and the Elder Wand's saved master, and writes the result back.
 *
 * <p><b>One model.</b> A wand answers to {@code WAND_MASTER}, with a bond of {@code WAND_ALLEGIANCE_SCORE},
 * and remembers the rest in {@code WAND_BOND_HISTORY}. The older {@code wand_allegiance_legacy} record and the
 * player-side {@code bonded_wand} attachment are no longer written: both had drifted from the master the cast
 * path actually checks — a wand "transferred" by them still refused its new owner and penalised its old one.
 *
 * <p><b>Winning a wand</b> (<i>Deathly Hallows</i> ch. 24): defeating its master — disarming, stunning or
 * killing them while they hold it — counts a win for the victor. Enough wins in a row, and it is theirs. The
 * master's next successful cast wipes the tally. Picking a wand up, however it came to be lying there, wins
 * nothing: it works for you, worse, and stays another's.
 *
 * <p><b>The Elder Wand</b> is the exception the books make: its allegiance follows the <em>first</em> defeat of
 * its master, whatever wand was involved and wherever the Elder Wand itself is (Draco disarmed Dumbledore of it
 * without touching it). {@link ElderWandSavedData} holds its master; stacks are reconciled to it. Nobody
 * becomes its master by making or finding it — a masterless Elder Wand accepts the wizard who defeats another
 * while wielding it.
 */
public final class WandAllegianceService {

    /** How a master was defeated. All three count the same; the kind is for the caller's clarity. */
    public enum DefeatKind { DISARM, STUN, KILL }

    /** Explosion damage below this does not mark a held wand. */
    private static final float EXPLOSION_DAMAGE_FLOOR = 2.0f;

    /** Magic damage a backfire deals its caster. Enough to notice, not enough to kill. */
    private static final float BACKFIRE_DAMAGE = 2.0f;

    private WandAllegianceService() {}

    // ── reading ─────────────────────────────────────────────────────────────────────────────────

    public static boolean isElderWand(ItemStack wand) {
        return ModDataComponents.isElderWand(wand);
    }

    /**
     * Whether a wand can be cast with at all. An ordinary wand that has chosen nobody has to choose first
     * ({@code WandResonanceSystem}); a wand with a master works for anyone, badly for strangers; the Elder Wand
     * works — poorly — even with no master.
     */
    public static boolean answersToSomeone(ItemStack wand) {
        return WandComponents.getMaster(wand).isPresent() || isElderWand(wand);
    }

    public static WandBondState stateFor(UUID holder, ItemStack wand) {
        Optional<UUID> master = WandComponents.getMaster(wand);
        boolean isMaster = master.isPresent() && master.get().equals(holder);
        return WandAllegianceRules.state(isMaster, WandComponents.getAllegianceScore(wand));
    }

    /** The wand serves a master other than the first wizard it chose. */
    public static boolean passedOn(ItemStack wand) {
        UUID first = WandComponents.getBondHistory(wand).firstMaster();
        Optional<UUID> master = WandComponents.getMaster(wand);
        return first != null && master.isPresent() && !master.get().equals(first);
    }

    /** The wand's temperament: its wood's and its core's combined. Neutral without registries. */
    public static WandTemperament temperamentOf(ItemStack wand, HolderLookup.@Nullable Provider registries) {
        if (registries == null) {
            return WandTemperament.NEUTRAL;
        }
        WandTemperament wood = definitionTemperament(registries, WandDatapackRegistries.WAND_WOOD_REGISTRY,
                WandStatsResolver.resolveWoodId(wand), WandWoodDefinition::temperament);
        WandTemperament core = definitionTemperament(registries, WandDatapackRegistries.WAND_CORE_REGISTRY,
                WandStatsResolver.resolveCoreId(wand), WandCoreDefinition::temperament);
        return wood.combine(core);
    }

    private static <T> WandTemperament definitionTemperament(HolderLookup.Provider registries,
                                                             ResourceKey<net.minecraft.core.Registry<T>> registry,
                                                             @Nullable Identifier id,
                                                             java.util.function.Function<T, WandTemperament> read) {
        if (id == null) {
            return WandTemperament.NEUTRAL;
        }
        return registries.lookup(registry)
                .flatMap(lookup -> lookup.get(ResourceKey.create(registry, id)))
                .map(holder -> read.apply(holder.value()))
                .orElse(WandTemperament.NEUTRAL);
    }

    // ── the cast ────────────────────────────────────────────────────────────────────────────────

    public static float powerMultiplier(Player caster, ItemStack wand, HolderLookup.@Nullable Provider registries) {
        return WandAllegianceRules.powerMultiplier(stateFor(caster.getUUID(), wand), temperamentOf(wand, registries),
                passedOn(wand), isElderWand(wand));
    }

    public static float cooldownMultiplier(Player caster, ItemStack wand, HolderLookup.@Nullable Provider registries) {
        return WandAllegianceRules.cooldownMultiplier(stateFor(caster.getUUID(), wand), temperamentOf(wand, registries));
    }

    public static boolean wouldBackfire(Player caster, ItemStack wand, HolderLookup.@Nullable Provider registries) {
        return WandAllegianceRules.backfires(stateFor(caster.getUUID(), wand), temperamentOf(wand, registries),
                WandComponents.getIntegrity(wand));
    }

    /**
     * The spell turns on its caster instead of casting. Said out loud, with the reason: a broken wand, or a wand
     * that will not work for a stranger.
     */
    public static void backfire(ServerPlayer caster, ItemStack wand) {
        ServerLevel level = caster.level();
        caster.hurtServer(level, level.damageSources().magic(), BACKFIRE_DAMAGE);
        level.playSound(null, caster.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5f, 0.7f);
        level.sendParticles(ParticleTypes.SMOKE, caster.getX(), caster.getEyeY() - 0.3, caster.getZ(),
                10, 0.2, 0.15, 0.2, 0.01);
        String key = WandComponents.getIntegrity(wand) <= WandAllegianceRules.BROKEN_AT
                ? "wandcraft.wand.backfire.broken" : "wandcraft.wand.backfire.foreign";
        PlayerFeedback.actionBar(caster, Component.translatable(key));
    }

    // ── the bond ────────────────────────────────────────────────────────────────────────────────

    /** A wand has chosen this wizard ({@code WandResonanceSystem}): the start of its first bond with them. */
    public static void chooseWizard(ServerPlayer wizard, ItemStack wand, float resonanceScore, float matchThreshold) {
        long now = wizard.level().getGameTime();
        wand.set(WandComponents.WAND_MASTER.get(), Optional.of(wizard.getUUID()));
        wand.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(),
                WandAllegianceRules.startingBond(resonanceScore, matchThreshold));
        wand.set(WandComponents.WAND_BOND_HISTORY.get(),
                WandComponents.getBondHistory(wand).withFirstMasterIfAbsent(wizard.getUUID()).withMasterCast(now));
    }

    /**
     * A successful cast. Only the master's casts move the bond — a stranger using a wand, however long, earns
     * nothing from it — and the master's cast also answers any standing challenge.
     */
    public static void onSuccessfulCast(ServerPlayer caster, ItemStack wand, @Nullable Spell spell) {
        if (!Config.enableWandAllegiance || !(wand.getItem() instanceof WandItem)) {
            return;
        }
        Optional<UUID> master = WandComponents.getMaster(wand);
        if (master.isEmpty() || !master.get().equals(caster.getUUID())) {
            return;
        }
        long now = caster.level().getGameTime();
        WandBondHistory history = WandComponents.getBondHistory(wand);
        float before = WandComponents.getAllegianceScore(wand);
        float bond = history.lastMasterUseTick() > 0L
                ? WandAllegianceRules.bondAfterNeglect(before, now - history.lastMasterUseTick())
                : before;
        WandAllegianceRules.CastCircumstances circumstances = new WandAllegianceRules.CastCircumstances(
                spell != null && spell.getCategory() == SpellCategory.DARK_ARTS,
                // Danger, for a wand whose bond is forged in it: a gameplay reading of "passed through danger".
                at.koopro.wizardsandbeasts.util.ImmediateDanger.test(caster),
                PlayerAbilityHelper.hasAbilityFlag(caster, ThestralEntity.WITNESSED_DEATH_FLAG));
        float after = WandAllegianceRules.bondAfterSuccessfulCast(
                bond, temperamentOf(wand, caster.registryAccess()), circumstances);

        wand.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), after);
        wand.set(WandComponents.WAND_BOND_HISTORY.get(), history.withMasterCast(now));
        announceShift(caster, WandAllegianceRules.state(true, before), WandAllegianceRules.state(true, after));
    }

    /** A bond crossing into or out of loyalty is worth one quiet line; everything in between stays silent. */
    private static void announceShift(ServerPlayer caster, WandBondState before, WandBondState after) {
        if (before == after) {
            return;
        }
        ServerLevel level = caster.level();
        if (after.ordinal() > before.ordinal() && after.isLoyal()) {
            String key = after == WandBondState.MASTERED ? "wandcraft.bond.rise.mastered" : "wandcraft.bond.rise.loyal";
            PlayerFeedback.toast(caster, NoticeKind.UNLOCK,
                    Component.translatable("wandcraft.allegiance.title"), Component.translatable(key));
            level.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.2f);
            wandMotes(caster, ParticleTypes.ENCHANT);
        } else if (after.ordinal() < before.ordinal()) {
            PlayerFeedback.actionBar(caster, Component.translatable("wandcraft.bond.fall"));
            level.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.4f, 0.7f);
        }
    }

    // ── defeat ──────────────────────────────────────────────────────────────────────────────────

    /**
     * {@code victor} has defeated {@code victim}.
     *
     * @param contested the ordinary wands the defeat was over — the one knocked from their hand, or the ones they
     *                  held. The Elder Wand needs no stack: its allegiance moves wherever it is.
     */
    public static void onDefeat(ServerPlayer victim, ServerPlayer victor, DefeatKind kind, Collection<ItemStack> contested) {
        if (victim == victor || victim.getUUID().equals(victor.getUUID())) {
            return;
        }
        ServerLevel level = victor.level();
        settleElderWand(level, victim, victor, contested);

        if (!Config.enableWandAllegiance) {
            return;
        }
        for (ItemStack wand : contested) {
            if (!(wand.getItem() instanceof WandItem) || isElderWand(wand)) {
                continue;
            }
            Optional<UUID> master = WandComponents.getMaster(wand);
            if (master.isEmpty() || !master.get().equals(victim.getUUID())) {
                continue;
            }
            WandTemperament temperament = temperamentOf(wand, level.registryAccess());
            WandAllegianceRules.DefeatOutcome outcome = WandAllegianceRules.defeat(
                    WandComponents.getBondHistory(wand), victor.getUUID(),
                    WandAllegianceRules.winsToTransfer(temperament, false));
            if (outcome.transferred()) {
                transfer(wand, victim.getUUID(), victor, WandAllegianceRules.bondAfterTransfer(temperament));
                announceTransfer(victor, victim, "wandcraft.allegiance.transferred", "wandcraft.allegiance.lost");
            } else {
                wand.set(WandComponents.WAND_BOND_HISTORY.get(), outcome.history());
                PlayerFeedback.actionBar(victor, Component.translatable("wandcraft.allegiance.wavers"));
                PlayerFeedback.actionBar(victim, Component.translatable("wandcraft.allegiance.wavers_master"));
            }
        }
    }

    private static void transfer(ItemStack wand, @Nullable UUID formerMaster, ServerPlayer newMaster, float bond) {
        wand.set(WandComponents.WAND_MASTER.get(), Optional.of(newMaster.getUUID()));
        wand.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), bond);
        wand.set(WandComponents.WAND_BOND_HISTORY.get(), WandComponents.getBondHistory(wand)
                .withFirstMasterIfAbsent(formerMaster != null ? formerMaster : newMaster.getUUID())
                .afterTransferFrom(formerMaster));
    }

    private static void announceTransfer(ServerPlayer victor, @Nullable ServerPlayer victim, String victorKey, String victimKey) {
        PlayerFeedback.toast(victor, NoticeKind.UNLOCK,
                Component.translatable("wandcraft.allegiance.title"), Component.translatable(victorKey));
        victor.level().playSound(null, victor.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7f, 0.8f);
        wandMotes(victor, ParticleTypes.ENCHANT);
        if (victim != null) {
            PlayerFeedback.toast(victim, NoticeKind.WARN,
                    Component.translatable("wandcraft.allegiance.title"), Component.translatable(victimKey));
        }
    }

    // ── the Elder Wand ──────────────────────────────────────────────────────────────────────────

    private static void settleElderWand(ServerLevel level, ServerPlayer victim, ServerPlayer victor,
                                        Collection<ItemStack> contested) {
        ElderWandSavedData data = ElderWandSavedData.get(level);
        UUID master = data.getMaster();
        boolean changed = false;
        if (victim.getUUID().equals(master)) {
            data.setMaster(victor.getUUID());
            announceTransfer(victor, victim, "wandcraft.elder.won", "wandcraft.elder.lost");
            changed = true;
        } else if (master == null && isElderWand(victor.getMainHandItem())) {
            // Nobody's wand yet, wielded by a wizard who has just proven themselves the superior.
            data.setMaster(victor.getUUID());
            announceTransfer(victor, null, "wandcraft.elder.chosen.body", "wandcraft.elder.lost");
            changed = true;
        }
        if (!changed) {
            return;
        }
        List<ItemStack> nearby = new ArrayList<>(contested);
        nearby.addAll(heldWands(victim));
        nearby.addAll(heldWands(victor));
        for (int i = 0; i < victor.getInventory().getContainerSize(); i++) {
            nearby.add(victor.getInventory().getItem(i));
        }
        for (int i = 0; i < victim.getInventory().getContainerSize(); i++) {
            nearby.add(victim.getInventory().getItem(i));
        }
        for (ItemStack stack : nearby) {
            reconcileElderStack(stack, data.getMaster());
        }
    }

    /**
     * Brings an Elder Wand stack in line with its saved master. The saved master is authoritative: the stack may
     * have spent the change in a chest. A stack that changes hands this way starts reluctant.
     *
     * @return whether the stack changed
     */
    public static boolean reconcileElderStack(ItemStack stack, @Nullable UUID savedMaster) {
        if (!isElderWand(stack)) {
            return false;
        }
        UUID current = WandComponents.getMaster(stack).orElse(null);
        if (Objects.equals(current, savedMaster)) {
            return false;
        }
        if (savedMaster == null) {
            stack.remove(WandComponents.WAND_MASTER.get());
            stack.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), 0.0f);
            stack.set(WandComponents.WAND_BOND_HISTORY.get(),
                    WandComponents.getBondHistory(stack).afterTransferFrom(current));
            return true;
        }
        stack.set(WandComponents.WAND_MASTER.get(), Optional.of(savedMaster));
        stack.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), WandAllegianceRules.BASE_TRANSFER_BOND);
        stack.set(WandComponents.WAND_BOND_HISTORY.get(), WandComponents.getBondHistory(stack)
                .withFirstMasterIfAbsent(savedMaster)
                .afterTransferFrom(current));
        return true;
    }

    // ── integrity ───────────────────────────────────────────────────────────────────────────────

    /** An explosion hurt a wizard holding a wand: the wand takes some of it (Harry's holly wand, <i>DH</i>). */
    public static void onExplosionDamage(ServerPlayer player, float damage) {
        if (damage < EXPLOSION_DAMAGE_FLOOR) {
            return;
        }
        for (ItemStack wand : heldWands(player)) {
            float before = WandComponents.getIntegrity(wand);
            float after = WandAllegianceRules.integrityAfterExplosion(before, damage);
            if (after == before) {
                continue;
            }
            wand.set(WandComponents.WAND_INTEGRITY.get(), after);
            WandAllegianceRules.Condition was = WandAllegianceRules.condition(before);
            WandAllegianceRules.Condition now = WandAllegianceRules.condition(after);
            if (now != was && (now == WandAllegianceRules.Condition.DAMAGED || now == WandAllegianceRules.Condition.BROKEN)) {
                String key = now == WandAllegianceRules.Condition.BROKEN
                        ? "wandcraft.wand.condition.broken" : "wandcraft.wand.condition.damaged";
                PlayerFeedback.toast(player, NoticeKind.WARN,
                        Component.translatable("wandcraft.wand.condition.title"), Component.translatable(key));
                player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(),
                        SoundSource.PLAYERS, 0.6f, now == WandAllegianceRules.Condition.BROKEN ? 0.6f : 1.1f);
            }
        }
    }

    /**
     * Reparo on a wand. Only the Elder Wand mends one ({@code DH} ch. 36); ordinary magic cannot.
     *
     * @return whether the wand was mended
     */
    public static boolean mendWand(ServerPlayer caster, ItemStack castingWand, ItemStack target) {
        if (!isElderWand(castingWand)) {
            PlayerFeedback.actionBar(caster, Component.translatable("wandcraft.wand.repair.refused"));
            return false;
        }
        if (WandComponents.getIntegrity(target) >= 1.0f) {
            return false;
        }
        target.set(WandComponents.WAND_INTEGRITY.get(), 1.0f);
        PlayerFeedback.actionBar(caster, Component.translatable("wandcraft.wand.repair.elder"));
        caster.level().playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.0f);
        wandMotes(caster, ParticleTypes.END_ROD);
        return true;
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** The wands in an entity's hands, main hand first. */
    public static List<ItemStack> heldWands(LivingEntity entity) {
        List<ItemStack> wands = new ArrayList<>(2);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = entity.getItemInHand(hand);
            if (stack.getItem() instanceof WandItem) {
                wands.add(stack);
            }
        }
        return wands;
    }

    private static void wandMotes(ServerPlayer player, net.minecraft.core.particles.SimpleParticleType type) {
        player.level().sendParticles(type, player.getX(), player.getEyeY() - 0.4, player.getZ(),
                16, 0.3, 0.2, 0.3, 0.02);
    }

    /** Defeats and explosions, from the events the game already raises. */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class Events {
        private Events() {}

        /**
         * A wizard killed by another has been defeated by them. A wizard who dies any other way was defeated by no
         * one — and the Elder Wand's allegiance dies with an undefeated master (Dumbledore's plan).
         */
        @SubscribeEvent
        public static void onDeath(LivingDeathEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer victim)) {
                return;
            }
            if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != victim) {
                onDefeat(victim, killer, DefeatKind.KILL, heldWands(victim));
                return;
            }
            ElderWandSavedData data = ElderWandSavedData.get(victim.level());
            if (victim.getUUID().equals(data.getMaster())) {
                data.setMaster(null);
                for (int i = 0; i < victim.getInventory().getContainerSize(); i++) {
                    reconcileElderStack(victim.getInventory().getItem(i), null);
                }
            }
        }

        /**
         * A wizard stunned by another has been defeated by them — Grindelwald won the Elder Wand by stunning
         * Gregorovitch. Read where a stun actually lands, the Stupefy effect arriving with its caster as source.
         */
        @SubscribeEvent
        public static void onEffectAdded(MobEffectEvent.Added event) {
            if (event.getEntity() instanceof ServerPlayer victim
                    && event.getEffectSource() instanceof ServerPlayer attacker
                    && attacker != victim
                    && event.getEffectInstance().is(ModEffects.STUPEFY)) {
                onDefeat(victim, attacker, DefeatKind.STUN, heldWands(victim));
            }
        }

        @SubscribeEvent
        public static void onDamage(LivingDamageEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player
                    && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
                onExplosionDamage(player, event.getNewDamage());
            }
        }
    }
}
