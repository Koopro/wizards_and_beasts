package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;

public final class ModVillager {
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, WizardsAndBeastsMod.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, WizardsAndBeastsMod.MODID);

    public static final ResourceKey<PoiType> OLLIVANDERS_BENCH_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ollivanders_bench"));

    public static final DeferredHolder<PoiType, PoiType> OLLIVANDERS_BENCH = POI_TYPES.register("ollivanders_bench", () -> {
        ImmutableSet.Builder<BlockState> states = ImmutableSet.builder();
        for (BlockState state : ModBlocks.WANDMAKERS_BENCH.get().getStateDefinition().getPossibleStates()) {
            states.add(state);
        }
        return new PoiType(states.build(), 1, 1);
    });

    public static final DeferredHolder<VillagerProfession, VillagerProfession> WANDMAKER =
            PROFESSIONS.register("wandmaker", () -> new VillagerProfession(
                    Component.translatable("entity.wizards_and_beasts.villager.wandmaker"),
                    h -> h.is(OLLIVANDERS_BENCH_KEY),
                    h -> h.is(OLLIVANDERS_BENCH_KEY),
                    ImmutableSet.of(
                            WandItemRegistry.PHOENIX_FEATHER.get(),
                            WandItemRegistry.DRAGON_HEARTSTRING.get(),
                            WandItemRegistry.UNICORN_HAIR.get(),
                            WandItemRegistry.WAND_BLANK.get()),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN));

    public static final ResourceKey<PoiType> APOTHECARY_CAULDRON_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "apothecary_cauldron"));

    /** Any wizarding cauldron — pewter, copper or brass — is an apothecary's workplace (location: apothecary). */
    public static final DeferredHolder<PoiType, PoiType> APOTHECARY_CAULDRON = POI_TYPES.register("apothecary_cauldron", () -> {
        ImmutableSet.Builder<BlockState> states = ImmutableSet.builder();
        for (var block : java.util.List.of(ModBlocks.PEWTER_CAULDRON, ModBlocks.WIZARDING_COPPER_CAULDRON,
                ModBlocks.BRASS_CAULDRON)) {
            states.addAll(block.get().getStateDefinition().getPossibleStates());
        }
        return new PoiType(states.build(), 1, 1);
    });

    /** The apothecary: buys creature materials, ingredients and brews; sells ingredients; treats creatures. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> APOTHECARY =
            PROFESSIONS.register("apothecary", () -> new VillagerProfession(
                    Component.translatable("entity.wizards_and_beasts.villager.apothecary"),
                    h -> h.is(APOTHECARY_CAULDRON_KEY),
                    h -> h.is(APOTHECARY_CAULDRON_KEY),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_CLERIC));

    public static final ResourceKey<PoiType> MINISTRY_DESK_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ministry_desk"));

    /** An examination desk: the Wizarding Examinations Authority is a Ministry office (location: ministry). */
    public static final DeferredHolder<PoiType, PoiType> MINISTRY_DESK = POI_TYPES.register("ministry_desk", () ->
            new PoiType(ImmutableSet.copyOf(ModBlocks.EXAMINATION_DESK.get().getStateDefinition().getPossibleStates()), 1, 1));

    /** A Ministry clerk: posts Ministry contracts and keeps the Portkey Office. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> MINISTRY_CLERK =
            PROFESSIONS.register("ministry_clerk", () -> new VillagerProfession(
                    Component.translatable("entity.wizards_and_beasts.villager.ministry_clerk"),
                    h -> h.is(MINISTRY_DESK_KEY),
                    h -> h.is(MINISTRY_DESK_KEY),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_CARTOGRAPHER));

    public static final ResourceKey<PoiType> BROOM_RACK_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "broom_rack"));

    /** A broom rack is the broommaker's workplace (location: broom_shop). */
    public static final DeferredHolder<PoiType, PoiType> BROOM_RACK = POI_TYPES.register("broom_rack", () ->
            new PoiType(ImmutableSet.copyOf(ModBlocks.BROOM_RACK.get().getStateDefinition().getPossibleStates()), 1, 1));

    /** The broommaker: sells brooms, polish and twigs; services brooms. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> BROOMMAKER =
            PROFESSIONS.register("broommaker", () -> new VillagerProfession(
                    Component.translatable("entity.wizards_and_beasts.villager.broommaker"),
                    h -> h.is(BROOM_RACK_KEY),
                    h -> h.is(BROOM_RACK_KEY),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_FLETCHER));

    private ModVillager() {
    }
}
